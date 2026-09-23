package fr.speedvision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectionTest {
    @Test fun landscapeLetterboxReversesPaddingAndRoiOffset() {
        val transform = Letterbox(1280, 720, originX = 10, originY = 20)
        assertEquals(0, transform.padX)
        assertEquals(140, transform.padY)
        assertEquals(PixelBox(10f, 20f, 1290f, 740f), transform.decode(320f, 320f, 640f, 360f))
    }

    @Test fun portraitAndOddDimensionsStayInBounds() {
        val transform = Letterbox(101, 203)
        val box = requireNotNull(transform.decode(320f, 320f, 640f, 640f))
        assertEquals(PixelBox(0f, 0f, 101f, 203f), box)
        assertNull(transform.decode(0f, 0f, 10f, 10f))
        assertNull(transform.decode(Float.NaN, 0f, 1f, 1f))
        assertNull(transform.decode(2f, 2f, -1f, 1f))
    }

    @Test fun rawYoloLayoutHasNoObjectnessAndRejectsOtherWinningClasses() {
        // Three candidates, two classes; candidate 0 belongs to class 0, 1 and 2 to class 1.
        val tensor = floatArrayOf(100f, 200f, 202f, 100f, 200f, 202f, 40f, 60f, 60f, 40f, 60f, 60f, 0.9f, 0.1f, 0.1f, 0.5f, 0.8f, 0.7f)
        val result = YoloPostprocessor.decode(tensor, 2, 3, Letterbox(640, 640), setOf(1))
        assertEquals(1, result.size)
        assertEquals(0.8f, result.single().score)
        assertEquals(PixelBox(170f, 170f, 230f, 230f), result.single().box)
    }

    @Test fun invalidScoresAndDimensionsAreRejected() {
        assertTrue(YoloPostprocessor.decode(floatArrayOf(20f, 20f, 10f, 10f, Float.NaN), 1, 1, Letterbox(640, 640), setOf(0)).isEmpty())
        assertFailsWith<IllegalArgumentException> { YoloPostprocessor.decode(FloatArray(10), 1, 1, Letterbox(10, 10), setOf(0)) }
        assertFailsWith<IllegalArgumentException> { PixelBox(0f, 0f, 0f, 10f) }
    }

    @Test fun nmsIsClassAwareAndDeterministic() {
        val box = PixelBox(0f, 0f, 100f, 100f)
        val detections = listOf(Detection(box, 0.8f, 2), Detection(box, 0.9f, 2), Detection(box, 0.7f, 5))
        assertEquals(listOf(0.9f, 0.7f), YoloPostprocessor.nms(detections).map { it.score })
        assertEquals(1f, box.iou(box))
        assertEquals(0f, box.iou(PixelBox(100f, 100f, 150f, 150f)))
    }

    @Test fun boundedLatencyPercentilesDoNotInventSamples() {
        val window = LatencyWindow(3)
        assertEquals(0.0, window.percentile(0.95))
        listOf(100.0, 30.0, 10.0, 20.0).forEach(window::add)
        assertEquals(3, window.count)
        assertEquals(20.0, window.percentile(0.5))
        assertEquals(30.0, window.percentile(0.95))
        assertFailsWith<IllegalArgumentException> { window.add(Double.NaN) }
    }
}
