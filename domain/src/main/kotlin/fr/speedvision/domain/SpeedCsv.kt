package fr.speedvision.domain

/** Strict observation interchange; no video, OCR, or automatic writes. */
object SpeedCsv {
    const val MAX_CHARS = 524_288
    const val MAX_ROWS = 5_000
    private val header =
        listOf(
            "sequence_id",
            "track_id",
            "calibration_id",
            "timestamp_us",
            "depth_m",
            "quality",
            "geometry_valid",
            "camera_fixed",
            "track_observed",
        )

    fun decode(text: String): List<DepthObservation> {
        require(text.length <= MAX_CHARS) { "CSV trop volumineux." }
        val rows = parse(text.removePrefix("\uFEFF"))
        require(rows.firstOrNull() == header) { "En-tête CSV incompatible." }
        require(rows.size in 2..MAX_ROWS + 1) { "CSV vide ou trop de lignes." }
        return rows.drop(1).mapIndexed { index, row ->
            require(row.size == header.size) { "Ligne ${index + 2} : nombre de colonnes invalide." }

            fun number(i: Int) = row[i].toDouble().also { require(it.isFinite()) }

            fun flag(i: Int): Boolean =
                when (row[i]) {
                    "true" -> true
                    "false" -> false
                    else -> error("Ligne ${index + 2} : booléen invalide.")
                }
            require(validId(row[0]) && validId(row[2])) { "Identifiant invalide." }
            DepthObservation(row[0], row[1].toLong(), row[2], row[3].toLong(), number(4), number(5), flag(6), flag(7), flag(8))
        }
    }

    private fun validId(value: String) = value.length in 1..128 && value.first().isLetterOrDigit() && value.all { it.code >= 32 }

    fun encodeObservations(observations: List<DepthObservation>): String =
        buildString {
            appendLine(header.joinToString(","))
            observations.forEach { row ->
                appendLine(
                    listOf(
                        row.sequenceId,
                        row.trackId,
                        row.calibrationId,
                        row.timestampUs,
                        row.depthMeters,
                        row.quality,
                        row.geometryValid,
                        row.cameraFixed,
                        row.trackObserved,
                    ).joinToString(",") { field(it.toString()) },
                )
            }
        }

    fun encodeResults(rows: List<SpeedReplayRow>): String =
        buildString {
            appendLine(
                "algorithm,sequence_id,track_id,calibration_id,timestamp_us,input_depth_m,status,rejection,speed_mps,speed_kmh,quality_score,samples,inliers,reference_us,window_us,residual_m,compute_ms",
            )
            rows.forEach { row ->
                val input = row.observation
                val result = row.estimate
                val accepted = result as? SpeedEstimate.Accepted
                val rejected = result as? SpeedEstimate.Rejected
                appendLine(
                    listOf(
                        SpeedEstimator.ALGORITHM_VERSION,
                        input.sequenceId,
                        input.trackId,
                        input.calibrationId,
                        input.timestampUs,
                        input.depthMeters,
                        if (accepted != null) "accepted" else "rejected",
                        rejected?.reason?.name ?: "",
                        accepted?.closingMps ?: "",
                        accepted?.closingKmh ?: "",
                        accepted?.qualityScore ?: "",
                        accepted?.sampleCount ?: rejected?.sampleCount ?: 0,
                        accepted?.inlierCount ?: "",
                        accepted?.referenceTimeUs ?: "",
                        accepted?.spanUs ?: "",
                        accepted?.residualRmsMeters ?: "",
                        row.computeMillis,
                    ).joinToString(",") { field(it.toString()) },
                )
            }
        }

    private fun field(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
            closed = false
        }

        fun finishRow() {
            finishField()
            rows.add(row)
            row = mutableListOf()
            require(rows.size <= MAX_ROWS + 1)
        }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') {
                    if (i < text.length && text[i] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                        closed = true
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        require(field.isEmpty() && !closed)
                        quoted = true
                    }
                    ',' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> {
                        require(i < text.length && text[i++] == '\n')
                        finishRow()
                    }
                    else -> {
                        require(!closed)
                        field.append(c)
                    }
                }
            }
        }
        require(!quoted) { "Champ CSV non terminé." }
        if (field.isNotEmpty() || closed || row.isNotEmpty()) finishRow()
        return rows
    }
}

data class SpeedReplayRow(
    val observation: DepthObservation,
    val estimate: SpeedEstimate,
    val computeMillis: Double,
)

object SpeedReplay {
    fun run(
        observations: List<DepthObservation>,
        checkCancelled: () -> Unit = {},
    ): List<SpeedReplayRow> {
        require(observations.size <= SpeedCsv.MAX_ROWS)
        val estimator = SpeedEstimator()
        return observations.map {
            checkCancelled()
            val start = System.nanoTime()
            val result = estimator.add(it)
            SpeedReplayRow(it, result, (System.nanoTime() - start) / 1e6)
        }
    }
}
