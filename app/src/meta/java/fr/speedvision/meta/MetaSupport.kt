package fr.speedvision.meta

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import fr.speedvision.domain.VideoSource
import kotlinx.coroutines.CoroutineScope

object MetaSupport {
    fun createSource(
        scope: CoroutineScope,
        deviceId: String,
    ): VideoSource = MetaGlassesVideoSource(scope, deviceId)

    @Composable
    fun Panel(
        onSelect: (String) -> Unit,
        close: () -> Unit,
    ) {
        val activity = requireNotNull(LocalActivity.current)
        var initialized by remember { mutableStateOf(false) }
        var granted by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("Activer la connexion puis inscrire l’application dans Meta AI.") }
        val permission =
            rememberLauncherForActivityResult(Wearables.RequestPermissionContract()) { result ->
                granted = result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted
                message = if (granted) "Caméra Meta autorisée. Choisir les lunettes, puis START." else "Caméra Meta refusée."
            }

        fun initialize() {
            initialized = runCatching { Wearables.initialize(activity.applicationContext).isSuccess }.getOrDefault(false)
            message =
                if (initialized) {
                    "SDK prêt. Vérifier l’inscription Meta et la permission caméra."
                } else {
                    "Initialisation Meta impossible. Vérifier Meta AI et la configuration de l’application."
                }
        }
        val bluetooth =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                if (allowed) initialize() else message = "Bluetooth refusé."
            }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Lunettes Meta · DAT 1.0.0", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Connexion via Meta AI. Aucun enregistrement ni microphone. Le flux s’arrête en arrière-plan ; la reprise est manuelle.",
                    )
                    Text(message)
                    if (!initialized) {
                        OutlinedButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 31) bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) else initialize()
                        }) { Text("Activer la connexion Meta") }
                    }
                    if (initialized) {
                        LaunchedEffect(Unit) {
                            Wearables.registrationErrorStream.collect { message = "Inscription Meta interrompue : $it" }
                        }
                        val registration by Wearables.registrationState.collectAsStateWithLifecycle()
                        val devices by Wearables.devices.collectAsStateWithLifecycle()
                        Text("Inscription : $registration · ${devices.size} appareil(s)")
                        OutlinedButton(onClick = {
                            runCatching { Wearables.startRegistration(activity) }.onFailure {
                                message =
                                    "Ouverture de Meta AI impossible."
                            }
                        }) { Text("Inscrire dans Meta AI") }
                        OutlinedButton(
                            onClick = {
                                granted = false
                                permission.launch(Permission.CAMERA)
                            },
                            enabled = registration == RegistrationState.REGISTERED,
                        ) { Text("Autoriser la caméra des lunettes") }
                        if (devices.isEmpty()) Text("Aucune lunette disponible. Vérifier appairage, port des lunettes et firmware.")
                        devices.sortedBy { it.identifier }.forEachIndexed { index, device ->
                            key(device) {
                                val info = Wearables.devicesMetadata[device]?.collectAsStateWithLifecycle()?.value
                                Text("${info?.name.orEmpty()} · ${info?.deviceType ?: "inconnu"} · ${info?.compatibility ?: "inconnue"}")
                                OutlinedButton(
                                    onClick = { onSelect(device.identifier) },
                                    enabled =
                                        granted && registration == RegistrationState.REGISTERED,
                                ) {
                                    Text("Utiliser les lunettes ${index + 1}")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = close) { Text("Fermer Meta") }
                }
            }
        }
    }
}
