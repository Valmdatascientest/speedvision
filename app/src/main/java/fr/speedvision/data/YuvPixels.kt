package fr.speedvision.data

/** BT.601 limited-range preview conversion. Geometry uses coordinates, not these colors. */
internal fun yuvToArgb(
    y: Int,
    u: Int,
    v: Int,
): Int {
    val c = (y - 16).coerceAtLeast(0)
    val d = u - 128
    val e = v - 128
    val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
    val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
    val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
    return (0xff shl 24) or (r shl 16) or (g shl 8) or b
}
