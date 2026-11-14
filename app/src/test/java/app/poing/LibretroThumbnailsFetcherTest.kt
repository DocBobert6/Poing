package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The libretro-thumbnails fetcher with the network faked: the
 * index is fetched once per system and cached on disk, a match
 * downloads one file, and any failure returns null.
 */
class LibretroThumbnailsFetcherTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val treeJson = """{"tree":[
        {"path":"Named_Boxarts/Tetris (World).png","type":"blob"},
        {"path":"Named_Boxarts/Dr. Mario (World).png","type":"blob"},
        {"path":"Named_Snaps/Tetris (World).png","type":"blob"}
    ]}"""

    private fun game(title: String, sourceName: String, system: GameSystem) = LibraryGame(
        id = "abc123",
        title = title,
        system = system,
        fileName = "abc123.gb",
        sourceName = sourceName,
        sha256 = "abc123",
        addedAt = 0L,
    )

    @Test
    fun fetchDownloadsOneFileAndCachesTheIndex() {
        var calls = 0
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            calls++
            when {
                url.contains("git/trees") -> treeJson.toByteArray()
                url.contains("Tetris") -> "art-bytes".toByteArray()
                else -> null
            }
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        val bytes = fetcher.fetch(game("TETRIS", "tetris.gb", GameSystem.GB))
        assertEquals("art-bytes", bytes?.decodeToString())
        // One index call + one art call; the index is on disk now.
        assertEquals(2, calls)
        assertTrue(temp.root.resolve("art-index-gb.txt").isFile)
        val second = fetcher.fetch(game("TETRIS", "tetris.gb", GameSystem.GB))
        assertEquals("art-bytes", second?.decodeToString())
        // Still 3: the second fetch re-downloads the art (the
        // fetcher caches the index, not art bytes) but makes no
        // second index call — a re-downloaded index would be 4.
        assertEquals(3, calls)
    }

    @Test
    fun indexIsCachedInMemory() {
        var treeCalls = 0
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls++
                treeJson.toByteArray()
            } else {
                "art-bytes".toByteArray()
            }
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        assertEquals(2, fetcher.loadIndex(GameSystem.GB)?.size)
        assertEquals(2, fetcher.loadIndex(GameSystem.GB)?.size)
        assertEquals(1, treeCalls) // the second load came from memory
        fetcher.fetch(game("TETRIS", "tetris.gb", GameSystem.GB))
        assertEquals(1, treeCalls) // the fetch reused the memory cache too
    }

    @Test
    fun noMatchIsMemoizedForTheSession() {
        var calls = 0
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            calls++
            if (url.contains("git/trees")) treeJson.toByteArray() else null
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        val unknown = game("ZZZ UNKNOWN", "zzz.gb", GameSystem.GB)
        assertNull(fetcher.fetch(unknown))
        val afterFirst = calls
        assertNull(fetcher.fetch(unknown))
        // The second fetch never reached the index or the network:
        // the miss was memoized. (A download failure would NOT be
        // memoized — only a no-match is deterministic.)
        assertEquals(afterFirst, calls)
    }

    @Test
    fun cachedIndexSurvivesANewFetcherWithNoNetwork() {
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            if (url.contains("git/trees")) treeJson.toByteArray() else null
        }
        val first = LibretroThumbnailsFetcher(temp.root, httpGet)
        assertEquals(2, first.loadIndex(GameSystem.GB)?.size)
        val dead: (String, Int) -> ByteArray? = { _, _ -> null }
        val second = LibretroThumbnailsFetcher(temp.root, dead)
        assertEquals(2, second.loadIndex(GameSystem.GB)?.size)
    }

    @Test
    fun indexesArePerSystem() {
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            if (url.contains("git/trees")) treeJson.toByteArray() else null
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        fetcher.loadIndex(GameSystem.GB)
        fetcher.loadIndex(GameSystem.GBA)
        assertTrue(temp.root.resolve("art-index-gb.txt").isFile)
        assertTrue(temp.root.resolve("art-index-gba.txt").isFile)
    }

    @Test
    fun noMatchMeansNoDownload() {
        var artCalls = 0
        val httpGet: (String, Int) -> ByteArray? = { url, _ ->
            if (url.contains("git/trees")) treeJson.toByteArray()
            else {
                artCalls++
                null
            }
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        assertNull(fetcher.fetch(game("A GAME THAT DOES NOT EXIST", "nope.gb", GameSystem.GB)))
        assertEquals(0, artCalls)
    }

    @Test
    fun failedIndexFetchMeansNull() {
        val dead: (String, Int) -> ByteArray? = { _, _ -> null }
        val fetcher = LibretroThumbnailsFetcher(temp.root, dead)
        assertNull(fetcher.fetch(game("TETRIS", "tetris.gb", GameSystem.GB)))
        assertNull(fetcher.loadIndex(GameSystem.GB))
    }

    @Test
    fun emptyIndexMeansNull() {
        val httpGet: (String, Int) -> ByteArray? = { _, _ -> """{"tree":[]}""".toByteArray() }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        assertNull(fetcher.loadIndex(GameSystem.GB))
    }
}
