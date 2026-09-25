package fr.speedvision.domain

/** Platform adapter. A successful return means submission, never proof that a listener heard speech. */
interface AudioOutput : AutoCloseable {
    fun speak(text: String): Boolean

    fun stop()

    fun apply(decision: VoiceDecision): Boolean =
        when (decision) {
            is VoiceDecision.Speak -> speak(decision.text)
            is VoiceDecision.Silent -> {
                if (decision.stopCurrent) stop()
                false
            }
        }
}
