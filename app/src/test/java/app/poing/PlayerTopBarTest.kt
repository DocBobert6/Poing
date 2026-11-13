package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen Optics dim fix (Robert, 2026-11-12): the live lux
 * shows in the diagnostic line when dev stats are on, so the
 * room's light — and whether the dim engages — is visible on
 * the screen itself. Pinned to emulated SDK 35, same as the
 * startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stats = PlayerStats(
        width = 160,
        height = 144,
        coreFps = 59.7,
        measuredFps = 58.9,
        framesDrawn = 296,
        sampleRate = 48000,
    )

    private fun setContent(lux: Int?) {
        composeRule.setContent {
            PoingTheme {
                TopBarRow(
                    stats = stats,
                    recording = false,
                    recordingElapsed = 0,
                    buttonLabel = "Pause",
                    onButtonClick = {},
                    lux = lux,
                )
            }
        }
    }

    @Test
    fun diagnosticLineShowsLiveLux() {
        setContent(lux = 42)
        // The diagnostic is one Text built from an annotated
        // string, so "42lx" is a substring of the line, not a
        // node of its own — match by substring.
        composeRule.onNodeWithText("42lx", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineHidesLuxWithoutAReading() {
        setContent(lux = null)
        composeRule.onAllNodesWithText("lx", substring = true).assertCountEquals(0)
    }
}
