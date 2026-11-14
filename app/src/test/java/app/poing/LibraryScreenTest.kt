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
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
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
        // Diagnostic (2026-11-14): the filter state is observable
        // through the shoulders row's tag. If the click fired, the
        // state reads GBC here, whatever the list below does —
        // that bisects a dead click from a stale list.
        composeRule.onNodeWithTag("filter-state-GBC").assertExists()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        // No single-node assertDoesNotExist on this classpath —
        // count the matches instead: zero TETRIS rows after the
        // GBC filter.
        composeRule.onAllNodesWithText("TETRIS").assertCountEquals(0)
        composeRule.onNodeWithTag("filter-ALL").performClick()
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        // The second row sits below the fold in the narrow
        // Robolectric window now that the view switch adds a row
        // to the header: scroll it into view first. Grid items
        // are 0 top bar, 1 Add row, 2 Licences/Settings,
        // 3 shoulders, 4 view, 5 TETRIS, 6 POKEMON, 7 BIOS card.
        // TETRIS is asserted before the scroll, since scrolling
        // POKEMON in can push it out.
        composeRule.onNodeWithTag("library-grid").performScrollToIndex(6)
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
    /**
     * The library buttons (Robert, 2026-11-14 device photos):
     * the Add row and the Licences/Settings row shared one
     * lazy-grid item, so the second row covered the first and
     * the Settings, Licences and Add folder buttons looked
     * missing. Each row is its own item now, so the
     * Licences/Settings row must sit strictly below the Add
     * row — in both List and Grid view. The test that would
     * have caught this and the shoulder bug: on the old
     * layout both rows start at the same item top, so the
     * Licences text sits inside the Add button's vertical
     * span and the assertion fails.
     */
    @Test
    fun licencesAndSettingsSitBelowTheAddRow() {
        seedGame()
        composeRule.waitForText("TETRIS")
        assertButtonsBelowAddRow()
        composeRule.onNodeWithTag("view-grid").performClick()
        assertButtonsBelowAddRow()
    }

    private fun assertButtonsBelowAddRow() {
        val add = composeRule.onNodeWithText("Add game")
            .fetchSemanticsNode().boundsInRoot
        val licencesTop = composeRule.onNodeWithText("Licences")
            .fetchSemanticsNode().boundsInRoot.top
        val settingsTop = composeRule.onNodeWithText("Settings")
            .fetchSemanticsNode().boundsInRoot.top
        // Stacked rows touch exactly: the second row's top
        // EQUALS the first row's bottom, so the no-overlap bar
        // is >=, not > (the first version used > and failed on
        // the fixed layout, 2026-11-14 — the equality is the
        // proof the rows stack). On the old shared-item layout
        // both rows started at the same top, so the
        // Licences/Settings tops sat at the Add button's top —
        // inside the Add row, not below it — and both bars fail.
        assertTrue(
            "Licences top ($licencesTop) must sit at or below the Add row bottom (${add.bottom})",
            licencesTop >= add.bottom,
        )
        assertTrue(
            "Settings top ($settingsTop) must sit at or below the Add row bottom (${add.bottom})",
            settingsTop >= add.bottom,
        )
        assertTrue(
            "Licences top ($licencesTop) must sit below the Add row top (${add.top})",
            licencesTop > add.top,
        )
        assertTrue(
            "Settings top ($settingsTop) must sit below the Add row top (${add.top})",
            settingsTop > add.top,
        )
    }

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
        // Diagnostic (2026-11-14): the filter state is observable
        // through the shoulders row's tag — see the list test.
        composeRule.onNodeWithTag("filter-state-GBC").assertExists()
        // The cell sits below the fold in the narrow Robolectric
        // window: scroll it into view first. Grid items are
        // 0 top bar, 1 Add row, 2 Licences/Settings, 3 shoulders,
        // 4 view, 5 POKEMON (the only game under the GBC filter),
        // 6 BIOS card.
        composeRule.onNodeWithTag("library-grid").performScrollToIndex(5)
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        composeRule.onAllNodesWithText("TETRIS").assertCountEquals(0)
        composeRule.onAllNodesWithTag("game-grid-cell").assertCountEquals(1)
        // Back to the top before touching the header again:
        // the filter tabs scroll out of view with the grid.
        composeRule.onNodeWithTag("library-grid").performScrollToIndex(0)
        composeRule.onNodeWithTag("filter-ALL").performClick()
        // ALL restores the first cell; the second cell's
        // visibility depends on the column count, so only the
        // first is pinned here — the list-view filter test
        // already pins both rows coming back.
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
    }
}
