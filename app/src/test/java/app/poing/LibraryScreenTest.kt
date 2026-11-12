package app.poing

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        store.addRom(GameSystemTest.fakeGbHeader(0x80, "POKEMON GOLD"), "pokemon.gbc")
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun seededGameShowsInLibrary() {
        seedGame()
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
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("POKEMON GOLD").assertIsDisplayed()
        composeRule.onNodeWithText("GBC").performClick()
        composeRule.onNodeWithText("POKEMON GOLD").assertIsDisplayed()
        composeRule.onNodeWithText("TETRIS").assertDoesNotExist()
        composeRule.onNodeWithText("ALL").performClick()
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("POKEMON GOLD").assertIsDisplayed()
    }

    @Test
    fun tappingGameReturnsHomeWithLoadError() {
        seedGame()
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
}
