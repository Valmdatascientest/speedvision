package fr.speedvision.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** Thresholds apply to a heuristic quality score, not a probability of measurement accuracy. */
data class VoiceConfig(
    val minQuality: Double = .8,
    val stableForMs: Long = 1_000,
    val minIntervalMs: Long = 5_000,
    val minDeltaKmh: Double = 5.0,
    val stabilityRangeKmh: Double = 3.0,
    val maxAgeMs: Long = 300,
    val maxGapMs: Long = 300,
) {
    init {
        require(minQuality in 0.0..1.0)
        require(stableForMs in 100..10_000 && minIntervalMs in 1_000..60_000)
        require(minDeltaKmh.isFinite() && minDeltaKmh > 0)
        require(stabilityRangeKmh.isFinite() && stabilityRangeKmh > 0)
        require(maxAgeMs in 1..1_000 && maxGapMs in 1..1_000)
    }
}

data class VoiceIdentity(
    val sequenceId: String,
    val trackId: Long,
    val calibrationId: String,
)

/** acquiredAtElapsedMs must share the caller's monotonic clock. Imported/historical data stays non-live. */
data class VoiceObservation(
    val identity: VoiceIdentity,
    val estimate: SpeedEstimate,
    val acquiredAtElapsedMs: Long,
    val live: Boolean = false,
)

enum class VoiceSilence {
    DISABLED,
    OUTPUT_UNAVAILABLE,
    NO_LIVE_DATA,
    REJECTED,
    INVALID,
    STALE,
    CLOCK_REVERSED,
    TIME_ORDER,
    IDENTITY_CHANGED,
    GAP,
    WARMUP,
    UNSTABLE,
    NO_NEW_SAMPLE,
    INTERVAL,
    SMALL_DELTA,
}

sealed interface VoiceDecision {
    /** The adapter must stop an in-flight utterance when stopCurrent is true. No speech queue. */
    data class Silent(
        val reason: VoiceSilence,
        val stopCurrent: Boolean = true,
    ) : VoiceDecision

    data class Speak(
        val identity: VoiceIdentity,
        val closingKmh: Int,
        val text: String,
    ) : VoiceDecision
}

/**
 * Serial, deterministic policy. Call on every result, rejection, output failure and lifecycle change;
 * also tick with the latest observation to expire it when frames stop arriving.
 * A Speak reserves the cooldown immediately, even if an audio adapter subsequently fails.
 */
class VoicePolicy(
    val config: VoiceConfig = VoiceConfig(),
) {
    private var identity: VoiceIdentity? = null
    private var lastNow: Long? = null
    private var lastPts: Long? = null
    private var lastAcquired: Long? = null
    private var lastEstimate: SpeedEstimate.Accepted? = null
    private var stableSince = 0L
    private var stableCount = 0
    private var minimum = 0.0
    private var maximum = 0.0
    private var lastSpokenAt: Long? = null
    private var lastSpokenIdentity: VoiceIdentity? = null
    private var lastSpokenKmh = 0.0

    private fun clearEvidence() {
        identity = null
        lastPts = null
        lastAcquired = null
        lastEstimate = null
        stableCount = 0
    }

    private fun silence(reason: VoiceSilence): VoiceDecision.Silent {
        clearEvidence()
        return VoiceDecision.Silent(reason)
    }

    fun evaluate(
        observation: VoiceObservation?,
        nowElapsedMs: Long,
        enabled: Boolean,
        outputReady: Boolean,
    ): VoiceDecision {
        val previousNow = lastNow
        lastNow = nowElapsedMs
        if (nowElapsedMs < 0 || (previousNow != null && nowElapsedMs < previousNow)) return silence(VoiceSilence.CLOCK_REVERSED)
        if (!enabled) return silence(VoiceSilence.DISABLED)
        if (!outputReady) return silence(VoiceSilence.OUTPUT_UNAVAILABLE)
        if (observation == null || !observation.live) return silence(VoiceSilence.NO_LIVE_DATA)
        val estimate = observation.estimate as? SpeedEstimate.Accepted ?: return silence(VoiceSilence.REJECTED)
        val acquired = observation.acquiredAtElapsedMs
        val id = observation.identity
        if (acquired < 0 ||
            acquired > nowElapsedMs ||
            estimate.latestTimeUs < 0 ||
            id.sequenceId.isBlank() ||
            id.calibrationId.isBlank() ||
            id.trackId < 0 ||
            !estimate.closingKmh.isFinite() ||
            abs(estimate.closingKmh) > 360 ||
            estimate.qualityScore !in config.minQuality..1.0
        ) {
            return silence(VoiceSilence.INVALID)
        }
        if (nowElapsedMs - acquired > config.maxAgeMs) return silence(VoiceSilence.STALE)
        val oldPts = lastPts
        val oldAcquired = lastAcquired
        val changed = identity != null && identity != id
        if (changed) clearEvidence()
        if (!changed && oldPts != null && oldAcquired != null) {
            if (estimate.latestTimeUs < oldPts || acquired < oldAcquired) return silence(VoiceSilence.TIME_ORDER)
            if (estimate.latestTimeUs == oldPts) {
                // A timer or duplicated result must never advance stability or refresh its acquisition time.
                if (acquired != oldAcquired || estimate != lastEstimate) return silence(VoiceSilence.TIME_ORDER)
                return VoiceDecision.Silent(VoiceSilence.NO_NEW_SAMPLE, false)
            }
            if (acquired == oldAcquired) return silence(VoiceSilence.TIME_ORDER)
        }
        val gap =
            !changed &&
                oldAcquired != null &&
                (acquired - oldAcquired > config.maxGapMs || estimate.latestTimeUs - (oldPts ?: 0) > config.maxGapMs * 1_000)
        val speed = estimate.closingKmh
        val unstable = stableCount > 0 && maxOf(maximum, speed) - minOf(minimum, speed) > config.stabilityRangeKmh
        if (stableCount == 0 || gap || unstable) {
            minimum = speed
            maximum = speed
            stableSince = acquired
            stableCount = 0
        }
        minimum = minOf(minimum, speed)
        maximum = maxOf(maximum, speed)
        stableCount = (stableCount + 1).coerceAtMost(3)
        identity = id
        lastPts = estimate.latestTimeUs
        lastAcquired = acquired
        lastEstimate = estimate
        if (changed) return VoiceDecision.Silent(VoiceSilence.IDENTITY_CHANGED)
        if (gap) return VoiceDecision.Silent(VoiceSilence.GAP)
        if (unstable) return VoiceDecision.Silent(VoiceSilence.UNSTABLE)
        if (stableCount < 3 || acquired - stableSince < config.stableForMs) return VoiceDecision.Silent(VoiceSilence.WARMUP)
        val spoken = lastSpokenAt
        if (spoken != null && nowElapsedMs - spoken < config.minIntervalMs) return VoiceDecision.Silent(VoiceSilence.INTERVAL, false)
        if (lastSpokenIdentity == id && abs(speed - lastSpokenKmh) < config.minDeltaKmh) {
            return VoiceDecision.Silent(VoiceSilence.SMALL_DELTA, false)
        }
        lastSpokenAt = nowElapsedMs
        lastSpokenIdentity = id
        lastSpokenKmh = speed
        val rounded = speed.roundToInt()
        val text =
            when {
                rounded > 0 -> "Rapprochement relatif, $rounded kilomètres par heure."
                rounded < 0 -> "Éloignement relatif, ${abs(rounded)} kilomètres par heure."
                else -> "Vitesse relative proche de zéro."
            }
        return VoiceDecision.Speak(id, rounded, text)
    }
}
