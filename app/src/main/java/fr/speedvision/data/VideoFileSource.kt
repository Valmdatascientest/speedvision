package fr.speedvision.data

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import fr.speedvision.domain.FrameSampler
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.SourceStatus
import fr.speedvision.domain.VideoFrame
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/** Real MediaCodec decoder. No copies of selected files, no network or audio playback. */
class VideoFileSource(
    private val context: Context,
    private val uri: Uri,
    private val scope: CoroutineScope,
) : VideoSource {
    private val mutableStatus = MutableStateFlow(SourceStatus(PlaybackState.READY))
    override val status = mutableStatus.asStateFlow()
    private val output = MutableSharedFlow<VideoFrame>()
    private val decoderLock = Mutex()
    private var job: Job? = null
    private var generation = 0L

    override fun frames() = output.asSharedFlow()

    @Synchronized
    override fun start() {
        if (job?.isActive == true && mutableStatus.value.state == PlaybackState.PLAYING) return
        job?.cancel()
        val session = ++generation
        mutableStatus.value = SourceStatus(PlaybackState.PLAYING)
        job =
            scope.launch(Dispatchers.IO) {
                decoderLock.withLock {
                    try {
                        decode()
                        publish(session, SourceStatus(PlaybackState.ENDED))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        publish(
                            session,
                            SourceStatus(
                                PlaybackState.ERROR,
                                "Lecture impossible. Essayez une vidéo locale MP4/H.264 SDR, au plus 1920 × 1920.",
                            ),
                        )
                    }
                }
            }
    }

    @Synchronized
    override fun stop() {
        generation++
        job?.cancel()
        job = null
        mutableStatus.value = SourceStatus(PlaybackState.STOPPED)
    }

    @Synchronized
    private fun publish(
        session: Long,
        status: SourceStatus,
    ) {
        if (session == generation) mutableStatus.value = status
    }

    private suspend fun decode() {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: error("No video track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            require(format.getInteger(MediaFormat.KEY_WIDTH) in 1..1920)
            require(format.getInteger(MediaFormat.KEY_HEIGHT) in 1..1920)
            // HDR is deliberately excluded until color conversion is implemented.
            if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                require(format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) !in listOf(6, 7))
            }
            val rotation =
                if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                    format.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    0
                }
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            val sampler = FrameSampler()
            var inputEnded = false
            var firstPtsUs: Long? = null
            var startedNanos = 0L
            var lastProgressNanos = SystemClock.elapsedRealtimeNanos()
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(inputIndex))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        lastProgressNanos = SystemClock.elapsedRealtimeNanos()
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    lastProgressNanos = SystemClock.elapsedRealtimeNanos()
                    try {
                        if (info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                            sampler.accept(info.presentationTimeUs)
                        ) {
                            if (firstPtsUs == null) {
                                firstPtsUs = info.presentationTimeUs
                                startedNanos = SystemClock.elapsedRealtimeNanos()
                            }
                            val elapsedUs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1000
                            val waitUs = info.presentationTimeUs - firstPtsUs - elapsedUs
                            // Reject discontinuous timestamps instead of holding codec buffers indefinitely.
                            require(waitUs < 5_000_000)
                            if (waitUs > 0) delay(waitUs / 1000)
                            val frame =
                                requireNotNull(decoder.getOutputImage(index)).use {
                                    it.toFrame(info.presentationTimeUs, rotation)
                                }
                            output.emit(frame)
                            lastProgressNanos = SystemClock.elapsedRealtimeNanos()
                        }
                    } finally {
                        decoder.releaseOutputBuffer(index, false)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                check(SystemClock.elapsedRealtimeNanos() - lastProgressNanos < 10_000_000_000L) { "Decoder stalled" }
            }
        } finally {
            // release also frees a codec whose configure/start failed.
            try {
                codec?.release()
            } finally {
                extractor.release()
            }
        }
    }

    private fun Image.toFrame(
        ptsUs: Long,
        rotation: Int,
    ): VideoFrame {
        require(format == ImageFormat.YUV_420_888)
        val crop = cropRect
        val step = max(1, (max(crop.width(), crop.height()) + 639) / 640)
        val width = crop.width() / step
        val height = crop.height() / step
        val pixels = IntArray(width * height)
        val buffers = planes.map { it.buffer.duplicate() }

        fun sample(
            plane: Int,
            x: Int,
            y: Int,
        ): Int {
            val p = planes[plane]
            val b = buffers[plane]
            return b.get(b.position() + y * p.rowStride + x * p.pixelStride).toInt() and 255
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sx = crop.left + x * step
                val sy = crop.top + y * step
                pixels[y * width + x] = yuvToArgb(sample(0, sx, sy), sample(1, sx / 2, sy / 2), sample(2, sx / 2, sy / 2))
            }
        }
        return VideoFrame(pixels, width, height, ptsUs, rotation, SystemClock.elapsedRealtimeNanos())
    }
}
