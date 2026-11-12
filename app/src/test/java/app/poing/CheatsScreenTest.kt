package app.poing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 3) — the Cheats screen: adding, rejecting and
 * listing per-game cheats. The session is never started (no native
 * code under Robolectric), so applyCheats is a no-op — the test
 * pins the screen's validation and persistence, not the core's
 * cheat device (covered by CheatFormatTest/CheatStoreTest).
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CheatsScreenTest {

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
            gamePath = File(temp.root, "game.gba").absolutePath,
            saveBase = "game",
            onFrame = {},
            onStats = {},
            onError = {},
        )
    }

    private fun setContent(store: CheatStore, session: PlayerSession) {
        composeRule.setContent {
            PoingTheme {
                CheatsScreen(
                    gameName = "GOLDEN SUN",
                    system = GameSystem.GBA,
                    saveBase = "game",
                    store = store,
                    session = session,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun cheatsScreenShowsTitleHintAndEmptyState() {
        val store = CheatStore(File(temp.root, "cheats").apply { mkdirs() })
        setContent(store, session())
        composeRule.onNodeWithText("Cheats").assertIsDisplayed()
        composeRule.onNodeWithText("GOLDEN SUN").assertIsDisplayed()
        composeRule.onNodeWithText("No cheats for this game yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Add").assertIsDisplayed()
    }

    @Test
    fun addingAValidCodeShowsItAndPersistsIt() {
        val store = CheatStore(File(temp.root, "cheats").apply { mkdirs() })
        setContent(store, session())
        composeRule.onNodeWithTag("cheat-code-field").performTextInput("01234567 89abcdef")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("01234567 89ABCDEF").performScrollTo().assertIsDisplayed()
        // Normalized to uppercase, stored enabled.
        assertEquals(listOf(Cheat("01234567 89ABCDEF", true)), store.list("game"))
    }

    @Test
    fun addingAnInvalidCodeShowsAMessageAndAddsNothing() {
        val store = CheatStore(File(temp.root, "cheats").apply { mkdirs() })
        setContent(store, session())
        composeRule.onNodeWithTag("cheat-code-field").performTextInput("bogus")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("That is not a valid GBA cheat code.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No cheats for this game yet.").performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<Cheat>(), store.list("game"))
    }

    @Test
    fun addingADuplicateCodeIsRefused() {
        val store = CheatStore(File(temp.root, "cheats").apply { mkdirs() })
        store.save("game", listOf(Cheat("01234567 89ABCDEF", true)))
        setContent(store, session())
        composeRule.onNodeWithTag("cheat-code-field").performTextInput("01234567 89ABCDEF")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("That code is already in the list.").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(Cheat("01234567 89ABCDEF", true)), store.list("game"))
    }
}
