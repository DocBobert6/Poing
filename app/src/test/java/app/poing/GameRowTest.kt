package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 5) — the library row shows "Played …" when the
 * game has playtime, and nothing when it has none.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun game() = LibraryGame(
        id = "abc123",
        title = "TETRIS",
        system = GameSystem.GB,
        fileName = "abc123.gb",
        sourceName = "tetris.gb",
        sha256 = "abc123",
        addedAt = 0L,
    )

    @Test
    fun rowShowsPlaytimeWhenPlayed() {
        composeRule.setContent {
            PoingTheme {
                GameRow(
                    game = game(),
                    coverFile = null,
                    onPlay = {},
                    onRemove = {},
                    playtimeSeconds = 7500,
                )
            }
        }
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("Played 2h 5m").assertIsDisplayed()
    }

    @Test
    fun rowShowsNoPlaytimeLineWhenNeverPlayed() {
        composeRule.setContent {
            PoingTheme {
                GameRow(
                    game = game(),
                    coverFile = null,
                    onPlay = {},
                    onRemove = {},
                    playtimeSeconds = 0,
                )
            }
        }
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Played", substring = true).assertCountEquals(0)
    }
}
