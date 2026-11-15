package app.poing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Hub + badges (2026-11-14) — the unlock timestamp recording:
 * an unlock that pops while a game is loaded is recorded in the
 * hub store with the time it happened, the game and the points,
 * so the hub's recent list survives a restart. Runs on the plain
 * JVM: RaClient never initialises without the native libraries,
 * so the refresh calls are no-ops and only the recording runs.
 */
class RaHubUnlockTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        // The client is a singleton: leave it as the other tests
        // found it — logged out, no hub store wired.
        RaClient.logout()
        RaClient.init(RaSessionStore(temp.root))
    }

    @Test
    fun unlockIsRecordedWithItsTimestamp() {
        val hub = RaHubStore(temp.root)
        RaClient.init(RaSessionStore(temp.root), hubStore = hub)
        RaClient.loadGame("/games/abc123.gba")

        val before = System.currentTimeMillis()
        RaClient.onUnlocked(5, "First!", "Do the thing.", 10)
        val after = System.currentTimeMillis()

        // The popup state is set too.
        assertEquals("First!", RaClient.lastUnlock?.title)
        assertEquals(10, RaClient.lastUnlock?.points)

        val recent = hub.load().recent
        assertEquals(1, recent.size)
        val entry = recent[0]
        assertEquals(5, entry.achievementId)
        assertEquals("First!", entry.title)
        assertEquals("abc123", entry.gameId)
        assertEquals(10, entry.points)
        assertTrue(entry.unlockedAt in before..after)
    }

    @Test
    fun unlockWithoutAGameIsNotRecorded() {
        val hub = RaHubStore(temp.root)
        RaClient.init(RaSessionStore(temp.root), hubStore = hub)
        // No loadGame: there is no game to attribute the unlock to.
        RaClient.onUnlocked(5, "First!", "Do the thing.", 10)
        assertTrue(hub.load().recent.isEmpty())
        // The popup still shows — the recording is the only part skipped.
        assertEquals("First!", RaClient.lastUnlock?.title)
    }

    @Test
    fun unlockWithoutAHubStoreIsNotRecorded() {
        RaClient.init(RaSessionStore(temp.root))
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onUnlocked(5, "First!", "Do the thing.", 10)
        // A fresh store over the same dir finds nothing recorded.
        assertTrue(RaHubStore(temp.root).load().recent.isEmpty())
        assertEquals("First!", RaClient.lastUnlock?.title)
    }

    @Test
    fun refreshUserScoreStaysNullWhileUninitialised() {
        RaClient.init(RaSessionStore(temp.root))
        RaClient.refreshUserScore()
        assertNull(RaClient.userScoreSoftcore)
    }
}
