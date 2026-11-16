package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Phase 6 (item 8) — the RetroAchievements session store (pure java.io). */
class RaSessionStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = RaSessionStore(temp.root)

    @Test
    fun defaultsAreLoggedOut() {
        val s = store()
        assertEquals("", s.username())
        assertEquals("", s.token())
        assertNull(s.storedLogin())
    }

    @Test
    fun usernameAndTokenRoundTrip() {
        val s = store()
        s.setUsername("DocBobert")
        s.setToken("abc123token")
        assertEquals("DocBobert", s.username())
        assertEquals("abc123token", s.token())
        assertEquals("DocBobert" to "abc123token", s.storedLogin())
        // A fresh store over the same dir reads them back.
        val s2 = RaSessionStore(temp.root)
        assertEquals("DocBobert", s2.username())
        assertEquals("DocBobert" to "abc123token", s2.storedLogin())
    }

    @Test
    fun storedLoginNeedsBothParts() {
        val s = store()
        s.setUsername("DocBobert")
        assertNull(s.storedLogin())
        s.setToken("abc123token")
        assertEquals("DocBobert" to "abc123token", s.storedLogin())
    }

    @Test
    fun clearLogsOut() {
        val s = store()
        s.setUsername("DocBobert")
        s.setToken("abc123token")
        s.clear()
        assertEquals("", s.username())
        assertEquals("", s.token())
        assertNull(s.storedLogin())
        assertTrue(RaSessionStore(temp.root).storedLogin() == null)
    }

    // Alpha.23 — the web API key.

    @Test
    fun apiKeyRoundTrip() {
        val s = store()
        assertEquals("", s.apiKey())
        s.setApiKey("abcd1234wxyz5678")
        assertEquals("abcd1234wxyz5678", s.apiKey())
        // A fresh store over the same dir reads it back.
        assertEquals("abcd1234wxyz5678", RaSessionStore(temp.root).apiKey())
    }

    @Test
    fun clearApiKeyRemovesOnlyTheKey() {
        val s = store()
        s.setUsername("DocBobert")
        s.setToken("abc123token")
        s.setApiKey("abcd1234wxyz5678")
        s.clearApiKey()
        assertEquals("", s.apiKey())
        // The login is untouched.
        assertEquals("DocBobert" to "abc123token", s.storedLogin())
    }

    @Test
    fun clearWipesTheKeyWithTheLogin() {
        val s = store()
        s.setUsername("DocBobert")
        s.setToken("abc123token")
        s.setApiKey("abcd1234wxyz5678")
        s.clear()
        assertEquals("", s.apiKey())
    }

    @Test
    fun maskShowsOnlyTheEnds() {
        assertEquals("abcd…5678", maskApiKey("abcd1234wxyz5678"))
        // A short key shows bullets only — never the key itself.
        assertEquals("•••", maskApiKey("abc"))
        assertEquals("", maskApiKey(""))
        // The masked form never contains the full key.
        val key = "abcd1234wxyz5678"
        assertFalse(maskApiKey(key).contains(key))
    }
}
