package fr.speedvision.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {
    private val id = VoiceIdentity("live-source", 1, "calibration")

    private fun sample(
        time: Long,
        speed: Double = 18.0,
        identity: VoiceIdentity = id,
        quality: Double = .95,
    ) = VoiceObservation(
        identity,
        SpeedEstimate.Accepted(
            speed / 3.6,
            quality,
            12,
            12,
            1_000_000,
            time * 1_000 - 500_000,
            time * 1_000,
            20.0,
            .01,
        ),
        time,
        true,
    )

    private fun feed(
        policy: VoicePolicy,
        start: Long = 1_000,
        speed: Double = 18.0,
        identity: VoiceIdentity = id,
    ): VoiceDecision {
        var decision: VoiceDecision = VoiceDecision.Silent(VoiceSilence.WARMUP)
        for (time in start..start + 1_000 step 100) decision = policy.evaluate(sample(time, speed, identity), time, true, true)
        return decision
    }

    private fun reason(
        decision: VoiceDecision,
        expected: VoiceSilence,
        stop: Boolean = true,
    ) {
        assertTrue("$decision", decision is VoiceDecision.Silent)
        decision as VoiceDecision.Silent
        assertEquals(expected, decision.reason)
        assertEquals(stop, decision.stopCurrent)
    }

    @Test fun stableLiveResultAnnouncesSignedRelativeSpeed() {
        val policy = VoicePolicy()
        reason(policy.evaluate(sample(900), 900, true, true), VoiceSilence.WARMUP)
        var spoken: VoiceDecision.Speak? = null
        for (time in 1_000L..2_000 step 100) {
            val result = policy.evaluate(sample(time, -18.0), time, true, true)
            if (result is VoiceDecision.Speak) spoken = result
        }
        assertEquals(-18, spoken!!.closingKmh)
        assertEquals("Éloignement relatif, 18 kilomètres par heure.", spoken.text)
    }

    @Test fun burstsAndDuplicateSamplesDoNotCreateStability() {
        val policy = VoicePolicy()
        val observation = sample(1_000)
        reason(policy.evaluate(observation, 1_000, true, true), VoiceSilence.WARMUP)
        repeat(200) { reason(policy.evaluate(observation, 1_000L + it, true, true), VoiceSilence.NO_NEW_SAMPLE, false) }
        reason(policy.evaluate(observation, 1_301, true, true), VoiceSilence.STALE)
    }

    @Test fun cooldownAndDeltaBothRequired() {
        val policy = VoicePolicy()
        assertTrue(feed(policy) is VoiceDecision.Speak)
        reason(feed(policy, 2_100, 25.0), VoiceSilence.INTERVAL, false)
        for (t in 3_200L..6_900 step 100) reason(policy.evaluate(sample(t, 25.0), t, true, true), VoiceSilence.INTERVAL, false)
        assertTrue(policy.evaluate(sample(7_000, 25.0), 7_000, true, true) is VoiceDecision.Speak)
        for (t in 7_100L..11_900 step 100) policy.evaluate(sample(t, 25.0), t, true, true)
        reason(policy.evaluate(sample(12_000, 26.0), 12_000, true, true), VoiceSilence.SMALL_DELTA, false)
    }

    @Test fun trackChangeCancelsAndCannotBypassGlobalCooldown() {
        val policy = VoicePolicy()
        assertTrue(feed(policy) is VoiceDecision.Speak)
        val other = id.copy(trackId = 2)
        reason(policy.evaluate(sample(2_100, identity = other), 2_100, true, true), VoiceSilence.IDENTITY_CHANGED)
        reason(feed(policy, 2_200, identity = other), VoiceSilence.INTERVAL, false)
    }

    @Test fun rejectionLowQualityDisableAndOutputFailureCancelAndReset() {
        val cases = listOf(VoiceSilence.REJECTED, VoiceSilence.INVALID, VoiceSilence.DISABLED, VoiceSilence.OUTPUT_UNAVAILABLE)
        for (expected in cases) {
            val policy = VoicePolicy()
            feed(policy)
            val value =
                when (expected) {
                    VoiceSilence.REJECTED -> sample(2_100).copy(estimate = SpeedEstimate.Rejected(SpeedRejection.TRACK_LOST, 0))
                    VoiceSilence.INVALID -> sample(2_100, quality = .79)
                    else -> sample(2_100)
                }
            reason(policy.evaluate(value, 2_100, expected != VoiceSilence.DISABLED, expected != VoiceSilence.OUTPUT_UNAVAILABLE), expected)
            reason(policy.evaluate(sample(2_200), 2_200, true, true), VoiceSilence.WARMUP)
        }
    }

    @Test fun historicalDataAndAbsentDataNeverSpeak() {
        val policy = VoicePolicy()
        reason(policy.evaluate(sample(1_000).copy(live = false), 1_000, true, true), VoiceSilence.NO_LIVE_DATA)
        reason(policy.evaluate(null, 1_100, true, true), VoiceSilence.NO_LIVE_DATA)
    }

    @Test fun gapsAndReversedClocksReset() {
        val policy = VoicePolicy()
        policy.evaluate(sample(1_000), 1_000, true, true)
        reason(policy.evaluate(sample(1_400), 1_400, true, true), VoiceSilence.GAP)
        reason(policy.evaluate(sample(1_300), 1_300, true, true), VoiceSilence.CLOCK_REVERSED)
        reason(policy.evaluate(sample(1_500), 1_500, true, true), VoiceSilence.WARMUP)
        reason(policy.evaluate(sample(1_400), 1_600, true, true), VoiceSilence.TIME_ORDER)
    }

    @Test fun instabilityFutureAcquisitionAndNonFiniteValuesReject() {
        val policy = VoicePolicy()
        policy.evaluate(sample(1_000), 1_000, true, true)
        reason(policy.evaluate(sample(1_100, 30.0), 1_100, true, true), VoiceSilence.UNSTABLE)
        reason(policy.evaluate(sample(1_300), 1_200, true, true), VoiceSilence.INVALID)
        reason(policy.evaluate(sample(1_300, Double.NaN), 1_300, true, true), VoiceSilence.INVALID)
        reason(policy.evaluate(sample(1_400, quality = Double.NaN), 1_400, true, true), VoiceSilence.INVALID)
    }

    @Test fun duplicateCannotRefreshAgeAndSourceTimestampCannotGoBackwards() {
        val policy = VoicePolicy()
        policy.evaluate(sample(1_000), 1_000, true, true)
        reason(policy.evaluate(sample(1_000).copy(acquiredAtElapsedMs = 1_100), 1_100, true, true), VoiceSilence.TIME_ORDER)
        policy.evaluate(sample(1_200), 1_200, true, true)
        reason(policy.evaluate(sample(1_100).copy(acquiredAtElapsedMs = 1_300), 1_300, true, true), VoiceSilence.TIME_ORDER)
    }

    @Test fun customQualityAndStabilityThresholdsAreApplied() {
        val policy = VoicePolicy(VoiceConfig(minQuality = .99, stableForMs = 200))
        reason(policy.evaluate(sample(1_000), 1_000, true, true), VoiceSilence.INVALID)
        policy.evaluate(sample(1_100, quality = 1.0), 1_100, true, true)
        policy.evaluate(sample(1_200, quality = 1.0), 1_200, true, true)
        assertTrue(policy.evaluate(sample(1_300, quality = 1.0), 1_300, true, true) is VoiceDecision.Speak)
        assertFalse(VoiceObservation(id, sample(1_000).estimate, 1_000).live)
    }

    @Test fun duplicateWithEditedValueCancelsAndCalibrationChangeRestarts() {
        val policy = VoicePolicy()
        policy.evaluate(sample(1_000), 1_000, true, true)
        reason(policy.evaluate(sample(1_000, 30.0), 1_100, true, true), VoiceSilence.TIME_ORDER)
        policy.evaluate(sample(1_200), 1_200, true, true)
        reason(policy.evaluate(sample(1_300, identity = id.copy(calibrationId = "new")), 1_300, true, true), VoiceSilence.IDENTITY_CHANGED)
    }
}
