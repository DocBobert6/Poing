package app.poing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hub + badges (2026-11-14) — the Achievement Center screen,
 * without a live account: the header (username + total points),
 * the recent unlocks in order, the per-game progress math and
 * the completed marker, and the plain word about unplayed games.
 * The hub reads its data from the store, so the screen is fed
 * seeded stores — no native code, no network.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaHubScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun seed(
        games: List<RaHubGame> = emptyList(),
        recent: List<RaRecentUnlock> = emptyList(),
    ): Pair<RaHubStore, RaBadgeStore> {
        val hub = RaHubStore(temp.root)
        games.forEach { hub.snapshotGame(it) }
        recent.forEach { hub.recordUnlock(it) }
        return hub to RaBadgeStore(temp.root)
    }

    private fun setContent(
        username: String = "DocBobert6",
        totalPoints: Int? = 1234,
        hub: RaHubStore,
        badges: RaBadgeStore,
    ) {
        composeRule.setContent {
            PoingTheme {
                RaHubScreen(
                    username = username,
                    totalPoints = totalPoints,
                    hubStore = hub,
                    badgeStore = badges,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun headerShowsUsernameAndTotalPoints() {
        val (hub, badges) = seed()
        setContent(hub = hub, badges = badges)
        composeRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeRule.onNodeWithText("Logged in as DocBobert6").assertIsDisplayed()
        composeRule.onNodeWithText("1234 points").assertIsDisplayed()
    }

    @Test
    fun loggedOutShowsTheLoginExplanationAndNoSections() {
        val (hub, badges) = seed()
        setContent(username = "", totalPoints = null, hub = hub, badges = badges)
        composeRule.onNodeWithText(
            "Log in to RetroAchievements in Settings to see your achievements.",
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Recent unlocks").assertCountEquals(0)
        composeRule.onAllNodesWithText("Games").assertCountEquals(0)
    }

    @Test
    fun emptyHubShowsTheEmptyStatesAndTheUnplayedNote() {
        val (hub, badges) = seed()
        setContent(hub = hub, badges = badges)
        composeRule.onNodeWithText(
            "No unlocks yet — play a game to earn your first achievement.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "No games yet — achievements appear here after you play a game for the first time.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Games appear here after their first launch — a game you have not played yet is not listed.",
        ).assertIsDisplayed()
    }

    @Test
    fun recentUnlocksShowMostRecentFirst() {
        val (hub, badges) = seed(
            recent = listOf(
                RaRecentUnlock(1, "First!", "abc123", "TETRIS", 10, 1_000L),
                RaRecentUnlock(2, "Second!", "abc123", "TETRIS", 5, 3_000L),
            ),
        )
        setContent(hub = hub, badges = badges)
        composeRule.onNodeWithText("First!").assertIsDisplayed()
        composeRule.onNodeWithText("Second!").assertIsDisplayed()
        // The rows are in layout order (most recent first), so
        // the first row's subtree holds "Second!" and the second
        // row's holds "First!".
        val rows = composeRule.onAllNodesWithTag("ra-hub-recent").fetchSemanticsNodes()
        assertEquals(2, rows.size)
        assertTrue(rows[0].containsText("Second!"))
        assertTrue(rows[1].containsText("First!"))
    }

    @Test
    fun perGameProgressShowsCountsPointsAndCompleted() {
        val (hub, badges) = seed(
            games = listOf(
                RaHubGame("abc123", "TETRIS", 12, 3, 30, 120, 1_000L),
                RaHubGame("def456", "POKEMON", 4, 4, 40, 40, 2_000L),
            ),
        )
        setContent(hub = hub, badges = badges)
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        composeRule.onNodeWithText("3 / 12 unlocked — 30 / 120 pts").assertIsDisplayed()
        composeRule.onNodeWithText("POKEMON").assertIsDisplayed()
        composeRule.onNodeWithText("4 / 4 unlocked — 40 / 40 pts").assertIsDisplayed()
        // Only the finished game carries the marker.
        composeRule.onAllNodesWithText("Completed").assertCountEquals(1)
        val rows = composeRule.onAllNodesWithTag("ra-hub-game").fetchSemanticsNodes()
        assertEquals(2, rows.size)
        assertTrue(rows[0].containsText("POKEMON"))
        assertTrue(rows[0].containsText("Completed"))
        assertTrue(rows[1].containsText("TETRIS"))
    }

    /** True when this node or any node below it carries [text]. */
    private fun SemanticsNode.containsText(text: String): Boolean {
        config.getOrNull(SemanticsProperties.Text)?.let { annotated ->
            if (annotated.any { it.text.contains(text) }) return true
        }
        return children.any { it.containsText(text) }
    }
}
