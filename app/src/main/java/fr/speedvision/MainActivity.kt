package fr.speedvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.speedvision.domain.PlaybackState
import fr.speedvision.presentation.PreviewState
import fr.speedvision.presentation.PreviewViewModel
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: PreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val picker =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) model.select(uri)
                }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7FE0C3), background = Color(0xFF10191E))) {
                PreviewScreen(state, { picker.launch(arrayOf("video/*")) }, model::start, model::stop, model::debug)
            }
        }
    }

    override fun onStop() {
        model.stop()
        super.onStop()
    }
}

@Composable
fun PreviewScreen(
    state: PreviewState,
    select: () -> Unit,
    start: () -> Unit,
    stop: () -> Unit,
    debug: (Boolean) -> Unit,
) {
    Scaffold { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SpeedVision", style = MaterialTheme.typography.headlineLarge)
            Text("LAB / 01     ·     VIDÉO LOCALE", color = MaterialTheme.colorScheme.primary)
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val image = state.image
                if (image != null) {
                    Image(image.asImageBitmap(), "Aperçu de la vidéo sélectionnée", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text("Sélectionnez une vidéo pour commencer")
                }
            }
            Text("État : ${state.state.label()}")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = select, modifier = Modifier.fillMaxWidth()) { Text("Choisir une vidéo") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = start, enabled = state.selected && state.state != PlaybackState.PLAYING) { Text("START · Rejouer") }
                OutlinedButton(onClick = stop, enabled = state.state == PlaybackState.PLAYING) { Text("STOP") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vitesse relative : —", style = MaterialTheme.typography.titleLarge)
                    Text("Distance : —     ·     Confiance : —")
                    Text("Mesure indisponible : détection et calibration prévues aux prochains sprints.")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Diagnostic vidéo")
                Switch(checked = state.debug, onCheckedChange = debug)
            }
            if (state.debug) {
                Text(
                    String.format(
                        Locale.FRANCE,
                        "Images : %d · Débit aperçu : %.1f fps\nPTS : %.3f s",
                        state.frameCount,
                        state.fps,
                        state.ptsUs / 1e6,
                    ),
                )
                Text("Aperçu limité à 10 images/s et 640 px. Horodatages issus du décodeur. La piste audio est ignorée.")
            }
            Text(
                "Sources caméra et Meta : prévues, non connectées.\nVoix et calibration : non disponibles dans cette version.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text("Prototype expérimental · Aucune vidéo enregistrée par l’application.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun PlaybackState.label(): String =
    when (this) {
        PlaybackState.READY -> "PRÊT"
        PlaybackState.PLAYING -> "LECTURE"
        PlaybackState.STOPPED -> "ARRÊTÉ"
        PlaybackState.ENDED -> "FIN DE VIDÉO"
        PlaybackState.ERROR -> "ERREUR"
    }
