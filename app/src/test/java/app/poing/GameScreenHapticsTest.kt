package app.poing

import android.content.Context
import android.os.Vibrator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 7) — haptics through the player: pressing A or
 * the D-pad clicks once; Start stays silent (only D-pad and A/B
 * vibrate). The Haptics is injected with the test's own Vibrator,
 * so the shadow records what the player vibrated.
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

    private fun vibrator(): Vibrator =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Vibrator::class.java)

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
                    haptics = Haptics(vibrator()),
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    @Test
    fun pressingAClicksOnce() {
        setContent()
        val v = vibrator()
        assertEquals(0, shadowOf(v).vibrations.size)
        composeRule.onNodeWithTag("hold-A").performTouchInput {
            down(center)
            up()
        }
        assertEquals(1, shadowOf(v).vibrations.size)
    }

    @Test
    fun pressingDpadClicksOnce() {
        setContent()
        val v = vibrator()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        assertEquals(1, shadowOf(v).vibrations.size)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
    }

    @Test
    fun pressingStartStaysSilent() {
        setContent()
        val v = vibrator()
        composeRule.onNodeWithTag("hold-Start").performTouchInput {
            down(center)
            up()
        }
        assertEquals(0, shadowOf(v).vibrations.size)
    }
}
