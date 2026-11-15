package app.poing

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cover-art rebuild (2026-11-15, alpha.6): the repository as
 * the single source of truth. Robolectric, not plain JVM: the
 * positive assertions need BitmapFactory to actually decode,
 * and the plain-JVM stub throws (see CoverArtStatusTest for
 * the pure-function tests, which stay on the plain JVM).
 *
 * The fake HTTP layer is a suspend lambda. A fake that never
 * returns must suspend cancellably ([delay]) — a blocking
 * sleep ignores coroutine cancellation, so the budget could
 * not end it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    companion object {
        // A 1x1 transparent PNG: the smallest decodable image.
        private val TINY_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )

        // A valid PNG header with a truncated body: the
        // signature gate passes, but no decoder can finish it.
        // On the device BitmapFactory returns null; under
        // Robolectric the shadow synthesises a bitmap, so the
        // truncated test injects a decode double for this one.
        private val TRUNCATED_PNG: ByteArray = TINY_PNG.copyOf(40)
    }

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

    private fun repo(
        httpGet: suspend (String, Int) -> HttpFetchResult,
        indexBudgetMs: Long = 500,
        artBudgetMs: Long = 500,
        decode: (ByteArray) -> Bitmap? = ::decodeCoverBytes,
    ) = CoverRepository(
        temp.root, httpGet, indexBudgetMs = indexBudgetMs, artBudgetMs = artBudgetMs, decode = decode,
    )

    /** A fake that never returns on its own: it suspends until cancelled, then gives up. */
    private suspend fun hangForever(): HttpFetchResult {
        delay(Long.MAX_VALUE)
        return HttpFetchResult.httpFailure(500)
    }

    /** A tree JSON with [entries] boxarts, for the parse-stall test. */
    private fun bigTreeJson(entries: Int): String {
        val sb = StringBuilder("{\"tree\":[")
        for (i in 0 until entries) {
            if (i > 0) sb.append(',')
            sb.append("{\"path\":\"Named_Boxarts/Game Number $i (World).png\",\"type\":\"blob\"}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun statusLine(repo: CoverRepository, games: List<LibraryGame>): String =
        coverArtStatusLine(repo.covers.value, repo.indexes.value, repo.gate.value, games)

    // -- timeouts: every step is bounded by withTimeout --------------------

    @Test
    fun hungIndexTimesOutAndNamesTheFailure() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ -> hangForever() }
        val r = repo(httpGet)
        val start = System.currentTimeMillis()
        r.setGames(listOf(game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("hung index must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertEquals(IndexState.Failed("timed out"), r.indexes.value[GameSystem.GBA])
        assertEquals(
            CoverState.Failed("index gba: timed out"),
            r.covers.value["gba1"],
        )
        val failed = r.trace.value.filterIsInstance<CoverTraceEvent.IndexFailed>()
        assertEquals(1, failed.size)
        assertEquals(GameSystem.GBA, failed.single().system)
        assertEquals("timed out", failed.single().detail)
        r.close()
    }

    @Test
    fun failedGbaIndexDoesNotBlockGbAndGbc() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> hangForever()
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        val start = System.currentTimeMillis()
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("pipelines must finish on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        assertTrue(r.covers.value["gbc1"] is CoverState.Ready)
        assertEquals(CoverState.Failed("index gba: timed out"), r.covers.value["gba1"])
        assertTrue(statusLine(r, games).contains("gba index failed (timed out)"))
        r.close()
    }

    @Test
    fun indexDownloadThatNeverReturnsInConnectStillTimesOut() = runBlocking {
        // A connect/DNS hang: the download never even starts,
        // so no socket exists to disconnect — the total budget
        // is the only thing that ends it.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ -> hangForever() }
        val r = repo(httpGet)
        val start = System.currentTimeMillis()
        r.setGames(listOf(game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("connect hang must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertEquals(IndexState.Failed("timed out"), r.indexes.value[GameSystem.GBA])
        r.close()
    }

    @Test
    fun slowDownloadPastTheBudgetStillTimesOut() = runBlocking {
        // A trickle that never stalls 15s — it just takes
        // longer than the total budget. The old per-read stall
        // timeout would never fire on it; the total budget must.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ ->
            delay(5_000)
            HttpFetchResult.success(treeJson.toByteArray())
        }
        val r = repo(httpGet)
        val start = System.currentTimeMillis()
        r.setGames(listOf(game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("slow download must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertEquals(IndexState.Failed("timed out"), r.indexes.value[GameSystem.GBA])
        r.close()
    }

    @Test
    fun indexLoadStalledOutsideTheDownloadStillTimesOut() = runBlocking {
        // The device case (2026-11-15, alpha.3): the GBA load
        // sat at "index gba started" for three and a half
        // minutes — past the 45s budget — with no timeout and
        // no failure. The budget bounded only the download.
        // The rebuild wraps the WHOLE load (download + parse +
        // cache write) in one withTimeout, so a stall anywhere
        // in the step is named "timed out". The stall here is
        // the parse itself: a 150k-entry tree, with a 50ms
        // budget — the cooperative yield after the parse lets
        // the timeout land mid-step instead of after it.
        val big = bigTreeJson(150_000)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") ->
                    HttpFetchResult.success(big.toByteArray())
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet, indexBudgetMs = 50)
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
        )
        val start = System.currentTimeMillis()
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("stalled load must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertEquals(IndexState.Failed("timed out"), r.indexes.value[GameSystem.GBA])
        // The other system loaded despite the GBA stall.
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        r.close()
    }

    @Test
    fun gbAndGbcArtLandWhileGbaIndexIsStillInFlight() = runBlocking {
        // The device case (2026-11-15, alpha.3): GB/GBC
        // indexes are cached on disk, the GBA index never
        // resolves — the GB/GBC covers must still land at
        // once, without waiting on the GBA index at all. A 30s
        // budget on purpose: if the pipelines waited for the
        // GBA index it would take 30s.
        val gbaStuck = repo(
            httpGet = { url, _ ->
                when {
                    url.contains("Nintendo_-_Game_Boy_Advance") -> hangForever()
                    url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                    else -> HttpFetchResult.success(TINY_PNG)
                }
            },
            indexBudgetMs = 30_000,
            artBudgetMs = 500,
        )
        // GB/GBC indexes cached on disk, as on the device.
        // The cache file holds BARE filenames: parseTreeJson
        // strips the "Named_Boxarts/" prefix before the file
        // is written.
        val indexLines = "Tetris (World).png\nDr. Mario (World).png\n"
        File(temp.root, "art-index-gb.txt").writeText(indexLines)
        File(temp.root, "art-index-gbc.txt").writeText(indexLines)
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        val start = System.currentTimeMillis()
        try {
            gbaStuck.setGames(games)
            gbaStuck.setGate(CoverArtGate.OPEN, "Wi-Fi")
            // Poll for the ready covers: the GBA pipeline is
            // still hung, so awaitIdle would wait out its 30s.
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (gbaStuck.covers.value["gb1"] is CoverState.Ready &&
                    gbaStuck.covers.value["gbc1"] is CoverState.Ready
                ) break
                delay(50)
            }
            val elapsed = System.currentTimeMillis() - start
            assertTrue(
                "ready art must land without waiting on the slow index, took ${elapsed}ms",
                elapsed < 5_000,
            )
            assertTrue(gbaStuck.covers.value["gb1"] is CoverState.Ready)
            assertTrue(gbaStuck.covers.value["gbc1"] is CoverState.Ready)
            // The GBA index is still in flight — Loading, not failed yet.
            assertEquals(IndexState.Loading, gbaStuck.indexes.value[GameSystem.GBA])
            assertEquals(CoverState.Loading, gbaStuck.covers.value["gba1"])
            // And the status line — derived from the same map
            // the tiles read — already counts the two landed
            // covers while the GBA index is still in flight.
            assertTrue(
                statusLine(gbaStuck, games).contains("fetching gba index… 2 landed"),
            )
        } finally {
            // Let the stuck GBA load unwind.
            gbaStuck.close()
        }
    }

    @Test
    fun gateClosedAbortsAHungFetch() = runBlocking {
        // A 60s budget on purpose: only the gate closing may
        // end this. The game returns to Missing — a cancel is
        // not a failure.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ -> hangForever() }
        val r = repo(httpGet, indexBudgetMs = 60_000, artBudgetMs = 60_000)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        val start = System.currentTimeMillis()
        try {
            r.setGames(listOf(g))
            r.setGate(CoverArtGate.OPEN, "Wi-Fi")
            // Wait until the index download is in flight.
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (r.indexes.value[GameSystem.GB] is IndexState.Loading) break
                delay(50)
            }
            assertEquals(IndexState.Loading, r.indexes.value[GameSystem.GB])
            r.setGate(CoverArtGate.NO_NETWORK)
            assertTrue(r.awaitIdle())
            val elapsed = System.currentTimeMillis() - start
            assertTrue("gate close must abort the hung fetch, took ${elapsed}ms", elapsed < 10_000)
            assertEquals(IndexState.NotLoaded, r.indexes.value[GameSystem.GB])
            assertEquals(CoverState.Missing, r.covers.value["gb1"])
            assertTrue(r.trace.value.any { it is CoverTraceEvent.PipelineParked })
        } finally {
            r.close()
        }
    }

    // -- index loading ------------------------------------------------------

    @Test
    fun fetchDownloadsOneFileAndCachesTheIndex() = runBlocking {
        val treeCalls = AtomicInteger(0)
        val artCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> {
                    treeCalls.incrementAndGet()
                    HttpFetchResult.success(treeJson.toByteArray())
                }
                else -> {
                    artCalls.incrementAndGet()
                    HttpFetchResult.success(TINY_PNG)
                }
            }
        }
        val r = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        assertEquals(1, treeCalls.get())
        assertEquals(1, artCalls.get())
        assertTrue(temp.root.resolve("art-index-gb.txt").isFile)
        assertTrue(r.coverFile(g).isFile)
        // A second setGames re-downloads nothing: the game is
        // Ready, and the index is cached in memory.
        r.setGames(listOf(g))
        assertTrue(r.awaitIdle())
        assertEquals(1, treeCalls.get())
        assertEquals(1, artCalls.get())
        r.close()
    }

    @Test
    fun indexIsCachedInMemory() = runBlocking {
        val treeCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                HttpFetchResult.success(treeJson.toByteArray())
            } else {
                HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        r.setGames(
            listOf(
                game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
                game("gb2", "DR MARIO", "drmario.gb", GameSystem.GB),
            ),
        )
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(1, treeCalls.get())
        // A third game of the same system reuses the memory cache too.
        r.setGames(
            listOf(
                game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
                game("gb2", "DR MARIO", "drmario.gb", GameSystem.GB),
                game("gb3", "TETRIS", "tetris2.gb", GameSystem.GB),
            ),
        )
        assertTrue(r.awaitIdle())
        assertEquals(1, treeCalls.get())
        r.close()
    }

    @Test
    fun noMatchIsTerminalUntilIndexReloads() = runBlocking {
        val treeCalls = AtomicInteger(0)
        val artCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                HttpFetchResult.success(treeJson.toByteArray())
            } else {
                artCalls.incrementAndGet()
                HttpFetchResult.httpFailure(404)
            }
        }
        val r = repo(httpGet)
        val unknown = game("gb9", "ZZZ UNKNOWN", "zzz.gb", GameSystem.GB)
        r.setGames(listOf(unknown))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(CoverState.NoMatch("no art in index"), r.covers.value["gb9"])
        assertEquals(0, artCalls.get())
        // A second setGames never re-attempts it: the miss is
        // state, not a memo — but just as terminal.
        r.setGames(listOf(unknown))
        assertTrue(r.awaitIdle())
        assertEquals(1, treeCalls.get())
        assertEquals(0, artCalls.get())
        // A forced index reload re-queues it — and re-downloads the index.
        r.refreshIndex(GameSystem.GB)
        assertTrue(r.awaitIdle())
        assertEquals(2, treeCalls.get())
        assertEquals(CoverState.NoMatch("no art in index"), r.covers.value["gb9"])
        r.close()
    }

    @Test
    fun cachedIndexSurvivesANewRepositoryWithNoNetwork() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success(TINY_PNG)
        }
        val first = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        first.setGames(listOf(g))
        first.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(first.awaitIdle())
        assertTrue(first.covers.value["gb1"] is CoverState.Ready)
        first.close()
        // The cover file goes: the second repository must prove
        // the INDEX came from the disk cache, not the cover.
        assertTrue(first.coverFile(g).delete())
        val dead: suspend (String, Int) -> HttpFetchResult =
            { _, _ -> HttpFetchResult.httpFailure(403) }
        val second = repo(dead)
        second.setGames(listOf(g))
        second.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(second.awaitIdle())
        assertEquals(IndexState.Ready(2, fromCache = true), second.indexes.value[GameSystem.GB])
        // The index loaded from cache; only the art download failed.
        assertEquals(CoverState.Failed("HTTP 403"), second.covers.value["gb1"])
        second.close()
    }

    @Test
    fun indexesArePerSystem() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success(TINY_PNG)
        }
        val r = repo(httpGet)
        r.setGames(
            listOf(
                game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
                game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            ),
        )
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertTrue(temp.root.resolve("art-index-gb.txt").isFile)
        assertTrue(temp.root.resolve("art-index-gba.txt").isFile)
        r.close()
    }

    @Test
    fun noMatchMeansNoDownload() = runBlocking {
        val artCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else {
                artCalls.incrementAndGet()
                HttpFetchResult.httpFailure(404)
            }
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb9", "A GAME THAT DOES NOT EXIST", "nope.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(CoverState.NoMatch("no art in index"), r.covers.value["gb9"])
        assertEquals(0, artCalls.get())
        r.close()
    }

    @Test
    fun failedIndexFailsTheGameWithTheReason() = runBlocking {
        val dead: suspend (String, Int) -> HttpFetchResult =
            { _, _ -> HttpFetchResult.httpFailure(403) }
        val r = repo(dead)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(IndexState.Failed("HTTP 403"), r.indexes.value[GameSystem.GB])
        assertEquals(CoverState.Failed("index gb: HTTP 403"), r.covers.value["gb1"])
        r.close()
    }

    @Test
    fun emptyIndexFailsLoudly() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ ->
            HttpFetchResult.success("""{"tree":[]}""".toByteArray())
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(IndexState.Failed("empty index"), r.indexes.value[GameSystem.GB])
        assertEquals(
            CoverState.Failed("index gb: empty index"),
            r.covers.value["gb1"],
        )
        r.close()
    }

    @Test
    fun failedIndexDownloadIsAttemptedOncePerPipelineRun() = runBlocking {
        // The diagnostic (2026-11-15): a failed index used to
        // be re-attempted for every game of the system in the
        // same pass — each GBA game re-downloaded the 6 MB
        // tree. One attempt per system per pipeline run now.
        val treeCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                HttpFetchResult.httpFailure(403)
            } else {
                HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        val games = listOf(
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gb2", "DR MARIO", "drmario.gb", GameSystem.GB),
            game("gb3", "TETRIS", "tetris2.gb", GameSystem.GB),
        )
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(1, treeCalls.get())
        for (g in games) {
            assertEquals(CoverState.Failed("index gb: HTTP 403"), r.covers.value[g.id])
        }
        // An explicit retry re-attempts it.
        r.retryFailed()
        assertTrue(r.awaitIdle())
        assertEquals(2, treeCalls.get())
        r.close()
    }

    @Test
    fun indexFailureDetailIsReported() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult =
            { _, _ -> HttpFetchResult.httpFailure(403) }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val failed = r.trace.value.filterIsInstance<CoverTraceEvent.IndexFailed>()
        assertEquals(1, failed.size)
        assertEquals(GameSystem.GB, failed.single().system)
        assertTrue(failed.single().detail.contains("403"))
        r.close()
    }

    @Test
    fun artFailureDetailIsReported() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.httpFailure(404)
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val failed = r.trace.value.filterIsInstance<CoverTraceEvent.ArtFailed>()
        assertEquals(1, failed.size)
        assertTrue(failed.single().detail.contains("404"))
        r.close()
    }

    @Test
    fun indexLoadedEventReportsEntriesAndCache() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success(TINY_PNG)
        }
        val r = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val loaded = r.trace.value.filterIsInstance<CoverTraceEvent.IndexLoaded>()
        assertEquals(1, loaded.size)
        assertEquals(2, loaded.single().entries)
        assertFalse(loaded.single().fromCache)
        r.close()
        // The disk cache reports fromCache on a new repository.
        assertTrue(r.coverFile(g).delete())
        val second = repo({ _, _ -> HttpFetchResult.httpFailure(403) })
        second.setGames(listOf(g))
        second.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(second.awaitIdle())
        val cached = second.trace.value.filterIsInstance<CoverTraceEvent.IndexLoaded>()
        assertEquals(1, cached.size)
        assertTrue(cached.single().fromCache)
        second.close()
    }

    // -- the saga's failure modes, each pinned ------------------------------

    @Test
    fun artDownloadThatNeverReturnsStillTimesOut() = runBlocking {
        // The same total budget on the art step: a hung
        // download fails the game with "timed out" — and the
        // pipeline job still ends, so the next game's pipeline
        // is not held hostage by it.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> hangForever()
            }
        }
        val r = repo(httpGet)
        val start = System.currentTimeMillis()
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val elapsed = System.currentTimeMillis() - start
        assertTrue("hung art download must fail on the budget, took ${elapsed}ms", elapsed < 10_000)
        assertEquals(CoverState.Failed("timed out"), r.covers.value["gb1"])
        assertTrue(
            r.trace.value.any {
                it is CoverTraceEvent.ArtFailed && it.detail == "timed out"
            },
        )
        r.close()
    }

    @Test
    fun truncatedArtDoesNotBrickTheGame() = runBlocking {
        // §1.3(a), the 2026-11-15 mechanism: a decode failure
        // left a file on disk and a memo blocked every retry,
        // while the trace kept saying "art landed". The
        // rebuild must make that outcome impossible: bytes are
        // verified in memory before they touch disk, a failed
        // decode writes nothing, and the game re-downloads on
        // retry. The decode double stands in for the platform
        // decoder on truncated bytes — under Robolectric the
        // shadow synthesises a bitmap from them, on the device
        // BitmapFactory returns null.
        val good = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> if (good.get() == 0) HttpFetchResult.success(TRUNCATED_PNG)
                else HttpFetchResult.success(TINY_PNG)
            }
        }
        val decode: (ByteArray) -> Bitmap? = { bytes ->
            if (bytes.contentEquals(TRUNCATED_PNG)) null else decodeCoverBytes(bytes)
        }
        val r = repo(httpGet, decode = decode)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        // Nothing was written, and the game failed loudly.
        assertFalse("a failed decode must write no file", r.coverFile(g).exists())
        assertEquals(CoverState.Failed("undecodable"), r.covers.value["gb1"])
        assertTrue(r.trace.value.any { it is CoverTraceEvent.ArtDiscarded })
        // The good bytes arrive: the game re-downloads — no
        // memo blocks it — and reaches Ready.
        good.set(1)
        r.retryFailed()
        assertTrue(r.awaitIdle())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        assertTrue(r.coverFile(g).exists())
        assertEquals(
            TINY_PNG.toList(),
            r.coverFile(g).readBytes().toList(),
        )
        r.close()
    }

    @Test
    fun corruptFileOnDiskIsHealedOnStartup() = runBlocking {
        // A truncated download from an earlier build sits on
        // disk. The startup scan must delete it and re-queue
        // the game — not trust it because the file exists.
        val coversDir = File(temp.root, "covers").apply { mkdirs() }
        File(coversDir, "gb1.png").writeBytes("not an image".toByteArray())
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success(TINY_PNG)
        }
        val r = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        assertEquals(TINY_PNG.toList(), r.coverFile(g).readBytes().toList())
        val scan = r.trace.value.filterIsInstance<CoverTraceEvent.DiskScan>()
        assertEquals(1, scan.size)
        assertEquals(0, scan.single().restored)
        assertEquals(1, scan.single().discarded)
        r.close()
    }

    @Test
    fun perSystemIndependenceEndToEnd() = runBlocking {
        // GBA index hung; GB/GBC complete. When the GBA
        // pipeline later times out, the GB/GBC states must not
        // move at all — not even a re-render of the same state.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> hangForever()
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet, indexBudgetMs = 2_000)
        val games = listOf(
            game("gba1", "TETRIS", "tetris.gba", GameSystem.GBA),
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        try {
            r.setGames(games)
            r.setGate(CoverArtGate.OPEN, "Wi-Fi")
            // GB/GBC land long before the GBA budget fires.
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (r.covers.value["gb1"] is CoverState.Ready &&
                    r.covers.value["gbc1"] is CoverState.Ready
                ) break
                delay(50)
            }
            val gbBefore = r.covers.value["gb1"] as CoverState.Ready
            val gbcBefore = r.covers.value["gbc1"] as CoverState.Ready
            assertTrue(r.awaitIdle())
            assertEquals(IndexState.Failed("timed out"), r.indexes.value[GameSystem.GBA])
            assertEquals(
                CoverState.Failed("index gba: timed out"),
                r.covers.value["gba1"],
            )
            // The GB/GBC states are the same objects as before
            // the GBA timeout — untouched by it.
            val gbAfter = r.covers.value["gb1"] as CoverState.Ready
            val gbcAfter = r.covers.value["gbc1"] as CoverState.Ready
            assertTrue(gbAfter.bitmap === gbBefore.bitmap)
            assertTrue(gbcAfter.bitmap === gbcBefore.bitmap)
        } finally {
            r.close()
        }
    }

    @Test
    fun statusLineCannotDisagreeWithTiles() = runBlocking {
        // Property-style: the status line is a pure function
        // of the same map the tiles read, so its counts cannot
        // disagree with them. After a mixed run — one ready,
        // two failed, one no-match — the line's "1/4 covers"
        // is the Ready count, and the failure is named.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                url.contains("Dr.%20Mario") -> HttpFetchResult.httpFailure(404)
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        val games = listOf(
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gb2", "DR MARIO", "drmario.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
            game("gb9", "ZZZ UNKNOWN", "zzz.gb", GameSystem.GB),
        )
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val ready = r.covers.value.values.filterIsInstance<CoverState.Ready>()
        assertEquals(1, ready.size)
        assertTrue(ready.all { it.bitmap.width > 0 })
        val line = statusLine(r, games)
        assertTrue(line.contains("1/4 covers"))
        assertTrue(line.contains("art DR MARIO: HTTP 404"))
        r.close()
    }

    @Test
    fun retryReenqueuesOnlyTheFailed() = runBlocking {
        val tetrisCalls = AtomicInteger(0)
        val marioCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                url.contains("Tetris") -> {
                    tetrisCalls.incrementAndGet()
                    HttpFetchResult.success(TINY_PNG)
                }
                else -> {
                    marioCalls.incrementAndGet()
                    HttpFetchResult.httpFailure(404)
                }
            }
        }
        val r = repo(httpGet)
        val games = listOf(
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gb2", "DR MARIO", "drmario.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        assertEquals(1, tetrisCalls.get())
        assertEquals(2, marioCalls.get())
        // Retry re-attempts exactly the two failed games; the
        // Ready one is untouched.
        r.retryFailed()
        assertTrue(r.awaitIdle())
        assertEquals(1, tetrisCalls.get())
        assertEquals(4, marioCalls.get())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        r.close()
    }

    @Test
    fun gateClosedParksAndGateOpenedResumes() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> {
                    delay(1_000)
                    HttpFetchResult.success(TINY_PNG)
                }
            }
        }
        val r = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        // Closed: no HTTP calls at all.
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.NO_NETWORK)
        assertTrue(r.awaitIdle())
        assertEquals(CoverState.Missing, r.covers.value["gb1"])
        // Open: the pipeline starts; close mid-download and
        // the in-flight download is cancelled — the game
        // returns to Missing, not Failed.
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (r.covers.value["gb1"] is CoverState.Loading) break
            delay(50)
        }
        assertEquals(CoverState.Loading, r.covers.value["gb1"])
        r.setGate(CoverArtGate.NO_NETWORK)
        assertTrue(r.awaitIdle())
        assertEquals(CoverState.Missing, r.covers.value["gb1"])
        // Open again: the download completes.
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertTrue(r.covers.value["gb1"] is CoverState.Ready)
        r.close()
    }

    @Test
    fun truncatedIndexFailsLoudly() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ ->
            HttpFetchResult.success(
                """{"truncated": true, "tree":[
                    {"path":"Named_Boxarts/Tetris (World).png","type":"blob"}
                ]}""".toByteArray(),
            )
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(IndexState.Failed("index truncated"), r.indexes.value[GameSystem.GB])
        assertFalse("a truncated index must write no cache", temp.root.resolve("art-index-gb.txt").isFile)
        r.close()
    }

    @Test
    fun rateLimit403IsNamed() = runBlocking {
        // The fake returns the reason the real client builds
        // from the rate-limit headers; the repository must
        // carry it verbatim into the game state and the trace.
        // (The header parsing itself is pinned in
        // CoverArtStatusTest.rateLimitDetail.)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { _, _ ->
            HttpFetchResult(null, 403, "HTTP 403 — rate limit, resets 15:32")
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(
            CoverState.Failed("index gb: HTTP 403 — rate limit, resets 15:32"),
            r.covers.value["gb1"],
        )
        assertTrue(
            r.trace.value.any {
                it is CoverTraceEvent.IndexFailed && it.detail.contains("rate limit")
            },
        )
        r.close()
    }

    @Test
    fun landedCoverIsDecodedIntoTheStateTheTilesRead() = runBlocking {
        // The centerpiece, adapted from
        // CoverArtDecodeOnLandTest: a landed cover must be
        // decoded into the state the tiles read — at landing
        // time, in the same worker step as the write, not at
        // the end of a pass that may never come.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        val g = game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)
        r.setGames(listOf(g))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val state = r.covers.value["gb1"]
        assertTrue(state is CoverState.Ready)
        assertNotNull(
            "a landed cover must be decoded into the covers map — " +
                "the tiles read that map, so until it is there the tile keeps its placeholder",
            (state as CoverState.Ready).bitmap,
        )
        assertTrue(r.trace.value.any { it is CoverTraceEvent.ArtReady })
        r.close()
    }

    // -- trace ---------------------------------------------------------------

    @Test
    fun traceRecordsLifecycleInOrder() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        r.setGames(listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB)))
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        val trace = r.trace.value
        val kinds = trace.map { it::class.simpleName }
        assertEquals(
            listOf(
                "PipelineStarted", "IndexStarted", "IndexLoaded",
                "ArtMatched", "ArtStarted", "ArtDownloaded", "ArtDecoded", "ArtReady",
            ),
            kinds,
        )
        val text = coverTraceLines(trace, fetching = false)
        assertTrue(text.contains("pipeline gb started"))
        assertTrue(text.contains("index gb started"))
        assertTrue(text.contains("index gb loaded (2 entries)"))
        assertTrue(text.contains("art matched: TETRIS (Tetris (World).png)"))
        assertTrue(text.contains("art downloaded: TETRIS"))
        assertTrue(text.contains("art decoded: TETRIS (1×1)"))
        assertTrue(text.contains("art ready: TETRIS"))
        r.close()
    }

    @Test
    fun throwingHttpGetFailsLoudlyInsteadOfKillingThePipeline() = runBlocking {
        // The old trace test's throwing fetcher: the pass still
        // ended with the error named. The rebuild has no pass —
        // a throwing HTTP layer fails the index with the reason,
        // and the other system's pipeline still completes.
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            // The trailing slash matters: "Nintendo_-_Game_Boy"
            // is a prefix of "Nintendo_-_Game_Boy_Color", so an
            // unanchored match would throw for the GBC system too.
            if (url.contains("Nintendo_-_Game_Boy/")) throw RuntimeException("boom")
            HttpFetchResult.success(TINY_PNG)
        }
        val r = repo(httpGet)
        val games = listOf(
            game("gb1", "TETRIS", "tetris.gb", GameSystem.GB),
            game("gbc1", "DR MARIO", "drmario.gbc", GameSystem.GBC),
        )
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(IndexState.Failed("RuntimeException"), r.indexes.value[GameSystem.GB])
        assertTrue(
            r.trace.value.any {
                it is CoverTraceEvent.IndexFailed && it.detail == "RuntimeException"
            },
        )
        // The GBC pipeline was not killed by the GB throw.
        assertTrue(r.covers.value["gbc1"] is CoverState.Ready)
        r.close()
    }

    @Test
    fun traceIsCappedAtOneHundred() = runBlocking {
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        val r = repo(httpGet)
        // 20 games × 5 art events + the pipeline/index trio =
        // 103 events; the trace keeps the newest 100.
        val games = (0 until 20).map { i ->
            game("gb$i", if (i % 2 == 0) "TETRIS" else "DR MARIO", "game$i.gb", GameSystem.GB)
        }
        r.setGames(games)
        r.setGate(CoverArtGate.OPEN, "Wi-Fi")
        assertTrue(r.awaitIdle())
        assertEquals(MAX_TRACE_EVENTS, r.trace.value.size)
        assertEquals(100, MAX_TRACE_EVENTS)
        r.close()
    }
}
