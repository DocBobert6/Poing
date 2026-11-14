package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoverArtTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun game() = LibraryGame(
        id = "abc123",
        title = "TETRIS",
        system = GameSystem.GB,
        fileName = "abc123.gb",
        sourceName = "tetris.gb",
        sha256 = "abc123",
        addedAt = 0L,
    )

    @Test
    fun noCoverWithoutWifi() {
        val art = CoverArtStore(temp.root)
        assertFalse(art.hasCover(game()))
        assertNull(art.coverOrFetch(game(), networkAvailable = false))
    }

    @Test
    fun noOpFetcherNeverProvidesCover() {
        val art = CoverArtStore(temp.root)
        assertNull(art.coverOrFetch(game(), networkAvailable = true))
    }

    @Test
    fun cachedCoverIsReturnedWithoutFetching() {
        var fetched = 0
        val fetcher = CoverArtFetcher { fetched++; "bytes".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        art.coverFile(game()).writeBytes("cached".toByteArray())
        assertTrue(art.hasCover(game()))
        assertEquals("cached", art.coverOrFetch(game(), networkAvailable = false)?.readText())
        assertEquals(0, fetched)
    }

    @Test
    fun wifiFetchIsCachedForNextTime() {
        val fetcher = CoverArtFetcher { "fresh".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        val first = art.coverOrFetch(game(), networkAvailable = true)
        assertEquals("fresh", first?.readText())
        val second = art.coverOrFetch(game(), networkAvailable = false)
        assertEquals(first, second)
    }

    @Test
    fun fetchMissingCoversFetchesOnlyWhatIsMissing() {
        var calls = 0
        val fetcher = CoverArtFetcher { calls++; "fresh".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        val missing = game()
        val cached = game().copy(id = "cached1", title = "CACHED")
        art.coverFile(cached).writeBytes("cached".toByteArray())
        // One pass lands the missing cover and skips the cached one.
        assertTrue(fetchMissingCovers(art, listOf(missing, cached), networkAvailable = true))
        assertEquals(1, calls)
        assertEquals("fresh", art.coverFile(missing).readText())
        // A second pass finds everything cached and fetches nothing.
        assertFalse(fetchMissingCovers(art, listOf(missing, cached), networkAvailable = true))
        assertEquals(1, calls)
    }

    @Test
    fun fetchMissingCoversDoesNothingOffline() {
        var calls = 0
        val fetcher = CoverArtFetcher { calls++; "fresh".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        assertFalse(fetchMissingCovers(art, listOf(game()), networkAvailable = false))
        assertEquals(0, calls)
        assertFalse(art.hasCover(game()))
    }

    @Test
    fun fetchMissingCoversCapsAPassAtTwelve() {
        var calls = 0
        val fetcher = CoverArtFetcher { calls++; "fresh".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        val games = (0 until 15).map { i -> game().copy(id = "game$i", title = "GAME $i") }
        // The first pass lands twelve and stops; the second lands
        // the remaining three; the third finds nothing to do.
        assertTrue(fetchMissingCovers(art, games, networkAvailable = true))
        assertEquals(12, calls)
        assertTrue(fetchMissingCovers(art, games, networkAvailable = true))
        assertEquals(15, calls)
        assertFalse(fetchMissingCovers(art, games, networkAvailable = true))
        assertEquals(15, calls)
    }

    @Test
    fun decodeIntoCacheIgnoresAnUndecodableCover() {
        val art = CoverArtStore(temp.root)
        art.coverFile(game()).writeBytes("not an image".toByteArray())
        art.decodeIntoCache(game())
        assertTrue(art.coverBitmaps.isEmpty())
        // A second attempt is a no-op (the failure is memoized),
        // not a second decode — and nothing bogus was cached.
        art.decodeIntoCache(game())
        assertTrue(art.coverBitmaps.isEmpty())
    }
}
