package fr.speedvision.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpeedEstimatorTest {
    private fun sample(
        t: Long,
        z: Double = 30 - 5 * t / 1e6,
    ) = DepthObservation("sequence", 1, "cal-v1", t, z, .95, true, true, true)

    private fun warm(
        estimator: SpeedEstimator = SpeedEstimator(),
        offset: Long = 0,
    ): SpeedEstimate {
        var result: SpeedEstimate = SpeedEstimate.Rejected(SpeedRejection.NO_DATA, 0)
        repeat(13) { result = estimator.add(sample(offset + it * 100_000, 30 - it * .5)) }
        return result
    }

    @Test fun constantApproachRecessionAndStationaryHaveCorrectUnitsAndReference() {
        for (speed in listOf(-5.0, 0.0, 5.0)) {
            val tracker = SpeedEstimator()
            var result: SpeedEstimate? = null
            repeat(13) { result = tracker.add(sample(it * 100_000L, 30 - speed * it / 10)) }
            val accepted = result as SpeedEstimate.Accepted
            assertEquals(speed, accepted.closingMps, 1e-8)
            assertEquals(speed * 3.6, accepted.closingKmh, 1e-8)
            assertEquals(600_000, accepted.referenceTimeUs)
            assertEquals(1_200_000, accepted.latestTimeUs)
            assertTrue(accepted.qualityScore in .6..1.0)
        }
    }

    @Test fun epochSizeTimestampsAndIrregularSamplingAreHandledWithoutPrecisionLoss() {
        val origin = 8_000_000_000_000_000L
        val estimator = SpeedEstimator()
        var result: SpeedEstimate? = null
        for (t in listOf(0L, 80_000, 190_000, 310_000, 420_000, 570_000, 680_000, 810_000, 950_000, 1_100_000)) {
            result = estimator.add(sample(origin + t, 30 - 5 * t / 1e6))
        }
        assertEquals(5.0, (result as SpeedEstimate.Accepted).closingMps, 1e-7)
    }

    @Test fun isolatedOutlierIsSuppressedAndLatestOutlierIsNotShownAsFreshSpeed() {
        val estimator = SpeedEstimator()
        var result: SpeedEstimate? = null
        repeat(13) {
            result = estimator.add(sample(it * 100_000L, 30 - it * .5 + if (it == 4) 8 else 0))
        }
        assertEquals(5.0, (result as SpeedEstimate.Accepted).closingMps, .05)
        val latest = estimator.add(sample(1_300_000, 50.0))
        assertTrue(latest is SpeedEstimate.Rejected)
    }

    @Test fun moderateAccelerationIsReportedAtWindowCenterNotAsInstantaneousSpeed() {
        val estimator = SpeedEstimator()
        var result: SpeedEstimate? = null
        repeat(13) {
            val t = it / 10.0
            result = estimator.add(sample(it * 100_000L, 30 - 3 * t - .5 * t * t))
        }
        assertEquals(3.6, (result as SpeedEstimate.Accepted).closingMps, .05)
    }

    @Test fun strongCurvatureAndContaminatedWindowsAreRejected() {
        val estimator = SpeedEstimator()
        var result: SpeedEstimate? = null
        repeat(13) {
            val t = it / 10.0
            result = estimator.add(sample(it * 100_000L, 50 - 3 * t - 10 * t * t))
        }
        assertTrue(result is SpeedEstimate.Rejected)
        estimator.reset()
        repeat(13) { result = estimator.add(sample(it * 100_000L, 30 - it * .5 + if (it % 2 == 0) 3 else -3)) }
        assertTrue(result is SpeedEstimate.Rejected)
    }

    @Test fun gapDuplicateBackwardAndStalenessInvalidatePreviousResults() {
        for (t in listOf(1_200_000L, 1_000_000L, 1_600_001L)) {
            val estimator = SpeedEstimator()
            warm(estimator)
            assertTrue(estimator.add(sample(t)) is SpeedEstimate.Rejected)
            assertTrue(estimator.sampleCount <= 1)
        }
        val estimator = SpeedEstimator()
        warm(estimator)
        assertEquals(SpeedRejection.STALE, (estimator.estimate(1_500_001) as SpeedEstimate.Rejected).reason)
        assertEquals(0, estimator.sampleCount)
    }

    @Test fun everyIdentityAndQualityBoundaryClearsHistory() {
        val next = sample(1_300_000)
        for (bad in listOf(
            next.copy(sequenceId = "other"),
            next.copy(trackId = 2),
            next.copy(calibrationId = "cal-v2"),
            next.copy(trackObserved = false),
            next.copy(cameraFixed = false),
            next.copy(geometryValid = false),
            next.copy(quality = .1),
            next.copy(depthMeters = Double.NaN),
            next.copy(depthMeters = -1.0),
        )) {
            val estimator = SpeedEstimator()
            warm(estimator)
            assertTrue(estimator.add(bad) is SpeedEstimate.Rejected)
            assertTrue(estimator.sampleCount <= 1)
        }
    }

    @Test fun windowsAreBoundedAndNoStateIsSharedBetweenInstances() {
        val a = SpeedEstimator()
        val b = SpeedEstimator()
        repeat(200) { a.add(sample(it * 100_000L, 200 - it * .5)) }
        assertEquals(13, a.sampleCount)
        assertEquals(0, b.sampleCount)
        a.reset()
        assertEquals(0, a.sampleCount)
        val highRate = SpeedEstimator()
        repeat(500) { highRate.add(sample(it * 1000L)) }
        assertEquals(64, highRate.sampleCount)
    }

    @Test fun warmupRequiresEnoughObservationsAndDurationAndConfigurationIsValidated() {
        val estimator = SpeedEstimator()
        repeat(7) { assertTrue(estimator.add(sample(it * 100_000L)) is SpeedEstimate.Rejected) }
        assertTrue(estimator.add(sample(700_000)) is SpeedEstimate.Accepted)
        assertFailsWith<IllegalArgumentException> { SpeedConfig(maxGapUs = 0) }
        assertFailsWith<IllegalArgumentException> { SpeedConfig(maxSpeedMps = Double.NaN) }
    }

    @Test fun csvRoundTripHandlesQuotesAndRejectsMalformedOrUnsafeIds() {
        val observations = listOf(sample(0).copy(sequenceId = "sequence,\"one\""), sample(100_000))
        assertEquals(observations, SpeedCsv.decode(SpeedCsv.encodeObservations(observations)))
        assertEquals(observations, SpeedCsv.decode(SpeedCsv.encodeObservations(observations).replace("\n", "\r\n")))
        for (bad in listOf(
            "",
            "a,b\n1,2",
            SpeedCsv.encodeObservations(observations).replace("true", "yes"),
            SpeedCsv.encodeObservations(listOf(sample(0).copy(sequenceId = "=formula"))),
            SpeedCsv.encodeObservations(listOf(sample(0).copy(depthMeters = Double.NaN))),
            "\"unterminated",
        )) {
            assertTrue(runCatching { SpeedCsv.decode(bad) }.isFailure)
        }
    }

    @Test fun replayCsvNeverExportsOldAcceptedSpeedForRejectedRows() {
        val rows = SpeedReplay.run((0..12).map { sample(it * 100_000L) } + sample(1_300_000).copy(cameraFixed = false))
        assertTrue(rows[12].estimate is SpeedEstimate.Accepted)
        assertTrue(rows.last().estimate is SpeedEstimate.Rejected)
        val csv = SpeedCsv.encodeResults(rows)
        assertTrue(csv.contains("CAMERA_MOVING_OR_UNKNOWN"))
        assertTrue(
            csv
                .trim()
                .lineSequence()
                .last()
                .contains("\"CAMERA_MOVING_OR_UNKNOWN\",\"\",\"\",\"\""),
        )
        assertTrue(rows.all { it.computeMillis >= 0 })
    }

    @Test fun allBenchmarkBaselinesRecoverAConstantSignedSlope() {
        val x = DoubleArray(13) { (it - 6) / 10.0 }
        val y = DoubleArray(13) { 30 - 5 * x[it] }
        for (method in SpeedBenchmark.Method.entries) {
            assertEquals(5.0, SpeedBenchmark.fit(method, x, y).first, .01, method.name)
        }
    }
}
