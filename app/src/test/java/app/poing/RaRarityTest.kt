package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.23 — RetroAchievements rarity tiers and the full unlock
 * history, from the RA web API. The fetcher is faked (no
 * network), the key is a test value in a temp session store,
 * and the screens read the client state the fetches land in.
 *
 * The rarity tiers come from API_GetGameInfoAndUserProgress
 * (NumAwarded over NumDistinctPlayersCasual); the history from
 * API_GetUserRecentAchievements (newest first, with dates).
 * Without a key — or with the network gate closed — the states
 * are Unavailable and the views simply do not appear.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaRarityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        // The client is a singleton: leave it as the other tests
        // found it — logged out, no hub store wired, and the
        // production fetcher back in place.
        RaClient.logout()
        RaClient.webApiFetcher = RaWebApiFetcher { url -> defaultRaWebApiHttpGet(url) }
        RaClient.init(RaSessionStore(temp.root))
    }

    private fun logIn() {
        RaClient.init(RaSessionStore(temp.root))
        RaClient.onLoginResult(true, null)
    }

    private fun storeKey(key: String = "test-api-key-1234") {
        RaSessionStore(temp.root).setApiKey(key)
    }

    private fun fakeFetcher() {
        RaClient.webApiFetcher = RaWebApiFetcher { url ->
            when {
                url.contains("GetGameInfoAndUserProgress") -> GAME_INFO_JSON
                url.contains("GetUserRecentAchievements") -> RECENT_JSON
                else -> null
            }
        }
    }

    private fun waitForRarity() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            RaClient.rarityState == RaWebApiState.Loaded ||
                RaClient.rarityState == RaWebApiState.Failed
        }
    }

    private fun waitForHistory() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            RaClient.historyState == RaWebApiState.Loaded ||
                RaClient.historyState == RaWebApiState.Failed
        }
    }

    @Test
    fun rarityLoadsFromTheWebApi() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshRarity(GAME_ID, true)
        waitForRarity()
        assertEquals(RaWebApiState.Loaded, RaClient.rarityState)
        val rarity = RaClient.rarityByAchievement
        assertEquals(3, rarity.size)
        assertEquals(RaRarityTier.Common, rarity[1]!!.tier)
        assertEquals(50.0, rarity[1]!!.percent, 0.001)
        assertEquals(RaRarityTier.UltraRare, rarity[2]!!.tier)
        assertEquals(2.5, rarity[2]!!.percent, 0.001)
        assertEquals(RaRarityTier.Legendary, rarity[3]!!.tier)
    }

    @Test
    fun rarityWithoutAKeyIsUnavailable() {
        logIn()
        fakeFetcher()
        RaClient.refreshRarity(GAME_ID, true)
        assertEquals(RaWebApiState.Unavailable, RaClient.rarityState)
        assertEquals(emptyMap<Int, RaRarity>(), RaClient.rarityByAchievement)
    }

    @Test
    fun rarityWithTheGateClosedIsUnavailable() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshRarity(GAME_ID, false)
        assertEquals(RaWebApiState.Unavailable, RaClient.rarityState)
    }

    @Test
    fun rarityFetchFailureIsFailedNotLoaded() {
        logIn()
        storeKey()
        RaClient.webApiFetcher = RaWebApiFetcher { null }
        RaClient.refreshRarity(GAME_ID, true)
        waitForRarity()
        assertEquals(RaWebApiState.Failed, RaClient.rarityState)
    }

    @Test
    fun achievementsScreenShowsRarityTiers() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshRarity(GAME_ID, true)
        waitForRarity()
        RaClient.onLoadGameResult(true, null)
        RaClient.setAchievementsForTest(
            listOf(
                RaAchievement(
                    id = 1, title = "First Win", description = "Win once.",
                    points = 10, state = RaAchievements.STATE_UNLOCKED, unlocked = true,
                ),
                RaAchievement(
                    id = 2, title = "Second Win", description = "Win twice.",
                    points = 5, state = RaAchievements.STATE_ACTIVE, unlocked = false,
                ),
            ),
        )
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "TETRIS", onBack = {})
            }
        }
        composeRule.onNodeWithText("10 pts — Unlocked — Common · 50%").assertIsDisplayed()
        composeRule.onNodeWithText("5 pts — Ultra Rare · 2.5%").assertIsDisplayed()
    }

    @Test
    fun historyLoadsNewestFirst() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshUnlockHistory(true)
        waitForHistory()
        assertEquals(RaWebApiState.Loaded, RaClient.historyState)
        assertEquals(listOf(2, 1), RaClient.unlockHistory.map { it.achievementId })
    }

    @Test
    fun hubShowsUnlockHistory() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshUnlockHistory(true)
        waitForHistory()
        composeRule.setContent {
            PoingTheme {
                RaHubScreen(
                    username = "DocBobert",
                    totalPoints = 100,
                    hubStore = RaHubStore(temp.root),
                    badgeStore = RaBadgeStore(temp.root),
                    onBack = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("ra-hub-history").assertCountEquals(2)
        composeRule.onNodeWithText("Second Win").assertIsDisplayed()
        composeRule.onNodeWithText("First Win").assertIsDisplayed()
    }

    @Test
    fun removingTheKeyHidesHistoryAndLeavesTheHub() {
        logIn()
        storeKey()
        fakeFetcher()
        RaClient.refreshUnlockHistory(true)
        waitForHistory()
        assertEquals(RaWebApiState.Loaded, RaClient.historyState)
        // Remove the key: the next fetch is Unavailable, and the
        // hub no longer shows the history section — while the
        // local recent unlocks and games stay exactly as they were.
        RaSessionStore(temp.root).clearApiKey()
        RaClient.refreshUnlockHistory(true)
        assertEquals(RaWebApiState.Unavailable, RaClient.historyState)
        composeRule.setContent {
            PoingTheme {
                RaHubScreen(
                    username = "DocBobert",
                    totalPoints = 100,
                    hubStore = RaHubStore(temp.root),
                    badgeStore = RaBadgeStore(temp.root),
                    onBack = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("ra-hub-history").assertCountEquals(0)
        composeRule.onNodeWithText("No games yet — achievements appear here after you play a game for the first time.")
            .assertIsDisplayed()
    }

    @Test
    fun hubWithoutAKeyShowsNoHistorySection() {
        logIn()
        fakeFetcher()
        RaClient.refreshUnlockHistory(true)
        assertEquals(RaWebApiState.Unavailable, RaClient.historyState)
        composeRule.setContent {
            PoingTheme {
                RaHubScreen(
                    username = "DocBobert",
                    totalPoints = 100,
                    hubStore = RaHubStore(temp.root),
                    badgeStore = RaBadgeStore(temp.root),
                    onBack = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("ra-hub-history").assertCountEquals(0)
        composeRule.onAllNodesWithText("Unlock history").assertCountEquals(0)
    }

    companion object {
        const val GAME_ID = 540

        const val GAME_INFO_JSON = """{"ID":540,"Title":"Super Mario Land 2",
            "NumDistinctPlayersCasual":200,"NumDistinctPlayersHardcore":50,
            "Achievements":{
              "1":{"ID":1,"NumAwarded":100,"NumAwardedHardcore":20},
              "2":{"ID":2,"NumAwarded":5,"NumAwardedHardcore":1},
              "3":{"ID":3,"NumAwarded":0,"NumAwardedHardcore":0}}}"""

        const val RECENT_JSON = """[{"AchievementID":2,"Title":"Second Win",
            "GameTitle":"Super Mario Land 2","Points":10,
            "Date":"2026-11-16 14:32:00","BadgeName":"abc123"},
            {"AchievementID":1,"Title":"First Win",
            "GameTitle":"Super Mario Land 2","Points":5,
            "Date":"2026-11-15 09:00:00","BadgeName":"def456"}]"""
    }
}
