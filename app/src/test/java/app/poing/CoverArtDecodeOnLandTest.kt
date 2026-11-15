package app.poing

import android.util.Base64
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cover-art display fix, 2026-11-15 (alpha.5): a landed cover
 * must be visible in the store state the tiles read — at
 * landing time, not at the end of the pass. On device (alpha.4)
 * 10 covers landed in ~4s and the status line said so, but the
 * tiles kept their placeholders: the decode only ran after the
 * whole pass returned, and the pass waited up to one index
 * budget (45s) on the stuck GBA index. Robolectric, not plain
 * JVM: the positive assertion needs BitmapFactory to actually
 * decode, and the plain-JVM stub throws (see
 * CoverArtTest.decodeIntoCacheIgnoresAnUndecodableCover, which
 * only asserts the failure path).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverArtDecodeOnLandTest {
    @get:Rule
    val temp = TemporaryFolder()

    companion object {
        // A 1x1 transparent PNG: the smallest decodable image
        // (same fixture as LibraryCoverFetchTest).
        private val TINY_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
    }

    private val treeJson = """{"tree":[
        {"path":"Named_Boxarts/Tetris (World).png","type":"blob"},
        {"path":"Named_Boxarts/Dr. Mario (World).png","type":"blob"}
    ]}"""

    private fun game() = LibraryGame(
        id = "gb1",
        title = "TETRIS",
        system = GameSystem.GB,
        fileName = "gb1.gb",
        sourceName = "tetris.gb",
        sha256 = "gb1",
        addedAt = 0L,
    )

    @Test
    fun landedCoverIsDecodedIntoTheStateTheTilesRead() {
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = art::applyEvent
        val g = game()
        val file = art.coverOrFetch(g, networkAvailable = true)
        assertNotNull("the fetch must land the file", file)
        assertTrue(art.hasCover(g))
        assertNotNull(
            "a landed cover must be decoded into coverBitmaps at landing time — " +
                "the tiles read that map, so until it is there the tile keeps its placeholder",
            art.coverBitmaps[g.id],
        )
    }
}
