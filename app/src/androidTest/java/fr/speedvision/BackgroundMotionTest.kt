package fr.speedvision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import fr.speedvision.domain.ImagePoint
import fr.speedvision.domain.PixelBox
import fr.speedvision.motion.BackgroundMotion
import fr.speedvision.motion.BackgroundMotionEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackgroundMotionTest {
    private fun texture(
        width: Int = 640,
        height: Int = 480,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 40, 40))
        val random = Random(624)
        val paint = Paint()
        repeat(1800) {
            val intensity = random.nextInt(70, 255)
            paint.color = Color.rgb(intensity, intensity, intensity)
            val x = random.nextInt(width).toFloat()
            val y = random.nextInt(height).toFloat()
            canvas.drawRect(x, y, x + random.nextInt(3, 12), y + random.nextInt(3, 12), paint)
        }
        return bitmap
    }

    private fun warp(
        source: Bitmap,
        matrix: Matrix,
    ): Bitmap =
        Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }

    private fun pair(
        a: Bitmap,
        b: Bitmap,
        boxesA: List<PixelBox> = emptyList(),
        boxesB: List<PixelBox> = boxesA,
    ): BackgroundMotion =
        BackgroundMotionEstimator().use {
            assertNull(it.process(a, boxesA, 0, "source").previousToCurrent)
            it.process(b, boxesB, 100_000, "source")
        }

    @Test fun stationaryAndTranslationAlignWithoutMetricClaim() {
        val a = texture()
        val b = warp(a, Matrix().apply { setTranslate(4f, -3f) })
        try {
            val fixed = pair(a, a)
            assertNotNull("$fixed", fixed.previousToCurrent)
            val p = fixed.predict(ImagePoint(300.0, 200.0))!!
            assertEquals(300.0, p.x, .1)
            assertEquals(200.0, p.y, .1)
            val translated = pair(a, b)
            assertNotNull("$translated", translated.previousToCurrent)
            val residual = translated.residual(ImagePoint(300.0, 200.0), ImagePoint(304.0, 197.0))!!
            assertEquals(0.0, residual.x, .4)
            assertEquals(0.0, residual.y, .4)
            assertEquals("Alignement visuel uniquement", translated.reason)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test fun opticalAxisRotationAndNativeScaling() {
        val a = texture(1280, 960)
        val matrix = Matrix().apply { setRotate(1.5f, 640f, 480f) }
        val b = warp(a, matrix)
        try {
            val result = pair(a, b)
            assertNotNull("$result", result.previousToCurrent)
            val point = floatArrayOf(400f, 300f)
            matrix.mapPoints(point)
            val p = result.predict(ImagePoint(400.0, 300.0))!!
            assertEquals(point[0].toDouble(), p.x, 1.0)
            assertEquals(point[1].toDouble(), p.y, 1.0)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test fun movingVehicleIsMaskedAtBothPositions() {
        val a = texture()
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        val foreground = texture(120, 160)
        Canvas(a).drawBitmap(foreground, 180f, 150f, null)
        Canvas(b).drawBitmap(foreground, 230f, 150f, null)
        try {
            val result = pair(a, b, listOf(PixelBox(180f, 150f, 300f, 310f)), listOf(PixelBox(230f, 150f, 350f, 310f)))
            assertNotNull("$result", result.previousToCurrent)
            val p = result.predict(ImagePoint(400.0, 250.0))!!
            assertEquals(400.0, p.x, .5)
            assertEquals(250.0, p.y, .5)
        } finally {
            a.recycle()
            b.recycle()
            foreground.recycle()
        }
    }

    @Test fun competingPlanesRejectParallax() {
        val a = texture()
        val b = a.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(b)
        canvas.save()
        canvas.clipRect(0, 0, 320, 480)
        canvas.drawBitmap(a, 7f, 0f, null)
        canvas.restore()
        canvas.save()
        canvas.clipRect(320, 0, 640, 480)
        canvas.drawBitmap(a, -7f, 0f, null)
        canvas.restore()
        try {
            assertNull("${pair(a, b)}", pair(a, b).previousToCurrent)
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    @Test fun missingTextureMaskOrCoverageReject() {
        val blank = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val textured = texture()
        try {
            assertNull(pair(blank, blank).previousToCurrent)
            assertNull(pair(textured, textured, listOf(PixelBox(0f, 0f, 640f, 480f))).previousToCurrent)
            assertNull(pair(textured, textured, listOf(PixelBox(150f, 0f, 640f, 480f))).previousToCurrent)
            BackgroundMotionEstimator().use {
                it.process(textured, emptyList(), 0, "a")
                assertNull(it.process(textured, null, 100_000, "a").previousToCurrent)
                assertNull(it.process(textured, emptyList(), 200_000, "a").previousToCurrent)
                assertNull(it.process(textured, List(30) { PixelBox(0f, 0f, 1f, 1f) }, 300_000, "a").previousToCurrent)
            }
        } finally {
            blank.recycle()
            textured.recycle()
        }
    }

    @Test fun gapsDuplicateTimeSourceAndCloseResetHistory() {
        val a = texture()
        try {
            BackgroundMotionEstimator().use {
                it.process(a, emptyList(), 0, "a")
                assertNull(it.process(a, emptyList(), 0, "a").previousToCurrent)
                assertNull(it.process(a, emptyList(), 400_000, "a").previousToCurrent)
                assertNotNull(it.process(a, emptyList(), 500_000, "a").previousToCurrent)
                assertNull(it.process(a, emptyList(), 600_000, "b").previousToCurrent)
                it.close()
                assertNull(it.process(a, emptyList(), 700_000, "b").previousToCurrent)
            }
        } finally {
            a.recycle()
        }
    }

    @Test fun aCoherentlyMovingSceneIsNotCertifiedAsStationary() {
        val a = texture()
        val b = warp(a, Matrix().apply { setTranslate(3f, 2f) })
        try {
            // The same pixels can be produced by moving a planar scene or moving the camera.
            val result = pair(a, b)
            assertNotNull("$result", result.previousToCurrent)
            assertTrue(result.reason.contains("uniquement"))
        } finally {
            a.recycle()
            b.recycle()
        }
    }
}
