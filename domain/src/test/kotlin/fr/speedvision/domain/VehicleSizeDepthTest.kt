package fr.speedvision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleSizeDepthTest {
    private val binding = CalibrationBinding("camera", 1920, 1080, 0, 0, 1920, 1080, 0)
    private val calibration =
        CameraCalibration(
            binding,
            1000.0,
            1000.0,
            960.0,
            540.0,
            List(5) { 0.0 },
            "synthetic prior",
            1.0,
            PlateProfile("plate", .52, .11),
        )

    @Test
    fun widthPriorProducesExplicitFallbackDepth() {
        val profile = VehicleSizeProfile(2, 2.0, "voiture")
        val detection = Detection(PixelBox(100f, 200f, 200f, 400f), .9f, 2)
        val estimate = VehicleSizeDepthEstimator.estimate(calibration, detection, profile)
        assertEquals(20.0, estimate?.axialMeters)
        assertEquals(100.0, estimate?.pixelWidth)
    }

    @Test
    fun tinyOrWrongClassDetectionsAreRejected() {
        val profile = VehicleSizeProfile(2, 2.0, "voiture")
        assertNull(
            VehicleSizeDepthEstimator.estimate(
                calibration,
                Detection(PixelBox(100f, 200f, 110f, 400f), .9f, 2),
                profile,
            ),
        )
        assertNull(
            VehicleSizeDepthEstimator.estimate(
                calibration,
                Detection(PixelBox(100f, 200f, 200f, 400f), .9f, 5),
                profile,
            ),
        )
    }
}
