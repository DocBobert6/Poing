package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Top bar / back swipe (Robert, 2026-11-12): a back swipe
 * NEVER closes the app except on the home page (the library),
 * and there only as a double swipe — the first shows a
 * "press back again to exit" hint, the second within ~2s
 * exits. On every other screen back navigates normally to
 * the previous screen. (The player owns back itself: a back
 * swipe shows the bar, never exits — see
 * GameScreenLayoutTest.)
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackSwipeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    @Test
    fun firstBackSwipeOnLibraryShowsExitHintAndDoesNotExit() {
        composeRule.waitForText("Add game")
        pressBack()
        composeRule.onNodeWithTag("exit-hint").assertIsDisplayed()
        composeRule.onNodeWithText("Press back again to exit").assertIsDisplayed()
        assertFalse("one back swipe must not close the app", composeRule.activity.isFinishing)
    }

    @Test
    fun secondBackSwipeOnLibraryExits() {
        composeRule.waitForText("Add game")
        pressBack()
        pressBack()
        assertTrue("two quick back swipes on the library must close the app",
            composeRule.activity.isFinishing)
    }

    @Test
    fun backSwipeWindowExpiryResetsTheHint() {
        composeRule.waitForText("Add game")
        pressBack()
        composeRule.onNodeWithTag("exit-hint").assertIsDisplayed()
        // Past the ~2s window the hint is gone and the next
        // swipe is a first swipe again, not a second.
        composeRule.mainClock.advanceTimeBy(DOUBLE_BACK_EXIT_WINDOW_MS + 500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("exit-hint").assertCountEquals(0)
        pressBack()
        composeRule.onNodeWithTag("exit-hint").assertIsDisplayed()
        assertFalse("a swipe after the window must not close the app",
            composeRule.activity.isFinishing)
    }

    @Test
    fun backSwipeOnLicencesReturnsToLibrary() {
        composeRule.waitForText("Add game")
        composeRule.onNodeWithText("Licences").performClick()
        composeRule.onNodeWithText("Mozilla Public License", substring = true).assertExists()
        pressBack()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
        assertFalse("back on Licences must not close the app", composeRule.activity.isFinishing)
    }

    @Test
    fun backSwipeOnSettingsReturnsToLibrary() {
        composeRule.waitForText("Add game")
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
        assertFalse("back on Settings must not close the app", composeRule.activity.isFinishing)
    }
}
