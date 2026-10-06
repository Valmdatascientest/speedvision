package fr.speedvision.domain

import kotlin.math.tan

enum class CalibrationMode { APPROXIMATE, CALIBRATED }

val CameraCalibration.mode: CalibrationMode
    get() = if (provenance.startsWith("automatic-approximate-profile-v1")) CalibrationMode.APPROXIMATE else CalibrationMode.CALIBRATED

/** Creates a conservative, unsaved profile when no validated profile matches the source. */
fun approximateCalibration(binding: CalibrationBinding): CameraCalibration {
    val horizontalFovRadians = Math.toRadians(60.0)
    val focal = binding.nativeWidth / (2.0 * tan(horizontalFovRadians / 2.0))
    return CameraCalibration(
        binding = binding,
        fx = focal,
        fy = focal,
        cx = binding.nativeWidth / 2.0,
        cy = binding.nativeHeight / 2.0,
        distortion = List(5) { 0.0 },
        provenance = "automatic-approximate-profile-v1:fov-60deg",
        validationRmsPx = 2.0,
        plate = PlateProfile("approximate-average-plate", 0.52, 0.11),
    )
}
