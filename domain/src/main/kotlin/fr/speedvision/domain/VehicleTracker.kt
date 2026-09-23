package fr.speedvision.domain

/** IDs are never reused by this instance, including after reset. Predictions are not observations. */
enum class TrackStatus { TENTATIVE, CONFIRMED, LOST }

data class VehicleTrack(
    val id: Long,
    val box: PixelBox,
    val classId: Int,
    val status: TrackStatus,
    val detectionIndex: Int?,
    val lastObservedUs: Long,
)

data class TrackedPlate(
    val trackId: Long,
    val detection: Detection,
)

data class TrackingResult(
    val tracks: List<VehicleTrack>,
    val plates: List<TrackedPlate>,
)

/** SORT-inspired, class-gated IoU/Hungarian association with four independent CV Kalman filters.
 * Call serially. Time is source PTS, never inference completion time. No appearance re-identification.
 */
class VehicleTracker(
    private val minHits: Int = 3,
    private val maxLostUs: Long = 500_000,
    private val minIou: Float = 0.2f,
    private val maxTracks: Int = 60,
) {
    init {
        require(minHits > 0 && maxLostUs > 0 && minIou > 0f && minIou <= 1f && maxTracks > 0)
    }

    private class State(
        val id: Long,
        val classId: Int,
        box: PixelBox,
        var lastSeen: Long,
    ) {
        val filters =
            listOf(
                MotionFilter((box.left + box.right) / 2.0),
                MotionFilter((box.top + box.bottom) / 2.0),
                MotionFilter(box.width.toDouble()),
                MotionFilter(box.height.toDouble()),
            )
        var hits = 1
        var confirmed = false
        var index: Int? = null

        fun box(): PixelBox {
            val w = filters[2].position.coerceAtLeast(1.0)
            val h = filters[3].position.coerceAtLeast(1.0)
            val x = filters[0].position
            val y = filters[1].position
            return PixelBox((x - w / 2).toFloat(), (y - h / 2).toFloat(), (x + w / 2).toFloat(), (y + h / 2).toFloat())
        }

        fun observe(
            detection: Detection,
            timestamp: Long,
            detectionIndex: Int,
        ) {
            val b = detection.box
            val values = doubleArrayOf((b.left + b.right) / 2.0, (b.top + b.bottom) / 2.0, b.width.toDouble(), b.height.toDouble())
            filters.zip(values.toList()).forEach { (filter, value) -> filter.correct(value) }
            lastSeen = timestamp
            index = detectionIndex
            hits++
        }
    }

    private val states = mutableListOf<State>()
    private var nextId = 1L
    private var previousUs: Long? = null
    private var geometry: Pair<Int, Int>? = null

    fun reset() {
        states.clear()
        previousUs = null
        geometry = null
    }

    fun update(
        timestampUs: Long,
        width: Int,
        height: Int,
        detections: DetectionResult,
    ): TrackingResult {
        require(timestampUs >= 0 && width > 0 && height > 0)
        require(detections.vehicles.size <= maxTracks)
        require(detections.vehicles.all { it.score.isFinite() && it.score in 0f..1f })
        require(detections.plates.all { it.vehicleIndex in detections.vehicles.indices })
        val previous = previousUs
        if (geometry != (width to height) || (previous != null && (timestampUs <= previous || timestampUs - previous > maxLostUs))) reset()
        val dt = previousUs?.let { (timestampUs - it) / 1e6 } ?: 0.0
        geometry = width to height
        previousUs = timestampUs
        states.removeAll { timestampUs - it.lastSeen > maxLostUs }
        states.forEach { state ->
            state.index = null
            state.filters.forEach { it.predict(dt) }
        }
        val vehicles = detections.vehicles
        // One dummy column per track makes leaving any row unmatched always possible.
        val costs =
            Array(states.size) { row ->
                DoubleArray(vehicles.size + states.size) { col ->
                    if (col >= vehicles.size) {
                        1.0
                    } else {
                        val overlap = states[row].box().iou(vehicles[col].box)
                        if (states[row].classId == vehicles[col].classId && overlap >= minIou) 1.0 - overlap else 1e6
                    }
                }
            }
        val used = mutableSetOf<Int>()
        minimumAssignment(costs).forEachIndexed { row, col ->
            if (col in vehicles.indices && costs[row][col] < 1.0) {
                states[row].observe(vehicles[col], timestampUs, col)
                if (states[row].hits >= minHits) states[row].confirmed = true
                used.add(col)
            }
        }
        // Tentative tracks require consecutive observations. Confirmed tracks may coast briefly.
        states.removeAll { !it.confirmed && it.index == null }
        vehicles.forEachIndexed { index, detection ->
            if (index !in used && states.size >= maxTracks) {
                states.filter { it.index == null }.minByOrNull { it.lastSeen }?.let { states.remove(it) }
            }
            if (index !in used && states.size < maxTracks) {
                states.add(
                    State(nextId++, detection.classId, detection.box, timestampUs).apply {
                        this.index = index
                        confirmed = minHits == 1
                    },
                )
            }
        }
        val tracks =
            states.map {
                VehicleTrack(
                    it.id,
                    it.box(),
                    it.classId,
                    when {
                        it.index == null -> TrackStatus.LOST
                        it.confirmed -> TrackStatus.CONFIRMED
                        else -> TrackStatus.TENTATIVE
                    },
                    it.index,
                    it.lastSeen,
                )
            }
        val parents = tracks.filter { it.status == TrackStatus.CONFIRMED }.associateBy { it.detectionIndex }
        val plates = detections.plates.mapNotNull { plate -> parents[plate.vehicleIndex]?.let { TrackedPlate(it.id, plate.detection) } }
        return TrackingResult(tracks, plates)
    }
}

/** 2x2 Kalman state [position, velocity], white acceleration Q, measurement variance 4 px². */
private class MotionFilter(
    var position: Double,
) {
    private var velocity = 0.0
    private var p00 = 10.0
    private var p01 = 0.0
    private var p11 = 10_000.0

    fun predict(dt: Double) {
        position += velocity * dt
        val dt2 = dt * dt
        p00 += 2 * dt * p01 + dt2 * p11 + 100 * dt2 * dt2 / 4
        p01 += dt * p11 + 100 * dt2 * dt / 2
        p11 += 100 * dt2
    }

    fun correct(value: Double) {
        val innovation = value - position
        val s = p00 + 4.0
        val k0 = p00 / s
        val k1 = p01 / s
        position += k0 * innovation
        velocity += k1 * innovation
        val cross = p01
        p00 *= 1 - k0
        p01 *= 1 - k0
        p11 -= k1 * cross
    }
}

/** Rectangular Hungarian minimization, rows <= columns. Deterministic tie order. */
internal fun minimumAssignment(costs: Array<DoubleArray>): IntArray {
    val n = costs.size
    if (n == 0) return IntArray(0)
    val m = costs[0].size
    require(m >= n && costs.all { it.size == m && it.all(Double::isFinite) })
    val u = DoubleArray(n + 1)
    val v = DoubleArray(m + 1)
    val p = IntArray(m + 1)
    val way = IntArray(m + 1)
    for (i in 1..n) {
        p[0] = i
        var j0 = 0
        val min = DoubleArray(m + 1) { Double.POSITIVE_INFINITY }
        val used = BooleanArray(m + 1)
        do {
            used[j0] = true
            val i0 = p[j0]
            var delta = Double.POSITIVE_INFINITY
            var j1 = 0
            for (j in 1..m) {
                if (used[j]) continue
                val current = costs[i0 - 1][j - 1] - u[i0] - v[j]
                if (current < min[j]) {
                    min[j] = current
                    way[j] = j0
                }
                if (min[j] < delta) {
                    delta = min[j]
                    j1 = j
                }
            }
            for (j in 0..m) {
                if (used[j]) {
                    u[p[j]] += delta
                    v[j] -= delta
                } else {
                    min[j] -= delta
                }
            }
            j0 = j1
        } while (p[j0] != 0)
        do {
            val j1 = way[j0]
            p[j0] = p[j1]
            j0 = j1
        } while (j0 != 0)
    }
    return IntArray(n) { -1 }.also { result -> for (j in 1..m) if (p[j] > 0) result[p[j] - 1] = j - 1 }
}
