package fr.speedvision

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.data.VideoFileSource
import fr.speedvision.domain.PlaybackState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoFileSourceTest {
    @Test fun actualDecoderEmitsFramesAndReplaysAfterEof() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val file = File(context.cacheDir, "decoder-test.mp4")
            instrumentation.context.assets
                .open("sample.mp4")
                .use { input -> file.outputStream().use(input::copyTo) }
            val source = VideoFileSource(context, Uri.fromFile(file), this)
            val pts = mutableListOf<Long>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    source.frames().collect {
                        assertEquals(it.width * it.height, it.argb.size)
                        assertTrue(it.width > 0)
                        pts.add(it.presentationTimeUs)
                    }
                }
            try {
                repeat(2) {
                    pts.clear()
                    source.start()
                    withTimeout(15_000) { source.status.first { it.state == PlaybackState.ENDED || it.state == PlaybackState.ERROR } }
                    assertEquals(PlaybackState.ENDED, source.status.value.state)
                    assertTrue(pts.size >= 5)
                    assertTrue(pts.zipWithNext().all { (a, b) -> b > a })
                }
            } finally {
                source.stop()
                collector.cancelAndJoin()
                file.delete()
            }
        }

    @Test fun missingFileBecomesErrorAndStopIsIdempotent() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = VideoFileSource(context, Uri.fromFile(File(context.cacheDir, "absent.mp4")), this)
            source.start()
            withTimeout(5_000) { source.status.first { it.state == PlaybackState.ERROR } }
            source.stop()
            source.stop()
            assertEquals(PlaybackState.STOPPED, source.status.value.state)
        }

    @Test
    fun stopDuringPlaybackCancelsFramesAndAllowsRestart() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val file = File(context.cacheDir, "stop-test.mp4")
            instrumentation.context.assets
                .open("sample.mp4")
                .use { input -> file.outputStream().use(input::copyTo) }
            val source = VideoFileSource(context, Uri.fromFile(file), this)
            var count = 0
            val firstFrame = kotlinx.coroutines.CompletableDeferred<Unit>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    source.frames().collect {
                        count++
                        firstFrame.complete(Unit)
                    }
                }
            try {
                source.start()
                source.start()
                withTimeout(5_000) { firstFrame.await() }
                source.stop()
                val stoppedCount = count
                kotlinx.coroutines.delay(250)
                assertEquals(stoppedCount, count)
                assertEquals(PlaybackState.STOPPED, source.status.value.state)
                source.start()
                withTimeout(15_000) { source.status.first { it.state == PlaybackState.ENDED || it.state == PlaybackState.ERROR } }
                assertEquals(PlaybackState.ENDED, source.status.value.state)
                assertTrue(count > stoppedCount)
            } finally {
                source.stop()
                collector.cancelAndJoin()
                file.delete()
            }
        }
}
