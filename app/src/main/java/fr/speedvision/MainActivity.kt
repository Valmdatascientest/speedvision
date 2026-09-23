package fr.speedvision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.speedvision.domain.PlaybackState
import fr.speedvision.presentation.PreviewState
import fr.speedvision.presentation.PreviewViewModel
import java.util.Locale
import kotlin.math.min

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
            val cameraPermission =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) model.selectCamera(this, window.decorView.display.rotation) else model.cameraDenied()
                }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7FE0C3), background = Color(0xFF10191E))) {
                PreviewScreen(
                    state,
                    { picker.launch(arrayOf("video/*")) },
                    model::start,
                    model::stop,
                    model::debug,
                    camera = {
                        model.stop()
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            model.selectCamera(this, window.decorView.display.rotation)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    detection = model::detection,
                )
            }
        }
    }

    override fun onDestroy() {
        model.detachCamera()
        super.onDestroy()
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
    camera: () -> Unit = {},
    detection: (Boolean) -> Unit = {},
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
            Text("LAB / 02     ·     ${state.sourceLabel}", color = MaterialTheme.colorScheme.primary)
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val image = state.image
                if (image != null) {
                    Image(image.asImageBitmap(), "Image analysée", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val scale = min(size.width / image.width, size.height / image.height)
                        val dx = (size.width - image.width * scale) / 2
                        val dy = (size.height - image.height * scale) / 2
                        state.detections?.let { result ->
                            val boxes =
                                result.vehicles.map { it.box to Color(0xFF7FE0C3) } +
                                    result.plates.map { it.detection.box to Color(0xFFFFCF70) }
                            for ((box, color) in boxes) {
                                drawRect(
                                    color,
                                    Offset(dx + box.left * scale, dy + box.top * scale),
                                    Size(
                                        box.width * scale,
                                        box.height * scale,
                                    ),
                                    style = Stroke(2.dp.toPx()),
                                )
                            }
                        }
                    }
                } else {
                    Text("Sélectionnez une vidéo pour commencer")
                }
            }
            Text("État : ${state.state.label()}")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = select, modifier = Modifier.fillMaxWidth()) { Text("Choisir une vidéo") }
            OutlinedButton(onClick = camera, modifier = Modifier.fillMaxWidth()) { Text("Caméra du téléphone") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = start, enabled = state.selected && state.state != PlaybackState.PLAYING) { Text("START · Rejouer") }
                OutlinedButton(onClick = stop, enabled = state.state == PlaybackState.PLAYING) { Text("STOP") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Détection véhicules / plaques")
                Switch(state.detectionEnabled, detection)
            }
            state.detectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.detections?.let {
                Text("${it.vehicles.size} véhicule(s) · ${it.plates.size} plaque(s) candidate(s)")
                Text("Vert : véhicule · Jaune : plaque. Détections expérimentales, sans suivi.", style = MaterialTheme.typography.bodySmall)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vitesse relative : —", style = MaterialTheme.typography.titleLarge)
                    Text("Distance : —     ·     Confiance : —")
                    Text("Mesure indisponible : suivi, calibration et estimation de vitesse prévus aux prochains sprints.")
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
                Text(
                    String.format(
                        Locale.FRANCE,
                        "Pipeline CPU p50 / p95 : %.0f / %.0f ms (%d mesures)\n" +
                            "Images ignorées après décodage : %d\nTemps depuis décodage : %.0f ms",
                        state.p50,
                        state.p95,
                        state.samples,
                        state.skipped,
                        state.processingAgeMillis,
                    ),
                )
                state.detections?.let {
                    Text(
                        String.format(
                            Locale.FRANCE,
                            "Véhicules : %.0f ms · Plaques : %.0f ms\nZones traitées : %d · Omises : %d",
                            it.vehicleMillis,
                            it.plateMillis,
                            it.roisProcessed,
                            it.roisOmitted,
                        ),
                    )
                }
                Text(
                    "Pixels natifs conservés. Vidéo : 10 images/s maximum ; caméra : dernière image disponible. " +
                        "Un score de détection ne représente pas une confiance de vitesse.",
                )
            }
            Text(
                "Meta, voix et calibration : non disponibles dans cette version.",
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
