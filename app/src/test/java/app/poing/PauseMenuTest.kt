package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 — the pause menu, extracted from GameScreen so the
 * player screen itself stays under the dex guard. The menu is an
 * Atomic translucent purple panel with a DotGothic16 title and
 * section header; save-state times are DSEG14.
 *
 * The session is never started (no native code under Robolectric),
 * so every slot reads empty and the Load buttons are disabled —
 * the test pins the rendering, not the save behaviour (covered in
 * PlayerSessionTest).
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PauseMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun session(): PlayerSession {
        val system = File(temp.root, "system").apply { mkdirs() }
        val saves = File(temp.root, "saves").apply { mkdirs() }
        return PlayerSession(
            systemDir = system.absolutePath,
            saveDir = saves.absolutePath,
            gamePath = File(temp.root, "game.gb").absolutePath,
            saveBase = "game",
            onFrame = {},
            onStats = {},
            onError = {},
        )
    }

    @Test
    fun pauseMenuShowsTitleAndSlots() {
        composeRule.setContent {
            PoingTheme {
                PauseMenu(
                    gameName = "TETRIS",
                    session = session(),
                    pauseMessage = null,
                    onMessage = {},
                    onResume = {},
                    onQuit = {},
                    onCoreOptions = {},
                )
            }
        }
        composeRule.onNodeWithText("Paused").assertIsDisplayed()
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("Save states").assertIsDisplayed()
        composeRule.onNodeWithText("Slot 0 (quick)").assertIsDisplayed()
        composeRule.onNodeWithText("Slot 3").assertIsDisplayed()
        composeRule.onAllNodesWithText("empty").assertCountEquals(4)
    }
}
