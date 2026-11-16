package app.poing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Alpha.22 — the game-load state machine: a failed set load is
 * classified, not just dropped. rcheevos reports "Unknown game"
 * when the server has no set for the ROM's hash, and anything
 * else means the load itself failed (and a retry may work).
 * Runs on the plain JVM: RaClient never initialises without the
 * native libraries, so the load itself is a no-op and only the
 * state transitions run.
 */
class RaGameLoadStateTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun setUp() {
        RaClient.logout()
    }

    @After
    fun tearDown() {
        // The client is a singleton: leave it as the other tests
        // found it — logged out, no hub store wired.
        RaClient.logout()
        RaClient.init(RaSessionStore(temp.root))
    }

    @Test
    fun loadGameSetsLoading() {
        RaClient.loadGame("/games/abc123.gba")
        assertEquals(RaGameLoadState.Loading, RaClient.gameLoadState)
    }

    @Test
    fun unknownGameErrorClassifiesAsUnrecognised() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, RaClient.UNKNOWN_GAME_ERROR)
        assertEquals(RaGameLoadState.Unrecognised, RaClient.gameLoadState)
        assertNull(RaClient.gameLoadError)
        assertEquals(false, RaClient.gameLoaded)
    }

    @Test
    fun otherErrorClassifiesAsFailedAndKeepsTheError() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        assertEquals(RaGameLoadState.Failed, RaClient.gameLoadState)
        assertEquals("API failure", RaClient.gameLoadError)
        assertEquals(false, RaClient.gameLoaded)
    }

    @Test
    fun nullErrorClassifiesAsFailed() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, null)
        assertEquals(RaGameLoadState.Failed, RaClient.gameLoadState)
    }

    @Test
    fun retryAfterFailureReattemptsTheLoad() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        assertEquals(RaGameLoadState.Failed, RaClient.gameLoadState)
        RaClient.retryLoadGame()
        assertEquals(RaGameLoadState.Loading, RaClient.gameLoadState)
    }

    @Test
    fun retryWhenNotFailedIsANoop() {
        // Idle: no game, nothing to retry.
        RaClient.retryLoadGame()
        assertEquals(RaGameLoadState.Idle, RaClient.gameLoadState)
        // Loading: a load is already in flight.
        RaClient.loadGame("/games/abc123.gba")
        RaClient.retryLoadGame()
        assertEquals(RaGameLoadState.Loading, RaClient.gameLoadState)
    }

    @Test
    fun retryAfterUnrecognisedIsANoop() {
        // An unrecognised ROM has no set to retry for — the
        // same answer would only repeat.
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, RaClient.UNKNOWN_GAME_ERROR)
        RaClient.retryLoadGame()
        assertEquals(RaGameLoadState.Unrecognised, RaClient.gameLoadState)
    }

    @Test
    fun quitResetsToIdle() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        RaClient.onGameQuit()
        assertEquals(RaGameLoadState.Idle, RaClient.gameLoadState)
        assertNull(RaClient.gameLoadError)
        // The retry path is gone with the game.
        RaClient.retryLoadGame()
        assertEquals(RaGameLoadState.Idle, RaClient.gameLoadState)
    }

    @Test
    fun logoutResetsToIdle() {
        RaClient.loadGame("/games/abc123.gba")
        RaClient.onLoadGameResult(false, "API failure")
        RaClient.logout()
        assertEquals(RaGameLoadState.Idle, RaClient.gameLoadState)
        assertNull(RaClient.gameLoadError)
    }
}
