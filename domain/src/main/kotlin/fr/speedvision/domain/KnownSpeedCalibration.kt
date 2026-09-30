package fr.speedvision.domain

import kotlin.math.abs
import kotlin.math.sign

data class KnownSpeedCalibrationQuality(
    val framesAnalyzed: Int,
    val measurementsRetained: Int,
    val trackingStability: Double,
    val residualSpeedMps: Double,
    val dispersionMps: Double,
    val scale: Double,
    val warnings: List<String>,
) {
    init {
        require(framesAnalyzed >= 0 && measurementsRetained in 0..framesAnalyzed)
        require(listOf(trackingStability, residualSpeedMps, dispersionMps, scale).all { it.isFinite() })
        require(trackingStability in 0.0..1.0 && scale in 0.25..4.0)
        require(warnings.size <= 8)
    }
}

sealed interface KnownSpeedCalibrationResult {
    data class Accepted(
        val calibration: CameraCalibration,
        val quality: KnownSpeedCalibrationQuality,
    ) : KnownSpeedCalibrationResult

    data class Rejected(
        val reason: String,
        val quality: KnownSpeedCalibrationQuality,
    ) : KnownSpeedCalibrationResult
}

/** Fits only the declared vehicle-width prior against a known-speed reference. */
object KnownSpeedCalibrationEngine {
    private const val MIN_FRAMES = 8
    private const val MIN_SPAN_US = 700_000L
    private const val MAX_GAP_US = 300_000L
    private const val MIN_SCALE = 0.25
    private const val MAX_SCALE = 4.0
    private const val MONOTONIC_TOLERANCE_METERS = 0.05

    fun fit(
        base: CameraCalibration,
        observations: List<DepthObservation>,
        knownSpeedKmh: Double,
    ): KnownSpeedCalibrationResult {
        val trackingStability =
            if (observations.isEmpty()) 0.0 else observations.count { it.trackObserved && it.geometryValid }.toDouble() / observations.size
        val commonQuality =
            KnownSpeedCalibrationQuality(
                observations.size,
                0,
                trackingStability,
                0.0,
                0.0,
                base.vehicleWidthScale,
                emptyList(),
            )
        if (!knownSpeedKmh.isFinite() ||
            knownSpeedKmh < 0.0
        ) {
            return KnownSpeedCalibrationResult.Rejected("Vitesse connue invalide.", commonQuality)
        }
        if (observations.size <
            MIN_FRAMES
        ) {
            return KnownSpeedCalibrationResult.Rejected("Nombre insuffisant de mesures exploitables.", commonQuality)
        }
        val allOrdered = observations.sortedBy { it.timestampUs }
        if (allOrdered.any { !it.cameraFixed || !it.trackObserved || !it.geometryValid || !it.depthMeters.isFinite() }) {
            return KnownSpeedCalibrationResult.Rejected("Mesures invalides ou caméra non déclarée fixe.", commonQuality)
        }
        val segments = buildList {
            var segment = mutableListOf<DepthObservation>()
            allOrdered.forEach { observation ->
                val previous = segment.lastOrNull()
                val contiguous =
                    previous != null &&
                        observation.timestampUs - previous.timestampUs <= MAX_GAP_US &&
                        observation.sequenceId == previous.sequenceId &&
                        observation.trackId == previous.trackId &&
                        observation.calibrationId == previous.calibrationId
                if (previous == null || contiguous) {
                    segment += observation
                } else {
                    add(segment)
                    segment = mutableListOf(observation)
                }
            }
            if (segment.isNotEmpty()) add(segment)
        }
        val ordered = segments.maxByOrNull { it.size }.orEmpty()
        if (ordered.size < MIN_FRAMES) {
            return KnownSpeedCalibrationResult.Rejected(
                "Horodatages discontinus ou tracking instable (${ordered.size}/$MIN_FRAMES mesures dans la meilleure séquence).",
                commonQuality,
            )
        }
        val span = ordered.last().timestampUs - ordered.first().timestampUs
        if (span < MIN_SPAN_US) return KnownSpeedCalibrationResult.Rejected("Vidéo trop courte ou variation insuffisante.", commonQuality)
        val direction =
            ordered
                .zipWithNext()
                .map { (previous, current) -> current.depthMeters - previous.depthMeters }
                .firstOrNull { abs(it) > MONOTONIC_TOLERANCE_METERS }
                ?.let(::sign)
                ?: 0.0
        val reversals =
            ordered.zipWithNext().count { (previous, current) ->
                direction * (current.depthMeters - previous.depthMeters) < -MONOTONIC_TOLERANCE_METERS
            }
        if (reversals > 0) {
            return KnownSpeedCalibrationResult.Rejected(
                "Mesures non monotones : éloignement et rapprochement mélangés ($reversals inversion(s)).",
                commonQuality.copy(measurementsRetained = ordered.size),
            )
        }
        val baseEstimate = SpeedEstimator().let { estimator -> ordered.map { estimator.add(it) }.last() }
        if (baseEstimate !is SpeedEstimate.Accepted || abs(baseEstimate.closingMps) < 0.01) {
            return KnownSpeedCalibrationResult.Rejected(
                "Variation apparente insuffisante pour ajuster la calibration.",
                commonQuality.copy(measurementsRetained = ordered.size),
            )
        }
        val targetMps = knownSpeedKmh / 3.6
        val scale = (targetMps / abs(baseEstimate.closingMps)).coerceIn(MIN_SCALE, MAX_SCALE)
        val fittedSpeed = abs(baseEstimate.closingMps) * scale
        val residual = abs(fittedSpeed - targetMps)
        val dispersion = baseEstimate.residualRmsMeters / baseEstimate.spanUs.coerceAtLeast(1L) * 1e6
        val warnings =
            buildList {
                if (scale == MIN_SCALE || scale == MAX_SCALE) add("Facteur limité par le domaine admissible.")
                if (baseEstimate.qualityScore < .75) add("Qualité de suivi inférieure à 0,75.")
                add("Facteur ajusté sur une largeur moyenne de véhicule ; ne pas généraliser sans validation.")
            }
        val quality =
            KnownSpeedCalibrationQuality(ordered.size, baseEstimate.inlierCount, trackingStability, residual, dispersion, scale, warnings)
        if (residual > .5 ||
            baseEstimate.qualityScore < .6
        ) {
            return KnownSpeedCalibrationResult.Rejected("Erreur résiduelle ou qualité insuffisante.", quality)
        }
        return KnownSpeedCalibrationResult.Accepted(base.copy(vehicleWidthScale = scale), quality.copy(scale = scale))
    }
}
