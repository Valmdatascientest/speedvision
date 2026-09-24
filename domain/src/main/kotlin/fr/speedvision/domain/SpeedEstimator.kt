package fr.speedvision.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Metric observations, never detector-box widths. IDs cover source/session and full calibration. */
data class DepthObservation(
    val sequenceId: String,
    val trackId: Long,
    val calibrationId: String,
    val timestampUs: Long,
    val depthMeters: Double,
    val quality: Double,
    val geometryValid: Boolean,
    val cameraFixed: Boolean,
    val trackObserved: Boolean,
)

data class SpeedConfig(
    val windowUs: Long = 1_200_000,
    val minSpanUs: Long = 700_000,
    val maxGapUs: Long = 300_000,
    val minSamples: Int = 8,
    val maxSamples: Int = 64,
    val minInputQuality: Double = .5,
    val minOutputQuality: Double = .6,
    val maxResidualMeters: Double = .25,
    val maxSpeedMps: Double = 100.0,
    val maxHalfSlopeDifferenceMps: Double = 4.0,
) {
    init {
        require(windowUs in 700_000..5_000_000 && minSpanUs in 1..windowUs && maxGapUs in 1..windowUs)
        require(minSamples in 3..maxSamples && maxSamples in 3..128)
        require(minInputQuality in 0.0..1.0 && minOutputQuality in 0.0..1.0)
        require(listOf(maxResidualMeters, maxSpeedMps, maxHalfSlopeDifferenceMps).all { it.isFinite() && it > 0 })
    }
}

enum class SpeedRejection {
    NO_DATA,
    INVALID_INPUT,
    GEOMETRY,
    CAMERA_MOVING_OR_UNKNOWN,
    TRACK_LOST,
    LOW_INPUT_QUALITY,
    TIME_ORDER,
    GAP,
    IDENTITY_CHANGED,
    WARMUP,
    INSUFFICIENT_INLIERS,
    LATEST_OUTLIER,
    RESIDUALS,
    NON_LINEAR_MOTION,
    LOW_OUTPUT_QUALITY,
    OUT_OF_RANGE,
    STALE,
}

sealed interface SpeedEstimate {
    data class Accepted(
        val closingMps: Double,
        val qualityScore: Double,
        val sampleCount: Int,
        val inlierCount: Int,
        val spanUs: Long,
        val referenceTimeUs: Long,
        val latestTimeUs: Long,
        val depthAtReferenceMeters: Double,
        val residualRmsMeters: Double,
    ) : SpeedEstimate {
        val closingKmh get() = closingMps * 3.6
    }

    data class Rejected(
        val reason: SpeedRejection,
        val sampleCount: Int,
    ) : SpeedEstimate
}

/** One active identity per instance. Call serially. Quality is a heuristic, never accuracy probability. */
class SpeedEstimator(
    val config: SpeedConfig = SpeedConfig(),
) {
    private val samples = ArrayDeque<DepthObservation>()
    val sampleCount get() = samples.size

    fun reset() {
        samples.clear()
    }

    fun add(observation: DepthObservation): SpeedEstimate {
        val invalid =
            when {
                observation.timestampUs < 0 ||
                    observation.trackId < 0 ||
                    observation.sequenceId.isBlank() ||
                    observation.calibrationId.isBlank() ||
                    !observation.depthMeters.isFinite() ||
                    observation.depthMeters !in .05..10_000.0 ||
                    !observation.quality.isFinite() ||
                    observation.quality !in 0.0..1.0 -> SpeedRejection.INVALID_INPUT
                !observation.trackObserved -> SpeedRejection.TRACK_LOST
                !observation.geometryValid -> SpeedRejection.GEOMETRY
                !observation.cameraFixed -> SpeedRejection.CAMERA_MOVING_OR_UNKNOWN
                observation.quality < config.minInputQuality -> SpeedRejection.LOW_INPUT_QUALITY
                else -> null
            }
        if (invalid != null) {
            reset()
            return SpeedEstimate.Rejected(invalid, 0)
        }
        val previous = samples.lastOrNull()
        var transition: SpeedRejection? = null
        if (previous != null) {
            if (observation.sequenceId != previous.sequenceId ||
                observation.trackId != previous.trackId ||
                observation.calibrationId != previous.calibrationId
            ) {
                reset()
                transition = SpeedRejection.IDENTITY_CHANGED
            } else if (observation.timestampUs <= previous.timestampUs) {
                reset()
                return SpeedEstimate.Rejected(SpeedRejection.TIME_ORDER, 0)
            } else if (observation.timestampUs - previous.timestampUs > config.maxGapUs) {
                reset()
                transition = SpeedRejection.GAP
            }
        }
        samples.addLast(observation)
        while (samples.size > config.maxSamples || observation.timestampUs - samples.first().timestampUs > config.windowUs) {
            samples.removeFirst()
        }
        return transition?.let { SpeedEstimate.Rejected(it, samples.size) } ?: estimate(observation.timestampUs)
    }

    fun estimate(nowUs: Long): SpeedEstimate {
        val latest = samples.lastOrNull() ?: return SpeedEstimate.Rejected(SpeedRejection.NO_DATA, 0)
        if (nowUs < latest.timestampUs || nowUs - latest.timestampUs > config.maxGapUs) {
            reset()
            return SpeedEstimate.Rejected(SpeedRejection.STALE, 0)
        }
        val span = latest.timestampUs - samples.first().timestampUs
        if (samples.size < config.minSamples || span < config.minSpanUs) return rejected(SpeedRejection.WARMUP)
        val reference = samples.first().timestampUs + span / 2
        val x = samples.map { (it.timestampUs - reference) / 1e6 }.toDoubleArray()
        val y = samples.map { it.depthMeters }.toDoubleArray()
        val weights = samples.map { it.quality }.toDoubleArray()
        val fit = TemporalRegression.huber(x, y, weights)
        val residuals = x.indices.map { y[it] - fit.value(x[it]) }
        val residualCenter = TemporalRegression.median(residuals)
        val scale = max(.02, 1.4826 * TemporalRegression.median(residuals.map { abs(it - residualCenter) }))
        val gate = max(.06, minOf(config.maxResidualMeters * 2, 3 * scale))
        val inliers = x.indices.filter { abs(residuals[it]) <= gate }
        if (inliers.size < config.minSamples ||
            inliers.size.toDouble() / x.size < .7 ||
            (samples[inliers.last()].timestampUs - samples[inliers.first()].timestampUs) < config.minSpanUs
        ) {
            return rejected(SpeedRejection.INSUFFICIENT_INLIERS)
        }
        if (abs(residuals.last()) > gate) return rejected(SpeedRejection.LATEST_OUTLIER)
        val rms = sqrt(inliers.sumOf { residuals[it] * residuals[it] } / inliers.size)
        if (rms > config.maxResidualMeters) return rejected(SpeedRejection.RESIDUALS)
        val half = x.size / 2
        val left = TemporalRegression.huber(x.copyOfRange(0, half), y.copyOfRange(0, half), weights.copyOfRange(0, half))
        val right = TemporalRegression.huber(x.copyOfRange(half, x.size), y.copyOfRange(half, x.size), weights.copyOfRange(half, x.size))
        if (abs(left.slope - right.slope) > config.maxHalfSlopeDifferenceMps) return rejected(SpeedRejection.NON_LINEAR_MOTION)
        val speed = -fit.slope
        if (!speed.isFinite() || abs(speed) > config.maxSpeedMps || fit.intercept <= 0) return rejected(SpeedRejection.OUT_OF_RANGE)
        val meanQuality = inliers.sumOf { weights[it] } / inliers.size
        val quality = meanQuality * (inliers.size.toDouble() / x.size) / (1 + rms / config.maxResidualMeters)
        if (quality < config.minOutputQuality) return rejected(SpeedRejection.LOW_OUTPUT_QUALITY)
        return SpeedEstimate.Accepted(speed, quality, x.size, inliers.size, span, reference, latest.timestampUs, fit.intercept, rms)
    }

    private fun rejected(reason: SpeedRejection) = SpeedEstimate.Rejected(reason, samples.size)

    companion object {
        const val ALGORITHM_VERSION = "huber-depth-v1"
    }
}
