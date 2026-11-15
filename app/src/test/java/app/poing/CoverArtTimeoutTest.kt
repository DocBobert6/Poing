package app.poing

import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Cover-art fix, 2026-11-15 (alpha, still broken on device):
 * the pass stuck at "Cover art: fetching… 0 landed" — the
 * 6 MB GBA index is fetched first (first game
 * alphabetically), and the old code had only a 15s per-read
 * stall timeout, so a slow trickle held the whole pass open
 * with no stage and no failure. One hung system also held
 * the other two behind it, and Retry queued behind the hung
 * fetch instead of cancelling it. The fix: a TOTAL budget
 * per download, the stage in the status line, the three
 * indexes in parallel, and a Retry that cancels first.
 * Plain JVM tests — the fetcher, store and status line are
 * plain Kotlin.
 */
class CoverArtTimeoutTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val treeJson = """{"tree":[
        {"path":"Named_Boxarts/Tetris (World).png","type":"blob"},
        {"path":"Named_Boxarts/Dr. Mario (World).png","type":"blob"},
        {"path":"Named_Snaps/Tetris (World).png","type":"blob"}
    ]}"""

    private fun game(id: String, title: String, sourceName: String, system: GameSystem) =
        LibraryGame(
            id = id,
            title = title,
            system = system,
            fileName = "$id.gb",
            sourceName = sourceName,
            sha256 = id,
            addedAt = 0L,
        )

    /** A fake download that never returns on its own: it sleeps until interrupted, then gives up. */
    private fun hangForever(): HttpFetchResult {
        try {
            Thread.sleep(60_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return HttpFetchResult.httpFailure(500)
    }

    @Test
    fun hungIndexTimesOutAndNamesTheFailure() {
        val events = mutableListOf<CoverArtFetchEvent>()
        val httpGet: (String, Int) -> HttpFetchResult = { _, _ -> hangForever() }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
        ) { events += it }
        val start = System.currentTimeMillis()
        assertNull(fetcher.loadIndex(GameSystem.GBA))
        val elapsed = System.currentTimeMillis() - start
        assertTrue("hung index must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        val failed = events.filterIsInstance<CoverArtFetchEvent.IndexFailed>()
        assertEquals(1, failed.size)
        assertEquals(GameSystem.GBA, failed.single().system)
        assertEquals("timed out", failed.single().detail)
    }

    @Test
    fun failedGbaIndexDoesNotBlockGbAndGbc() {
        // The device case (2026-11-15): the GBA index stalls,
        // the GB/GBC covers must still land in the same pass.
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> hangForever()
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success("fake-png-bytes".toByteArray())
            }
        }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
        )
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = art::applyEvent
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        val start = System.currentTimeMillis()
        assertTrue(fetchMissingCovers(art, games, networkAvailable = true))
        val elapsed = System.currentTimeMillis() - start
        assertTrue("pass must finish on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertTrue(art.hasCover(games[1]))
        assertTrue(art.hasCover(games[2]))
        assertFalse(art.hasCover(games[0]))
        assertEquals(CoverIndexStatus.Failed("timed out"), art.status.value.indexes[GameSystem.GBA])
        // The effect's completion step, as LibraryScreen does it.
        art.noteCounts(games.size, games.count { art.hasCover(it) })
        assertTrue(coverArtStatusLine(art.status.value).contains("gba index failed (timed out)"))
    }

    @Test
    fun indexDownloadThatNeverReturnsInConnectStillTimesOut() {
        // A connect/DNS hang: the download never even starts,
        // so no socket exists to disconnect — the total budget
        // is the only thing that ends it.
        val events = mutableListOf<CoverArtFetchEvent>()
        val never = CountDownLatch(1)
        val httpGet: (String, Int) -> HttpFetchResult = { _, _ ->
            try {
                never.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            HttpFetchResult.httpFailure(500)
        }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
        ) { events += it }
        val start = System.currentTimeMillis()
        assertNull(fetcher.loadIndex(GameSystem.GBA))
        val elapsed = System.currentTimeMillis() - start
        assertTrue("connect hang must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        val failed = events.filterIsInstance<CoverArtFetchEvent.IndexFailed>()
        assertEquals(1, failed.size)
        assertEquals("timed out", failed.single().detail)
    }

    @Test
    fun slowDownloadPastTheBudgetStillTimesOut() {
        // A trickle that never stalls 15s — it just takes
        // longer than the total budget. The old per-read stall
        // timeout would never fire on it; the total budget must.
        val events = mutableListOf<CoverArtFetchEvent>()
        val httpGet: (String, Int) -> HttpFetchResult = { _, _ ->
            try {
                Thread.sleep(5_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            HttpFetchResult.success(treeJson.toByteArray())
        }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
        ) { events += it }
        val start = System.currentTimeMillis()
        assertNull(fetcher.loadIndex(GameSystem.GBA))
        val elapsed = System.currentTimeMillis() - start
        assertTrue("slow download must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        val failed = events.filterIsInstance<CoverArtFetchEvent.IndexFailed>()
        assertEquals(1, failed.size)
        assertEquals("timed out", failed.single().detail)
    }

    @Test
    fun indexLoadStalledOutsideTheDownloadStillTimesOut() {
        // The device case (2026-11-15, alpha.3): the GBA load
        // sat at "index gba started" for three and a half
        // minutes — past the 45s budget — with no timeout and
        // no failure. The budget bounds only the download
        // inside getBudgeted; a thread stalled OUTSIDE it (here,
        // delivering the loaded event) held the old pass's
        // unbounded join forever. The join is now bounded by
        // one budget, so the stall is named "timed out" and the
        // pass moves on.
        val events = Collections.synchronizedList(mutableListOf<CoverArtFetchEvent>())
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success("fake-png-bytes".toByteArray())
            }
        }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
        )
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = { event ->
            if (event is CoverArtFetchEvent.IndexLoaded && event.system == GameSystem.GBA) {
                // Stalled after the download, in event delivery
                // — getBudgeted cannot bound this.
                try {
                    Thread.sleep(60_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            events += event
            art.applyEvent(event)
        }
        val start = System.currentTimeMillis()
        fetcher.prefetchIndexes(setOf(GameSystem.GB, GameSystem.GBC, GameSystem.GBA))
        fetcher.awaitIndexes()
        val elapsed = System.currentTimeMillis() - start
        assertTrue("await must return on the budget, took ${elapsed}ms", elapsed < 10_000)
        val failed = synchronized(events) {
            events.filterIsInstance<CoverArtFetchEvent.IndexFailed>()
        }
        assertEquals(1, failed.size)
        assertEquals(GameSystem.GBA, failed.single().system)
        assertEquals("timed out", failed.single().detail)
        // The other two systems loaded despite the GBA stall.
        assertTrue(art.status.value.indexes[GameSystem.GB] is CoverIndexStatus.Downloaded)
        assertTrue(art.status.value.indexes[GameSystem.GBC] is CoverIndexStatus.Downloaded)
    }

    @Test
    fun gbAndGbcArtLandWhileGbaIndexIsStillInFlight() {
        // The device case (2026-11-15, alpha.3): GB/GBC
        // indexes are cached on disk, the GBA index never
        // resolves — the pass must still land the GB/GBC covers
        // at once, without waiting on the GBA index at all. A
        // 30s budget on purpose: if the pass waited for the
        // GBA index it would take 30s.
        val events = Collections.synchronizedList(mutableListOf<CoverArtFetchEvent>())
        val gbaStuck = CountDownLatch(1)
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> {
                    try {
                        gbaStuck.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    HttpFetchResult.httpFailure(500)
                }
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success("fake-png-bytes".toByteArray())
            }
        }
        // GB/GBC indexes cached on disk, as on the device.
        val indexLines =
            "Named_Boxarts/Tetris (World).png\nNamed_Boxarts/Dr. Mario (World).png\n"
        File(temp.root, "art-index-gb.txt").writeText(indexLines)
        File(temp.root, "art-index-gbc.txt").writeText(indexLines)
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 30_000, artBudgetMs = 500,
        )
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = { event ->
            events += event
            art.applyEvent(event)
        }
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        val start = System.currentTimeMillis()
        try {
            assertTrue(fetchMissingCovers(art, games, networkAvailable = true, maxNew = 2))
            val elapsed = System.currentTimeMillis() - start
            assertTrue(
                "ready art must land without waiting on the slow index, took ${elapsed}ms",
                elapsed < 5_000,
            )
            assertTrue(art.hasCover(games[1]))
            assertTrue(art.hasCover(games[2]))
            assertFalse(art.hasCover(games[0]))
            // The GBA index is still in flight when the pass
            // ends — it was never waited on, so no failure is
            // recorded yet.
            val failed = synchronized(events) {
                events.filterIsInstance<CoverArtFetchEvent.IndexFailed>()
            }
            assertTrue("no index may have failed yet", failed.isEmpty())
        } finally {
            // Let the stuck GBA load unwind.
            gbaStuck.countDown()
        }
    }

    @Test
    fun statusLineShowsTheStageWhileFetching() {
        val staged = CoverArtStatus(fetching = true, stage = "gba index", landedThisPass = 0)
        assertEquals("Cover art: fetching gba index… 0 landed", coverArtStatusLine(staged))
        val failed = staged.copy(failedThisPass = 1, lastError = "index gba: timed out")
        assertEquals(
            "Cover art: fetching gba index… 0 landed, 1 failed · index gba: timed out",
            coverArtStatusLine(failed),
        )
        val plain = CoverArtStatus(fetching = true)
        assertEquals("Cover art: fetching… 0 landed", coverArtStatusLine(plain))
    }

    @Test
    fun indexStartedSetsTheStageAndFailureClearsIt() {
        val art = CoverArtStore(temp.root)
        art.beginPass()
        art.applyEvent(CoverArtFetchEvent.IndexStarted(GameSystem.GBA))
        assertEquals("gba index", art.status.value.stage)
        assertEquals("Cover art: fetching gba index… 0 landed", coverArtStatusLine(art.status.value))
        art.applyEvent(CoverArtFetchEvent.IndexFailed(GameSystem.GBA, "timed out"))
        assertNull(art.status.value.stage)
        assertEquals("index gba: timed out", art.status.value.lastError)
    }

    @Test
    fun cancelInflightAbortsAHungFetch() {
        val started = CountDownLatch(1)
        val httpGet: (String, Int) -> HttpFetchResult = { _, _ ->
            started.countDown()
            hangForever()
        }
        val fetcher = LibretroThumbnailsFetcher(
            temp.root, httpGet, indexBudgetMs = 60_000, artBudgetMs = 60_000,
        )
        // A 60s budget on purpose: only the cancel may end this.
        var result: List<Thumbnails.IndexedArt>? = null
        val worker = Thread { result = fetcher.loadIndex(GameSystem.GB) }.apply {
            isDaemon = true
        }
        worker.start()
        assertTrue("fake never started", started.await(10, TimeUnit.SECONDS))
        fetcher.cancelInflight()
        worker.join(5_000)
        assertFalse("cancel must abort the hung fetch", worker.isAlive)
        assertNull(result)
        assertTrue(fetcher.passCancelled())
    }
}
