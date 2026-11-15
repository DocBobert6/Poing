package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Cover-art fetch trace (2026-11-15, alpha.3): Robert's call.
 * The status line alone could not say why a pass never landed
 * a cover — on device it went quiet with no reason — so the
 * trace keeps every pass event with a timestamp, on a Trace
 * button in the library. Plain JVM tests: the store, the pass
 * and the trace lines are plain Kotlin.
 */
class CoverArtTraceTest {
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

    private fun lines(trace: List<CoverArtTraceEvent>, fetching: Boolean = false): String =
        coverArtTraceLines(trace, fetching)

    @Test
    fun fullPassRecordsLifecycleInOrder() {
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success("fake-png-bytes".toByteArray())
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = art::applyEvent
        art.noteGate(CoverArtGate.OPEN, "Wi-Fi")
        val games = listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB))
        assertTrue(fetchMissingCovers(art, games, networkAvailable = true))
        assertTrue(art.hasCover(games[0]))
        val trace = art.trace.toList()
        assertEquals(6, trace.size)
        assertTrue(trace[0] is CoverArtTraceEvent.PassStarted)
        assertTrue(trace[1] is CoverArtTraceEvent.Fetch)
        assertTrue(trace[2] is CoverArtTraceEvent.Fetch)
        assertTrue(trace[3] is CoverArtTraceEvent.Fetch)
        assertTrue(trace[4] is CoverArtTraceEvent.Fetch)
        assertTrue(trace[5] is CoverArtTraceEvent.PassEnded)
        val started = trace[0] as CoverArtTraceEvent.PassStarted
        assertEquals(CoverArtGate.OPEN, started.gate)
        assertEquals("Wi-Fi", started.networkLabel)
        assertEquals(1, started.totalGames)
        assertEquals(1, started.withoutCover)
        val ended = trace[5] as CoverArtTraceEvent.PassEnded
        assertEquals(1, ended.landed)
        assertEquals(0, ended.failed)
        assertEquals(null, ended.note)
        val text = lines(trace)
        assertTrue(text.contains("pass started — gate open (Wi-Fi), 1 games, 1 without cover"))
        assertTrue(text.contains("index gb started"))
        assertTrue(text.contains("index gb loaded (2 entries)"))
        assertTrue(text.contains("art landed: TETRIS"))
        assertTrue(text.contains("pass ended — 1 landed, 0 failed"))
        // Newest first: the pass-ended line comes before the
        // pass-started line in the view's text.
        assertTrue(text.indexOf("pass ended") < text.indexOf("pass started"))
    }

    @Test
    fun failedIndexIsNamedInTheTrace() {
        val httpGet: (String, Int) -> HttpFetchResult = { _, _ ->
            HttpFetchResult.httpFailure(403)
        }
        val fetcher = LibretroThumbnailsFetcher(temp.root, httpGet)
        val art = CoverArtStore(temp.root, fetcher)
        fetcher.onEvent = art::applyEvent
        art.noteGate(CoverArtGate.OPEN, "Wi-Fi")
        val games = listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB))
        assertFalse(fetchMissingCovers(art, games, networkAvailable = true))
        val text = lines(art.trace)
        assertTrue(text.contains("index gb failed: HTTP 403"))
        assertTrue(text.contains("pass ended — 0 landed, 1 failed"))
    }

    @Test
    fun gateClosedPassIsRecorded() {
        val art = CoverArtStore(temp.root)
        art.noteGate(CoverArtGate.METERED_NO_OPT_IN, "mobile data (not allowed)")
        val games = listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB))
        assertFalse(fetchMissingCovers(art, games, networkAvailable = false))
        val text = lines(art.trace)
        assertTrue(text.contains("pass started — gate closed — metered, mobile data off (mobile data (not allowed))"))
        assertTrue(text.contains("pass ended — 0 landed, 0 failed"))
    }

    @Test
    fun emptyTraceSaysNoPassStarted() {
        val text = lines(emptyList())
        assertTrue(text.contains("No fetch pass has started since the library opened."))
        assertFalse(text.contains("pass started"))
    }

    @Test
    fun runningPassHeaderNamesIt() {
        val art = CoverArtStore(temp.root)
        art.recordTrace(
            CoverArtTraceEvent.PassStarted(0L, CoverArtGate.OPEN, "Wi-Fi", 1, 1),
        )
        assertTrue(lines(art.trace, fetching = true).startsWith("A fetch pass is running."))
        assertTrue(lines(art.trace, fetching = false).startsWith("Last fetch pass:"))
    }

    @Test
    fun traceIsCappedAtOneHundred() {
        val art = CoverArtStore(temp.root)
        for (i in 0 until 105) {
            art.recordTrace(CoverArtTraceEvent.PassSkipped(i.toLong(), "skip $i"))
        }
        assertEquals(MAX_TRACE_EVENTS, art.trace.size)
        assertEquals(100, MAX_TRACE_EVENTS)
        val first = art.trace.first() as CoverArtTraceEvent.PassSkipped
        assertEquals("skip 5", first.reason)
        val last = art.trace.last() as CoverArtTraceEvent.PassSkipped
        assertEquals("skip 104", last.reason)
    }

    @Test
    fun throwingFetcherEndsThePassWithAnErrorNote() {
        val throwing = object : CoverArtFetcher {
            override fun fetch(game: LibraryGame): ByteArray? = null
            override fun prefetchIndexes(systems: Set<GameSystem>) {
                throw RuntimeException("boom")
            }
        }
        val art = CoverArtStore(temp.root, throwing)
        art.noteGate(CoverArtGate.OPEN, "Wi-Fi")
        val games = listOf(game("gb1", "TETRIS", "tetris.gb", GameSystem.GB))
        try {
            fetchMissingCovers(art, games, networkAvailable = true)
            fail("a throwing fetcher must propagate")
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }
        // The pass is still recorded as ended — with the error
        // named — instead of leaving the status line stuck on
        // "fetching…" with no reason.
        val ended = art.trace.last() as CoverArtTraceEvent.PassEnded
        assertEquals("error: RuntimeException", ended.note)
        // The duration sits between the counts and the note
        // ("pass ended — 0 landed, 0 failed (0s) — error: …"),
        // so assert the parts, not one literal.
        val text = lines(art.trace)
        assertTrue(text.contains("pass ended — 0 landed, 0 failed"))
        assertTrue(text.contains("error: RuntimeException"))
    }

    @Test
    fun skippedPassIsRecorded() {
        val art = CoverArtStore(temp.root)
        art.recordTrace(
            CoverArtTraceEvent.PassSkipped(0L, "another pass is already running"),
        )
        assertTrue(
            lines(art.trace).contains("pass skipped — another pass is already running"),
        )
    }

    @Test
    fun networkLabelPureForm() {
        assertEquals("Wi-Fi", coverArtNetworkLabel(true, true, false))
        assertEquals("metered Wi-Fi", coverArtNetworkLabel(true, false, false))
        assertEquals("mobile data", coverArtNetworkLabel(false, true, true))
        assertEquals("mobile data (not allowed)", coverArtNetworkLabel(false, true, false))
    }
}
