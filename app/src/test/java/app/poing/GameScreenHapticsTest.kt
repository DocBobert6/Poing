package app.poing

import android.content.Context
import android.os.Vibrator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 7) — haptics through the player: pressing A or
 * the D-pad clicks once. Alpha.8 — every overlay button clicks:
 * L, R, Start and Select click too, and a strength of 0 silences
 * them all. The Haptics is injected with the test's own Vibrator,
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

    private fun setContent(haptics: Haptics? = null, isGba: Boolean = false) {
        val root = File(temp.root, "stores").apply { mkdirs() }
        val game = File(temp.root, if (isGba) "game.gba" else "game.gb")
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = game.absolutePath,
                    gameName = "TETRIS",
                    isGba = isGba,
                    optionsStore = CoreOptionsStore(root),
                    appSettings = AppSettingsStore(root),
                    haptics = haptics ?: Haptics(vibrator()),
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    @Test
    fun pressingAClicks() {
        setContent()
        val v = vibrator()
        assertFalse(shadowOf(v).isVibrating)
        composeRule.onNodeWithTag("hold-A").performTouchInput {
            down(center)
            up()
        }
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun pressingDpadClicks() {
        setContent()
        val v = vibrator()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        assertTrue(shadowOf(v).isVibrating)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
    }

    @Test
    fun pressingStartClicks() {
        // Alpha.8 — Start clicks like every other button
        // (it was silent before: only D-pad and A/B vibrated).
        setContent()
        val v = vibrator()
        composeRule.onNodeWithTag("hold-Start").performTouchInput {
            down(center)
            up()
        }
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun pressingLClicks() {
        // Alpha.8 — L clicks too. L/R only exist on GBA
        // (GB/GBC have no shoulder buttons), so this test
        // plays a GBA game.
        setContent(isGba = true)
        val v = vibrator()
        composeRule.onNodeWithTag("hold-L").performTouchInput {
            down(center)
            up()
        }
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun pressingRClicks() {
        setContent(isGba = true)
        val v = vibrator()
        composeRule.onNodeWithTag("hold-R").performTouchInput {
            down(center)
            up()
        }
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun pressingSelectClicks() {
        setContent()
        val v = vibrator()
        composeRule.onNodeWithTag("hold-Select").performTouchInput {
            down(center)
            up()
        }
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun strengthZeroSilencesEveryButton() {
        // Alpha.8 — 0 on the strength slider silences the
        // click, the same as the toggle off.
        val v = vibrator()
        setContent(Haptics(v, strength = 0))
        assertFalse(shadowOf(v).isVibrating)
        composeRule.onNodeWithTag("hold-A").performTouchInput {
            down(center)
            up()
        }
        assertFalse(shadowOf(v).isVibrating)
    }
}
