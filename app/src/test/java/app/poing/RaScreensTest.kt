package app.poing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 8c) — the RetroAchievements screens, without a
 * live account: the login screen's logged-out rendering and field
 * behaviour, and the achievements / leaderboards screens' empty
 * states (not logged in, no game recognised). A real login needs
 * the server, so the logged-in states are a device test for
 * Robert — under Robolectric there is no .so to load, so
 * RaClient never initialises and the login is a no-op.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun session() = RaSessionStore(temp.root)

    @Test
    fun loginScreenShowsFieldsAndDisabledLoginWhenEmpty() {
        composeRule.setContent {
            PoingTheme {
                RaLoginScreen(session = session(), onBack = {})
            }
        }
        composeRule.onNodeWithText("RetroAchievements").assertIsDisplayed()
        composeRule.onNodeWithTag("ra-username-field").assertIsDisplayed()
        composeRule.onNodeWithTag("ra-password-field").assertIsDisplayed()
        composeRule.onNodeWithTag("ra-login-button").assertIsNotEnabled()
        composeRule.onNodeWithText("Not logged in.").assertIsDisplayed()
    }

    @Test
    fun loginButtonEnablesWhenBothFieldsAreFilled() {
        composeRule.setContent {
            PoingTheme {
                RaLoginScreen(session = session(), onBack = {})
            }
        }
        composeRule.onNodeWithTag("ra-username-field").performTextInput("DocBobert")
        composeRule.onNodeWithTag("ra-password-field").performTextInput("secret")
        composeRule.onNodeWithTag("ra-login-button").assertIsEnabled()
    }

    @Test
    fun loginStoresTheUsernameButNeverThePassword() {
        composeRule.setContent {
            PoingTheme {
                RaLoginScreen(session = session(), onBack = {})
            }
        }
        composeRule.onNodeWithTag("ra-username-field").performTextInput("DocBobert")
        composeRule.onNodeWithTag("ra-password-field").performTextInput("secret")
        // No native code under Robolectric: the login itself is a
        // no-op, but the username is stored first — the password
        // is used once for the login and is never written anywhere.
        composeRule.onNodeWithTag("ra-login-button").performClick()
        assertEquals("DocBobert", RaSessionStore(temp.root).username())
        assertEquals("", RaSessionStore(temp.root).token())
    }

    @Test
    fun achievementsScreenExplainsWhenNotLoggedIn() {
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "Tetris", onBack = {})
            }
        }
        composeRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeRule.onNodeWithText("Tetris").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Log in to RetroAchievements in Settings to see achievements.",
        ).assertIsDisplayed()
    }

    @Test
    fun leaderboardsScreenExplainsWhenNotLoggedIn() {
        composeRule.setContent {
            PoingTheme {
                RaLeaderboardsScreen(gameName = "Tetris", onBack = {})
            }
        }
        composeRule.onNodeWithText("Leaderboards").assertIsDisplayed()
        composeRule.onNodeWithText("Tetris").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Log in to RetroAchievements in Settings to see leaderboards.",
        ).assertIsDisplayed()
    }
}
