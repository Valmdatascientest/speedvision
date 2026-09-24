package fr.speedvision.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CalibrationTest {
    private val binding = CalibrationBinding("test", 1280, 960, 100, 50, 800, 600, 0)
    private val plate = PlateProfile("measured", .52, .11)

    private fun profile() = CameraCalibration(binding, 1000.0, 1000.0, 640.0, 480.0, List(5) { 0.0 }, "test", .3, plate)

    @Test fun cropAndAllRotationsReturnSameNativePixel() {
        val native = ImagePoint(120.0, 80.0)
        assertEquals(native, binding.toNative(ImagePoint(20.0, 30.0)))
        assertEquals(native, binding.copy(rotation = 90).toNative(ImagePoint(569.0, 20.0)))
        assertEquals(native, binding.copy(rotation = 180).toNative(ImagePoint(779.0, 569.0)))
        assertEquals(native, binding.copy(rotation = 270).toNative(ImagePoint(30.0, 779.0)))
        assertEquals(600, binding.copy(rotation = 90).uprightWidth)
    }

    @Test fun calibrationRejectsNonFiniteAndIncompatibleParameters() {
        assertFailsWith<IllegalArgumentException> { profile().copy(fx = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { profile().copy(fy = -1.0) }
        assertFailsWith<IllegalArgumentException> { profile().copy(cx = 1280.0) }
        assertFailsWith<IllegalArgumentException> { profile().copy(distortion = listOf(0.0)) }
        assertFailsWith<IllegalArgumentException> { profile().copy(validationRmsPx = 3.0) }
        assertFailsWith<IllegalArgumentException> { binding.copy(cropLeft = 900) }
        assertFailsWith<IllegalArgumentException> { binding.copy(rotation = 45) }
        assertFailsWith<IllegalArgumentException> { plate.copy(widthMeters = .01) }
    }

    @Test fun cornersRejectCrossingWindingSmallOutOfFrameAndCollinear() {
        val points = listOf(ImagePoint(100.0, 100.0), ImagePoint(300.0, 100.0), ImagePoint(300.0, 150.0), ImagePoint(100.0, 150.0))
        assertNull(validatePlateCorners(points, binding))
        assertNotNull(validatePlateCorners(points.reversed(), binding))
        assertNotNull(validatePlateCorners(listOf(points[0], points[2], points[1], points[3]), binding))
        assertNotNull(validatePlateCorners(points.map { ImagePoint(it.x / 10, it.y / 10) }, binding))
        assertNotNull(validatePlateCorners(points.map { ImagePoint(it.x + 800, it.y) }, binding))
        assertNotNull(validatePlateCorners(points.map { ImagePoint(it.x, 100.0) }, binding))
        assertNotNull(validatePlateCorners(points.take(3), binding))
    }

    @Test fun poseSelectionRejectsAmbiguityNegativeDepthAndBadReprojection() {
        val best = DistanceEstimate.Accepted(5.0, .1, 20.0)
        assertEquals(best, selectDistancePose(listOf(best, best.copy(reprojectionRmsPx = .2))))
        assertEquals(best, selectDistancePose(listOf(best, best.copy(axialMeters = 6.0, reprojectionRmsPx = 1.0))))
        for (poses in listOf(
            emptyList(),
            listOf(best.copy(axialMeters = -1.0)),
            listOf(best.copy(axialMeters = Double.NaN)),
            listOf(best.copy(reprojectionRmsPx = 2.1)),
            listOf(best.copy(tiltDegrees = 66.0)),
            listOf(best, best.copy(axialMeters = 6.0)),
            listOf(best, best.copy(tiltDegrees = 30.0)),
        )) {
            kotlin.test.assertTrue(selectDistancePose(poses) is DistanceEstimate.Rejected)
        }
    }
}
