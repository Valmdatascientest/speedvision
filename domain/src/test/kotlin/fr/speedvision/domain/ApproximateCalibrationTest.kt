package fr.speedvision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApproximateCalibrationTest {
    @Test
    fun createsUnsavedProfileForExactBinding() {
        val binding = CalibrationBinding("video", 1920, 1080, 0, 0, 1920, 1080, 0)
        val profile = approximateCalibration(binding)
        assertEquals(binding, profile.binding)
        assertEquals(CalibrationMode.APPROXIMATE, profile.mode)
        assertTrue(profile.provenance.startsWith("automatic-approximate-profile-v1"))
    }
}
