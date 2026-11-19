package app.poing

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.31 — Achievement Center build 1: live progress bars,
 * Search for Help, closest-first sort (current game only).
 *
 * The bar renders from the measured progress the bridge already
 * carries (measured_progress / measured_percent), and only
 * where an achievement reports it. The progress-indicator event
 * is pinned at the Kotlin seam (the id it records); the native
 * forwarding is code-verified, and the live behaviour on device
 * is Robert's check. Search for Help is pinned by the query
 * string it builds — no browser is launched in tests.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaProgressBarsTest {

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
        composeRule.setContent {
            PoingTheme {
                RaAchievementsScreen(gameName = "TETRIS", onBack = {})
            }
        }
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
    fun measuredAchievementShowsProgressBarAndUnmeasuredDoesNot() {
        loadGameWith(
            listOf(
                measured(1, "Halfway There", "3/6", 50.0),
                plain(2, "Just Do It"),
            ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ra-progress-bar-1").assertIsDisplayed()
        composeRule.onNodeWithTag("ra-progress-bar-1")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0.0f..1.0f))
        composeRule.onNodeWithText("3/6", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("ra-progress-bar-2").assertCountEquals(0)
    }

    @Test
    fun progressIndicatorEventRecordsTheTrackedAchievement() {
        // No native layer under Robolectric: the refresh is a
        // no-op there, so the recorded id is what proves the
        // event reached Kotlin — and that the handler is safe
        // to call uninitialised.
        RaClient.onProgressIndicatorUpdate(7)
        assertEquals(7, RaClient.lastProgressIndicatorAchievementId)
        RaClient.onProgressIndicatorUpdate(12)
        assertEquals(12, RaClient.lastProgressIndicatorAchievementId)
    }

    @Test
    fun helpButtonAppearsOnlyOnLockedRows() {
        loadGameWith(
            listOf(
                plain(1, "Still To Do"),
                plain(2, "Already Done", unlocked = true),
            ),
        )
        composeRule.onNodeWithTag("ra-help-1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ra-help-2").assertCountEquals(0)
    }

    @Test
    fun helpSearchUrlCarriesGameAndAchievementTitles() {
        val url = RaWebApi.helpSearchUrl("Super Mario Land 2", "Coin Collector")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("Super+Mario+Land+2"))
        assertTrue(url.contains("Coin+Collector"))
        assertTrue(url.contains("RetroAchievements"))
    }

    @Test
    fun guideUrlParsedFromGameInfo() {
        assertEquals(
            "https://example.com/guide",
            RaWebApi.parseGuideUrl("""{"GuideURL":"https://example.com/guide"}"""),
        )
        assertNull(RaWebApi.parseGuideUrl("""{"Title":"No Guide"}"""))
        assertNull(RaWebApi.parseGuideUrl("""{"GuideURL":""}"""))
        assertNull(RaWebApi.parseGuideUrl("not json"))
    }

    @Test
    fun guideUrlLandsFromTheGameInfoFetch() {
        logIn()
        storeKey()
        RaClient.webApiFetcher = RaWebApiFetcher { url ->
            if (url.contains("GetGameInfoAndUserProgress")) GAME_INFO_WITH_GUIDE_JSON else null
        }
        RaClient.refreshRarity(GAME_ID, true)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            RaClient.rarityState == RaWebApiState.Loaded ||
                RaClient.rarityState == RaWebApiState.Failed
        }
        assertEquals(RaWebApiState.Loaded, RaClient.rarityState)
        assertEquals("https://example.com/guide", RaClient.guideUrl)
    }

    @Test
    fun closestFirstSortOrdersLockedByPercentDescending() {
        loadGameWith(
            listOf(
                measured(1, "Slow Start", "1/5", 20.0),
                measured(2, "Almost There", "4/5", 80.0),
                plain(3, "No Measure"),
                plain(4, "Done", unlocked = true),
            ),
        )
        // Server order first: the toggle has to change something.
        assertEquals(
            listOf("ra-achievement-1", "ra-achievement-2", "ra-achievement-3", "ra-achievement-4"),
            rowOrder(),
        )
        composeRule.onNodeWithTag("ra-sort-closest").performClick()
        // Locked closest-first, unmeasured locked after the
        // measured ones, unlocked last.
        assertEquals(
            listOf("ra-achievement-2", "ra-achievement-1", "ra-achievement-3", "ra-achievement-4"),
            rowOrder(),
        )
    }

    /** The row tags in display order (top to bottom). */
    private fun rowOrder(): List<String> {
        fun top(tag: String): Float =
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        return listOf(1, 2, 3, 4)
            .map { "ra-achievement-$it" }
            .sortedBy { top(it) }
    }

    companion object {
        const val GAME_ID = 540

        const val GAME_INFO_WITH_GUIDE_JSON = """{"ID":540,"Title":"Super Mario Land 2",
            "GuideURL":"https://example.com/guide",
            "NumDistinctPlayersCasual":200,"NumDistinctPlayersHardcore":50,
            "Achievements":{
              "1":{"ID":1,"NumAwarded":100,"NumAwardedHardcore":20},
              "2":{"ID":2,"NumAwarded":5,"NumAwardedHardcore":1}}}"""
    }
}
