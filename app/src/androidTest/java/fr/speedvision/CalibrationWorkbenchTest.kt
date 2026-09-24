package fr.speedvision

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import fr.speedvision.domain.CalibrationBinding
import fr.speedvision.presentation.CalibrationWorkbench
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

class CalibrationWorkbenchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun validProfileAndObservedCornersEnableManualDepthOnly() {
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        compose.setContent {
            MaterialTheme {
                CalibrationWorkbench(image, CalibrationBinding("ui-fixture", 1280, 720, 0, 0, 1280, 720, 0), 123_000, {})
            }
        }
        compose.onNodeWithText("Appliquer la calibration").assertIsNotEnabled()
        for ((label, value) in listOf(
            "fx natif (px)" to "1000",
            "fy natif (px)" to "1000",
            "cx natif (px)" to "640",
            "cy natif (px)" to "360",
            "Provenance, caméra, mode, date" to "synthetic UI test",
            "RMS sur vues de validation (px, ≤ 2)" to "0.1",
            "Largeur réelle (mm)" to "520",
            "Hauteur réelle (mm)" to "110",
        )) {
            compose.onNodeWithText(label).performScrollTo().performTextInput(value)
        }
        // The acknowledgement is the sole checkbox in this dialog.
        compose
            .onNode(
                androidx.compose.ui.test
                    .isToggleable(),
            ).performScrollTo()
            .performClick()
        compose.onNodeWithText("Appliquer la calibration").performScrollTo().performClick()
        compose.onNodeWithText("Profil appliqué à cette source et ce mode.").assertExists()
        compose.onNodeWithTag("plate-corners").performScrollTo()
        for (p in listOf(380f to 305f, 900f to 305f, 900f to 415f, 380f to 415f)) {
            compose.onNodeWithTag("plate-corners").performTouchInput {
                val scale = min(width / 1280f, height / 720f)
                click(Offset((width - 1280 * scale) / 2 + p.first * scale, (height - 720 * scale) / 2 + p.second * scale))
            }
        }
        compose.onNodeWithText("Estimer la profondeur axiale").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Profondeur axiale Z :", substring = true),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Incertitude métrique non quantifiée.", substring = true).assertExists()
        compose.onNodeWithText("Effacer les coins").performScrollTo().performClick()
        compose.onNodeWithText("Estimer la profondeur axiale").assertIsNotEnabled()
    }
}
