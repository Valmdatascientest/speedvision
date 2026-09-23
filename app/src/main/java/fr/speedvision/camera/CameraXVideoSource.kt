package fr.speedvision.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import fr.speedvision.domain.FrameGeometry
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.SourceStatus
import fr.speedvision.domain.VideoFrame
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Bind/unbind on Main; copy and close every ImageProxy on a single analysis worker. */
class CameraXVideoSource(
    private val context: Context,
    owner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val rotation: Int = Surface.ROTATION_0,
) : VideoSource {
    private val owner = WeakReference(owner)
    private val mutableStatus = MutableStateFlow(SourceStatus(PlaybackState.READY))
    override val status = mutableStatus.asStateFlow()
    private val output = MutableSharedFlow<VideoFrame>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var job: Job? = null
    private var executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private var closed = false
    private var observedState: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>? = null
    private var observer: androidx.lifecycle.Observer<androidx.camera.core.CameraState>? = null

    override fun frames() = output.asSharedFlow()

    override fun start() {
        if (closed || mutableStatus.value.state == PlaybackState.PLAYING) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableStatus.value = SourceStatus(PlaybackState.ERROR, "Autorisation caméra refusée. Vous pouvez utiliser une vidéo locale.")
            return
        }
        val session = generation.incrementAndGet()
        mutableStatus.value = SourceStatus(PlaybackState.PLAYING)
        job =
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(context).await()
                    if (generation.get() != session) return@launch
                    provider = cameraProvider
                    val useCase =
                        ImageAnalysis
                            .Builder()
                            .setResolutionSelector(
                                ResolutionSelector
                                    .Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                                    ).build(),
                            ).setTargetRotation(rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    var sequence = 0L
                    useCase.setAnalyzer(executor) { image ->
                        try {
                            if (generation.get() == session) {
                                sequence++
                                val bitmap = image.toBitmap()
                                try {
                                    val crop = image.cropRect
                                    val pixels = IntArray(crop.width() * crop.height())
                                    bitmap.getPixels(pixels, 0, crop.width(), crop.left, crop.top, crop.width(), crop.height())
                                    val frame =
                                        VideoFrame(
                                            pixels,
                                            crop.width(),
                                            crop.height(),
                                            image.imageInfo.timestamp / 1000,
                                            image.imageInfo.rotationDegrees,
                                            SystemClock.elapsedRealtimeNanos(),
                                            FrameGeometry(image.width, image.height, crop.left, crop.top, "camera-monotonic"),
                                            sequence,
                                        )
                                    if (generation.get() == session) output.tryEmit(frame)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        } catch (_: Exception) {
                            scope.launch(Dispatchers.Main.immediate) {
                                if (generation.get() == session) {
                                    stop()
                                    mutableStatus.value =
                                        SourceStatus(
                                            PlaybackState.ERROR,
                                            "Lecture caméra impossible.",
                                        )
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    analysis = useCase
                    val lifecycle = owner.get() ?: error("Camera lifecycle expired")
                    val camera = cameraProvider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, useCase)
                    val stateObserver =
                        androidx.lifecycle.Observer<androidx.camera.core.CameraState> { state ->
                            if (state.error != null && generation.get() == session) {
                                stop()
                                mutableStatus.value = SourceStatus(PlaybackState.ERROR, "Caméra indisponible. Choisissez une vidéo.")
                            }
                        }
                    observedState = camera.cameraInfo.cameraState
                    observer = stateObserver
                    observedState?.observe(lifecycle, stateObserver)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation.get() == session) {
                        stop()
                        mutableStatus.value = SourceStatus(PlaybackState.ERROR, "Caméra indisponible. Choisissez une vidéo.")
                    }
                }
            }
    }

    override fun stop() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        observer?.let { observedState?.removeObserver(it) }
        observer = null
        observedState = null
        analysis?.let {
            it.clearAnalyzer()
            provider?.unbind(it)
        }
        analysis = null
        mutableStatus.value = SourceStatus(PlaybackState.STOPPED)
    }

    override fun close() {
        stop()
        closed = true
        owner.clear()
        executor.shutdown()
    }
}
