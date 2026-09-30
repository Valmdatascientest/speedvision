package fr.speedvision.data

import android.content.Context
import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.domain.CameraCalibration
import fr.speedvision.geometry.CalibrationJson
import java.security.MessageDigest

/** Stores explicit calibration profiles locally, keyed by the exact source geometry. */
class CalibrationRepository(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun save(profile: CameraCalibration) {
        preferences.edit().putString(key(profile.binding), CalibrationJson.encode(profile)).apply()
    }

    fun load(binding: CalibrationBinding): CameraCalibration? =
        preferences.getString(key(binding), null)?.let { text ->
            runCatching { CalibrationJson.decode(text).takeIf { it.binding == binding } }.getOrNull()
        }

    fun delete(binding: CalibrationBinding) {
        preferences.edit().remove(key(binding)).apply()
    }

    private fun key(binding: CalibrationBinding): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(CalibrationJson.bindingJson(binding).toString().toByteArray())
        return "profile-" + digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FILE_NAME = "speedvision_calibration_profiles"
    }
}
