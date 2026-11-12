package app.poing

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Properties
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 4) — per-game saved control positions, through
 * the player: pause, "Move controls", drag the D-pad, Done, and
 * the layout is in the game's file.
 *
 * The session is never started (no native code under Robolectric),
 * so the game itself never runs — the test pins the layout flow,
 * not playback. The layout file lives in the app context's
 * filesDir, like on the device.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameScreenLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun setContent(gamePath: String) {
        val root = File(temp.root, "stores").apply { mkdirs() }
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = gamePath,
                    gameName = "TETRIS",
                    isGba = false,
                    optionsStore = CoreOptionsStore(root),
                    appSettings = AppSettingsStore(root),
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    private fun layoutFile(base: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.filesDir.resolve("control-layouts").resolve("$base.properties")
    }

    @Test
    fun moveControlsDragSavesPerGameLayout() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithText("Drag the controls to move them").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithText("Done").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        val y = props.getProperty("dpad.y")?.toFloatOrNull()
        assertNotNull("dpad.y should be saved after the drag", y)
        assertTrue("dragging up should save a negative y, got $y", y!! < 0f)
    }

    @Test
    fun resetRestoresTheDefaults() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithTag("controls-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithText("Reset").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        assertTrue(props.getProperty("dpad.y")?.toFloatOrNull() == 0f)
    }
}
