package fr.speedvision.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class MetaPixelsTest {
    @Test fun neutralBlackWhiteAndBufferOwnership() {
        val bytes = byteArrayOf(99, 16, 235.toByte(), 16, 235.toByte(), 128.toByte(), 128.toByte(), 88)
        val buffer =
            ByteBuffer.wrap(bytes).apply {
                position(1)
                limit(7)
            }
        val pixels = MetaPixels.argb(buffer, 2, 2)
        assertEquals(1, buffer.position())
        assertEquals(7, buffer.limit())
        assertEquals(0xff000000.toInt(), pixels[0])
        assertEquals(0xffffffff.toInt(), pixels[1])
        val other = MetaPixels.argb(buffer.asReadOnlyBuffer(), 2, 2)
        assertNotSame(pixels, other)
        bytes[1] = 235.toByte()
        assertEquals(0xff000000.toInt(), pixels[0])
    }

    @Test fun planarChromaOrderingAndBt709Color() {
        // Independently computed limited-range BT.709 red: Y=63, Cb=102, Cr=240.
        val buffer =
            ByteBuffer.allocateDirect(6).apply {
                put(byteArrayOf(63, 63, 63, 63, 102, 240.toByte()))
                flip()
            }
        val pixel = MetaPixels.argb(buffer, 2, 2)[0]
        assertEquals(255, (pixel ushr 16) and 255)
        assertEquals(1, (pixel ushr 8) and 255)
        assertEquals(0, pixel and 255)
    }

    @Test fun rejectsOddOversizedShortOrPaddedFrames() {
        for ((width, height) in listOf(0 to 2, 3 to 2, 2 to 3, 1922 to 2, Int.MAX_VALUE to 2)) {
            assertThrows(IllegalArgumentException::class.java) { MetaPixels.argb(ByteBuffer.allocate(6), width, height) }
        }
        for (size in listOf(
            0,
            5,
            7,
        )) {
            assertThrows(IllegalArgumentException::class.java) { MetaPixels.argb(ByteBuffer.allocate(size), 2, 2) }
        }
    }
}
