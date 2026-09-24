package fr.speedvision.presentation

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.CameraCalibration
import fr.speedvision.domain.DepthObservation
import fr.speedvision.domain.DistanceEstimate
import fr.speedvision.domain.ImagePoint
import fr.speedvision.domain.PlateProfile
import fr.speedvision.domain.SpeedCsv
import fr.speedvision.geometry.CalibrationJson
import fr.speedvision.geometry.DistanceEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

/** Frozen-frame tool. Profiles/images never leave the process except an explicit JSON export. */
@Composable
fun CalibrationWorkbench(
    image: Bitmap,
    binding: CalibrationBinding,
    timestampUs: Long,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fx by remember { mutableStateOf("") }
    var fy by remember { mutableStateOf("") }
    var cx by remember { mutableStateOf("") }
    var cy by remember { mutableStateOf("") }
    var distortion by remember { mutableStateOf("0,0,0,0,0") }
    var provenance by remember { mutableStateOf("") }
    var rms by remember { mutableStateOf("") }
    var plateWidth by remember { mutableStateOf("") }
    var plateHeight by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("Dimensions mesurées") }
    var acknowledged by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf<CameraCalibration?>(null) }
    var points by remember { mutableStateOf<List<ImagePoint>>(emptyList()) }
    var result by remember { mutableStateOf<DistanceEstimate?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var centerX by remember { mutableFloatStateOf(.5f) }
    var centerY by remember { mutableFloatStateOf(.5f) }
    var revision by remember { mutableIntStateOf(0) }

    fun invalidate() {
        calibration = null
        result = null
        revision++
    }
    var exportText by remember { mutableStateOf("") }
    var annotationTrack by remember { mutableStateOf("") }
    var observationQuality by remember { mutableStateOf("") }
    var cameraStationary by remember { mutableStateOf(false) }
    val exportObservation =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                scope.launch {
                    message =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                output.bufferedWriter().use { writer -> writer.write(exportText) }
                            }
                            "Observation exportée. Rassembler une série du même véhicule avant estimation de vitesse."
                        }.getOrElse { "Export impossible." }
                }
            }
        }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                scope.launch {
                    message =
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                output.bufferedWriter().use { writer ->
                                    writer.write(exportText)
                                }
                            }
                            "Profil exporté. Aucune image exportée."
                        }.getOrElse { "Export impossible." }
                }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    invalidate()
                    message =
                        runCatching {
                            val imported =
                                withContext(Dispatchers.IO) {
                                    val text =
                                        requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                            val buffer = java.io.ByteArrayOutputStream()
                                            val chunk = ByteArray(4096)
                                            var count = it.read(chunk)
                                            while (count != -1) {
                                                require(buffer.size() + count <= 32_768)
                                                buffer.write(chunk, 0, count)
                                                count = it.read(chunk)
                                            }
                                            buffer.toByteArray().toString(Charsets.UTF_8)
                                        }
                                    CalibrationJson.decode(text)
                                }
                            require(imported.binding == binding) { "Source ou géométrie différente." }
                            fx = imported.fx.toString()
                            fy = imported.fy.toString()
                            cx = imported.cx.toString()
                            cy = imported.cy.toString()
                            distortion = imported.distortion.joinToString(",")
                            provenance = imported.provenance
                            rms = imported.validationRmsPx.toString()
                            plateWidth = (imported.plate.widthMeters * 1000).toString()
                            plateHeight = (imported.plate.heightMeters * 1000).toString()
                            profileName = imported.plate.name
                            acknowledged = false
                            "Profil chargé. Vérifiez le mode et appliquez-le explicitement."
                        }.getOrElse { "Profil refusé : schéma, paramètres ou source incompatibles." }
                }
            }
        }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Calibration et distance", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = close) { Text("Fermer") }
                Text(
                    "Image arrêtée · PTS ${String.format(
                        Locale.FRANCE,
                        "%.3f",
                        timestampUs / 1e6,
                    )} s. Mesure manuelle, pas de distance en direct.",
                )
                Text(
                    "1. Paramètres natifs avant crop/rotation : ${binding.nativeWidth} × ${binding.nativeHeight} px. Utilisez une calibration vérifiée sur des vues indépendantes.",
                )
                Text(
                    "Crop ${binding.cropLeft},${binding.cropTop} / ${binding.width} × ${binding.height} · rotation ${binding.rotation}°",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { import.launch(arrayOf("application/json", "text/plain")) },
                    enabled = !busy,
                ) { Text("Importer un profil JSON") }
                OutlinedButton(onClick = {
                    exportText =
                        org.json
                            .JSONObject()
                            .put("binding", CalibrationJson.bindingJson(binding))
                            .toString(2)
                    export.launch("speedvision-source.json")
                }, enabled = !busy) { Text("Exporter la géométrie pour la mire") }
                OutlinedTextField(fx, {
                    fx = it
                    invalidate()
                }, label = { Text("fx natif (px)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fy, {
                    fy = it
                    invalidate()
                }, label = { Text("fy natif (px)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cx, {
                    cx = it
                    invalidate()
                }, label = { Text("cx natif (px)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cy, {
                    cy = it
                    invalidate()
                }, label = { Text("cy natif (px)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(distortion, {
                    distortion = it
                    invalidate()
                }, label = { Text("k1,k2,p1,p2,k3 (point décimal)") }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Les cinq zéros ne conviennent que si l’absence de distorsion est vérifiée.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(provenance, {
                    provenance = it
                    invalidate()
                }, label = { Text("Provenance, caméra, mode, date") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rms, {
                    rms = it
                    invalidate()
                }, label = { Text("RMS sur vues de validation (px, ≤ 2)") }, modifier = Modifier.fillMaxWidth())
                Text("2. Dimensions physiques connues. Aucun format n’est reconnu automatiquement.")
                OutlinedButton(onClick = {
                    plateWidth = "520"
                    plateHeight = "110"
                    profileName = "Format supposé 520 × 110 mm"
                    invalidate()
                }) { Text("Profil 520 × 110 mm (à vérifier)") }
                OutlinedTextField(plateWidth, {
                    plateWidth = it
                    profileName = "Dimensions mesurées"
                    invalidate()
                }, label = { Text("Largeur réelle (mm)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(plateHeight, {
                    plateHeight = it
                    profileName = "Dimensions mesurées"
                    invalidate()
                }, label = { Text("Hauteur réelle (mm)") }, modifier = Modifier.fillMaxWidth())
                Row {
                    Checkbox(acknowledged, {
                        acknowledged = it
                        invalidate()
                    })
                    Text(
                        "Je confirme la source, le mode optique fixe (zoom/stabilisation), la calibration et les dimensions de cette plaque.",
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(enabled = acknowledged && !busy, onClick = {
                    invalidate()
                    message =
                        runCatching {
                            calibration =
                                CameraCalibration(
                                    binding,
                                    fx.toDouble(),
                                    fy.toDouble(),
                                    cx.toDouble(),
                                    cy.toDouble(),
                                    distortion.split(',').map { it.trim().toDouble() },
                                    provenance,
                                    rms.toDouble(),
                                    PlateProfile(profileName, plateWidth.toDouble() / 1000, plateHeight.toDouble() / 1000),
                                )
                            "Profil appliqué à cette source et ce mode."
                        }.getOrElse { "Paramètres invalides : vérifiez les valeurs, unités et RMS." }
                }) { Text("Appliquer la calibration") }
                OutlinedButton(enabled = calibration != null && !busy, onClick = {
                    exportText = CalibrationJson.encode(requireNotNull(calibration))
                    export.launch("speedvision-calibration-v1.json")
                }) { Text("Exporter le profil JSON") }
                message?.let { Text(it) }
                Text(
                    "3. Touchez les coins physiques dans l’ordre HG → HD → BD → BG. N’utilisez pas les coins d’une boîte de détection. Plaque entière, nette et plane requise.",
                )
                Text("Zoom ${String.format(Locale.FRANCE, "%.1f", zoom)}× · ${points.size}/4 coins")
                Slider(zoom, { zoom = it }, valueRange = 1f..8f)
                if (zoom > 1) {
                    Text("Centre horizontal / vertical")
                    Slider(centerX, { centerX = it })
                    Slider(centerY, { centerY = it })
                }
                Canvas(
                    Modifier.testTag("plate-corners").fillMaxWidth().height(360.dp).pointerInput(zoom, centerX, centerY, points) {
                        detectTapGestures { tap ->
                            val scale = min(size.width.toFloat() / image.width, size.height.toFloat() / image.height) * zoom
                            val dx = size.width / 2f - image.width * centerX * scale
                            val dy = size.height / 2f - image.height * centerY * scale
                            val p = ImagePoint(((tap.x - dx) / scale).toDouble(), ((tap.y - dy) / scale).toDouble())
                            if (points.size < 4 && p.x >= 0 && p.y >= 0 && p.x <= image.width - 1 && p.y <= image.height - 1) {
                                points = points + p
                                result = null
                                revision++
                            }
                        }
                    },
                ) {
                    val scale = min(size.width / image.width, size.height / image.height) * zoom
                    val dx = size.width / 2 - image.width * centerX * scale
                    val dy = size.height / 2 - image.height * centerY * scale
                    clipRect {
                        withTransform({
                            translate(dx, dy)
                            scale(scale, scale, Offset.Zero)
                        }) { drawImage(image.asImageBitmap()) }
                        points.forEachIndexed { index, p ->
                            val position = Offset(dx + p.x.toFloat() * scale, dy + p.y.toFloat() * scale)
                            drawCircle(if (index == 0) Color.Yellow else Color.Cyan, 5.dp.toPx(), position)
                            if (index > 0) {
                                val before = points[index - 1]
                                drawLine(
                                    Color.Cyan,
                                    Offset(dx + before.x.toFloat() * scale, dy + before.y.toFloat() * scale),
                                    position,
                                    2.dp.toPx(),
                                )
                            }
                        }
                    }
                }
                points.forEachIndexed { i, p -> Text("${listOf("HG", "HD", "BD", "BG")[i]} : ${p.x.toInt()}, ${p.y.toInt()} px") }
                OutlinedButton(onClick = {
                    points = emptyList()
                    result = null
                    revision++
                }) { Text("Effacer les coins") }
                Button(enabled = calibration != null && points.size == 4 && !busy, onClick = {
                    val profile = requireNotNull(calibration)
                    val observed = points.toList()
                    val request = revision
                    busy = true
                    scope.launch {
                        try {
                            val answer = withContext(Dispatchers.Default) { DistanceEstimator().estimate(profile, binding, observed) }
                            if (request == revision) result = answer
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "Calcul…" else "Estimer la profondeur axiale") }
                when (val answer = result) {
                    is DistanceEstimate.Accepted -> {
                        Text(
                            String.format(Locale.FRANCE, "Profondeur axiale Z : %.2f m", answer.axialMeters),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            String.format(
                                Locale.FRANCE,
                                "Reprojection : %.2f px · inclinaison : %.1f°",
                                answer.reprojectionRmsPx,
                                answer.tiltDegrees,
                            ),
                        )
                        Text(
                            "Incertitude métrique non quantifiée. Sensible aux coins, dimensions et calibration. La reprojection n’est pas une garantie de précision. Aucune vitesse calculée.",
                        )
                        Text(
                            "Exporter pour une série : identifiez manuellement le même véhicule entre images. Aucun ID du tracker n’est attribué ici.",
                        )
                        OutlinedTextField(annotationTrack, { annotationTrack = it }, label = { Text("ID annoté (entier positif)") })
                        OutlinedTextField(
                            observationQuality,
                            { observationQuality = it },
                            label = { Text("Qualité géométrique évaluée (0 à 1)") },
                        )
                        Text("Ce score doit suivre votre protocole de validation ; ce n’est pas une probabilité de précision.")
                        Row {
                            Checkbox(cameraStationary, { cameraStationary = it })
                            Text("Caméra fixe pendant toute la séquence de mesure.", modifier = Modifier.weight(1f))
                        }
                        OutlinedButton(
                            enabled =
                                cameraStationary &&
                                    annotationTrack.toLongOrNull()?.let { it >= 0 } == true &&
                                    observationQuality.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1.0 } == true,
                            onClick = {
                                val profile = requireNotNull(calibration)
                                val calibrationId =
                                    java.security.MessageDigest
                                        .getInstance("SHA-256")
                                        .digest(CalibrationJson.encode(profile).toByteArray())
                                        .joinToString("") { "%02x".format(it) }
                                val observation =
                                    DepthObservation(
                                        binding.sourceId,
                                        annotationTrack.toLong(),
                                        calibrationId,
                                        timestampUs,
                                        answer.axialMeters,
                                        observationQuality.toDouble(),
                                        true,
                                        true,
                                        true,
                                    )
                                exportText = SpeedCsv.encodeObservations(listOf(observation))
                                exportObservation.launch("speedvision-depth-$timestampUs.csv")
                            },
                        ) { Text("Exporter cette observation CSV") }
                    }
                    is DistanceEstimate.Rejected -> Text("Mesure rejetée : ${answer.reason}", color = MaterialTheme.colorScheme.error)
                    null -> Text("Aucune distance validée sur cette image.")
                }
            }
        }
    }
}
