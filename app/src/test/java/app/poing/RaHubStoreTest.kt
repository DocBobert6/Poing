package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Hub + badges (2026-11-14) — the Achievement Center's memory:
 * the per-game cached sets and the recent unlocks round-trip
 * through the store, recent unlocks order most-recent-first and
 * cap, and the progress math the hub rows use.
 */
class RaHubStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = RaHubStore(temp.root)

    private fun game(
        id: String = "abc123",
        title: String = "TETRIS",
        total: Int = 12,
        unlocked: Int = 3,
        pointsEarned: Int = 30,
        pointsTotal: Int = 120,
        lastPlayed: Long = 1_000L,
        badgeUrl: String? = "https://media.retroachievements.org/Badge/1.png",
    ) = RaHubGame(id, title, total, unlocked, pointsEarned, pointsTotal, lastPlayed, badgeUrl)

    private fun unlock(
        achievementId: Int = 5,
        title: String = "First!",
        gameId: String = "abc123",
        gameTitle: String = "TETRIS",
        points: Int = 10,
        unlockedAt: Long = 2_000L,
        badgeUrl: String? = "https://media.retroachievements.org/Badge/1.png",
    ) = RaRecentUnlock(achievementId, title, gameId, gameTitle, points, unlockedAt, badgeUrl)

    @Test
    fun emptyStoreLoadsEmpty() {
        val data = store().load()
        assertTrue(data.games.isEmpty())
        assertTrue(data.recent.isEmpty())
    }

    @Test
    fun snapshotRoundTrips() {
        val s = store()
        s.snapshotGame(game())
        val data = s.load()
        assertEquals(1, data.games.size)
        val saved = data.games[0]
        assertEquals("abc123", saved.id)
        assertEquals("TETRIS", saved.title)
        assertEquals(12, saved.total)
        assertEquals(3, saved.unlocked)
        assertEquals(30, saved.pointsEarned)
        assertEquals(120, saved.pointsTotal)
        assertEquals(1_000L, saved.lastPlayed)
        assertEquals("https://media.retroachievements.org/Badge/1.png", saved.badgeUrl)
    }

    @Test
    fun snapshotReplacesTheSameGameAndKeepsTheOthers() {
        val s = store()
        s.snapshotGame(game())
        s.snapshotGame(game(id = "def456", title = "POKEMON", lastPlayed = 3_000L))
        // A fresh snapshot of the first game replaces it, in place.
        s.snapshotGame(game(unlocked = 4, pointsEarned = 40, lastPlayed = 4_000L))
        val data = s.load()
        assertEquals(2, data.games.size)
        // Games order by last played, most recent first.
        assertEquals("abc123", data.games[0].id)
        assertEquals(4, data.games[0].unlocked)
        assertEquals("def456", data.games[1].id)
    }

    @Test
    fun titleWithQuotesAndNewlinesRoundTrips() {
        val s = store()
        s.snapshotGame(game(title = "Say \"Hi\"\nAgain"))
        s.recordUnlock(unlock(title = "Won \\ \"All\""))
        val data = s.load()
        assertEquals("Say \"Hi\"\nAgain", data.games[0].title)
        assertEquals("Won \\ \"All\"", data.recent[0].title)
    }

    @Test
    fun recentUnlocksOrderMostRecentFirst() {
        val s = store()
        s.recordUnlock(unlock(achievementId = 1, unlockedAt = 1_000L))
        s.recordUnlock(unlock(achievementId = 2, unlockedAt = 3_000L))
        s.recordUnlock(unlock(achievementId = 3, unlockedAt = 2_000L))
        val recent = s.load().recent
        // Most recent first: 3_000, 2_000, 1_000.
        assertEquals(listOf(2, 3, 1), recent.map { it.achievementId })
        assertEquals(3_000L, recent[0].unlockedAt)
        assertEquals(2_000L, recent[1].unlockedAt)
        assertEquals(1_000L, recent[2].unlockedAt)
    }

    @Test
    fun recentUnlocksCapAtFifty() {
        val s = store()
        for (i in 1..55) {
            s.recordUnlock(unlock(achievementId = i, unlockedAt = i.toLong()))
        }
        val recent = s.load().recent
        assertEquals(RaHubStore.MAX_RECENT, recent.size)
        assertEquals(55, recent[0].achievementId)
        assertEquals(6, recent.last().achievementId)
    }

    @Test
    fun reUnlockReplacesTheOldEntry() {
        val s = store()
        s.recordUnlock(unlock(achievementId = 7, unlockedAt = 1_000L))
        s.recordUnlock(unlock(achievementId = 8, unlockedAt = 2_000L))
        s.recordUnlock(unlock(achievementId = 7, title = "Again!", unlockedAt = 3_000L))
        val recent = s.load().recent
        assertEquals(2, recent.size)
        assertEquals(7, recent[0].achievementId)
        assertEquals("Again!", recent[0].title)
        assertEquals(3_000L, recent[0].unlockedAt)
    }

    @Test
    fun removeGameDropsTheSetButKeepsRecentUnlocks() {
        val s = store()
        s.snapshotGame(game())
        s.recordUnlock(unlock())
        s.removeGame("abc123")
        val data = s.load()
        assertTrue(data.games.isEmpty())
        assertEquals(1, data.recent.size)
        // Removing a game that is not cached is a no-op.
        s.removeGame("nope")
        assertEquals(1, s.load().recent.size)
    }

    @Test
    fun corruptFileLoadsEmpty() {
        temp.newFile("ra-hub.json").writeText("{not json")
        val data = store().load()
        assertTrue(data.games.isEmpty())
        assertTrue(data.recent.isEmpty())
    }

    @Test
    fun progressMath() {
        assertEquals(0f, raProgressFraction(0, 0))
        assertEquals(0f, raProgressFraction(0, 12))
        assertEquals(0.25f, raProgressFraction(3, 12))
        assertEquals(1f, raProgressFraction(12, 12))
        // Counts that disagree clamp instead of overflowing the bar.
        assertEquals(1f, raProgressFraction(15, 12))
        assertEquals(0f, raProgressFraction(-1, 12))
        assertFalse(raIsCompleted(3, 12))
        assertFalse(raIsCompleted(0, 0))
        assertTrue(raIsCompleted(12, 12))
        assertTrue(raIsCompleted(15, 12))
    }

    @Test
    fun gameProgressAndCompleted() {
        assertEquals(0.25f, game(unlocked = 3, total = 12).progress)
        assertFalse(game(unlocked = 3, total = 12).completed)
        assertTrue(game(unlocked = 12, total = 12).completed)
        assertFalse(game(unlocked = 0, total = 0).completed)
    }

    @Test
    fun unlockTimeFormatsLikeTheSaveStates() {
        // "MMM d, HH:mm" — the same shape as the pause menu's
        // save-state times. The exact hour depends on the test
        // machine's zone, so only the shape is pinned.
        val formatted = formatUnlockTime(1_793_000_000_000L)
        assertTrue(formatted.matches(Regex("[A-Z][a-z]{2} \\d{1,2}, \\d{2}:\\d{2}")))
    }

    @Test
    fun jsonEscapeRoundTripsThroughTheParser() {
        val tricky = "quote \" backslash \\ newline \n tab \t bell \u0007"
        val escaped = jsonEscape(tricky)
        val parsed = RaJson.parseObject("{\"v\":\"$escaped\"}")
        assertEquals(tricky, parsed["v"])
    }
}
