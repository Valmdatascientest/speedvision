package fr.speedvision

import android.graphics.BitmapFactory
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.vision.DetectionEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Explicit diagnostic only: repeated fixture, no camera throughput or accuracy claim. */
class DetectionBenchmarkTest {
    @Test fun repeatedFixtureBenchmark() =
        runBlocking<Unit> {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue("Opt in with runBenchmark=true", args.getString("runBenchmark") == "true")
            val count = (args.getString("benchmarkSamples") ?: "100").toInt().also { require(it in 5..5000) }
            val warmup = (args.getString("benchmarkWarmup") ?: "5").toInt().also { require(it in 1..100) }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val output = File(context.filesDir, "detection-benchmark.json")
            require(!output.exists()) { "Retrieve and remove the previous benchmark first" }
            val fixture =
                instrumentation.context.assets
                    .open("detection/bus.jpg")
                    .use { it.readBytes() }

            fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val models = JSONObject()
            for (name in listOf("vehicle.onnx", "plate.onnx")) {
                models.put(name, context.assets.open("models/$name").use { hash(it.readBytes()) })
            }
            val image = requireNotNull(BitmapFactory.decodeByteArray(fixture, 0, fixture.size))
            val engine = DetectionEngine(context)
            val power = context.getSystemService(PowerManager::class.java)
            val samples = JSONArray()
            try {
                val coldStart = SystemClock.elapsedRealtimeNanos()
                engine.detect(image)
                val coldMillis = (SystemClock.elapsedRealtimeNanos() - coldStart) / 1e6
                repeat(warmup) { engine.detect(image) }
                val origin = SystemClock.elapsedRealtimeNanos()
                repeat(count) { index ->
                    check(
                        SystemClock.elapsedRealtimeNanos() - origin < 1_800_000_000_000L,
                    ) { "Benchmark exceeded 30 minutes; no complete report" }
                    val start = SystemClock.elapsedRealtimeNanos()
                    val result = engine.detect(image)
                    val end = SystemClock.elapsedRealtimeNanos()
                    samples.put(
                        JSONObject()
                            .put("index", index)
                            .put("start_ns", start - origin)
                            .put("end_ns", end - origin)
                            .put("vehicle_ms", result.vehicleMillis)
                            .put("plate_ms", result.plateMillis)
                            .put("total_ms", result.totalMillis)
                            .put("rois_processed", result.roisProcessed)
                            .put("rois_omitted", result.roisOmitted)
                            .put("thermal_status", if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else JSONObject.NULL),
                    )
                }
                val report =
                    JSONObject()
                        .put("schema", 1)
                        .put("workload", "repeated-bus-fixture")
                        .put("provider", "CPU")
                        .put("intra_threads", 2)
                        .put("inter_threads", 1)
                        .put("app_version", BuildConfig.VERSION_NAME)
                        .put("meta_enabled", BuildConfig.META_ENABLED)
                        .put("device", Build.MODEL)
                        .put("sdk", Build.VERSION.SDK_INT)
                        .put("abi", Build.SUPPORTED_ABIS.first())
                        .put("fixture_sha256", hash(fixture))
                        .put("model_sha256", models)
                        .put("width", image.width)
                        .put("height", image.height)
                        .put("cold_call_ms", coldMillis)
                        .put("warmup_calls", warmup)
                        .put("samples", samples)
                check(output.createNewFile()) { "Output already exists" }
                try {
                    output.writeText(report.toString(2))
                } catch (failure: Exception) {
                    output.delete()
                    throw failure
                }
            } finally {
                engine.close()
                image.recycle()
            }
        }
}
