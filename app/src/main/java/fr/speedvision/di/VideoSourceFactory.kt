package fr.speedvision.di

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.speedvision.camera.CameraXVideoSource
import fr.speedvision.data.VideoFileSource
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class VideoSourceFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun camera(
            owner: LifecycleOwner,
            rotation: Int,
            scope: CoroutineScope,
        ): VideoSource = CameraXVideoSource(context, owner, scope, rotation)

        fun create(
            uri: Uri,
            scope: CoroutineScope,
        ): VideoSource = VideoFileSource(context, uri, scope)
    }
