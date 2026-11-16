package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Cover-art rebuild (2026-11-15, alpha.6): the pure
 * functions the library reads — the derived status line and
 * the rate-limit reason. Plain JVM: none of
 * these touch BitmapFactory, so no test here builds a
 * [CoverState.Ready] — the Ready counts are pinned in
 * [CoverRepositoryTest], under Robolectric, where a bitmap
 * can exist.
 *
 * The status line is a pure function of the covers map the
 * tiles read, so its counts cannot disagree with them — the
 * 2026-11-15 failure was two truths (the status said
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
        // Alpha.21 diagnostic: the fetching line names the
        // game, not just its system — "fetching gb art…"
        // does not say WHICH game the pass is fetching for.
        assertTrue(line.contains("fetching art for DR MARIO…"))
        assertTrue(line.contains("0 landed"))
    }

    @Test
    fun statusLineFetchingNamesSeveralLoadingGames() {
        // Two pipelines at once (one per system): both games
        // are named, in library order — the old line named
        // only the first game's system ("fetching gb art…").
        val games = listOf(
            game("gb1", "TETRIS", GameSystem.GB),
            game("gba1", "KIRBY", GameSystem.GBA),
            game("gb2", "DR MARIO", GameSystem.GB),
        )
        val covers = mapOf<String, CoverState>(
            "gb1" to CoverState.Loading,
            "gba1" to CoverState.Loading,
            "gb2" to CoverState.NoMatch("no art in index"),
        )
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GB to IndexState.Ready(2, fromCache = true),
            GameSystem.GBA to IndexState.Ready(1, fromCache = true),
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        assertTrue(line.contains("fetching art for TETRIS, KIRBY…"))
    }

    @Test
    fun statusLineFetchingWithIndexOnlyKeepsSystemName() {
        // An index-only load has no game to name: the line
        // keeps the system name, as before.
        val games = listOf(game("gb1", "TETRIS", GameSystem.GB))
        val covers = mapOf<String, CoverState>(
            "gb1" to CoverState.Missing,
        )
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GB to IndexState.Loading,
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        assertTrue(line.contains("fetching gb index…"))
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
        // Alpha.21 diagnostic: the idle line names the game
        // still Missing after the pass — gb1 never reached a
        // terminal state, so the next open will fetch it again.
        assertEquals(
            "Cover art: 0/2 covers · Wi-Fi OK · gb index cached (2) · still missing: TETRIS",
            line,
        )
    }

    @Test
    fun statusLineIdleNamesStillMissingGames() {
        // No fetch running, no failure — but a game is still
        // Missing after the pass, so the idle line names it:
        // without this the line would say nothing about the
        // game the next open will fetch again.
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
        assertTrue(line.contains("still missing: TETRIS"))
    }

    @Test
    fun statusLineMissingNamesAreCapped() {
        // Five Missing games: the line names the first three
        // and counts the rest — it must not become a paragraph.
        val games = (1..5).map { game("gb$it", "GAME$it", GameSystem.GB) }
        val covers = games.associate { it.id to CoverState.Missing }
        val indexes = mapOf<GameSystem, IndexState>(
            GameSystem.GB to IndexState.Ready(5, fromCache = true),
        )
        val line = coverArtStatusLine(covers, indexes, CoverArtGate.OPEN, games)
        assertTrue(line.contains("still missing: GAME1, GAME2, GAME3 and 2 more"))
        assertTrue(!line.contains("GAME4"))
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
