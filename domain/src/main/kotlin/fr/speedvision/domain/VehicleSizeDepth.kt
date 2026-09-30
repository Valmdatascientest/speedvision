package fr.speedvision.domain

/**
 * Fallback depth estimate from a vehicle bounding-box width.
 *
 * This is deliberately separate from plate/PnP depth: a class-average width is
 * only a prior and must be shown as a lower-confidence estimate to the user.
 */
data class VehicleSizeProfile(
    val classId: Int,
    val widthMeters: Double,
    val label: String,
) {
    init {
        require(classId >= 0)
        require(widthMeters.isFinite() && widthMeters in 0.2..5.0)
        require(label.isNotBlank() && label.length <= 80)
    }
}

data class VehicleSizeDepth(
    val axialMeters: Double,
    val profile: VehicleSizeProfile,
    val pixelWidth: Double,
    /** A prior-based result is never equivalent to a validated planar pose. */
    val quality: Double,
)

object VehicleSizeDepthEstimator {
    const val MIN_PIXEL_WIDTH = 12.0

    /** Conservative COCO-class priors; applications should expose replacement profiles. */
    val defaultProfiles =
        listOf(
            VehicleSizeProfile(2, 1.80, "voiture"),
            VehicleSizeProfile(3, 0.85, "moto"),
            VehicleSizeProfile(5, 2.50, "bus"),
            VehicleSizeProfile(7, 2.50, "camion"),
        )

    fun estimate(
        calibration: CameraCalibration,
        detection: Detection,
        profile: VehicleSizeProfile,
    ): VehicleSizeDepth? {
        if (detection.classId != profile.classId || detection.score < 0.35f) return null
        val pixelWidth = detection.box.width.toDouble()
        if (!pixelWidth.isFinite() || pixelWidth < MIN_PIXEL_WIDTH) return null
        val depth = calibration.fx * profile.widthMeters * calibration.vehicleWidthScale / pixelWidth
        if (!depth.isFinite() || depth !in .05..10_000.0) return null
        val quality = (detection.score.toDouble() * (pixelWidth / 96.0).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        return VehicleSizeDepth(depth, profile, pixelWidth, quality)
    }
}
