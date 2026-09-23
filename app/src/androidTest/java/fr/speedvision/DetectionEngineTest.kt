package fr.speedvision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.domain.LatencyWindow
import fr.speedvision.domain.VideoFrame
import fr.speedvision.presentation.uprightBitmap
import fr.speedvision.vision.DetectionEngine
import fr.speedvision.vision.OnnxDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DetectionEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun requireModels() {
        val available = context.assets.list("models")?.containsAll(listOf("vehicle.onnx", "plate.onnx")) == true
        if (InstrumentationRegistry.getArguments().getString("requireModels") == "true") {
            assertTrue("Provision models before running the required model suite", available)
        } else {
            assumeTrue("Model suite requires scripts/prepare_models.py --accept-agpl", available)
        }
    }

    @Test fun realVehicleAndPlateModelsRunAndReportTimings() =
        runBlocking {
            requireModels()
            val image =
                instrumentation.context.assets
                    .open("detection/bus.jpg")
                    .use { BitmapFactory.decodeStream(it) }
            val engine = DetectionEngine(context)
            try {
                engine.detect(image) // Warmup; not counted as a benchmark sample.
                val latencies = LatencyWindow(8)
                repeat(8) {
                    val result = engine.detect(image)
                    assertTrue(result.vehicles.any { it.classId == 5 })
                    assertTrue(result.roisProcessed in 1..4)
                    assertTrue(result.plateMillis > 0)
                    assertTrue(
                        result.plates.all { p ->
                            p.vehicleIndex in result.vehicles.indices && p.detection.box.right <= image.width &&
                                p.detection.box.bottom <= image.height
                        },
                    )
                    latencies.add(result.totalMillis)
                }
                Log.i(
                    "SpeedVisionBenchmark",
                    "provider=CPU device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT} " +
                        "n=${latencies.count} p50_ms=${latencies.percentile(
                            0.5,
                        )} p95_ms=${latencies.percentile(0.95)} fixture=bus smoke_only=true",
                )
            } finally {
                engine.close()
                image.recycle()
            }
        }

    @Test fun bothRealModelsRejectBlankFrame() {
        requireModels()
        val blank = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        try {
            OnnxDetector(context, "vehicle.onnx", 80, setOf(2, 3, 5, 7)).use { assertTrue(it.detect(blank).isEmpty()) }
            OnnxDetector(context, "plate.onnx", 1, setOf(0)).use { assertTrue(it.detect(blank).isEmpty()) }
        } finally {
            blank.recycle()
        }
    }

    @Test fun rotationUsesUprightPixelsBeforeInference() {
        val frame = VideoFrame(intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.YELLOW, Color.BLACK), 2, 3, 0, 90, 0)
        val bitmap = uprightBitmap(frame)
        try {
            assertEquals(3, bitmap.width)
            assertEquals(2, bitmap.height)
            assertEquals(Color.YELLOW, bitmap.getPixel(0, 0))
            assertEquals(Color.RED, bitmap.getPixel(2, 0))
        } finally {
            bitmap.recycle()
        }
    }
}
