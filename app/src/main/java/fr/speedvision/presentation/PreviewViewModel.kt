package fr.speedvision.presentation

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.speedvision.di.VideoSourceFactory
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PreviewState(
    val selected: Boolean = false,
    val state: PlaybackState = PlaybackState.READY,
    val image: Bitmap? = null,
    val frameCount: Int = 0,
    val ptsUs: Long = 0,
    val fps: Double = 0.0,
    val error: String? = null,
    val debug: Boolean = false,
)

@HiltViewModel
class PreviewViewModel
    @Inject
    constructor(
        private val factory: VideoSourceFactory,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PreviewState())
        val state = mutableState.asStateFlow()
        private var source: VideoSource? = null
        private var framesJob: Job? = null
        private var statusJob: Job? = null

        fun select(uri: Uri) {
            source?.stop()
            framesJob?.cancel()
            statusJob?.cancel()
            mutableState.value = PreviewState(selected = true, debug = mutableState.value.debug)
            val selectedSource = factory.create(uri, viewModelScope)
            source = selectedSource
            statusJob =
                viewModelScope.launch {
                    selectedSource.status.collect { status ->
                        mutableState.update { it.copy(state = status.state, error = status.message) }
                    }
                }
            framesJob =
                viewModelScope.launch {
                    var firstNanos = 0L
                    selectedSource.frames().collect { frame ->
                        val bitmap =
                            withContext(Dispatchers.Default) {
                                val raw = Bitmap.createBitmap(frame.argb, frame.width, frame.height, Bitmap.Config.ARGB_8888)
                                if (frame.rotationDegrees == 0) {
                                    raw
                                } else {
                                    val matrix = Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) }
                                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                                        if (it !== raw) raw.recycle()
                                    }
                                }
                            }
                        // A stop or source replacement may have happened during conversion.
                        if (source === selectedSource && selectedSource.status.value.state == PlaybackState.PLAYING) {
                            val count = mutableState.value.frameCount + 1
                            if (count == 1) firstNanos = frame.decodedAtNanos
                            val duration = (frame.decodedAtNanos - firstNanos) / 1e9
                            mutableState.update {
                                it.copy(
                                    image = bitmap,
                                    frameCount = count,
                                    ptsUs = frame.presentationTimeUs,
                                    fps = if (duration > 0) (count - 1) / duration else 0.0,
                                )
                            }
                        }
                    }
                }
        }

        fun start() {
            if (mutableState.value.state == PlaybackState.PLAYING) return
            mutableState.update { it.copy(frameCount = 0, ptsUs = 0, fps = 0.0, image = null, error = null) }
            source?.start()
        }

        fun stop() {
            source?.stop()
        }

        fun debug(enabled: Boolean) {
            mutableState.update { it.copy(debug = enabled) }
        }

        override fun onCleared() {
            source?.stop()
        }
    }
