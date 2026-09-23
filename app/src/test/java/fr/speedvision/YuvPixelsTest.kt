package fr.speedvision

import fr.speedvision.data.yuvToArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class YuvPixelsTest {
    @Test fun blackAndWhite() {
        assertEquals(0xff000000.toInt(), yuvToArgb(16, 128, 128))
        assertEquals(0xffffffff.toInt(), yuvToArgb(235, 128, 128))
    }

    @Test fun redAndClamping() {
        assertEquals(0xffff0000.toInt(), yuvToArgb(81, 90, 240))
        assertEquals(0xff000000.toInt(), yuvToArgb(0, 128, 128))
    }
}
