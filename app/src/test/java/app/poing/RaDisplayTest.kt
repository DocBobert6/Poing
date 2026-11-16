package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.22 — the RetroAchievements display pair: the
 * achievements screen and the in-game indicator tell an
 * unrecognised ROM, a failed load, and a genuinely empty set
 * apart, and a failed load offers a manual Retry (on the screen
 * and on the indicator chip). Alpha.25 — the unlock toast is
 * one slim line (title + points, no description) and movable
 * through the overlay editor (ControlId.POPUP); the
 * description lives in Pause → Achievements and the hub.
 *
 * The load states are driven through RaClient's own callbacks
 * (onLoginResult / onLoadGameResult / loadGame), the same entry
 * points the native layer calls — under Robolectric there is no
 * .so to load, so the native calls are no-ops and only the
 * state transitions run.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaDisplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        // The client is a singleton: leave it as the other tests
        // found it — logged out, no hub store wired.
        RaClient.logout()
        RaClient.init(RaSessionStore(temp.root))
    }

    private fun logIn() {
        RaClient.onLoginResult(true, null)
    }

    private fun achievementsScreen() {
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "TETRIS", onBack = {})
            }
        }
    }

    @Test
    fun achievementsScreenNamesUnrecognisedRom() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, RaClient.UNKNOWN_GAME_ERROR)
        achievementsScreen()
        composeRule.onNodeWithText(
            "RetroAchievements doesn't recognise this ROM, so it has no achievement set.",
        ).assertIsDisplayed()
        // Neither of the other two no-set lines appears.
        composeRule.onAllNodesWithText(
            "RetroAchievements has no achievements for this game.",
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            "This game has no achievements.",
        ).assertCountEquals(0)
    }

    @Test
    fun achievementsScreenOffersRetryOnFailedLoad() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        achievementsScreen()
        composeRule.onNodeWithText(
            "RetroAchievements couldn't load this game's achievements.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("ra-retry-load").assertIsDisplayed()
        // The retry re-attempts the load: the screen goes back
        // to the loading line instead of staying failed.
        composeRule.onNodeWithTag("ra-retry-load").performClick()
        composeRule.onNodeWithText("Loading achievements…").assertIsDisplayed()
    }

    @Test
    fun achievementsScreenShowsLoadingWhileLoadInFlight() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        achievementsScreen()
        composeRule.onNodeWithText("Loading achievements…").assertIsDisplayed()
    }

    @Test
    fun achievementsScreenShowsEmptySetMessageWhenLoadedWithNoAchievements() {
        logIn()
        RaClient.onLoadGameResult(true, null)
        achievementsScreen()
        composeRule.onNodeWithText("This game has no achievements.").assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "RetroAchievements doesn't recognise this ROM, so it has no achievement set.",
        ).assertCountEquals(0)
    }

    @Test
    fun indicatorShowsNoAchievementsWhenSetLoadedButEmpty() {
        logIn()
        RaClient.onLoadGameResult(true, null)
        composeRule.setContent {
            PoingTheme {
                RaGameIndicator(paused = false)
            }
        }
        composeRule.onNodeWithText("RA: no achievements").assertIsDisplayed()
    }

    @Test
    fun indicatorShowsUnrecognisedRom() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, RaClient.UNKNOWN_GAME_ERROR)
        composeRule.setContent {
            PoingTheme {
                RaGameIndicator(paused = false)
            }
        }
        composeRule.onNodeWithText("RA: unrecognised ROM").assertIsDisplayed()
    }

    @Test
    fun indicatorShowsFailedLoadWithRetry() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        composeRule.setContent {
            PoingTheme {
                RaGameIndicator(paused = false)
            }
        }
        composeRule.onNodeWithText("RA: load failed — Retry").assertIsDisplayed()
        // Tapping the chip re-attempts the load.
        composeRule.onNodeWithTag("ra-indicator").performClick()
        composeRule.onNodeWithText("RA: loading…").assertIsDisplayed()
    }

    @Test
    fun indicatorHiddenWhenLoggedOut() {
        // No login: no indicator at all, in any state.
        RaClient.loadGame("/games/abc123.gba")
        composeRule.setContent {
            PoingTheme {
                RaGameIndicator(paused = false)
            }
        }
        composeRule.onAllNodesWithTag("ra-indicator").assertCountEquals(0)
    }

    @Test
    fun indicatorHiddenWhilePaused() {
        logIn()
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        composeRule.setContent {
            PoingTheme {
                RaGameIndicator(paused = true)
            }
        }
        composeRule.onAllNodesWithTag("ra-indicator").assertCountEquals(0)
    }

    @Test
    fun unlockToastShowsTitleAndPointsWithoutDescription() {
        val hub = RaHubStore(temp.root)
        RaClient.init(RaSessionStore(temp.root), hubStore = hub)
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onUnlocked(5, "First!", "Do the thing.", 10)
        composeRule.setContent {
            PoingTheme {
                RaUnlockPopup(paused = false)
            }
        }
        composeRule.onNodeWithText(
            "Achievement Unlocked: First! (+10 pts)",
        ).assertIsDisplayed()
        // Alpha.25 — the description left the popup (it lives
        // in Pause → Achievements and the hub now): the slim
        // popup is one line, title + points only. This fails
        // on the old code, which renders the description.
        composeRule.onAllNodesWithText("Do the thing.").assertCountEquals(0)
    }
}
