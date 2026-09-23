package fr.speedvision.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Pixels are owned by the receiver; sources never mutate an emitted array. */
data class VideoFrame(
    val argb: IntArray,
    val width: Int,
    val height: Int,
    val presentationTimeUs: Long,
    val rotationDegrees: Int,
    val decodedAtNanos: Long,
    val geometry: FrameGeometry = FrameGeometry(width, height),
    val sequenceNumber: Long = 0,
)

/** Frame pixels are the native crop before rotation, without resize. */
data class FrameGeometry(
    val nativeWidth: Int,
    val nativeHeight: Int,
    val cropLeft: Int = 0,
    val cropTop: Int = 0,
    val timestampOrigin: String = "media-pts",
)

enum class PlaybackState { READY, PLAYING, STOPPED, ENDED, ERROR }

data class SourceStatus(
    val state: PlaybackState,
    val message: String? = null,
)

/** One session per source. start replays from the beginning; stop cancels decoding. */
interface VideoSource {
    val status: StateFlow<SourceStatus>

    fun start()

    fun stop()

    fun frames(): Flow<VideoFrame>

    fun close() {
        stop()
    }
}

/** Future speech adapter. No implementation or speech in Sprint 1. */
interface AudioOutput {
    suspend fun speak(text: String)

    fun stop()
}
