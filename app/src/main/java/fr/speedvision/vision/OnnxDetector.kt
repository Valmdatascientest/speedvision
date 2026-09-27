package fr.speedvision.vision

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import fr.speedvision.domain.Detection
import fr.speedvision.domain.Letterbox
import fr.speedvision.domain.YoloPostprocessor
import java.nio.FloatBuffer

/** One worker owns a session. All runs and close are serialized by DetectionEngine. */
internal class OnnxDetector(
    context: Context,
    asset: String,
    private val classes: Int,
    private val allowed: Set<Int>,
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    // Owned by this serialized session; fully overwritten for every frame/ROI.
    private val pixels = IntArray(640 * 640)
    private val rgb = FloatArray(640 * 640 * 3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var inputBitmap: Bitmap? = null
    private var closed = false

    init {
        val bytes = context.assets.open("models/$asset").use { it.readBytes() }
        session =
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                environment.createSession(bytes, options)
            }
        try {
            require(session.inputInfo.size == 1 && session.outputInfo.size == 1) { "Unsupported model interface" }
            inputName = session.inputNames.single()
            val input = session.inputInfo.getValue(inputName).info as TensorInfo
            require(
                input.type == OnnxJavaType.FLOAT && input.shape.contentEquals(longArrayOf(1, 3, 640, 640)),
            ) { "Expected float32 NCHW 640" }
            val output =
                session.outputInfo.values
                    .single()
                    .info as TensorInfo
            require(output.type == OnnxJavaType.FLOAT && output.shape.contentEquals(longArrayOf(1, (classes + 4).toLong(), 8400))) {
                "Expected raw YOLO11 output"
            }
        } catch (failure: Exception) {
            session.close()
            throw failure
        }
    }

    fun detect(
        bitmap: Bitmap,
        roi: Rect = Rect(0, 0, bitmap.width, bitmap.height),
    ): List<Detection> {
        check(!closed) { "Detector is closed" }
        require(roi.left >= 0 && roi.top >= 0 && roi.right <= bitmap.width && roi.bottom <= bitmap.height && !roi.isEmpty)
        val transform = Letterbox(roi.width(), roi.height(), originX = roi.left, originY = roi.top)
        val input = inputBitmap ?: Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also { inputBitmap = it }
        val canvas = Canvas(input)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(
            bitmap,
            roi,
            Rect(
                transform.padX,
                transform.padY,
                transform.padX + transform.resizedWidth,
                transform.padY + transform.resizedHeight,
            ),
            paint,
        )
        input.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        for (i in pixels.indices) {
            rgb[i] = ((pixels[i] shr 16) and 255) / 255f
            rgb[i + pixels.size] = ((pixels[i] shr 8) and 255) / 255f
            rgb[i + pixels.size * 2] = (pixels[i] and 255) / 255f
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(rgb), longArrayOf(1, 3, 640, 640)).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val buffer = output.floatBuffer
                val values = FloatArray(buffer.remaining())
                buffer.get(values)
                return YoloPostprocessor.decode(values, classes, 8400, transform, allowed)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            session.close()
        } finally {
            inputBitmap?.recycle()
            inputBitmap = null
        }
    }
}
