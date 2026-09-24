package fr.speedvision.domain

import kotlin.math.hypot

/** Exact source/mode geometry. No silent resize or profile reuse across sources. */
data class CalibrationBinding(
    val sourceId: String,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val width: Int,
    val height: Int,
    val rotation: Int,
) {
    init {
        require(sourceId.isNotBlank() && sourceId.length <= 512)
        require(nativeWidth in 1..16384 && nativeHeight in 1..16384)
        require(width > 0 && height > 0 && cropLeft >= 0 && cropTop >= 0)
        require(width <= nativeWidth - cropLeft && height <= nativeHeight - cropTop)
        require(rotation in setOf(0, 90, 180, 270))
    }

    val uprightWidth get() = if (rotation % 180 == 0) width else height
    val uprightHeight get() = if (rotation % 180 == 0) height else width

    /** Pixel centers, inverse of Android's clockwise bitmap rotation, then restore native crop. */
    fun toNative(p: ImagePoint): ImagePoint {
        val q =
            when (rotation) {
                90 -> ImagePoint(p.y, height - 1.0 - p.x)
                180 -> ImagePoint(width - 1.0 - p.x, height - 1.0 - p.y)
                270 -> ImagePoint(width - 1.0 - p.y, p.x)
                else -> p
            }
        return ImagePoint(q.x + cropLeft, q.y + cropTop)
    }
}

data class ImagePoint(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite())
    }

    fun distance(other: ImagePoint) = hypot(x - other.x, y - other.y)
}

data class PlateProfile(
    val name: String,
    val widthMeters: Double,
    val heightMeters: Double,
) {
    init {
        require(name.isNotBlank() && name.length <= 120)
        require(widthMeters.isFinite() && widthMeters in 0.05..2.0)
        require(heightMeters.isFinite() && heightMeters in 0.02..1.0)
        require(widthMeters > heightMeters)
    }
}

/** Brown-Conrady k1,k2,p1,p2,k3 in the native image, before crop/rotation. */
data class CameraCalibration(
    val binding: CalibrationBinding,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distortion: List<Double>,
    val provenance: String,
    val validationRmsPx: Double,
    val plate: PlateProfile,
) {
    init {
        require(listOf(fx, fy, cx, cy, validationRmsPx).all { it.isFinite() })
        require(fx in 1.0..100_000.0 && fy in 1.0..100_000.0)
        require(cx >= 0 && cx < binding.nativeWidth && cy >= 0 && cy < binding.nativeHeight)
        require(distortion.size == 5 && distortion.all { it.isFinite() && kotlin.math.abs(it) <= 10 })
        require(provenance.isNotBlank() && provenance.length <= 1000)
        require(validationRmsPx in 0.0..2.0)
    }
}

/** Observed corners ordered TL, TR, BR, BL in the physical plate orientation; never bbox corners. */
fun validatePlateCorners(
    points: List<ImagePoint>,
    binding: CalibrationBinding,
): String? {
    if (points.size != 4) return "Sélectionnez les quatre coins réels : HG, HD, BD, BG."
    if (points.any {
            it.x < 0 || it.y < 0 || it.x > binding.uprightWidth - 1 || it.y > binding.uprightHeight - 1
        }
    ) {
        return "Coin hors de l’image."
    }
    val edges = points.indices.map { points[it].distance(points[(it + 1) % 4]) }
    if (minOf(edges[0], edges[2]) < 32 || minOf(edges[1], edges[3]) < 8) return "Plaque trop petite (largeur ≥ 32 px, hauteur ≥ 8 px)."
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % 4]
        val c = points[(i + 2) % 4]
        if ((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) <= 1e-3) return "Coins croisés, inversés ou dégénérés."
    }
    return null
}

sealed interface DistanceEstimate {
    data class Accepted(
        val axialMeters: Double,
        val reprojectionRmsPx: Double,
        val tiltDegrees: Double,
    ) : DistanceEstimate

    data class Rejected(
        val reason: String,
    ) : DistanceEstimate
}

/** Depth-quality gates are separate from the native solver, so rejection semantics are testable. */
fun selectDistancePose(candidates: List<DistanceEstimate.Accepted>): DistanceEstimate {
    val valid =
        candidates
            .filter {
                it.axialMeters.isFinite() &&
                    it.axialMeters > 0 &&
                    it.reprojectionRmsPx.isFinite() &&
                    it.reprojectionRmsPx >= 0 &&
                    it.tiltDegrees.isFinite() &&
                    it.tiltDegrees in 0.0..90.0
            }.sortedBy { it.reprojectionRmsPx }
    val best = valid.firstOrNull() ?: return DistanceEstimate.Rejected("Aucune pose de profondeur positive.")
    if (best.reprojectionRmsPx > 2.0) return DistanceEstimate.Rejected("Erreur de reprojection > 2 px.")
    if (best.tiltDegrees > 65) return DistanceEstimate.Rejected("Inclinaison > 65° : géométrie trop oblique.")
    val alternative = valid.drop(1).firstOrNull { it.reprojectionRmsPx <= best.reprojectionRmsPx + 0.5 }
    if (alternative != null &&
        (
            kotlin.math.abs(alternative.axialMeters - best.axialMeters) / best.axialMeters > .02 ||
                kotlin.math.abs(alternative.tiltDegrees - best.tiltDegrees) > 5
        )
    ) {
        return DistanceEstimate.Rejected("Pose planaire ambiguë.")
    }
    return best
}
