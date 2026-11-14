package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The libretro-thumbnails matcher, measured against a captured
 * sample of the real indexes (filenames as they stand in
 * libretro-thumbnails on 2026-11-14; the full 50-game measurement
 * against the complete indexes is in
 * ~/workspace/poing/verify/library/MEASUREMENTS.md — 50/50 the
 * correct game). Every rule that earned that number is pinned
 * here: the ", The" convention, the "$" quirk, ranked (never
 * first-alphabetical) matches, the >= 2-word prefix rule, and the
 * mid-word bare-prefix rule.
 */
class ThumbnailsTest {

    /** A captured slice of the real Named_Boxarts indexes. */
    private val sampleIndex = Thumbnails.normalizeIndex(
        listOf(
            "Pokemon - Red Version (USA, Europe) (SGB Enhanced).png",
            "Pokemon - Blue Version (USA, Europe) (SGB Enhanced).png",
            "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).png",
            "Legend of Zelda, The - Link's Awakening (USA, Europe).png",
            "Legend of Zelda, The - Link's Awakening (USA, Europe) (Rev 1).png",
            "Zelda no Densetsu - Yume o Miru Shima (Japan).png",
            "Super Mario Land 2 - 6 Golden Coins (USA, Europe).png",
            "Tetris (Japan) (En).png",
            "Tetris (World) (Rev 1).png",
            "Tetris (World).png",
            "Dr. Mario (World) (Beta).png",
            "Dr. Mario (World) (Rev 1).png",
            "Dr. Mario (World).png",
            "Final Fantasy Legend, The (USA).png",
            "Castlevania II - Belmont's Revenge (USA, Europe).png",
            "Castlevania II - Belmont's Revenge (USA, Europe) (Castlevania Anniversary Collection).png",
            "Legend of Zelda, The - Oracle of Ages (Europe) (En,Fr,De,Es,It).png",
            "Legend of Zelda, The - Oracle of Ages (USA, Australia).png",
            "Harvest Moon 2 GBC (USA) (SGB Enhanced) (GB Compatible).png",
            "Harvest Moon 3 GBC (USA).png",
            "Harvest Moon GB (Europe) (SGB Enhanced) (GB Compatible).png",
            "Harvest Moon GBC (USA) (SGB Enhanced) (GB Compatible).png",
            "Donkey Kong Country (USA, Europe) (En,Fr,De,Es,It) (Sample).png",
            "Donkey Kong Country (USA, Europe) (En,Fr,De,Es,It).png",
            "Golden Sun (France).png",
            "Golden Sun (USA, Europe).png",
            "Metroid Fusion (USA).png",
            "Metroid Fusion (USA) (Virtual Console).png",
            "Metroid Fusion Special Edition (USA).png",
            "Fire Emblem (USA, Australia).png",
            "Pokemon - Emerald Version (USA, Europe).png",
            "Kirby _ The Amazing Mirror (USA).png",
            "Kirby _ The Amazing Mirror (Europe) (En,Fr,De,Es,It) (Virtual Console).png",
            "Advance Wars (USA).png",
            "Advance Wars (USA) (Virtual Console).png",
            "WarioWare, Inc. - Mega Microgame$! (USA).png",
            "WarioWare, Inc. - Mega Microgame$! (USA) (Virtual Console).png",
            "WarioWare - Twisted! (USA).png",
            "V-Rally 3 (Japan).png",
            "V-Rally 3 (Europe) (En,Fr,De,Es,It).png",
            "V-Rally 3 (USA) (En,Fr,Es).png",
            "Mario _ Luigi - Superstar Saga (USA).png",
            "Legend of Zelda, The - A Link to the Past _ Four Swords (USA).png",
            "Legend of Zelda, The - The Minish Cap (USA).png",
            "Metal Gear Solid (2000)(Konami)(EU)(M5).png",
            "Metal Gear Solid (USA).png",
            "Shantae (USA).png",
        ),
    )

    @Test
    fun normalizeStripsParensAndRegionTags() {
        assertEquals(
            "pokemon red version",
            Thumbnails.normalizeTitle("Pokemon - Red Version (USA, Europe)"),
        )
        assertEquals(
            "tetris",
            Thumbnails.normalizeTitle("Tetris (World) (Rev 1)"),
        )
    }

    @Test
    fun normalizeDropsCommaThe() {
        assertEquals(
            "legend of zelda oracle of ages",
            Thumbnails.normalizeTitle("Legend of Zelda, The - Oracle of Ages (USA, Europe)"),
        )
        assertEquals(
            "final fantasy legend",
            Thumbnails.normalizeTitle("Final Fantasy Legend, The (USA)"),
        )
    }

    @Test
    fun normalizeReadsDollarAsS() {
        assertEquals(
            "warioware inc mega microgames",
            Thumbnails.normalizeTitle("WarioWare, Inc. - Mega Microgame$! (USA)"),
        )
    }

    @Test
    fun normalizeKeepsTheInsideATitle() {
        assertEquals(
            "legend of zelda the minish cap",
            Thumbnails.normalizeTitle("The Legend of Zelda - The Minish Cap (USA, Europe)"),
        )
    }

    @Test
    fun exactMatchPrefersThePlainRelease() {
        // The plain file beats Rev 1, and the Beta loses to it.
        assertEquals(
            "Legend of Zelda, The - Link's Awakening (USA, Europe).png",
            Thumbnails.matchArtFile(
                "The Legend of Zelda - Link's Awakening (USA, Europe) (Rev 1)",
                sampleIndex,
            ),
        )
        assertEquals(
            "Dr. Mario (World).png",
            Thumbnails.matchArtFile("Dr. Mario (World) (Rev 1)", sampleIndex),
        )
        assertEquals(
            "Tetris (World).png",
            Thumbnails.matchArtFile("Tetris (World)", sampleIndex),
        )
    }

    @Test
    fun exactMatchPrefersUsaOverAnotherRegion() {
        assertEquals(
            "Golden Sun (USA, Europe).png",
            Thumbnails.matchArtFile("Golden Sun (USA, Europe)", sampleIndex),
        )
        assertEquals(
            "V-Rally 3 (USA) (En,Fr,Es).png",
            Thumbnails.matchArtFile("V-Rally 3 (USA, Europe)", sampleIndex),
        )
    }

    @Test
    fun exactMatchSkipsReReleaseVariants() {
        // The plain box beats the Virtual Console re-release and
        // the Anniversary Collection repack.
        assertEquals(
            "Metroid Fusion (USA).png",
            Thumbnails.matchArtFile("Metroid Fusion (USA, Europe)", sampleIndex),
        )
        assertEquals(
            "Castlevania II - Belmont's Revenge (USA, Europe).png",
            Thumbnails.matchArtFile(
                "Castlevania II - Belmont's Revenge (USA, Europe)",
                sampleIndex,
            ),
        )
        assertEquals(
            "Donkey Kong Country (USA, Europe) (En,Fr,De,Es,It).png",
            Thumbnails.matchArtFile("Donkey Kong Country (USA, Europe)", sampleIndex),
        )
    }

    @Test
    fun commaTheAndUnderscoreTitlesMatch() {
        assertEquals(
            "Legend of Zelda, The - Oracle of Ages (USA, Australia).png",
            Thumbnails.matchArtFile(
                "The Legend of Zelda - Oracle of Ages (USA, Europe)",
                sampleIndex,
            ),
        )
        assertEquals(
            "Kirby _ The Amazing Mirror (USA).png",
            Thumbnails.matchArtFile("Kirby & the Amazing Mirror (USA, Europe)", sampleIndex),
        )
        assertEquals(
            "Mario _ Luigi - Superstar Saga (USA).png",
            Thumbnails.matchArtFile("Mario & Luigi - Superstar Saga (USA, Europe)", sampleIndex),
        )
        assertEquals(
            "Legend of Zelda, The - A Link to the Past _ Four Swords (USA).png",
            Thumbnails.matchArtFile(
                "The Legend of Zelda - A Link to the Past & Four Swords (USA, Europe)",
                sampleIndex,
            ),
        )
    }

    @Test
    fun dollarTitleMatches() {
        assertEquals(
            "WarioWare, Inc. - Mega Microgame$! (USA).png",
            Thumbnails.matchArtFile(
                "WarioWare, Inc. - Mega Microgames! (USA, Europe)",
                sampleIndex,
            ),
        )
    }

    @Test
    fun prefixMatchFindsTheGameAheadOfItsSequel() {
        // "Harvest Moon GB" is the game; "Harvest Moon 3 GBC" is a
        // sequel with fewer parenthetical groups in its filename,
        // so the title length — not the group count — must decide.
        assertEquals(
            "Harvest Moon GB (Europe) (SGB Enhanced) (GB Compatible).png",
            Thumbnails.matchArtFile("Harvest Moon (USA, Europe)", sampleIndex),
        )
    }

    @Test
    fun truncatedHeaderTitleMatchesByMidWordPrefix() {
        assertEquals(
            "Pokemon - Emerald Version (USA, Europe).png",
            Thumbnails.matchArtFile("POKEMON EMER", sampleIndex),
        )
    }

    @Test
    fun bareSeriesHeaderNeverMatchesAnotherGame() {
        // "WARIOWARE" is a series name, not a game: it must not
        // land on "WarioWare - Twisted!".
        assertNull(Thumbnails.matchArtFile("WARIOWARE", sampleIndex))
        // A bare "ZELDA" must not land on the Japanese
        // Link's Awakening box.
        assertNull(Thumbnails.matchArtFile("ZELDA", sampleIndex))
    }

    @Test
    fun noMatchReturnsNull() {
        assertNull(Thumbnails.matchArtFile("A Game That Does Not Exist", sampleIndex))
        assertNull(Thumbnails.matchArtFile("", sampleIndex))
    }

    @Test
    fun resolveTriesTheFilenameStemFirst() {
        assertEquals(
            "Pokemon - Red Version (USA, Europe) (SGB Enhanced).png",
            Thumbnails.resolveArtFile(
                "Pokemon - Red Version (USA, Europe)",
                "POKEMON RED",
                sampleIndex,
            ),
        )
    }

    @Test
    fun resolveFallsBackToTheHeaderTitle() {
        assertEquals(
            "Tetris (World).png",
            Thumbnails.resolveArtFile(null, "TETRIS", sampleIndex),
        )
        assertNull(Thumbnails.resolveArtFile(null, null, sampleIndex))
    }

    @Test
    fun parseTreeJsonKeepsOnlyBoxarts() {
        val json = """{"tree":[
            {"path":"Named_Boxarts/Tetris (World).png","type":"blob"},
            {"path":"Named_Snaps/Tetris (World).png","type":"blob"},
            {"path":"Named_Boxarts/readme.txt","type":"blob"},
            {"path":"Named_Boxarts","type":"tree"}
        ]}"""
        assertEquals(
            listOf("Tetris (World).png"),
            Thumbnails.parseTreeJson(json),
        )
    }

    @Test
    fun artUrlEncodesTheFilename() {
        val url = Thumbnails.artUrl(
            GameSystem.GBA,
            "Kirby _ The Amazing Mirror (USA).png",
        )
        assertTrue(url.startsWith("https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Game_Boy_Advance/master/Named_Boxarts/"))
        assertTrue(url.endsWith("Kirby%20_%20The%20Amazing%20Mirror%20%28USA%29.png"))
    }

    @Test
    fun reposArePerSystem() {
        assertEquals("Nintendo_-_Game_Boy", Thumbnails.repoFor(GameSystem.GB))
        assertEquals("Nintendo_-_Game_Boy_Color", Thumbnails.repoFor(GameSystem.GBC))
        assertEquals("Nintendo_-_Game_Boy_Advance", Thumbnails.repoFor(GameSystem.GBA))
        assertTrue(
            Thumbnails.indexUrl(GameSystem.GBA).contains("Nintendo_-_Game_Boy_Advance"),
        )
    }
}
