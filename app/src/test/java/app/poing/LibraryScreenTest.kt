package app.poing

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library screen gate: a seeded game shows in the list with its
 * system badge, and tapping it reaches the player — which, with no
 * native code under Robolectric, fails to load and returns home
 * with the error shown (the player error path, not a crash).
 *
 * The rule launches the activity before the test body runs, so the
 * store is seeded and then the scenario is recreated — the library
 * re-reads the store on launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun seedGame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context.filesDir)
        store.addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
        composeRule.activityRule.scenario.recreate()
    }

    private fun seedTwoGames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context.filesDir)
        store.addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
        // "POKEMON" — the GBC header title field holds 10 bytes in
        // the fake header (11 on a real cart), so a longer title
        // would be truncated and the row would never show it.
        store.addRom(GameSystemTest.fakeGbHeader(0x80, "POKEMON"), "pokemon.gbc")
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun seededGameShowsInLibrary() {
        seedGame()
        // Phase 5 — recreation re-shows the boot splash (~1s).
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("GB · tetris.gb").assertIsDisplayed()
    }

    /**
     * Phase 5 — the shoulder filter (idea 5): the GB / GBC / GBA
     * tabs at the top of the library filter the list, ALL shows
     * everything again.
     */
    @Test
    fun shoulderTabsFilterTheList() {
        seedTwoGames()
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        // The tab is clicked by tag, not by text: the cover-tile
        // badge on the GBC row also reads "GBC", so a text lookup
        // would match two nodes.
        composeRule.onNodeWithTag("filter-GBC").performClick()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        // No single-node assertDoesNotExist on this classpath —
        // count the matches instead: zero TETRIS rows after the
        // GBC filter.
        composeRule.onAllNodesWithText("TETRIS").assertCountEquals(0)
        composeRule.onNodeWithTag("filter-ALL").performClick()
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
    }

    @Test
    fun tappingGameReturnsHomeWithLoadError() {
        seedGame()
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithText("TETRIS").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("Native libraries failed to load.", substring = true)
                    .assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithText("Native libraries failed to load.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
    }

    /**
     * The view switch (Robert, 2026-11-14): List is the default
     * and shows rows; Grid shows cover tiles instead, with the
     * same title, badge and actions.
     */
    @Test
    fun gridToggleSwitchesToGridCells() {
        seedGame()
        composeRule.waitForText("TETRIS")
        composeRule.onAllNodesWithTag("game-row").assertCountEquals(1)
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(0)
        composeRule.onNodeWithTag("view-grid").performClick()
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(1)
        composeRule.onAllNodesWithTag("game-row").assertCountEquals(0)
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("GB · tetris.gb").assertIsDisplayed()
        composeRule.onNodeWithTag("view-list").performClick()
        composeRule.onAllNodesWithTag("game-row").assertCountEquals(1)
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(0)
    }

    @Test
    fun gridChoicePersistsAcrossRecreate() {
        seedGame()
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithTag("view-grid").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("TETRIS")
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(1)
        composeRule.onAllNodesWithTag("game-row").assertCountEquals(0)
    }

    @Test
    fun shoulderFilterWorksInGrid() {
        seedTwoGames()
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithTag("view-grid").performClick()
        // Only one cell survives the GBC filter, so it is the
        // first item after the header and always composed.
        composeRule.onNodeWithTag("filter-GBC").performClick()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        composeRule.onAllNodesWithText("TETRIS").assertCountEquals(0)
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(1)
        composeRule.onNodeWithTag("filter-ALL").performClick()
        // ALL restores the first cell; the second cell's
        // visibility depends on the column count, so only the
        // first is pinned here — the list-view filter test
        // already pins both rows coming back.
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
    }
}
