package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        // The menu scrolls (it is taller than the test window), so
        // the last slot row starts below the fold — scroll it into
        // view before asserting it is displayed.
        composeRule.onNodeWithText("Slot 3").performScrollTo().assertIsDisplayed()
        // Four empty slots + the empty auto-save row.
        composeRule.onAllNodesWithText("empty").assertCountEquals(5)
    }

    @Test
    fun slotThumbnailShowsWhenThePngExists() {
        val saves = File(temp.root, "saves").apply { mkdirs() }
        File(saves, "game.state0").writeBytes(byteArrayOf(1, 2, 3))
        writePng(File(saves, "game.state0.png"))
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
        composeRule.onNodeWithTag("state-thumb-0").assertIsDisplayed()
        composeRule.onNodeWithTag("state-thumb-1").assertIsDisplayed()
    }

    @Test
    fun autoSaveRowShowsWhenAnAutoSaveExists() {
        val saves = File(temp.root, "saves").apply { mkdirs() }
        File(saves, "game.stateAuto").writeBytes(byteArrayOf(1, 2, 3))
        writePng(File(saves, "game.stateAuto.png"))
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
        composeRule.onNodeWithText("Auto-save").performScrollTo().assertIsDisplayed()
        // The thumbnail row sits below the header + description,
        // so it starts below the fold too — scroll it into view
        // before asserting it is displayed (same as Slot 3).
        composeRule.onNodeWithTag("state-thumb-auto").performScrollTo().assertIsDisplayed()
        // Four empty slots; the auto-save row shows a time, not
        // "empty", so the count stays four.
        composeRule.onAllNodesWithText("empty").assertCountEquals(4)
    }

    /** A tiny valid PNG, the way the session's thumbnail writer makes one. */
    private fun writePng(file: File) {
        val bitmap = android.graphics.Bitmap.createBitmap(120, 108, android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
