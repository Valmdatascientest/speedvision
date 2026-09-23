package fr.speedvision.di

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.speedvision.data.VideoFileSource
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class VideoSourceFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun create(
            uri: Uri,
            scope: CoroutineScope,
        ): VideoSource = VideoFileSource(context, uri, scope)
    }
