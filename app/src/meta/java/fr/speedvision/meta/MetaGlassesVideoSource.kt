package fr.speedvision.meta

import android.os.SystemClock
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import fr.speedvision.domain.FrameGeometry
import fr.speedvision.domain.FrameSampler
import fr.speedvision.domain.MetaPixels
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.SourceStatus
import fr.speedvision.domain.VideoFrame
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.nio.ByteBuffer

/** Real DAT 1.0.0 camera. No mock, no audio, no recording, no automatic reconnect. Main-thread lifecycle. */
class MetaGlassesVideoSource(
    private val scope: CoroutineScope,
    private val deviceId: String,
) : VideoSource {
    private val state = MutableStateFlow(SourceStatus(PlaybackState.READY))
    override val status = state.asStateFlow()
    private val output = MutableSharedFlow<VideoFrame>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val lock = Mutex()
    private var job: Job? = null
    private var generation = 0L

    override fun frames() = output.asSharedFlow()

    override fun start() {
        if (job?.isActive == true) return
        val run = ++generation
        state.value = SourceStatus(PlaybackState.PLAYING, "Connexion aux lunettes…")
        job =
            scope.launch {
                lock.withLock {
                    var session: DeviceSession? = null
                    var camera: Camera? = null
                    try {
                        check(Wearables.registrationState.value == RegistrationState.REGISTERED) { "Inscription Meta requise." }
                        val id = DeviceIdentifier(deviceId)
                        check(id in Wearables.devices.value) { "Lunettes absentes. Reconnecter puis appuyer sur START." }
                        check(Wearables.checkPermissionStatus(Permission.CAMERA).getOrThrow() == PermissionStatus.Granted) {
                            "Autorisation caméra Meta requise. Rouvrir le panneau lunettes."
                        }
                        val active = Wearables.createSession(SpecificDeviceSelector(id)).getOrThrow()
                        session = active
                        active.start()
                        withTimeout(15_000) { active.state.first { it == DeviceSessionState.STARTED || it == DeviceSessionState.STOPPED } }
                        check(active.state.value == DeviceSessionState.STARTED) { "Session lunettes interrompue." }
                        val attached =
                            active
                                .addCamera(
                                    StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 15, compressVideo = false),
                                ).getOrThrow()
                        camera = attached
                        val stream = attached.stream
                        var lastFrameAt = SystemClock.elapsedRealtime()
                        coroutineScope {
                            launch {
                                active.state.collect {
                                    check(
                                        it == DeviceSessionState.STARTED,
                                    ) { "Session interrompue. Reprise manuelle requise." }
                                }
                            }
                            launch { active.errors.collect { error("Erreur session Meta : $it") } }
                            launch { stream.errorStream.collect { error("Erreur caméra Meta : $it") } }
                            launch {
                                active.deviceInfo.collect {
                                    if (run == generation) {
                                        state.value =
                                            SourceStatus(
                                                PlaybackState.PLAYING,
                                                "Meta · batterie ${it.batteryLevel}% · thermique ${it.thermalLevel}",
                                            )
                                    }
                                }
                            }
                            launch {
                                while (true) {
                                    delay(1_000)
                                    check(SystemClock.elapsedRealtime() - lastFrameAt < 10_000) { "Aucune image Meta depuis 10 secondes." }
                                }
                            }
                            launch {
                                var lastPts = -1L
                                var sequence = 0L
                                val sampler = FrameSampler()
                                stream.videoStream
                                    .map { frame ->
                                        check(!frame.isCompressed && !frame.isCodecConfig) { "Format Meta inattendu." }
                                        check(frame.width in 2..1920 && frame.height in 2..1920) { "Résolution Meta non prise en charge." }
                                        check(frame.presentationTimeUs >= 0 && frame.presentationTimeUs > lastPts) { "PTS Meta invalides." }
                                        lastPts = frame.presentationTimeUs
                                        lastFrameAt = SystemClock.elapsedRealtime()
                                        sequence++
                                        // Copy SDK-owned bytes before suspension; only decoded images may be conflated.
                                        val bytes = ByteArray(frame.buffer.remaining().also { require(it <= 1920 * 1920 * 3 / 2) })
                                        frame.buffer.duplicate().get(bytes)
                                        Packet(bytes, frame.width, frame.height, frame.presentationTimeUs, sequence)
                                    }.buffer(1, BufferOverflow.DROP_OLDEST)
                                    .collect { packet ->
                                        if (sampler.accept(packet.pts)) {
                                            val pixels =
                                                withContext(
                                                    Dispatchers.Default,
                                                ) { MetaPixels.argb(ByteBuffer.wrap(packet.bytes), packet.width, packet.height) }
                                            if (run == generation) {
                                                output.emit(
                                                    VideoFrame(
                                                        pixels,
                                                        packet.width,
                                                        packet.height,
                                                        packet.pts,
                                                        0,
                                                        SystemClock.elapsedRealtimeNanos(),
                                                        FrameGeometry(packet.width, packet.height, timestampOrigin = "meta-dat-pts"),
                                                        packet.sequence,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                            }
                            yield()
                            stream.start().getOrThrow()
                            launch {
                                stream.state.collect {
                                    check(it !in setOf(StreamState.PAUSED, StreamState.STOPPED, StreamState.CLOSED, StreamState.STOPPING)) {
                                        "Flux Meta interrompu. Reprise manuelle requise."
                                    }
                                }
                            }
                            awaitCancellation()
                        }
                    } catch (cancelled: CancellationException) {
                        if (cancelled is kotlinx.coroutines.TimeoutCancellationException && run == generation) {
                            state.value = SourceStatus(PlaybackState.ERROR, "Connexion Meta expirée. Reprise manuelle requise.")
                        } else {
                            throw cancelled
                        }
                    } catch (_: Exception) {
                        if (run == generation) {
                            state.value =
                                SourceStatus(
                                    PlaybackState.ERROR,
                                    "Flux Meta indisponible ou interrompu. Vérifier inscription, permission, connexion et firmware ; puis relancer START.",
                                )
                        }
                    } finally {
                        runCatching { camera?.close() }
                        runCatching { session?.stop() }
                    }
                }
            }
    }

    override fun stop() {
        generation++
        job?.cancel()
        job = null
        state.value = SourceStatus(PlaybackState.STOPPED)
    }

    private data class Packet(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val pts: Long,
        val sequence: Long,
    )
}
