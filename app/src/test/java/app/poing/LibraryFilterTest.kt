package app.poing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 5 — the shoulder filter (idea 5) is a pure function over
 * the library list, so its behaviour is pinned here rather than
 * only through the UI.
 */
class LibraryFilterTest {

    private fun game(title: String, system: GameSystem) = LibraryGame(
        id = title,
        title = title,
        system = system,
        fileName = "$title.${system.extensions.first()}",
        sourceName = "$title.${system.extensions.first()}",
        sha256 = "00",
        addedAt = 0L,
    )

    private val games = listOf(
        game("TETRIS", GameSystem.GB),
        game("POKEMON GOLD", GameSystem.GBC),
        game("GOLDEN SUN", GameSystem.GBA),
        game("MARIO LAND", GameSystem.GB),
    )

    @Test
    fun allShowsEveryGame() {
        assertEquals(games, filterGames(games, null))
    }

    @Test
    fun gbShowsOnlyGb() {
        assertEquals(
            listOf("TETRIS", "MARIO LAND"),
            filterGames(games, GameSystem.GB).map { it.title },
        )
    }

    @Test
    fun gbcShowsOnlyGbc() {
        assertEquals(
            listOf("POKEMON GOLD"),
            filterGames(games, GameSystem.GBC).map { it.title },
        )
    }

    @Test
    fun gbaShowsOnlyGba() {
        assertEquals(
            listOf("GOLDEN SUN"),
            filterGames(games, GameSystem.GBA).map { it.title },
        )
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals(emptyList<LibraryGame>(), filterGames(emptyList(), GameSystem.GB))
    }
}
