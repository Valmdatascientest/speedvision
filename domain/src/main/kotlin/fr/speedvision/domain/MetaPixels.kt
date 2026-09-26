package fr.speedvision.domain

import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** DAT decoded I420, tightly packed, no guessed stride or automatic format sniffing. BT.709 preview. */
object MetaPixels {
    fun argb(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
    ): IntArray {
        require(width in 2..1920 && height in 2..1920 && width % 2 == 0 && height % 2 == 0)
        val size = width * height
        require(buffer.remaining() == size * 3 / 2)
        val bytes = ByteArray(buffer.remaining())
        buffer.duplicate().get(bytes)
        return IntArray(size) { i ->
            val chroma = (i / width / 2) * (width / 2) + (i % width / 2)
            val y = ((bytes[i].toInt() and 255) - 16) * (255.0 / 219)
            val u = ((bytes[size + chroma].toInt() and 255) - 128) * (255.0 / 224)
            val v = ((bytes[size + size / 4 + chroma].toInt() and 255) - 128) * (255.0 / 224)

            fun channel(value: Double) = value.roundToInt().coerceIn(0, 255)
            (0xff shl 24) or (channel(y + 1.5748 * v) shl 16) or
                (channel(y - .187324 * u - .468124 * v) shl 8) or channel(y + 1.8556 * u)
        }
    }
}
