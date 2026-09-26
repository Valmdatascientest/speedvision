package fr.speedvision.meta

import androidx.compose.runtime.Composable
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CoroutineScope

object MetaSupport {
    fun createSource(
        scope: CoroutineScope,
        deviceId: String,
    ): VideoSource = error("Meta build required")

    @Composable
    fun Panel(
        onSelect: (String) -> Unit,
        close: () -> Unit,
    ) = Unit
}
