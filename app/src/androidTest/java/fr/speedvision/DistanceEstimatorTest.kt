package fr.speedvision

import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.CameraCalibration
import fr.speedvision.domain.DistanceEstimate
import fr.speedvision.domain.ImagePoint
import fr.speedvision.domain.PlateProfile
import fr.speedvision.geometry.CalibrationJson
import fr.speedvision.geometry.DistanceEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DistanceEstimatorTest {
    private val binding = CalibrationBinding("synthetic-camera", 1920, 1080, 0, 0, 1920, 1080, 0)
    private val calibration =
        CameraCalibration(
            binding,
            1400.0,
            1380.0,
            960.0,
            540.0,
            listOf(.08, -.03, .001, -.002, .01),
            "synthetic only",
            .1,
            PlateProfile("known", .52, .11),
        )

    // Analytic pinhole/Brown projection independent of OpenCV's projectPoints implementation.
    private fun corners(
        z: Double,
        yaw: Double = .35,
        roll: Double = .12,
        c: CameraCalibration = calibration,
    ): List<ImagePoint> =
        listOf(-.26 to -.055, .26 to -.055, .26 to .055, -.26 to .055).map { (x, y) ->
            val rx = cos(yaw) * x
            val rz = -sin(yaw) * x
            val nx = (cos(roll) * rx - sin(roll) * y + .2) / (z + rz)
            val ny = (sin(roll) * rx + cos(roll) * y + .1) / (z + rz)
            val r2 = nx * nx + ny * ny
            val d = c.distortion
            val radial = 1 + d[0] * r2 + d[1] * r2 * r2 + d[4] * r2 * r2 * r2
            ImagePoint(
                c.fx * (nx * radial + 2 * d[2] * nx * ny + d[3] * (r2 + 2 * nx * nx)) + c.cx,
                c.fy * (ny * radial + d[2] * (r2 + 2 * ny * ny) + 2 * d[3] * nx * ny) + c.cy,
            )
        }

    @Test fun recoversMetricDepthWithYawRollAndDistortion() {
        for (z in listOf(2.0, 4.0, 8.0)) {
            val result = DistanceEstimator().estimate(calibration, binding, corners(z))
            assertTrue("$result", result is DistanceEstimate.Accepted)
            assertEquals(z, (result as DistanceEstimate.Accepted).axialMeters, .005)
            assertTrue(result.reprojectionRmsPx < .01)
        }
    }

    @Test fun cropAndNinetyDegreeRotationPreserveDepth() {
        val b = binding.copy(cropLeft = 200, cropTop = 100, width = 1500, height = 800, rotation = 90)
        val upright = corners(4.0).map { ImagePoint(b.height - 1.0 - (it.y - b.cropTop), it.x - b.cropLeft) }
        val result = DistanceEstimator().estimate(calibration.copy(binding = b), b, upright)
        assertTrue("$result", result is DistanceEstimate.Accepted)
        assertEquals(4.0, (result as DistanceEstimate.Accepted).axialMeters, .005)
    }

    @Test fun noiseRemainsBoundedButBadCornersAndWrongSourceAreRejected() {
        val noisy = corners(3.0).mapIndexed { i, p -> ImagePoint(p.x + if (i % 2 == 0) .2 else -.2, p.y + if (i < 2) .1 else -.1) }
        val result = DistanceEstimator().estimate(calibration, binding, noisy)
        assertTrue("$result", result is DistanceEstimate.Accepted)
        assertEquals(3.0, (result as DistanceEstimate.Accepted).axialMeters, .15)
        assertTrue(DistanceEstimator().estimate(calibration, binding.copy(sourceId = "other"), corners(3.0)) is DistanceEstimate.Rejected)
        assertTrue(DistanceEstimator().estimate(calibration, binding, corners(50.0)) is DistanceEstimate.Rejected)
        assertTrue(DistanceEstimator().estimate(calibration, binding, corners(2.0, yaw = 1.3)) is DistanceEstimate.Rejected)
        val bad = corners(2.0).toMutableList().apply { this[2] = ImagePoint(this[2].x + 30, this[2].y + 25) }
        assertTrue(DistanceEstimator().estimate(calibration, binding, bad) is DistanceEstimate.Rejected)
    }

    @Test fun wrongPhysicalWidthChangesScaleAndCannotBeDetectedFromReprojection() {
        val wrong = calibration.copy(plate = PlateProfile("wrong", 1.04, .22))
        val result = DistanceEstimator().estimate(wrong, binding, corners(3.0)) as DistanceEstimate.Accepted
        assertEquals(6.0, result.axialMeters, .01)
        assertTrue(result.reprojectionRmsPx < .01)
    }

    @Test fun jsonRoundTripAndInvalidSchemaNumbersAreRejected() {
        val json = CalibrationJson.encode(calibration)
        assertEquals(calibration, CalibrationJson.decode(json))
        for (invalid in listOf(
            json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            json.replace("\"nativeWidth\": 1920", "\"nativeWidth\": 1920.5"),
            json.replace("\"fx\": 1400", "\"fx\": -1400"),
            json + " trailing",
            "{}",
        )) {
            assertTrue(invalid, runCatching { CalibrationJson.decode(invalid) }.isFailure)
        }
    }
}
