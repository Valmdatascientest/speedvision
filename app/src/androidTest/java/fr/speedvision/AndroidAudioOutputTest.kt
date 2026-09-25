package fr.speedvision

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.audio.AndroidAudioOutput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AndroidAudioOutputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 12_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("Timed out waiting for real Android TTS", condition())
    }

    @Test fun realEngineInitializationIsSilentAndShutdownIsIdempotent() {
        lateinit var output: AndroidAudioOutput
        instrumentation.runOnMainSync { output = AndroidAudioOutput(instrumentation.targetContext) }
        try {
            waitFor { !output.state.value.initializing }
            assertFalse(output.state.value.enabled)
            assertFalse(output.state.value.speaking)
            instrumentation.runOnMainSync {
                assertFalse(output.speak(AndroidAudioOutput.TEST_PHRASE))
                output.close()
                output.close()
                output.enable(true)
                assertFalse(output.speak(AndroidAudioOutput.TEST_PHRASE))
            }
            assertFalse(output.state.value.ready)
            assertFalse(output.state.value.enabled)
        } finally {
            instrumentation.runOnMainSync { output.close() }
        }
    }

    @Test fun installedOfflineVoiceSubmitsRealSpeechAndCanBeStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var output: AndroidAudioOutput
            scenario.onActivity { output = AndroidAudioOutput(it) }
            try {
                waitFor { !output.state.value.initializing }
                val required = InstrumentationRegistry.getArguments().getString("requireOfflineVoice") == "true"
                if (required) assertTrue(output.state.value.message, output.state.value.ready)
                assumeTrue("Offline French voice unavailable: ${output.state.value.message}", output.state.value.ready)
                scenario.onActivity {
                    output.enable(true)
                    assertTrue(output.speak(AndroidAudioOutput.TEST_PHRASE))
                }
                waitFor {
                    output.state.value.message == "Lecture vocale en cours." ||
                        output.state.value.message
                            .startsWith("Lecture terminée") ||
                        !output.state.value.ready
                }
                assertTrue(output.state.value.message, output.state.value.ready)
                scenario.onActivity { output.enable(false) }
                assertFalse(output.state.value.speaking)
                assertFalse(output.state.value.enabled)
            } finally {
                instrumentation.runOnMainSync { output.close() }
            }
        }
    }
}
