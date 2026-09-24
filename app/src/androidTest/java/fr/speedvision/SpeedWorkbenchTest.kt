package fr.speedvision

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import fr.speedvision.presentation.SpeedWorkbench
import org.junit.Rule
import org.junit.Test

class SpeedWorkbenchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitSyntheticReplayShowsSignedSpeedAndClearRemovesIt() {
        compose.setContent { MaterialTheme { SpeedWorkbench {} } }
        compose.onNodeWithText("Aucune série chargée").assertExists()
        compose.onNodeWithText("Exporter les résultats CSV").assertDoesNotExist()
        compose.onNodeWithText("Charger un exemple synthétique").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Vitesse relative axiale : +18,0 km/h")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("EXEMPLE SYNTHÉTIQUE", substring = true).assertExists()
        compose.onNodeWithText("Exporter les résultats CSV").performScrollTo().performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(10_000) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.packageName
                ?.toString()
                ?.contains("documentsui") == true
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("Vitesse relative axiale : +18,0 km/h").assertExists()
        compose.onNodeWithText("Effacer la série en mémoire").performScrollTo().performClick()
        compose.onNodeWithText("Vitesse relative axiale : +18,0 km/h").assertDoesNotExist()
        compose.onNodeWithText("Exporter les résultats CSV").assertDoesNotExist()
    }
}
