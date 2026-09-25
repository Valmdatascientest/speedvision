package fr.speedvision

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.speedvision.presentation.VoiceWorkbench
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceWorkbenchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingIsSilentAndHistoricalMeasurementsAreExcluded() {
        var closed = false
        compose.setContent { MaterialTheme { VoiceWorkbench { closed = true } } }
        compose.onNodeWithText("Tester la voix sans mesure").assertIsNotEnabled()
        compose.onNodeWithText("Aucune vitesse live disponible.", substring = true).assertExists()
        compose.onNodeWithText("Couper la voix").performScrollTo().performClick()
        compose.onNodeWithText("Tester la voix sans mesure").assertIsNotEnabled()
        compose.onNodeWithText("Fermer la voix").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
