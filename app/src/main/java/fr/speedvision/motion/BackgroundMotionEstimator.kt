package fr.speedvision.motion

import android.graphics.Bitmap
import fr.speedvision.domain.ImagePoint
import fr.speedvision.domain.PixelBox
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Image-plane alignment only. A homography cannot certify a stationary camera or metric translation. */
data class BackgroundMotion(
    val reason: String,
    val previousToCurrent: List<Double>? = null,
    val inliers: Int = 0,
    val candidates: Int = 0,
    val occupiedCells: Int = 0,
    val residualP95Px: Double = 0.0,
    val computeMillis: Double = 0.0,
) {
    init {
        require(previousToCurrent == null || (previousToCurrent.size == 9 && previousToCurrent.all { it.isFinite() }))
    }

    fun predict(point: ImagePoint): ImagePoint? {
        val h = previousToCurrent ?: return null
        val z = h[6] * point.x + h[7] * point.y + h[8]
        if (!z.isFinite() || kotlin.math.abs(z) < 1e-9) return null
        val x = (h[0] * point.x + h[1] * point.y + h[2]) / z
        val y = (h[3] * point.x + h[4] * point.y + h[5]) / z
        return if (x.isFinite() && y.isFinite()) ImagePoint(x, y) else null
    }

    /** Residual relative to the background, in upright pixels; never metres or vehicle speed. */
    fun residual(
        previous: ImagePoint,
        current: ImagePoint,
    ): ImagePoint? = predict(previous)?.let { ImagePoint(current.x - it.x, current.y - it.y) }
}

/** Serial coroutine ownership: close after processing completes; no shared native Mat with the UI. */
class BackgroundMotionEstimator : AutoCloseable {
    private var gray: Mat? = null
    private var mask: Mat? = null
    private var key: String? = null
    private var timestampUs = -1L

    override fun close() {
        gray?.release()
        mask?.release()
        gray = null
        mask = null
        key = null
    }

    fun process(
        bitmap: Bitmap,
        boxes: List<PixelBox>?,
        ptsUs: Long,
        identity: String,
    ): BackgroundMotion {
        val started = System.nanoTime()
        if (boxes == null || boxes.size >= 30 || ptsUs < 0) {
            close()
            return BackgroundMotion("Masque indisponible ou saturé")
        }
        if (!OpenCVLoader.initLocal()) return BackgroundMotion("OpenCV indisponible")
        val resources = mutableListOf<Mat>()

        fun <T : Mat> own(value: T): T {
            resources.add(value)
            return value
        }
        var nextGray: Mat? = null
        var nextMask: Mat? = null
        try {
            val scale = minOf(1.0, 640.0 / max(bitmap.width, bitmap.height))
            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val sx = width.toDouble() / bitmap.width
            val sy = height.toDouble() / bitmap.height
            val rgba = own(Mat())
            val small = own(Mat())
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.resize(rgba, small, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            val current = Mat().also { nextGray = it }
            Imgproc.cvtColor(small, current, Imgproc.COLOR_RGBA2GRAY)
            val allowed = Mat(height, width, CvType.CV_8UC1, Scalar(255.0)).also { nextMask = it }
            // Exclude the LK support around both old and new vehicle boxes, including their edges.
            boxes.forEach {
                Imgproc.rectangle(
                    allowed,
                    Point(it.left * sx - 20, it.top * sy - 20),
                    Point(it.right * sx + 20, it.bottom * sy + 20),
                    Scalar(0.0),
                    -1,
                )
            }
            val frameKey = "$identity:${bitmap.width}:${bitmap.height}"
            val previous = gray
            val result =
                when {
                    previous == null || key != frameKey -> BackgroundMotion("Initialisation du fond")
                    ptsUs <= timestampUs || ptsUs - timestampUs > 300_000 -> BackgroundMotion("Rupture des horodatages")
                    else -> {
                        val corners = own(MatOfPoint())
                        Imgproc.goodFeaturesToTrack(previous, corners, 250, .01, 10.0, mask)
                        if (corners.rows() < 30) {
                            BackgroundMotion("Fond insuffisamment texturé")
                        } else {
                            val p0 = own(MatOfPoint2f(*corners.toArray()))
                            val p1 = own(MatOfPoint2f())
                            val back = own(MatOfPoint2f())
                            val forwardStatus = own(MatOfByte())
                            val backwardStatus = own(MatOfByte())
                            val forwardError = own(MatOfFloat())
                            val backwardError = own(MatOfFloat())
                            Video.calcOpticalFlowPyrLK(previous, current, p0, p1, forwardStatus, forwardError)
                            Video.calcOpticalFlowPyrLK(current, previous, p1, back, backwardStatus, backwardError)
                            val a = p0.toArray()
                            val b = p1.toArray()
                            val c = back.toArray()
                            val fs = forwardStatus.toArray()
                            val bs = backwardStatus.toArray()
                            val errors = forwardError.toArray()
                            val valid =
                                a.indices.filter { i ->
                                    val q = b[i]
                                    fs[i].toInt() != 0 &&
                                        bs[i].toInt() != 0 &&
                                        errors[i] < 30 &&
                                        hypot(a[i].x - c[i].x, a[i].y - c[i].y) <= 1 &&
                                        q.x >= 0 &&
                                        q.y >= 0 &&
                                        q.x < width &&
                                        q.y < height &&
                                        allowed.get(q.y.toInt(), q.x.toInt())[0] > 0
                                }
                            if (valid.size < 30) {
                                BackgroundMotion("Flot insuffisant", candidates = valid.size)
                            } else {
                                val from = own(MatOfPoint2f(*valid.map { a[it] }.toTypedArray()))
                                val to = own(MatOfPoint2f(*valid.map { b[it] }.toTypedArray()))
                                val consensus = own(Mat())
                                val h = own(Calib3d.findHomography(from, to, Calib3d.RANSAC, 1.5, consensus, 1000, .995))
                                if (h.empty()) {
                                    BackgroundMotion("Transformation indéterminée")
                                } else {
                                    val values = DoubleArray(9)
                                    h.get(0, 0, values)
                                    val native =
                                        listOf(
                                            values[0],
                                            values[1] * sy / sx,
                                            values[2] / sx,
                                            values[3] * sx / sy,
                                            values[4],
                                            values[5] / sy,
                                            values[6] * sx,
                                            values[7] * sy,
                                            values[8],
                                        )
                                    val fit = BackgroundMotion("Alignement visuel uniquement", native)
                                    val accepted = valid.indices.filter { consensus.get(it, 0)[0] > 0 }
                                    val cells =
                                        accepted
                                            .map { a[valid[it]] }
                                            .map {
                                                (it.x * 4 / width).toInt().coerceIn(0, 3) + 4 * (it.y * 4 / height).toInt().coerceIn(0, 3)
                                            }.toSet()
                                            .size
                                    val residuals =
                                        valid
                                            .map { i ->
                                                val predicted = fit.predict(ImagePoint(a[i].x / sx, a[i].y / sy))
                                                if (predicted == null) {
                                                    Double.POSITIVE_INFINITY
                                                } else {
                                                    hypot(predicted.x * sx - b[i].x, predicted.y * sy - b[i].y)
                                                }
                                            }.sorted()
                                    val p95 = residuals[(.95 * (residuals.size - 1)).roundToInt()]
                                    val cornersValid =
                                        listOf(
                                            ImagePoint(0.0, 0.0),
                                            ImagePoint(bitmap.width.toDouble(), 0.0),
                                            ImagePoint(0.0, bitmap.height.toDouble()),
                                            ImagePoint(bitmap.width.toDouble(), bitmap.height.toDouble()),
                                        ).all { p ->
                                            fit.predict(p)?.let {
                                                hypot(it.x - p.x, it.y - p.y) <
                                                    .3 * hypot(bitmap.width.toDouble(), bitmap.height.toDouble())
                                            } ==
                                                true
                                        }
                                    val reliable =
                                        accepted.size >= 30 && accepted.size >= .85 * valid.size && cells >= 8 && p95 <= 1.5 && cornersValid
                                    BackgroundMotion(
                                        if (reliable) fit.reason else "Fond incohérent ou couverture insuffisante",
                                        if (reliable) native else null,
                                        accepted.size,
                                        valid.size,
                                        cells,
                                        p95,
                                    )
                                }
                            }
                        }
                    }
                }
            close()
            gray = current
            mask = allowed
            key = frameKey
            timestampUs = ptsUs
            nextGray = null
            nextMask = null
            return result.copy(computeMillis = (System.nanoTime() - started) / 1e6)
        } catch (_: CvException) {
            close()
            return BackgroundMotion("Flot non résolu")
        } finally {
            resources.forEach { it.release() }
            nextGray?.release()
            nextMask?.release()
        }
    }
}
