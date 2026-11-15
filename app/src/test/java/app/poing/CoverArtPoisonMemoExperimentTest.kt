package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * §6 experiment for the cover-art rebuild (2026-11-15,
 * alpha.6): reproduce the 2026-11-15 15:28 state against
 * the OLD pipeline — the trace says "art landed", the
 * tiles keep their placeholders forever.
 *
 * The device timings: 10 fast GB/GBC landings + a GBA
 * index that never returns + one decode that fails. On the
 * plain JVM every decode fails (BitmapFactory is a stub),
 * so the "one failed decode" stands in for the truncated
 * download on the device — the mechanism under test is the
 * store's, not the decoder's: once a decode fails, the memo
 * plus the hasCover short-circuit brick the game permanently.
 *
 * DELETE WITH THE OLD PIPELINE (step 3 of the rebuild).
 * It exists so the rebuild's core claim is demonstrated,
 * not asserted: the new repository makes this outcome
 * impossible by construction (verify-before-write, no memo).
 */
class CoverArtPoisonMemoExperimentTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun game(id: String, title: String) = LibraryGame(
        id = id,
        title = title,
        system = GameSystem.GB,
        fileName = "$id.gb",
        sourceName = "$id.gb",
        sha256 = id,
        addedAt = 0L,
    )

    @Test
    fun failedDecodeBricksTheGameForever() {
        var calls = 0
        val fetcher = CoverArtFetcher { calls++; "bytes".toByteArray() }
        val art = CoverArtStore(temp.root, fetcher)
        val games = (0 until 3).map { i -> game("game$i", "GAME $i") }

        // Pass 1: all three land files on disk — the trace
        // says they landed, and the fetcher was called once
        // per game.
        assertTrue(fetchMissingCovers(art, games, networkAvailable = true))
        assertEquals(3, calls)
        for (g in games) assertTrue(art.hasCover(g))
        // ...but no bitmap reached the tiles: every decode
        // failed (on the plain JVM the stub throws; on the
        // device the truncated bytes return null).
        assertTrue(art.coverBitmaps.isEmpty())

        // Pass 2: nothing is re-fetched — hasCover is true
        // for all three, so the pass filters them out before
        // coverOrFetch could ever clear the memo.
        assertFalse(fetchMissingCovers(art, games, networkAvailable = true))
        assertEquals(3, calls)
        // ...and nothing is re-decoded: the memo blocks it.
        // (This is the end-of-pass decode loop's backstop
        // path, and it is a no-op for these games.)
        for (g in games) art.decodeIntoCache(g)
        assertTrue(art.coverBitmaps.isEmpty())

        // The files are on disk, the fetcher works, the
        // passes run — and the tiles keep their placeholders
        // forever. That is §1.3(a), reproduced.
    }
}
