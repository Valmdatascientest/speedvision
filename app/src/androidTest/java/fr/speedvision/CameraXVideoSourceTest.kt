package fr.speedvision

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.camera.CameraXVideoSource
import fr.speedvision.domain.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CameraXVideoSourceTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun aDeniedPermissionIsAnErrorWithoutStartingCamera() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals(
                "Run on a fresh install without -g",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.CAMERA),
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                withContext(Dispatchers.Main) {
                    val source = CameraXVideoSource(context, Owner(), scope)
                    try {
                        source.start()
                        assertEquals(PlaybackState.ERROR, source.status.value.state)
                    } finally {
                        source.close()
                    }
                }
            } finally {
                scope.cancel()
            }
        }

    @Test fun bRealCameraProducesFramesAndReleasesOnStop() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var source: CameraXVideoSource
                scenario.onActivity { source = CameraXVideoSource(context, it, scope) }
                try {
                    val frame = async { withTimeout(20_000) { source.frames().first() } }
                    withContext(Dispatchers.Main) { source.start() }
                    val actual = frame.await()
                    assertTrue(actual.width > 0 && actual.height > 0)
                    assertEquals(actual.width * actual.height, actual.argb.size)
                    assertEquals("camera-monotonic", actual.geometry.timestampOrigin)
                    withContext(Dispatchers.Main) { source.stop() }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    delay(100)
                    assertEquals(PlaybackState.STOPPED, source.status.value.state)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    val again =
                        async {
                            withTimeout(20_000) {
                                source.frames().first { it.presentationTimeUs > actual.presentationTimeUs }
                            }
                        }
                    withContext(Dispatchers.Main) { source.start() }
                    assertTrue(again.await().argb.isNotEmpty())
                } finally {
                    withContext(Dispatchers.Main) { source.close() }
                    scope.cancel()
                }
            }
        }
}
