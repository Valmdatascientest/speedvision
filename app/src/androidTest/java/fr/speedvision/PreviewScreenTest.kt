package fr.speedvision

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.speedvision.presentation.PreviewState
import org.junit.Rule
import org.junit.Test

class PreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noMeasurementAndNoStartBeforeSelection() {
        compose.setContent { MaterialTheme { PreviewScreen(PreviewState(), {}, {}, {}, {}) } }
        compose.onNodeWithText("START · Rejouer").assertIsNotEnabled()
        compose.onNodeWithText("Vitesse relative : —").assertExists()
    }
}
