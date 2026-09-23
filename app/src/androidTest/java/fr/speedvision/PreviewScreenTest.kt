package fr.speedvision

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.speedvision.domain.PixelBox
import fr.speedvision.domain.TrackStatus
import fr.speedvision.domain.TrackingResult
import fr.speedvision.domain.VehicleTrack
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

    @Test fun trackingStatusDoesNotEnableUnimplementedMeasurements() {
        val tracks =
            listOf(
                VehicleTrack(12, PixelBox(0f, 0f, 40f, 40f), 2, TrackStatus.CONFIRMED, 0, 100_000),
                VehicleTrack(13, PixelBox(50f, 0f, 90f, 40f), 2, TrackStatus.LOST, null, 0),
            )
        compose.setContent {
            MaterialTheme { PreviewScreen(PreviewState(tracking = TrackingResult(tracks, emptyList())), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Suivi : 1 confirmée(s) · 1 perdue(s)").assertExists()
        compose.onNodeWithText("Vitesse relative : —").assertExists()
        compose.onNodeWithText("Distance : —     ·     Confiance : —").assertExists()
    }
}
