package fr.speedvision.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.MainThread
import fr.speedvision.domain.AudioOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioState(
    val initializing: Boolean = true,
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val speaking: Boolean = false,
    val message: String = "Initialisation TTS…",
    val outputs: List<String> = emptyList(),
)

/** Main-thread owner, offline French voice only. Android controls the actual media output route. */
@MainThread
class AndroidAudioOutput(
    context: Context,
) : AudioOutput {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mutable = MutableStateFlow(AudioState())
    val state = mutable.asStateFlow()
    private var closed = false
    private var initializing = true
    private var sequence = 0L
    private var currentUtterance: String? = null
    private var deviceIds: Set<Int>? = null
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (!closed && change != AudioManager.AUDIOFOCUS_GAIN) disable("Focus audio perdu : réactiver la voix.")
            }, main)
            .build()
    private val devices =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevices()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevices()
        }
    private val initTimeout =
        Runnable {
            if (!closed && initializing) {
                initializing = false
                fail("Initialisation TTS expirée.")
            }
        }
    private val utteranceTimeout =
        Runnable {
            if (!closed && currentUtterance != null) disable("Délai audio dépassé : réactiver la voix.")
        }
    private var engine: TextToSpeech? = null

    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        refreshDevices()
        audio.registerAudioDeviceCallback(devices, main)
        main.postDelayed(initTimeout, 10_000)
        engine = TextToSpeech(context.applicationContext) { status -> main.post { initialized(status) } }
    }

    private fun initialized(status: Int) {
        if (closed || !initializing) return
        initializing = false
        main.removeCallbacks(initTimeout)
        val tts = engine ?: return fail("Moteur TTS indisponible.")
        if (status != TextToSpeech.SUCCESS) return fail("Moteur TTS indisponible.")
        val voice =
            tts.voices
                .orEmpty()
                .filter {
                    it.locale.language == "fr" &&
                        !it.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                }.sortedBy { it.name }
                .firstOrNull()
        if (voice == null) return fail("Voix française hors ligne absente. Installer les données vocales dans Android.")
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS || tts.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) {
            return fail("Configuration TTS refusée.")
        }
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post {
                        if (!closed &&
                            currentUtterance == utteranceId
                        ) {
                            mutable.value =
                                mutable.value.copy(speaking = true, message = "Lecture vocale en cours.")
                        }
                    }
                }

                override fun onDone(utteranceId: String?) {
                    main.post {
                        if (!closed && currentUtterance == utteranceId) {
                            stop()
                            mutable.value = mutable.value.copy(message = "Lecture terminée ; sortie physique à vérifier.")
                        }
                    }
                }

                @Deprecated("Legacy callback required by Android")
                override fun onError(utteranceId: String?) {
                    error(utteranceId)
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    error(utteranceId)
                }

                private fun error(id: String?) {
                    main.post {
                        if (!closed && currentUtterance == id) fail("Échec de synthèse TTS. Fermer puis rouvrir pour réessayer.")
                    }
                }
            },
        )
        mutable.value = mutable.value.copy(initializing = false, ready = true, message = "Voix française hors ligne prête.")
    }

    fun enable(value: Boolean) {
        if (closed) return
        stop()
        mutable.value = mutable.value.copy(enabled = value && mutable.value.ready)
    }

    private fun disable(message: String) {
        stop()
        mutable.value = mutable.value.copy(enabled = false, message = message)
    }

    private fun fail(message: String) {
        disable(message)
        mutable.value = mutable.value.copy(initializing = false, ready = false)
    }

    private fun refreshDevices() {
        if (closed) return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val ids = outputs.map { it.id }.toSet()
        if (deviceIds != null && deviceIds != ids) disable("Sorties audio modifiées : vérifier le routage puis réactiver.")
        deviceIds = ids
        mutable.value =
            mutable.value.copy(
                outputs =
                    outputs
                        .map { device ->
                            when (device.type) {
                                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Haut-parleur"
                                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Écouteur téléphone"
                                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                                AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                                -> "Bluetooth"
                                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Casque filaire"
                                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
                                else -> "Autre sortie"
                            }
                        }.distinct(),
            )
    }

    override fun speak(text: String): Boolean {
        if (closed || !mutable.value.enabled || !mutable.value.ready || text.isBlank() || text.length > 300) return false
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            disable("Focus audio refusé : réactiver au premier plan.")
            return false
        }
        val id = "speedvision-${++sequence}"
        currentUtterance = id
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            fail("Envoi TTS refusé.")
            return false
        }
        mutable.value = mutable.value.copy(speaking = true, message = "Texte transmis au moteur TTS.")
        // A missing terminal callback must not hold focus or leave a stale utterance indefinitely.
        main.postDelayed(utteranceTimeout, 15_000)
        return true
    }

    override fun stop() {
        main.removeCallbacks(utteranceTimeout)
        currentUtterance = null
        engine?.stop()
        audio.abandonAudioFocusRequest(focus)
        mutable.value = mutable.value.copy(speaking = false)
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        main.removeCallbacksAndMessages(null)
        audio.unregisterAudioDeviceCallback(devices)
        engine?.shutdown()
        engine = null
        mutable.value = mutable.value.copy(initializing = false, ready = false, enabled = false, message = "Audio fermé.")
    }

    companion object {
        const val TEST_PHRASE = "Test audio SpeedVision. Aucune mesure de vitesse annoncée."
    }
}
