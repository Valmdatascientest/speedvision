package fr.speedvision

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.speedvision.meta.MetaSupport
import org.junit.Rule
import org.junit.Test

class MetaPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sdkPanelRequiresExplicitActivationAndDoesNotInventDevices() {
        compose.setContent { MaterialTheme { MetaSupport.Panel({}, {}) } }
        compose.onNodeWithText("Activer la connexion Meta").assertExists()
        compose.onNodeWithText("Utiliser les lunettes 1").assertDoesNotExist()
    }
}
