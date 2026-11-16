package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    coverBitmap = null,
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
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                    playtimeSeconds = 0,
                )
            }
        }
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Played", substring = true).assertCountEquals(0)
    }

    @Test
    fun sampleSizeBringsTheLongSideTo512() {
        assertEquals(1, sampleSizeFor(240, 160))
        assertEquals(1, sampleSizeFor(512, 512))
        assertEquals(2, sampleSizeFor(513, 513))
        assertEquals(2, sampleSizeFor(1024, 1024))
        assertEquals(4, sampleSizeFor(2048, 1024))
        assertEquals(8, sampleSizeFor(4096, 300))
    }

    /**
     * The grid cell shows the same title, badge, playtime and
     * actions as the row — it is the same game, only laid out
     * as a tile.
     */
    @Test
    fun gridCellShowsPlaytimeWhenPlayed() {
        composeRule.setContent {
            PoingTheme {
                GameGridCell(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                    playtimeSeconds = 7500,
                )
            }
        }
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("GB · tetris.gb").assertIsDisplayed()
        composeRule.onNodeWithText("Played 2h 5m").assertIsDisplayed()
        composeRule.onNodeWithText("Saves").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun gridCellShowsNoPlaytimeLineWhenNeverPlayed() {
        composeRule.setContent {
            PoingTheme {
                GameGridCell(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                    playtimeSeconds = 0,
                )
            }
        }
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onAllNodesWithText("Played", substring = true).assertCountEquals(0)
    }

    @Test
    fun gridCellTapPlays() {
        var played = false
        composeRule.setContent {
            PoingTheme {
                GameGridCell(
                    game = game(),
                    coverBitmap = null,
                    onPlay = { played = true },
                    onRemove = {},
                )
            }
        }
        // The title itself, not the merged cell: performClick
        // taps the node's center, and the merged cell node's
        // center falls on the button strip in the narrow test
        // window (the Cover button widened it past center) —
        // the tap hit Saves instead of the cell. The unmerged
        // title's center is on the tile/title area, inside
        // the cell's clickable, as a real tap on the title is.
        composeRule.onNodeWithText("TETRIS", useUnmergedTree = true).performClick()
        assertTrue(played)
    }

    /**
     * Alpha.22 — the library row shows the game's achievement
     * progress ("Achievements 3/12") once its set has loaded,
     * and nothing when it has never loaded — never a
     * misleading 0/N.
     */
    private fun progress() = RaHubGame(
        id = "abc123",
        title = "TETRIS",
        total = 12,
        unlocked = 3,
        pointsEarned = 30,
        pointsTotal = 120,
        lastPlayed = 0L,
    )

    @Test
    fun rowShowsRaProgressWhenSetLoaded() {
        composeRule.setContent {
            PoingTheme {
                GameRow(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                    raProgress = progress(),
                )
            }
        }
        composeRule.onNodeWithText("Achievements 3/12").assertIsDisplayed()
    }

    @Test
    fun rowShowsNoRaProgressWhenNeverLoaded() {
        composeRule.setContent {
            PoingTheme {
                GameRow(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Achievements", substring = true).assertCountEquals(0)
    }

    @Test
    fun gridCellShowsRaProgressWhenSetLoaded() {
        composeRule.setContent {
            PoingTheme {
                GameGridCell(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                    raProgress = progress(),
                )
            }
        }
        composeRule.onNodeWithText("Achievements 3/12").assertIsDisplayed()
    }

    @Test
    fun gridCellShowsNoRaProgressWhenNeverLoaded() {
        composeRule.setContent {
            PoingTheme {
                GameGridCell(
                    game = game(),
                    coverBitmap = null,
                    onPlay = {},
                    onRemove = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Achievements", substring = true).assertCountEquals(0)
    }
}
