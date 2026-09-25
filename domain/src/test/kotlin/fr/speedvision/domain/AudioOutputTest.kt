package fr.speedvision.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputTest {
    @Test fun decisionDispatchStopsRejectedSpeechAndDoesNotQueueFailures() {
        val spoken = mutableListOf<String>()
        var stops = 0
        var available = true
        val output =
            object : AudioOutput {
                override fun speak(text: String): Boolean {
                    if (available) spoken.add(text)
                    return available
                }

                override fun stop() {
                    stops++
                }

                override fun close() {
                    stop()
                }
            }
        val decision = VoiceDecision.Speak(VoiceIdentity("live", 1, "calibration"), 18, "Rapprochement relatif, 18 kilomètres par heure.")
        assertTrue(output.apply(decision))
        assertFalse(output.apply(VoiceDecision.Silent(VoiceSilence.INTERVAL, false)))
        assertEquals(0, stops)
        assertFalse(output.apply(VoiceDecision.Silent(VoiceSilence.REJECTED)))
        assertEquals(1, stops)
        available = false
        assertFalse(output.apply(decision))
        available = true
        assertEquals(listOf(decision.text), spoken)
        output.close()
        assertEquals(2, stops)
    }
}
