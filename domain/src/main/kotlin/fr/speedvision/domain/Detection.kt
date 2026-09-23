package fr.speedvision.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel coordinates in the upright, cropped image delivered to the detector. Not track IDs. */
data class PixelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(right > left && bottom > top)
    }

    val width get() = right - left
    val height get() = bottom - top

    fun iou(other: PixelBox): Float {
        val intersection =
            max(0f, min(right, other.right) - max(left, other.left)) *
                max(0f, min(bottom, other.bottom) - max(top, other.top))
        return intersection / (width * height + other.width * other.height - intersection)
    }
}

data class Detection(
    val box: PixelBox,
    val score: Float,
    val classId: Int,
)

data class PlateDetection(
    val detection: Detection,
    val vehicleIndex: Int,
)

data class DetectionResult(
    val vehicles: List<Detection>,
    val plates: List<PlateDetection>,
    val vehicleMillis: Double,
    val plateMillis: Double,
    val totalMillis: Double,
    val roisProcessed: Int,
    val roisOmitted: Int,
)

/** Exact integer resize and padding used by preprocessing; reverse mapping includes ROI origin. */
data class Letterbox(
    val width: Int,
    val height: Int,
    val size: Int = 640,
    val originX: Int = 0,
    val originY: Int = 0,
) {
    init {
        require(width > 0 && height > 0 && size > 0)
    }

    private val scale = min(size.toFloat() / width, size.toFloat() / height)
    val resizedWidth = (width * scale).roundToInt().coerceIn(1, size)
    val resizedHeight = (height * scale).roundToInt().coerceIn(1, size)
    val padX = (size - resizedWidth) / 2
    val padY = (size - resizedHeight) / 2

    fun decode(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
    ): PixelBox? {
        if (!listOf(cx, cy, w, h).all { it.isFinite() } || w <= 0 || h <= 0) return null
        val sx = width.toFloat() / resizedWidth
        val sy = height.toFloat() / resizedHeight
        val left = ((cx - w / 2 - padX) * sx).coerceIn(0f, width.toFloat())
        val top = ((cy - h / 2 - padY) * sy).coerceIn(0f, height.toFloat())
        val right = ((cx + w / 2 - padX) * sx).coerceIn(0f, width.toFloat())
        val bottom = ((cy + h / 2 - padY) * sy).coerceIn(0f, height.toFloat())
        if (right <= left || bottom <= top) return null
        return PixelBox(left + originX, top + originY, right + originX, bottom + originY)
    }
}

/** Raw YOLO11 [1,4+classes,N] output. No objectness channel, no embedded NMS. */
object YoloPostprocessor {
    fun decode(
        output: FloatArray,
        classes: Int,
        candidates: Int,
        transform: Letterbox,
        allowedClasses: Set<Int>,
        threshold: Float = 0.35f,
        iouThreshold: Float = 0.45f,
        limit: Int = 30,
    ): List<Detection> {
        require(classes > 0 && candidates > 0 && output.size == (classes + 4) * candidates)
        require(threshold in 0f..1f && iouThreshold in 0f..1f && limit > 0)
        require(allowedClasses.all { it in 0 until classes })
        val detections =
            buildList {
                for (i in 0 until candidates) {
                    var bestClass = -1
                    var best = -1f
                    // Argmax over all classes, THEN filter: do not reinterpret a person as a low-score car.
                    for (c in 0 until classes) {
                        val score = output[(4 + c) * candidates + i]
                        if (score.isFinite() && score > best) {
                            best = score
                            bestClass = c
                        }
                    }
                    if (bestClass !in allowedClasses || best < threshold || best > 1f) continue
                    val box =
                        transform.decode(
                            output[i],
                            output[candidates + i],
                            output[2 * candidates + i],
                            output[3 * candidates + i],
                        ) ?: continue
                    add(Detection(box, best, bestClass))
                }
            }
        return nms(detections, iouThreshold, limit)
    }

    fun nms(
        detections: List<Detection>,
        threshold: Float = 0.45f,
        limit: Int = 30,
    ): List<Detection> {
        require(threshold in 0f..1f && limit > 0)
        val selected = mutableListOf<Detection>()
        for (candidate in detections.sortedByDescending { it.score }) {
            if (selected.none { it.classId == candidate.classId && it.box.iou(candidate.box) > threshold }) {
                selected.add(candidate)
                if (selected.size == limit) break
            }
        }
        return selected
    }
}

/** Nearest-rank percentiles over a bounded window of completed pipeline durations. */
class LatencyWindow(
    private val capacity: Int = 120,
) {
    init {
        require(capacity > 0)
    }

    private val values = ArrayDeque<Double>()

    fun add(millis: Double) {
        require(millis.isFinite() && millis >= 0)
        values.addLast(millis)
        if (values.size > capacity) values.removeFirst()
    }

    val count get() = values.size

    fun percentile(q: Double): Double {
        require(q in 0.0..1.0)
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[(ceil(q * sorted.size).toInt() - 1).coerceAtLeast(0)]
    }
}
