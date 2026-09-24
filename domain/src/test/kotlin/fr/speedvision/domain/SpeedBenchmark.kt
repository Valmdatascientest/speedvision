package fr.speedvision.domain

import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Baselines exist only in tests/benchmark; production always uses the gated Huber estimator. */
object SpeedBenchmark {
    enum class Method { HUBER, OLS, KALMAN, LOCAL_QUADRATIC, RANSAC }

    internal fun fit(
        method: Method,
        x: DoubleArray,
        y: DoubleArray,
    ): Pair<Double, Double> {
        val weights = DoubleArray(x.size) { 1.0 }
        return when (method) {
            Method.HUBER -> -TemporalRegression.huber(x, y, weights).slope to 0.0
            Method.OLS -> -TemporalRegression.linear(x, y, weights).slope to 0.0
            Method.RANSAC -> {
                var best = emptyList<Int>()
                var bestError = Double.POSITIVE_INFINITY
                for (i in x.indices) {
                    for (j in i + 1 until x.size) {
                        val slope = (y[j] - y[i]) / (x[j] - x[i])
                        val intercept = y[i] - slope * x[i]
                        val inliers = x.indices.filter { abs(y[it] - intercept - slope * x[it]) <= .15 }
                        val error = inliers.sumOf { abs(y[it] - intercept - slope * x[it]) }
                        if (inliers.size > best.size || (inliers.size == best.size && error < bestError)) {
                            best = inliers
                            bestError = error
                        }
                    }
                }
                -TemporalRegression.linear(x, y, DoubleArray(x.size) { if (it in best) 1.0 else 0.0 }).slope to 0.0
            }
            Method.LOCAL_QUADRATIC -> {
                val matrix =
                    Array(3) { row ->
                        DoubleArray(4) { col ->
                            if (col == 3) x.indices.sumOf { pow(x[it], row) * y[it] } else x.sumOf { pow(it, row + col) }
                        }
                    }
                for (i in 0..2) {
                    val pivot = (i..2).maxBy { abs(matrix[it][i]) }
                    val swap = matrix[i]
                    matrix[i] = matrix[pivot]
                    matrix[pivot] = swap
                    val divisor = matrix[i][i]
                    for (j in i..3) matrix[i][j] /= divisor
                    for (r in 0..2) {
                        if (r != i) {
                            val factor = matrix[r][i]
                            for (j in i..3) matrix[r][j] -= factor * matrix[i][j]
                        }
                    }
                }
                -matrix[1][3] to 0.0
            }
            Method.KALMAN -> {
                var z = y[0]
                var velocity = 0.0
                var p00 = .01
                var p01 = 0.0
                var p11 = 100.0
                for (i in 1 until x.size) {
                    val dt = x[i] - x[i - 1]
                    z += velocity * dt
                    p00 += 2 * dt * p01 + dt * dt * p11 + .5 * pow(dt, 4) / 4
                    p01 += dt * p11 + .5 * pow(dt, 3) / 2
                    p11 += .5 * dt * dt
                    val innovation = y[i] - z
                    val k0 = p00 / (p00 + .01)
                    val k1 = p01 / (p00 + .01)
                    z += k0 * innovation
                    velocity += k1 * innovation
                    val oldCross = p01
                    p00 *= 1 - k0
                    p01 *= 1 - k0
                    p11 -= k1 * oldCross
                }
                -velocity to x.last()
            }
        }
    }

    private fun pow(
        value: Double,
        exponent: Int,
    ): Double = (0 until exponent).fold(1.0) { product, _ -> product * value }

    @JvmStatic fun main(args: Array<String>) {
        val output = File(args.single())
        output.parentFile.mkdirs()
        val lines = mutableListOf("scenario,method,windows,mae_kmh,rmse_kmh,accepted,rejected,coverage")
        for (scenario in listOf("constant_clean", "noise", "outliers", "acceleration", "strong_acceleration")) {
            val acceleration =
                when (scenario) {
                    "acceleration" -> 1.0
                    "strong_acceleration" -> 12.0
                    else -> 0.0
                }
            val random = Random(2026)
            val observations =
                (0..60).map { i ->
                    val time = i / 10.0
                    val noise = if (scenario == "constant_clean") 0.0 else random.nextDouble(-.06, .06)
                    val outlier = if (scenario == "outliers" && i % 11 == 4) 3.0 else 0.0
                    DepthObservation(
                        "synthetic-$scenario",
                        1,
                        "synthetic-known",
                        i * 100_000L,
                        500 - 5 * time - acceleration * time * time / 2 + noise + outlier,
                        .95,
                        true,
                        true,
                        true,
                    )
                }
            for (method in Method.entries) {
                val errors = mutableListOf<Double>()
                for (last in 12..60) {
                    val center = (last - 6) / 10.0
                    val window = observations.subList(last - 12, last + 1)
                    val x = window.map { it.timestampUs / 1e6 - center }.toDoubleArray()
                    val y = window.map { it.depthMeters }.toDoubleArray()
                    val (speed, reference) = fit(method, x, y)
                    errors.add((speed - (5 + acceleration * (center + reference))) * 3.6)
                }
                lines.add(line(scenario, method.name, errors, errors.size, 0))
            }
            val estimator = SpeedEstimator()
            val acceptedErrors = mutableListOf<Double>()
            var rejected = 0
            observations.forEachIndexed { i, sample ->
                val answer = estimator.add(sample)
                if (i >= 12) {
                    if (answer is SpeedEstimate.Accepted) {
                        acceptedErrors.add((answer.closingMps - (5 + acceleration * answer.referenceTimeUs / 1e6)) * 3.6)
                    } else {
                        rejected++
                    }
                }
            }
            lines.add(line(scenario, "HUBER_GATED", acceptedErrors, acceptedErrors.size, rejected))
        }
        output.writeText(lines.joinToString("\n", postfix = "\n"))
        println("Synthetic benchmark: ${output.absolutePath}")
    }

    private fun line(
        scenario: String,
        method: String,
        errors: List<Double>,
        accepted: Int,
        rejected: Int,
    ): String {
        fun format(value: Double) = String.format(Locale.ROOT, "%.6f", value)
        val mae = if (errors.isEmpty()) "" else format(errors.sumOf { abs(it) } / errors.size)
        val rmse = if (errors.isEmpty()) "" else format(sqrt(errors.sumOf { it * it } / errors.size))
        return "$scenario,$method,${accepted + rejected},$mae,$rmse,$accepted,$rejected,${format(
            accepted.toDouble() / (accepted + rejected),
        )}"
    }
}
