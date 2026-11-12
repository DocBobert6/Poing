package app.poing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 7) — haptics through the player: pressing A or
 * the D-pad clicks once; Start stays silent (only D-pad and A/B
 * vibrate). The Haptics is injected with a fake Vibrator, so the
 * test counts the clicks the player actually produced.
 *
 * The session is never started (no native code under Robolectric),
 * so the game itself never runs — the test pins the wiring, not
 * playback.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameScreenHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val vibrator = FakeVibrator()

    private fun setContent() {
        val root = File(temp.root, "stores").apply { mkdirs() }
        val game = File(temp.root, "game.gb")
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = game.absolutePath,
                    gameName = "TETRIS",
                    isGba = false,
                    optionsStore = CoreOptionsStore(root),
                    appSettings = AppSettingsStore(root),
                    haptics = Haptics(vibrator),
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    @Test
    fun pressingAClicksOnce() {
        setContent()
        assertEquals(0, vibrator.clicks)
        composeRule.onNodeWithTag("hold-A").performTouchInput {
            down(center)
            up()
        }
        assertEquals(1, vibrator.clicks)
    }

    @Test
    fun pressingDpadClicksOnce() {
        setContent()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        assertEquals(1, vibrator.clicks)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
    }

    @Test
    fun pressingStartStaysSilent() {
        setContent()
        composeRule.onNodeWithTag("hold-Start").performTouchInput {
            down(center)
            up()
        }
        assertEquals(0, vibrator.clicks)
    }
}
