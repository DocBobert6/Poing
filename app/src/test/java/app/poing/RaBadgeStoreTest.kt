package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Hub + badges (2026-11-14) — the badge downloader + disk cache:
 * the cache file is keyed on the URL, a cached badge never
 * fetches again, nothing fetches while the network gate is
 * closed, and an over-cap body is refused. The fetch and the cap
 * run on the plain JVM; the decode runs under Robolectric in
 * [RaBadgeDecodeTest] (BitmapFactory is a stub on the plain JVM).
 */
class RaBadgeStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val url = "https://media.retroachievements.org/Badge/12345.png"
    private val otherUrl = "https://media.retroachievements.org/Badge/12345_lock.png"

    private fun store(fetcher: RaBadgeFetcher = RaBadgeFetcher { null }) =
        RaBadgeStore(temp.root, fetcher)

    @Test
    fun badgeFileIsStablePerUrlAndDiffersPerUrl() {
        val s = store()
        assertEquals(s.badgeFile(url), s.badgeFile(url))
        assertTrue(s.badgeFile(url).name.endsWith(".png"))
        assertFalse(s.badgeFile(url) == s.badgeFile(otherUrl))
        // A different host with the same badge name is a different file.
        val otherHost = "https://example.com/Badge/12345.png"
        assertFalse(s.badgeFile(url) == s.badgeFile(otherHost))
    }

    @Test
    fun noBadgeWithoutNetwork() {
        var fetched = 0
        val s = store { fetched++; "bytes".toByteArray() }
        assertFalse(s.hasBadge(url))
        assertNull(s.badgeOrFetch(url, networkAvailable = false))
        assertEquals(0, fetched)
    }

    @Test
    fun cachedBadgeIsReturnedWithoutFetching() {
        var fetched = 0
        val s = store { fetched++; "bytes".toByteArray() }
        s.badgeFile(url).writeBytes("cached".toByteArray())
        assertTrue(s.hasBadge(url))
        assertEquals("cached", s.badgeOrFetch(url, networkAvailable = false)?.readText())
        assertEquals(0, fetched)
    }

    @Test
    fun fetchIsCachedForNextTime() {
        var fetched = 0
        val s = store { fetched++; "fresh".toByteArray() }
        val first = s.badgeOrFetch(url, networkAvailable = true)
        assertEquals("fresh", first?.readText())
        assertEquals(1, fetched)
        // A second call finds the file and fetches nothing, even offline.
        val second = s.badgeOrFetch(url, networkAvailable = false)
        assertEquals(first, second)
        assertEquals(1, fetched)
    }

    @Test
    fun fetchFailureReturnsNullAndDoesNotCache() {
        val s = store { null }
        assertNull(s.badgeOrFetch(url, networkAvailable = true))
        assertFalse(s.hasBadge(url))
    }

    @Test
    fun overCapBodyIsRefused() {
        val big = ByteArray(RaBadgeStore.MAX_BADGE_BYTES + 1)
        val s = RaBadgeStore(temp.root, RaBadgeFetcher { big })
        assertNull(s.badgeOrFetch(url, networkAvailable = true))
        assertFalse(s.hasBadge(url))
    }
}
