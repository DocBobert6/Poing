package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 9) — the rewind control: the RW hold button is
 * off the overlay by default (the standing overlay rule) and
 * appears when the Settings toggle is on. The session itself
 * never starts under Robolectric (no native code), so the game
 * never runs — the test pins the control's visibility and the
 * session's safe no-ops, not live rewinding.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RewindControlTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun setContent(rewindOn: Boolean) {
        val root = File(temp.root, "stores").apply { mkdirs() }
        if (rewindOn) AppSettingsStore(root).setRewindControlVisible(true)
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = File(temp.root, "game.gb").absolutePath,
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

    @Test
    fun rewindButtonHiddenByDefault() {
        setContent(rewindOn = false)
        composeRule.onAllNodesWithTag("hold-action-RW").assertCountEquals(0)
    }

    @Test
    fun rewindButtonShowsWhenTheToggleIsOn() {
        setContent(rewindOn = true)
        composeRule.onNodeWithTag("hold-action-RW").assertIsDisplayed()
    }

    @Test
    fun rewindIsANoOpBeforeStart() {
        val system = File(temp.root, "system").apply { mkdirs() }
        val saves = File(temp.root, "saves").apply { mkdirs() }
        val session = PlayerSession(
            systemDir = system.absolutePath,
            saveDir = saves.absolutePath,
            gamePath = File(temp.root, "game.gb").absolutePath,
            saveBase = "game",
            onFrame = {},
            onStats = {},
            onError = {},
        )
        assertFalse(session.startRewind())
        assertFalse(session.stepRewind())
        // endRewind never resumes a game that was never rewinding.
        session.endRewind()
        assertFalse(session.isPaused)
        assertEquals(0, session.rewindSize())
    }
}
