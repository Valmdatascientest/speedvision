package fr.speedvision.domain

import kotlin.math.abs
import kotlin.math.max

internal data class TemporalFit(
    val intercept: Double,
    val slope: Double,
    val quadratic: Double = 0.0,
) {
    fun value(x: Double) = intercept + slope * x + quadratic * x * x
}

/** Actual timestamps in seconds about a central reference, never frame indices. */
internal object TemporalRegression {
    fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2 else sorted[sorted.size / 2]
    }

    fun linear(
        x: DoubleArray,
        y: DoubleArray,
        w: DoubleArray,
    ): TemporalFit {
        val sum = w.sum()
        val mx = x.indices.sumOf { w[it] * x[it] } / sum
        val my = y.indices.sumOf { w[it] * y[it] } / sum
        val denominator = x.indices.sumOf { w[it] * (x[it] - mx) * (x[it] - mx) }
        val slope = if (denominator > 1e-12) x.indices.sumOf { w[it] * (x[it] - mx) * (y[it] - my) } / denominator else 0.0
        return TemporalFit(my - slope * mx, slope)
    }

    fun huber(
        x: DoubleArray,
        y: DoubleArray,
        base: DoubleArray,
    ): TemporalFit {
        val slopes =
            buildList {
                for (i in x.indices) for (j in i + 1 until x.size) if (x[j] - x[i] > 1e-6) add((y[j] - y[i]) / (x[j] - x[i]))
            }
        val slope = if (slopes.isEmpty()) 0.0 else median(slopes)
        var fit = TemporalFit(median(x.indices.map { y[it] - slope * x[it] }), slope)
        repeat(5) {
            val residuals = x.indices.map { y[it] - fit.value(x[it]) }
            val center = median(residuals)
            val scale = max(.02, 1.4826 * median(residuals.map { abs(it - center) }))
            val w = DoubleArray(x.size) { base[it] * minOf(1.0, 1.345 * scale / max(abs(residuals[it]), 1e-12)) }
            fit = linear(x, y, w)
        }
        return fit
    }
}
