package app.poing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 6) — the input display: a row of pills above
 * the game view showing the buttons currently held, toggleable
 * in Settings and off by default.
 *
 * The session is never started (no native code under Robolectric),
 * so the game itself never runs — the test pins that the display
 * mirrors the merged mask the core sees, through the real
 * D-pad, not playback.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameScreenInputDisplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun setContent(isGba: Boolean = false, displayOn: Boolean = true) {
        val root = File(temp.root, "stores").apply { mkdirs() }
        val settings = AppSettingsStore(root)
        settings.setInputDisplayVisible(displayOn)
        val game = File(temp.root, "game.gb")
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = game.absolutePath,
                    gameName = "TETRIS",
                    isGba = isGba,
                    optionsStore = CoreOptionsStore(root),
                    appSettings = settings,
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    @Test
    fun displayShowsReleasedPillsWhenOn() {
        setContent()
        composeRule.onNodeWithTag("input-display").assertIsDisplayed()
        composeRule.onNodeWithTag("input-released-up").assertIsDisplayed()
        composeRule.onNodeWithTag("input-released-a").assertIsDisplayed()
        composeRule.onAllNodesWithTag("input-pressed-up").assertCountEquals(0)
        // No shoulder pills on a GB game.
        composeRule.onAllNodesWithTag("input-released-l").assertCountEquals(0)
    }

    @Test
    fun displayShowsShoulderPillsForGba() {
        setContent(isGba = true)
        composeRule.onNodeWithTag("input-released-l").assertIsDisplayed()
        composeRule.onNodeWithTag("input-released-r").assertIsDisplayed()
    }

    @Test
    fun pressingDpadMarksUpPressedThenReleased() {
        setContent()
        // Press near the top edge of the D-pad and hold: the pill
        // flips to pressed while the finger is down.
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        composeRule.onNodeWithTag("input-pressed-up").assertIsDisplayed()
        composeRule.onAllNodesWithTag("input-released-up").assertCountEquals(0)
        // Release: the pill flips back to released.
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        composeRule.onNodeWithTag("input-released-up").assertIsDisplayed()
    }

    @Test
    fun displayHiddenWhenOff() {
        setContent(displayOn = false)
        composeRule.onAllNodesWithTag("input-display").assertCountEquals(0)
    }
}
