package fr.speedvision.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.speedvision.domain.DetectionResult
import fr.speedvision.domain.PlateDetection
import fr.speedvision.domain.YoloPostprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

/** Vehicle → bounded native-resolution ROI → plate. No tracking or speed estimation. */
class DetectionEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()
        private var vehicle: OnnxDetector? = null
        private var plate: OnnxDetector? = null

        suspend fun detect(bitmap: Bitmap): DetectionResult =
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    if (vehicle == null) {
                        val newVehicle = OnnxDetector(context, "vehicle.onnx", 80, setOf(2, 3, 5, 7))
                        try {
                            plate = OnnxDetector(context, "plate.onnx", 1, setOf(0))
                            vehicle = newVehicle
                        } catch (failure: Exception) {
                            newVehicle.close()
                            throw failure
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val start = SystemClock.elapsedRealtimeNanos()
                    val vehicles = requireNotNull(vehicle).detect(bitmap)
                    val afterVehicle = SystemClock.elapsedRealtimeNanos()
                    val found = mutableListOf<PlateDetection>()
                    // Bound worst-case work; omitted ROIs are explicitly reported, never silently treated as empty.
                    val rois = vehicles.withIndex().sortedByDescending { it.value.score }.take(4)
                    for ((index, detection) in rois) {
                        currentCoroutineContext().ensureActive()
                        val box = detection.box
                        val roi = Rect(floor(box.left).toInt(), floor(box.top).toInt(), ceil(box.right).toInt(), ceil(box.bottom).toInt())
                        found += requireNotNull(plate).detect(bitmap, roi).map { PlateDetection(it, index) }
                    }
                    currentCoroutineContext().ensureActive()
                    val unique = YoloPostprocessor.nms(found.map { it.detection })
                    val end = SystemClock.elapsedRealtimeNanos()
                    DetectionResult(
                        vehicles,
                        unique.map { d -> found.first { it.detection == d } },
                        (afterVehicle - start) / 1e6,
                        (end - afterVehicle) / 1e6,
                        (end - start) / 1e6,
                        rois.size,
                        vehicles.size - rois.size,
                    )
                }
            }

        suspend fun close() =
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    try {
                        vehicle?.close()
                    } finally {
                        plate?.close()
                        vehicle = null
                        plate = null
                    }
                }
            }
    }
