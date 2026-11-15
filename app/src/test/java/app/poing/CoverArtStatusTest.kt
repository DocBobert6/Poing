package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Cover-art rebuild (2026-11-15, alpha.6): the pure
 * functions the library reads — the derived status line,
 * the trace text, the rate-limit reason. Plain JVM: none of
 * these touch BitmapFactory, so no test here builds a
 * [CoverState.Ready] — the Ready counts are pinned in
 * [CoverRepositoryTest], under Robolectric, where a bitmap
 * can exist.
 *
 * The status line is a pure function of the covers map the
 * tiles read, so its counts cannot disagree with them — the
 * 2026-11-15 failure was two truths (the trace said
 * "landed", the tiles showed placeholders). There is one
 * map now, and the line is derived from it.
 */
class CoverArtStatusTest {
    private fun game(id: String, title: String, system: GameSystem) =
        LibraryGame(
            id = id,
            title = title,
            system = system,
            fileName = "$id.gb",
            sourceName = "$id.gb",
            sha256 = id,
            addedAt = 0L,
        )

    // -- status line ---------------------------------------------------------

    @Test
    fun statusLineFetchingShowsStageAndCounts() {
        val games = listOf(
            game("gb1", "TETRIS", GameSystem.GB),
            game("gb2", "DR MARIO", GameSystem.GB),
        )
        val covers = mapOf<String, CoverState>(
            "gb1" to CoverState.Missing,
            "gb2" to CoverState.Loading,
        )
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GB to IndexState.Ready(2, fromCache = false),
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        assertTrue(line.contains("fetching gb art…"))
        assertTrue(line.contains("0 landed"))
    }

    @Test
    fun statusLineIdleShowsCoversGateAndIndexStates() {
        val games = listOf(
            game("gb1", "TETRIS", GameSystem.GB),
            game("gb2", "DR MARIO", GameSystem.GB),
        )
        val covers = mapOf<String, CoverState>(
            "gb1" to CoverState.Missing,
            "gb2" to CoverState.NoMatch("no art in index"),
        )
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GB to IndexState.Ready(2, fromCache = true),
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        assertEquals(
            "Cover art: 0/2 covers · Wi-Fi OK · gb index cached (2)",
            line,
        )
    }

    @Test
    fun statusLineShowsMeteredGate() {
        val games = listOf(game("gb1", "TETRIS", GameSystem.GB))
        val line = coverArtStatusLine(
            emptyMap(), emptyMap(), CoverArtGate.METERED_NO_OPT_IN, games,
        )
        assertTrue(line.contains("metered Wi-Fi — mobile data off"))
    }

    @Test
    fun statusLineFirstFailureIsDeterministic() {
        // Two failures, at once: the index failure is named
        // first (GB before the games, in library order) — the
        // old line showed the LATEST failure, which changed as
        // failures arrived.
        val games = listOf(
            game("gb1", "TETRIS", GameSystem.GB),
            game("gba1", "TETRIS", GameSystem.GBA),
        )
        val covers = mapOf<String, CoverState>(
            "gb1" to CoverState.Failed("HTTP 404"),
            "gba1" to CoverState.Failed("index gba: timed out"),
        )
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GBA to IndexState.Failed("timed out"),
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        // The idle branch carries no failed count and no
        // "first failure:" label — the first failure is
        // appended bare after the index states.
        assertEquals(
            "Cover art: 0/2 covers · Wi-Fi OK · gba index failed (timed out) " +
                "· index gba: timed out",
            line,
        )
    }

    // -- trace text -----------------------------------------------------------

    @Test
    fun coverTraceLinesEmpty() {
        assertEquals(
            "No fetch pass has started since the library opened.",
            coverTraceLines(emptyList(), fetching = false),
        )
    }

    @Test
    fun coverTraceLinesFetchingHeader() {
        val trace = listOf(
            CoverTraceEvent.PipelineStarted(1_000L, GameSystem.GB),
        )
        val text = coverTraceLines(trace, fetching = true)
        assertTrue(text.startsWith("A fetch is running.\n"))
        assertTrue(text.contains("pipeline gb started"))
    }

    @Test
    fun coverTraceLinesLastHeader() {
        val trace = listOf(
            CoverTraceEvent.ArtReady(1_000L, game("gb1", "TETRIS", GameSystem.GB), 68L),
        )
        val text = coverTraceLines(trace, fetching = false)
        assertTrue(text.startsWith("Last fetch:\n"))
        assertTrue(text.contains("art ready: TETRIS"))
    }

    @Test
    fun traceEventLines() {
        val g = game("gb1", "TETRIS", GameSystem.GB)
        assertEquals("pipeline gb started", CoverTraceEvent.PipelineStarted(0L, GameSystem.GB).line())
        assertEquals(
            "pipeline gba parked: gate closed",
            CoverTraceEvent.PipelineParked(0L, GameSystem.GBA, "gate closed").line(),
        )
        assertEquals("index gb started", CoverTraceEvent.IndexStarted(0L, GameSystem.GB).line())
        assertEquals(
            "index gbc loaded (2 entries, cached)",
            CoverTraceEvent.IndexLoaded(0L, GameSystem.GBC, 2, fromCache = true).line(),
        )
        assertEquals(
            "index gba failed: timed out",
            CoverTraceEvent.IndexFailed(0L, GameSystem.GBA, "timed out").line(),
        )
        assertEquals(
            "art matched: TETRIS (Tetris (World).png)",
            CoverTraceEvent.ArtMatched(0L, g, "Tetris (World).png").line(),
        )
        assertEquals("art started: TETRIS", CoverTraceEvent.ArtStarted(0L, g).line())
        assertEquals(
            "art downloaded: TETRIS (68 bytes)",
            CoverTraceEvent.ArtDownloaded(0L, g, 68).line(),
        )
        assertEquals(
            "art decoded: TETRIS (1×1)",
            CoverTraceEvent.ArtDecoded(0L, g, 1, 1).line(),
        )
        assertEquals("art ready: TETRIS", CoverTraceEvent.ArtReady(0L, g, 68L).line())
        assertEquals(
            "art failed: TETRIS: HTTP 404",
            CoverTraceEvent.ArtFailed(0L, g, "HTTP 404").line(),
        )
        assertEquals(
            "art discarded: TETRIS: undecodable",
            CoverTraceEvent.ArtDiscarded(0L, g, "undecodable").line(),
        )
        assertEquals(
            "disk scan: 3 restored, 1 discarded",
            CoverTraceEvent.DiskScan(0L, restored = 3, discarded = 1).line(),
        )
    }

    // -- rate-limit reason -----------------------------------------------------

    @Test
    fun rateLimitDetailFormatsReset() {
        // formatTraceTime reads the default timezone, so pin
        // it to UTC for this assertion — the reset epoch is
        // 2026-11-15 15:32:00 UTC.
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            // formatTraceTime is HH:mm:ss, so the reset reads
            // 15:32:00, not 15:32.
            assertEquals(
                "HTTP 403 — rate limit — resets 15:32:00",
                rateLimitDetail(403, "0", "1794756720"),
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun rateLimitDetailNullWhenNotRateLimited() {
        assertNull(rateLimitDetail(403, "59", "1794756720"))
        assertNull(rateLimitDetail(403, null, "1794756720"))
        assertNull(rateLimitDetail(404, "0", "1794756720"))
        // A rate-limit 403 without a reset header still names
        // the rate limit — only the "resets …" suffix is absent.
        assertEquals("HTTP 403 — rate limit", rateLimitDetail(403, "0", null))
    }
}
