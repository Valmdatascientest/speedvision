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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.speedvision.domain.CalibrationMode
import fr.speedvision.domain.PlaybackState
import fr.speedvision.domain.SpeedEstimate
import fr.speedvision.domain.TrackStatus
import fr.speedvision.domain.mode
import fr.speedvision.meta.MetaSupport
import fr.speedvision.presentation.CalibrationWorkbench
import fr.speedvision.presentation.PreviewState
import fr.speedvision.presentation.PreviewViewModel
import fr.speedvision.presentation.SpeedWorkbench
import fr.speedvision.presentation.VoiceWorkbench
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
            var calibrationOpen by remember { mutableStateOf(false) }
            var metaOpen by remember { mutableStateOf(false) }
            var voiceOpen by remember { mutableStateOf(false) }
            var speedOpen by remember { mutableStateOf(false) }
            var fullscreen by remember { mutableStateOf(false) }
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
                    model::nextFrame,
                    model::previousFrame,
                    model::debug,
                    fullscreen = fullscreen,
                    toggleFullscreen = { fullscreen = !fullscreen },
                    camera = {
                        model.stop()
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            model.selectCamera(this, window.decorView.display.rotation)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    cameraFixed = model::cameraFixed,
                    detection = model::detection,
                    meta = {
                        model.stop()
                        metaOpen = true
                    },
                    voice = {
                        model.stop()
                        voiceOpen = true
                    },
                    speed = {
                        model.stop()
                        speedOpen = true
                    },
                    calibration = {
                        model.stop()
                        calibrationOpen = true
                    },
                )
                if (metaOpen) {
                    MetaSupport.Panel({ id ->
                        model.selectMeta(id)
                        metaOpen = false
                    }, { metaOpen = false })
                }
                if (voiceOpen) VoiceWorkbench { voiceOpen = false }
                if (speedOpen) SpeedWorkbench { speedOpen = false }
                val image = state.image
                val binding = state.calibrationBinding
                if (calibrationOpen && image != null && binding != null) {
                    CalibrationWorkbench(
                        image,
                        binding,
                        state.ptsUs,
                        { calibrationOpen = false },
                        model::applyCalibration,
                        state.liveObservations,
                    )
                }
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
    nextFrame: () -> Unit,
    previousFrame: () -> Unit,
    debug: (Boolean) -> Unit,
    fullscreen: Boolean = false,
    toggleFullscreen: () -> Unit = {},
    camera: () -> Unit = {},
    cameraFixed: (Boolean) -> Unit = {},
    detection: (Boolean) -> Unit = {},
    calibration: () -> Unit = {},
    speed: () -> Unit = {},
    voice: () -> Unit = {},
    meta: () -> Unit = {},
) {
    if (fullscreen) {
        FullscreenDetectionView(state, toggleFullscreen)
        return
    }
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
            Text("LAB / 08     ·     ${state.sourceLabel}", color = MaterialTheme.colorScheme.primary)
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
                        val labelPaint =
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE
                                textSize = 14.dp.toPx()
                                setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
                            }
                        state.tracking?.tracks?.forEach { track ->
                            val box = track.box
                            if (track.status == TrackStatus.LOST) {
                                drawRect(
                                    Color.Gray,
                                    Offset(dx + box.left * scale, dy + box.top * scale),
                                    Size(box.width * scale, box.height * scale),
                                    style = Stroke(1.dp.toPx()),
                                )
                            }
                            val status =
                                when (track.status) {
                                    TrackStatus.TENTATIVE -> "provisoire"
                                    TrackStatus.CONFIRMED -> "confirmée"
                                    TrackStatus.LOST -> "perdue"
                                }
                            drawContext.canvas.nativeCanvas.drawText(
                                "#${track.id} $status",
                                (dx + box.left * scale).coerceIn(0f, size.width),
                                (dy + box.top * scale - 4.dp.toPx()).coerceIn(labelPaint.textSize, size.height),
                                labelPaint,
                            )
                        }
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
                Button(onClick = start, enabled = state.selected && state.state != PlaybackState.PLAYING) { Text("Continuer la lecture") }
                OutlinedButton(onClick = stop, enabled = state.state == PlaybackState.PLAYING) { Text("STOP") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = previousFrame,
                    enabled = state.selected && !state.camera && state.state != PlaybackState.PLAYING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Image précédente")
                }
                OutlinedButton(
                    onClick = nextFrame,
                    enabled = state.selected && !state.camera && state.state != PlaybackState.PLAYING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Image suivante")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Détection véhicules / plaques")
                Switch(state.detectionEnabled, detection)
            }
            state.detectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.detections?.let {
                Text("${it.vehicles.size} véhicule(s) · ${it.plates.size} plaque(s) candidate(s)")
                Text(
                    "Vert : véhicule · Jaune : plaque. Identités expérimentales : provisoire, confirmée ou perdue.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.tracking?.let { tracking ->
                Text(
                    "Suivi : ${tracking.tracks.count { it.status == TrackStatus.CONFIRMED }} confirmée(s) · " +
                        "${tracking.tracks.count { it.status == TrackStatus.LOST }} perdue(s)",
                )
                Text("${tracking.plates.size} plaque(s) rattachée(s) à une piste confirmée")
            }
            OutlinedButton(onClick = calibration, enabled = state.image != null && state.calibrationBinding != null) {
                Text("Calibration / distance sur image arrêtée")
            }
            OutlinedButton(onClick = toggleFullscreen, enabled = state.image != null, modifier = Modifier.fillMaxWidth()) {
                Text("Affichage plein écran · détection vitesse")
            }
            if (BuildConfig.META_ENABLED) OutlinedButton(onClick = meta) { Text("Lunettes Meta") }
            OutlinedButton(onClick = speed) { Text("Laboratoire de vitesse / CSV") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val live = state.liveSpeed) {
                        is SpeedEstimate.Accepted -> {
                            Text("Mode : ${state.cameraCalibration?.mode?.label() ?: "INACTIF"}")
                            Text(
                                String.format(Locale.FRANCE, "Vitesse relative : %+.1f km/h", live.closingKmh),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                String.format(
                                    Locale.FRANCE,
                                    "Distance : %.2f m · Qualité : %.2f",
                                    live.depthAtReferenceMeters,
                                    live.qualityScore,
                                ),
                            )
                        }
                        is SpeedEstimate.Rejected -> {
                            Text("Mode : ${state.cameraCalibration?.mode?.label() ?: "INACTIF"}")
                            Text("Vitesse relative : —", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Distance : ${state.liveDepthMeters?.let {
                                    String.format(
                                        Locale.FRANCE,
                                        "%.2f m",
                                        it,
                                    )
                                } ?: "—"} · ${live.reason}",
                            )
                        }
                    }
                    Text(
                        if (state.cameraCalibration?.mode == CalibrationMode.APPROXIMATE) {
                            "Profil approximatif actif. Effectuez une calibration pour améliorer la précision."
                        } else {
                            "La vitesse live exige une caméra fixe déclarée et une piste stable."
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Caméra fixe déclarée")
                Switch(checked = state.cameraFixed, onCheckedChange = cameraFixed)
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
                        "Détection CPU p50 / p95 : %.0f / %.0f ms (%d mesures)\n" +
                            "Images ignorées après décodage : %d\nTemps depuis décodage : %.0f ms",
                        state.p50,
                        state.p95,
                        state.samples,
                        state.skipped,
                        state.processingAgeMillis,
                    ),
                )
                state.backgroundMotion?.let { motion ->
                    Text("Fond : ${motion.reason}")
                    Text(
                        String.format(
                            Locale.FRANCE,
                            "%d/%d points · %d/16 zones · résidu p95 %.2f px (image réduite) · %.0f ms",
                            motion.inliers,
                            motion.candidates,
                            motion.occupiedCells,
                            motion.residualP95Px,
                            motion.computeMillis,
                        ),
                    )
                    Text("Rotation et translation métrique non séparées. Caméra immobile non certifiée. Aucun capteur IMU associé.")
                }
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
            OutlinedButton(onClick = voice) { Text("Voix / test audio") }
            Text(
                if (BuildConfig.META_ENABLED) {
                    "Voix : test explicite. Meta : connexion via le panneau lunettes."
                } else {
                    "Voix : test explicite. SDK Meta non inclus dans ce build."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text("Prototype expérimental · Aucune vidéo enregistrée par l’application.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FullscreenDetectionView(
    state: PreviewState,
    exit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        state.image?.let { image ->
            Image(
                image.asImageBitmap(),
                "Image de détection en plein écran",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Card(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Mode : ${state.cameraCalibration?.mode?.label() ?: "INACTIF"}")
                when (val live = state.liveSpeed) {
                    is SpeedEstimate.Accepted -> {
                        Text(String.format(Locale.FRANCE, "%.1f km/h", live.closingKmh), style = MaterialTheme.typography.headlineMedium)
                        Text(String.format(Locale.FRANCE, "Distance %.2f m · qualité %.2f", live.depthAtReferenceMeters, live.qualityScore))
                    }
                    is SpeedEstimate.Rejected -> {
                        Text("Vitesse : —", style = MaterialTheme.typography.headlineMedium)
                        Text("Mesures : ${live.sampleCount} · ${live.reason}")
                    }
                }
            }
        }
        OutlinedButton(onClick = exit, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("Quitter")
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

private fun CalibrationMode.label(): String =
    when (this) {
        CalibrationMode.APPROXIMATE -> "APPROXIMATIF"
        CalibrationMode.CALIBRATED -> "CALIBRÉ"
    }
