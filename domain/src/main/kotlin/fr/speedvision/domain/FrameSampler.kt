package fr.speedvision.domain

/** Select frames by source PTS, never by decoder completion time. New instance per replay. */
class FrameSampler(
    private val intervalUs: Long = 100_000L,
) {
    init {
        require(intervalUs > 0)
    }

    private var lastAcceptedUs: Long? = null

    fun accept(presentationTimeUs: Long): Boolean {
        if (presentationTimeUs < 0) return false
        val last = lastAcceptedUs
        if (last != null && presentationTimeUs - last < intervalUs) return false
        lastAcceptedUs = presentationTimeUs
        return true
    }
}
