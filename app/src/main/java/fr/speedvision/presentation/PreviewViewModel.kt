package fr.speedvision.presentation

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.speedvision.di.VideoSourceFactory
import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.DetectionResult
import fr.speedvision.domain.LatencyWindow
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.TrackingResult
import fr.speedvision.domain.VehicleTracker
import fr.speedvision.domain.VideoFrame
import fr.speedvision.domain.VideoSource
import fr.speedvision.meta.MetaSupport
import fr.speedvision.motion.BackgroundMotion
import fr.speedvision.motion.BackgroundMotionEstimator
import fr.speedvision.vision.DetectionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PreviewState(
    val selected: Boolean = false,
    val sourceLabel: String = "VIDÉO LOCALE",
    val camera: Boolean = false,
    val state: PlaybackState = PlaybackState.READY,
    val image: Bitmap? = null,
    val frameCount: Int = 0,
    val ptsUs: Long = 0,
    val fps: Double = 0.0,
    val error: String? = null,
    val debug: Boolean = false,
    val detectionEnabled: Boolean = true,
    val detections: DetectionResult? = null,
    val detectionError: String? = null,
    val tracking: TrackingResult? = null,
    val backgroundMotion: BackgroundMotion? = null,
    val calibrationBinding: CalibrationBinding? = null,
    val p50: Double = 0.0,
    val p95: Double = 0.0,
    val samples: Int = 0,
    val skipped: Long = 0,
    val processingAgeMillis: Double = 0.0,
)

internal fun uprightBitmap(frame: VideoFrame): Bitmap {
    require(frame.rotationDegrees in setOf(0, 90, 180, 270))
    val raw = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888)
    if (frame.rotationDegrees == 0) return raw
    val matrix = Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, false).also { if (it !== raw) raw.recycle() }
}

@HiltViewModel
class PreviewViewModel
    @Inject
    constructor(
        private val factory: VideoSourceFactory,
        private val detector: DetectionEngine,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PreviewState())
        val state = mutableState.asStateFlow()
        private var source: VideoSource? = null
        private var sourceId = ""
        private var framesJob: Job? = null
        private var statusJob: Job? = null
        private var run = 0L
        private val tracker = VehicleTracker()
        private var detectionEpoch = 0L
        private var frameGeometry: Pair<fr.speedvision.domain.FrameGeometry, Int>? = null

        private fun resetTracking() {
            detectionEpoch++
            tracker.reset()
            frameGeometry = null
        }

        fun select(uri: Uri) {
            sourceId =
                "video:" +
                java.security.MessageDigest
                    .getInstance(
                        "SHA-256",
                    ).digest(uri.toString().toByteArray())
                    .joinToString("") { "%02x".format(it) }
            attach(factory.create(uri, viewModelScope), false)
        }

        fun selectCamera(
            owner: LifecycleOwner,
            rotation: Int,
        ) {
            sourceId = "camera-back:${android.os.Build.MANUFACTURER}:${android.os.Build.MODEL}"
            attach(factory.camera(owner, rotation, viewModelScope), true)
        }

        fun selectMeta(deviceId: String) {
            sourceId = "meta:" +
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(deviceId.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            attach(MetaSupport.createSource(viewModelScope, deviceId), false, "LUNETTES META")
        }

        fun cameraDenied() {
            mutableState.update { it.copy(error = "Caméra refusée. Vous pouvez choisir une vidéo locale.") }
        }

        private fun attach(
            selectedSource: VideoSource,
            camera: Boolean,
            label: String = if (camera) "CAMÉRA TÉLÉPHONE" else "VIDÉO LOCALE",
        ) {
            stop()
            source?.close()
            framesJob?.cancel()
            statusJob?.cancel()
            val old = mutableState.value
            mutableState.value =
                PreviewState(
                    selected = true,
                    camera = camera,
                    sourceLabel = label,
                    debug = old.debug,
                    detectionEnabled = old.detectionEnabled,
                )
            source = selectedSource
            statusJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    selectedSource.status.collect { status ->
                        if (status.state == PlaybackState.ERROR) {
                            resetTracking()
                            mutableState.update { it.copy(tracking = null, detections = null, backgroundMotion = null) }
                        }
                        mutableState.update { it.copy(state = status.state, error = status.message) }
                    }
                }
            framesJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    var currentRun = -1L
                    var firstNanos = 0L
                    var lastSequence = 0L
                    var latency = LatencyWindow()
                    val motion = BackgroundMotionEstimator()
                    try {
                        selectedSource.frames().conflate().collect { frame ->
                            val frameRun = run
                            val frameEpoch = detectionEpoch
                            if (frameRun != currentRun) {
                                currentRun = frameRun
                                firstNanos = 0
                                lastSequence = 0
                                latency = LatencyWindow()
                            }
                            val bitmap = withContext(Dispatchers.Default) { uprightBitmap(frame) }
                            var result: DetectionResult? = null
                            val enabled = mutableState.value.detectionEnabled
                            if (enabled && mutableState.value.detectionError == null) {
                                try {
                                    result = detector.detect(bitmap)
                                } catch (cancelled: CancellationException) {
                                    bitmap.recycle()
                                    throw cancelled
                                } catch (_: Exception) {
                                    if (frameRun == run && frameEpoch == detectionEpoch) {
                                        mutableState.update {
                                            it.copy(detectionError = "Modèles absents ou incompatibles dans cette installation.")
                                        }
                                    }
                                }
                            }
                            val background =
                                try {
                                    withContext(Dispatchers.Default) {
                                        motion.process(
                                            bitmap,
                                            result?.vehicles?.map { it.box },
                                            frame.presentationTimeUs,
                                            "$frameRun:$frameEpoch:${frame.geometry}:${frame.rotationDegrees}",
                                        )
                                    }
                                } catch (cancelled: CancellationException) {
                                    bitmap.recycle()
                                    throw cancelled
                                }
                            val active = selectedSource.status.value.state in setOf(PlaybackState.PLAYING, PlaybackState.ENDED)
                            if (source === selectedSource && frameRun == run && active) {
                                if (frameEpoch != detectionEpoch) result = null
                                val geometry = frame.geometry to frame.rotationDegrees
                                if (frameGeometry != geometry) tracker.reset()
                                frameGeometry = geometry
                                val tracking = result?.let { tracker.update(frame.presentationTimeUs, bitmap.width, bitmap.height, it) }
                                val now = SystemClock.elapsedRealtimeNanos()
                                val count = mutableState.value.frameCount + 1
                                if (firstNanos == 0L) firstNanos = now
                                val duration = (now - firstNanos) / 1e9
                                result?.let { latency.add(it.totalMillis) }
                                val omitted = (frame.sequenceNumber - lastSequence - 1).coerceAtLeast(0)
                                lastSequence = frame.sequenceNumber
                                mutableState.update {
                                    it.copy(
                                        image = bitmap,
                                        calibrationBinding =
                                            CalibrationBinding(
                                                sourceId,
                                                frame.geometry.nativeWidth,
                                                frame.geometry.nativeHeight,
                                                frame.geometry.cropLeft,
                                                frame.geometry.cropTop,
                                                frame.width,
                                                frame.height,
                                                frame.rotationDegrees,
                                            ),
                                        frameCount = count,
                                        ptsUs = frame.presentationTimeUs,
                                        fps = if (duration > 0) (count - 1) / duration else 0.0,
                                        detections = if (it.detectionEnabled) result else null,
                                        tracking = if (it.detectionEnabled) tracking else null,
                                        backgroundMotion = if (frameEpoch == detectionEpoch) background else null,
                                        p50 = latency.percentile(0.5),
                                        p95 = latency.percentile(0.95),
                                        samples = latency.count,
                                        skipped = it.skipped + omitted,
                                        processingAgeMillis = (now - frame.decodedAtNanos) / 1e6,
                                    )
                                }
                            } else {
                                bitmap.recycle()
                            }
                        }
                    } finally {
                        motion.close()
                    }
                }
        }

        fun start() {
            if (mutableState.value.state == PlaybackState.PLAYING) return
            run++
            resetTracking()
            mutableState.update {
                it.copy(
                    frameCount = 0,
                    ptsUs = 0,
                    fps = 0.0,
                    image = null,
                    calibrationBinding = null,
                    error = null,
                    detections = null,
                    tracking = null,
                    backgroundMotion = null,
                    detectionError = null,
                    samples = 0,
                    p50 = 0.0,
                    p95 = 0.0,
                    skipped = 0,
                    processingAgeMillis = 0.0,
                )
            }
            source?.start()
        }

        fun stop() {
            run++
            resetTracking()
            source?.stop()
            mutableState.update { it.copy(detections = null, tracking = null, backgroundMotion = null) }
        }

        /** Activity destruction must not leave an old camera LifecycleOwner in the retained ViewModel. */
        fun detachCamera() {
            if (!mutableState.value.camera) return
            stop()
            source?.close()
            source = null
            framesJob?.cancel()
            statusJob?.cancel()
            mutableState.update { it.copy(selected = false) }
        }

        fun debug(enabled: Boolean) {
            mutableState.update { it.copy(debug = enabled) }
        }

        fun detection(enabled: Boolean) {
            resetTracking()
            mutableState.update {
                it.copy(detectionEnabled = enabled, detectionError = null, detections = null, tracking = null, backgroundMotion = null)
            }
        }

        override fun onCleared() {
            source?.close()
            CoroutineScope(Dispatchers.Default).launch { detector.close() }
        }
    }
