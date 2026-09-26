package fr.speedvision

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.core.Wearables
import fr.speedvision.domain.PlaybackState
import fr.speedvision.meta.MetaGlassesVideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaSourceTest {
    @Test fun noRegistrationNeverProducesAPretendStreamAndStopIsIdempotent() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            val source = MetaGlassesVideoSource(scope, "absent-device")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            try {
                instrumentation.runOnMainSync { source.start() }
                val rejected = withTimeout(20_000) { source.status.first { it.state == PlaybackState.ERROR } }
                assertEquals(PlaybackState.ERROR, rejected.state)
                instrumentation.runOnMainSync {
                    source.stop()
                    source.stop()
                    source.start()
                    source.stop()
                }
                assertEquals(PlaybackState.STOPPED, source.status.value.state)
            } finally {
                instrumentation.runOnMainSync { source.close() }
                scope.cancel()
            }
        }

    @Test fun realSdkInitializesOrReportsUnavailableWithoutCompanion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 31) {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        }
        instrumentation.runOnMainSync {
            try {
                val initialized = Wearables.initialize(instrumentation.targetContext)
                if (initialized.isSuccess) {
                    assertTrue(Wearables.devices.value.isEmpty())
                } else {
                    assertTrue(initialized.isFailure)
                }
            } finally {
                Wearables.reset()
            }
        }
    }
}
