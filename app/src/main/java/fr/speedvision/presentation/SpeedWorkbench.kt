package fr.speedvision.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.speedvision.domain.DepthObservation
import fr.speedvision.domain.SpeedCsv
import fr.speedvision.domain.SpeedEstimate
import fr.speedvision.domain.SpeedRejection
import fr.speedvision.domain.SpeedReplay
import fr.speedvision.domain.SpeedReplayRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Explicit offline replay, deliberately separate from the live preview. No persistence on open. */
@Composable
fun SpeedWorkbench(close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SpeedReplayRow>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("Aucune série chargée") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    fun load(
        observations: suspend () -> List<DepthObservation>,
        name: String,
    ) {
        rows = emptyList()
        index = 0
        error = null
        busy = true
        label = name
        scope.launch {
            try {
                rows =
                    withContext(Dispatchers.Default) {
                        val input = observations()
                        val task = coroutineContext
                        SpeedReplay.run(input) { task.ensureActive() }
                    }
                index = rows.lastIndex.coerceAtLeast(0)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Série refusée : vérifiez le schéma CSV, les identifiants et les nombres finis."
            } finally {
                busy = false
            }
        }
    }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                load({
                    val text =
                        withContext(Dispatchers.IO) {
                            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                                val buffer = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(4096)
                                var n = input.read(chunk)
                                while (n != -1) {
                                    require(buffer.size() + n <= SpeedCsv.MAX_CHARS)
                                    buffer.write(chunk, 0, n)
                                    n = input.read(chunk)
                                }
                                buffer.toByteArray().toString(Charsets.UTF_8)
                            }
                        }
                    SpeedCsv.decode(text)
                }, "Série importée · déclarations non vérifiées automatiquement")
            }
        }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { writer ->
                                writer.write(exportText)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "Export impossible."
                    }
                }
            }
        }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Laboratoire de vitesse", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = close) { Text("Fermer") }
                Text("Lecture de profondeurs horodatées, sans vitesse en direct. Caméra fixe requise ; mouvement non compensé refusé.")
                Text(
                    "CSV : temps source en µs, profondeur axiale en m, identité et calibration constantes. Le score est une qualité heuristique, pas une précision.",
                )
                OutlinedButton(enabled = !busy, onClick = {
                    import.launch(arrayOf("text/*", "application/csv", "application/octet-stream"))
                }) { Text("Importer une série CSV") }
                OutlinedButton(enabled = !busy, onClick = {
                    load({
                        (0..20).map { i ->
                            DepthObservation(
                                "synthetic-example",
                                1,
                                "synthetic-known",
                                i * 100_000L,
                                30 - i * .5,
                                .95,
                                true,
                                true,
                                true,
                            )
                        }
                    }, "EXEMPLE SYNTHÉTIQUE · approche de 18 km/h, aucune mesure réelle")
                }) { Text("Charger un exemple synthétique") }
                Text(if (busy) "Analyse…" else label)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (rows.isNotEmpty()) {
                    val accepted = rows.count { it.estimate is SpeedEstimate.Accepted }
                    Text("${rows.size} observations · $accepted acceptées · ${rows.size - accepted} rejetées")
                    if (rows.size > 1) Slider(index.toFloat(), { index = it.roundToInt() }, valueRange = 0f..rows.lastIndex.toFloat())
                    val row = rows[index]
                    Text("Observation ${index + 1}/${rows.size} · piste ${row.observation.trackId}")
                    Text(
                        String.format(
                            Locale.FRANCE,
                            "Temps source %.3f s · Z observé %.2f m",
                            row.observation.timestampUs / 1e6,
                            row.observation.depthMeters,
                        ),
                    )
                    when (val answer = row.estimate) {
                        is SpeedEstimate.Accepted -> {
                            Text(
                                String.format(Locale.FRANCE, "Vitesse relative axiale : %+.1f km/h", answer.closingKmh),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (answer.closingMps >
                                    .1
                                ) {
                                    "Rapprochement"
                                } else if (answer.closingMps < -.1) {
                                    "Éloignement"
                                } else {
                                    "Variation faible"
                                },
                            )
                            Text(
                                String.format(
                                    Locale.FRANCE,
                                    "Référence %.3f s · fenêtre %.2f s · %d/%d observations retenues",
                                    answer.referenceTimeUs / 1e6,
                                    answer.spanUs / 1e6,
                                    answer.inlierCount,
                                    answer.sampleCount,
                                ),
                            )
                            Text(
                                String.format(
                                    Locale.FRANCE,
                                    "Qualité : %.2f · résidu %.3f m",
                                    answer.qualityScore,
                                    answer.residualRmsMeters,
                                ),
                            )
                            Text(
                                "Incertitude métrique non quantifiée. Résultat au centre de la fenêtre, pas une vitesse routière absolue ni une vitesse instantanée garantie.",
                            )
                        }
                        is SpeedEstimate.Rejected -> {
                            Text("Vitesse relative axiale : —", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Rejet : ${answer.reason.description()} · ${answer.sampleCount} observations",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(String.format(Locale.FRANCE, "Calcul de cette observation : %.2f ms", row.computeMillis))
                    OutlinedButton(enabled = !busy, onClick = {
                        exportText = SpeedCsv.encodeResults(rows)
                        export.launch("speedvision-speed-results.csv")
                    }) { Text("Exporter les résultats CSV") }
                    OutlinedButton(enabled = !busy, onClick = {
                        exportText = SpeedCsv.encodeObservations(rows.map { it.observation })
                        export.launch("speedvision-depth-observations.csv")
                    }) { Text("Exporter les observations CSV") }
                    OutlinedButton(enabled = !busy, onClick = {
                        rows = emptyList()
                        label = "Aucune série chargée"
                        error = null
                    }) { Text("Effacer la série en mémoire") }
                }
                Text(
                    "Aucun fichier créé automatiquement. Annuler le sélecteur annule l’export. Fermer efface la session ; les fichiers déjà exportés restent au lieu choisi.",
                )
            }
        }
    }
}

private fun SpeedRejection.description(): String =
    when (this) {
        SpeedRejection.NO_DATA -> "aucune observation"
        SpeedRejection.INVALID_INPUT -> "profondeur, temps ou identifiant invalide"
        SpeedRejection.GEOMETRY -> "géométrie non validée"
        SpeedRejection.CAMERA_MOVING_OR_UNKNOWN -> "caméra mobile ou mouvement inconnu"
        SpeedRejection.TRACK_LOST -> "véhicule perdu"
        SpeedRejection.LOW_INPUT_QUALITY -> "qualité de l’observation insuffisante"
        SpeedRejection.TIME_ORDER -> "temps dupliqué ou inversé"
        SpeedRejection.GAP -> "interruption trop longue"
        SpeedRejection.IDENTITY_CHANGED -> "source, véhicule ou calibration changé"
        SpeedRejection.WARMUP -> "pas assez d’observations ou de durée"
        SpeedRejection.INSUFFICIENT_INLIERS -> "trop peu d’observations cohérentes"
        SpeedRejection.LATEST_OUTLIER -> "dernière profondeur aberrante"
        SpeedRejection.RESIDUALS -> "écarts au modèle trop importants"
        SpeedRejection.NON_LINEAR_MOTION -> "variation de mouvement trop importante"
        SpeedRejection.LOW_OUTPUT_QUALITY -> "qualité de la fenêtre insuffisante"
        SpeedRejection.OUT_OF_RANGE -> "résultat hors domaine expérimental"
        SpeedRejection.STALE -> "observations périmées"
    }
