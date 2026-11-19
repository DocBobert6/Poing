package app.poing

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.33 — Achievement Center build 2: the HUD pin chip and
 * the rarity pop-up variants (visual half only).
 *
 * The pin action lives on locked + measured rows of Pause →
 * Achievements; the chip reads the live in-memory list, so it
 * is pinned here by composing RaHudChip directly on a measured
 * achievement. The popup variants read the web-API rarity the
 * same way the list screen does (fake fetcher, no network):
 * under 10% is rare, under 5% ultra-rare, and no rarity data
 * at all is the plain gold popup.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaHudTest {

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

    private fun loadGameWith(list: List<RaAchievement>) {
        logIn()
        RaClient.onLoadGameResult(true, null)
        RaClient.setAchievementsForTest(list)
    }

    private fun measured(
        id: Int,
        title: String,
        progress: String,
        percent: Double,
        unlocked: Boolean = false,
    ) = RaAchievement(
        id = id, title = title, description = "Do the thing.",
        points = 5,
        state = if (unlocked) RaAchievements.STATE_UNLOCKED else RaAchievements.STATE_ACTIVE,
        unlocked = unlocked,
        measuredProgress = progress,
        measuredPercent = percent,
    )

    private fun plain(id: Int, title: String, unlocked: Boolean = false) = RaAchievement(
        id = id, title = title, description = "Do the other thing.",
        points = 10,
        state = if (unlocked) RaAchievements.STATE_UNLOCKED else RaAchievements.STATE_ACTIVE,
        unlocked = unlocked,
    )

    @Test
    fun pinButtonPresentOnlyOnMeasuredLockedRows() {
        loadGameWith(
            listOf(
                measured(1, "Halfway There", "3/6", 50.0),
                plain(2, "Just Do It"),
                measured(3, "Done Already", "6/6", 100.0, unlocked = true),
            ),
        )
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "TETRIS", onBack = {}, onTogglePin = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ra-pin-1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ra-pin-2").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ra-pin-3").assertCountEquals(0)
    }

    @Test
    fun pinButtonAbsentWithoutAToggle() {
        loadGameWith(listOf(measured(1, "Halfway There", "3/6", 50.0)))
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "TETRIS", onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("ra-pin-1").assertCountEquals(0)
    }

    @Test
    fun tappingPinCallsToggleWithTheAchievementId() {
        loadGameWith(listOf(measured(1, "Halfway There", "3/6", 50.0)))
        val toggled = mutableListOf<Int>()
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(
                    gameName = "TETRIS",
                    onBack = {},
                    onTogglePin = { toggled.add(it) },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ra-pin-1").performClick()
        assertEquals(listOf(1), toggled)
    }

    @Test
    fun pinnedRowShowsPinned() {
        loadGameWith(listOf(measured(1, "Halfway There", "3/6", 50.0)))
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(
                    gameName = "TETRIS",
                    onBack = {},
                    pinnedAchievementId = 1,
                    onTogglePin = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pinned ✓").assertIsDisplayed()
    }

    @Test
    fun hudChipShowsTitleProgressAndBar() {
        composeRule.setContent {
            PoingTheme {
                RaHudChip(measured(1, "Halfway There", "42/100", 42.0))
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ra-hud-chip").assertIsDisplayed()
        composeRule.onNodeWithText("Halfway There").assertIsDisplayed()
        composeRule.onNodeWithText("42/100").assertIsDisplayed()
        composeRule.onNodeWithTag("ra-hud-chip-bar")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.42f, 0.0f..1.0f))
    }

    @Test
    fun hudChipSampleShows() {
        composeRule.setContent {
            PoingTheme {
                RaHudChipSample()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sample Achievement").assertIsDisplayed()
        composeRule.onNodeWithText("42/100").assertIsDisplayed()
    }

    // The popup variants: rarity comes from the web API (fake
    // fetcher, no network), exactly as the list screen reads
    // it. The canned set is 200 players: id 1 at 50% (Common),
    // id 2 at 2.5% (Ultra Rare).

    private fun fakeRarityFetcher(json: String) {
        RaClient.webApiFetcher = RaWebApiFetcher { url ->
            if (url.contains("GetGameInfoAndUserProgress")) json else null
        }
    }

    private fun waitForRarity() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            RaClient.rarityState == RaWebApiState.Loaded ||
                RaClient.rarityState == RaWebApiState.Failed
        }
    }

    private fun showUnlockPopup(id: Int, title: String) {
        RaClient.onUnlocked(id, title, "Do the thing.", 10)
        composeRule.setContent {
            PoingTheme {
                RaUnlockPopup(paused = false)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun rareUnlockPopupShowsDiamondAndTier() {
        // 15 of 200 players: 7.5% — Very Rare, the rare band.
        logIn()
        storeKey()
        fakeRarityFetcher(
            """{"ID":540,"NumDistinctPlayersCasual":200,"Achievements":{"7":{"ID":7,"NumAwarded":15}}}""",
        )
        RaClient.refreshRarity(540, true)
        waitForRarity()
        loadGameWith(listOf(measured(7, "Rare One", "1/2", 50.0)))
        showUnlockPopup(7, "Rare One")
        composeRule.onNodeWithText("◆ Achievement Unlocked: Rare One (+10 pts) — Very Rare · 7.5%")
            .assertIsDisplayed()
    }

    @Test
    fun ultraRareUnlockPopupShowsDiamondAndTier() {
        logIn()
        storeKey()
        fakeRarityFetcher(RaRarityTest.GAME_INFO_JSON)
        RaClient.refreshRarity(540, true)
        waitForRarity()
        loadGameWith(listOf(measured(2, "Second Win", "1/2", 50.0)))
        showUnlockPopup(2, "Second Win")
        composeRule.onNodeWithText("◆ Achievement Unlocked: Second Win (+10 pts) — Ultra Rare · 2.5%")
            .assertIsDisplayed()
    }

    @Test
    fun commonUnlockPopupStaysPlain() {
        logIn()
        storeKey()
        fakeRarityFetcher(RaRarityTest.GAME_INFO_JSON)
        RaClient.refreshRarity(540, true)
        waitForRarity()
        loadGameWith(listOf(measured(1, "First Win", "1/2", 50.0)))
        showUnlockPopup(1, "First Win")
        composeRule.onNodeWithText("Achievement Unlocked: First Win (+10 pts)").assertIsDisplayed()
        composeRule.onAllNodesWithText("◆", substring = true).assertCountEquals(0)
    }

    @Test
    fun unlockPopupWithoutRarityStaysPlain() {
        // No key, no fetch: rarity is not known, so the popup
        // is plain gold — never an invented tier.
        loadGameWith(listOf(measured(1, "First Win", "1/2", 50.0)))
        showUnlockPopup(1, "First Win")
        composeRule.onNodeWithText("Achievement Unlocked: First Win (+10 pts)").assertIsDisplayed()
        composeRule.onAllNodesWithText("◆", substring = true).assertCountEquals(0)
    }
}
