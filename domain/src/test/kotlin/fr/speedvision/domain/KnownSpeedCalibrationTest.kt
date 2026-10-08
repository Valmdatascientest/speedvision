package fr.speedvision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KnownSpeedCalibrationTest {
    private val binding = CalibrationBinding("camera", 1920, 1080, 0, 0, 1920, 1080, 0)
    private val base =
        CameraCalibration(
            binding,
            1000.0,
            1000.0,
            960.0,
            540.0,
            List(5) { 0.0 },
            "known-speed fixture",
            1.0,
            PlateProfile("plate", .52, .11),
        )

    @Test
    fun fitsWidthScaleAgainstKnownApproachSpeed() {
        val observations =
            (0..12).map { i ->
                DepthObservation("video", 1, "calibration", i * 100_000L, 30.0 - i * .5, .95, true, true, true)
            }
        val result = assertIs<KnownSpeedCalibrationResult.Accepted>(KnownSpeedCalibrationEngine.fit(base, observations, 18.0))
        assertEquals(1.0, result.calibration.vehicleWidthScale, absoluteTolerance = .0001)
        assertTrue(result.quality.measurementsRetained >= 8)
    }

    @Test
    fun rejectsShortOrInvalidReference() {
        val observation = DepthObservation("video", 1, "calibration", 0, 30.0, .95, true, true, true)
        val result = assertIs<KnownSpeedCalibrationResult.Rejected>(KnownSpeedCalibrationEngine.fit(base, listOf(observation), 18.0))
        assertTrue(result.reason.contains("insuffisant", ignoreCase = true))
        val negative = KnownSpeedCalibrationEngine.fit(base, emptyList(), -1.0)
        assertTrue(negative is KnownSpeedCalibrationResult.Rejected)
    }

    @Test
    fun filtersDepthDirectionReversal() {
        val observations =
            (0..12).map { i ->
                val depth = if (i == 6) 34.0 else 30.0 - i * .5
                DepthObservation("video", 1, "calibration", i * 100_000L, depth, .95, true, true, true)
            }
        val result = assertIs<KnownSpeedCalibrationResult.Accepted>(KnownSpeedCalibrationEngine.fit(base, observations, 18.0))
        assertTrue(result.quality.measurementsRetained >= 8)
    }
}
