package fr.speedvision.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.speedvision.audio.AndroidAudioOutput

@Composable
fun VoiceWorkbench(close: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val output = remember { AndroidAudioOutput(context) }
    val state by output.state.collectAsStateWithLifecycle()
    var settingsError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(output, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) output.enable(false) }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            output.close()
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Voix / test audio", style = MaterialTheme.typography.headlineSmall)
                Text("Aucune vitesse live disponible. Les séries CSV et l’exemple synthétique ne déclenchent pas d’annonce.")
                Text(state.message)
                Text("Activer le test vocal")
                Switch(checked = state.enabled, onCheckedChange = output::enable, enabled = state.ready)
                OutlinedButton(
                    onClick = { output.speak(AndroidAudioOutput.TEST_PHRASE) },
                    enabled =
                        state.enabled && state.ready && !state.speaking,
                ) {
                    Text("Tester la voix sans mesure")
                }
                Text("Phrase : ${AndroidAudioOutput.TEST_PHRASE}")
                OutlinedButton(onClick = { output.enable(false) }) { Text("Couper la voix") }
                Text("Sorties disponibles : ${state.outputs.joinToString().ifBlank { "aucune signalée" }}")
                Text(
                    "La liste ne confirme pas la sortie active. Choisir téléphone ou Bluetooth dans Android et vérifier à l’écoute. Aucune liaison Meta dédiée.",
                )
                OutlinedButton(onClick = {
                    output.enable(false)
                    try {
                        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                    } catch (
                        _: ActivityNotFoundException,
                    ) {
                        settingsError = "Réglages audio indisponibles sur cet appareil."
                    }
                }) { Text("Réglages audio Android") }
                settingsError?.let { Text(it) }
                OutlinedButton(onClick = close) { Text("Fermer la voix") }
            }
        }
    }
}
