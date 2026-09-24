package fr.speedvision.geometry

import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.CameraCalibration
import fr.speedvision.domain.DistanceEstimate
import fr.speedvision.domain.ImagePoint
import fr.speedvision.domain.selectDistancePose
import fr.speedvision.domain.validatePlateCorners
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Planar IPPE, both solutions inspected. No distance from detection boxes or arbitrary warped widths. */
class DistanceEstimator {
    fun estimate(
        calibration: CameraCalibration,
        binding: CalibrationBinding,
        corners: List<ImagePoint>,
    ): DistanceEstimate {
        if (calibration.binding != binding) return DistanceEstimate.Rejected("Calibration incompatible avec cette source ou ce mode.")
        validatePlateCorners(corners, binding)?.let { return DistanceEstimate.Rejected(it) }
        if (!OpenCVLoader.initLocal()) return DistanceEstimate.Rejected("Moteur géométrique indisponible.")
        val resources = mutableListOf<Mat>()
        val rotations = mutableListOf<Mat>()
        val translations = mutableListOf<Mat>()

        fun <T : Mat> own(mat: T): T {
            resources.add(mat)
            return mat
        }
        try {
            val w = calibration.plate.widthMeters / 2
            val h = calibration.plate.heightMeters / 2
            val world = arrayOf(Point3(-w, -h, 0.0), Point3(w, -h, 0.0), Point3(w, h, 0.0), Point3(-w, h, 0.0))
            val objectPoints = own(MatOfPoint3f(*world))
            val native = corners.map { binding.toNative(it) }
            val imagePoints = own(MatOfPoint2f(*native.map { Point(it.x, it.y) }.toTypedArray()))
            val k = own(Mat.eye(3, 3, CvType.CV_64F))
            k.put(0, 0, calibration.fx)
            k.put(1, 1, calibration.fy)
            k.put(0, 2, calibration.cx)
            k.put(1, 2, calibration.cy)
            val distortion = own(MatOfDouble(*calibration.distortion.toDoubleArray()))
            Calib3d.solvePnPGeneric(objectPoints, imagePoints, k, distortion, rotations, translations, false, Calib3d.SOLVEPNP_IPPE)
            val candidates =
                rotations.indices
                    .mapNotNull { i ->
                        val t = translations[i]
                        val r = own(Mat())
                        Calib3d.Rodrigues(rotations[i], r)
                        val z = t.get(2, 0)[0]
                        val depths = world.map { r.get(2, 0)[0] * it.x + r.get(2, 1)[0] * it.y + z }
                        if (!z.isFinite() || depths.any { !it.isFinite() || it <= 0 }) return@mapNotNull null
                        val projected = own(MatOfPoint2f())
                        Calib3d.projectPoints(objectPoints, rotations[i], t, k, distortion, projected)
                        val error =
                            sqrt(
                                projected.toArray().zip(native).sumOf { (a, b) ->
                                    (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
                                } / 4,
                            )
                        val tilt = Math.toDegrees(acos(abs(r.get(2, 2)[0]).coerceIn(0.0, 1.0)))
                        if (!error.isFinite() || !tilt.isFinite()) null else DistanceEstimate.Accepted(z, error, tilt)
                    }.sortedBy { it.reprojectionRmsPx }
            return selectDistancePose(candidates)
        } catch (_: org.opencv.core.CvException) {
            return DistanceEstimate.Rejected("Pose non résolue : vérifier coins et calibration.")
        } finally {
            (resources + rotations + translations).forEach { it.release() }
        }
    }
}
