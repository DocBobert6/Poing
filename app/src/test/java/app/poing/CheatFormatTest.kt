package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 (item 3) — CheatFormat: the per-system code formats
 * the core accepts, and their normalization.
 */
class CheatFormatTest {
    @Test
    fun `gba gameshark with space is valid`() {
        assertTrue(CheatFormat.isValid("01234567 89ABCDEF", GameSystem.GBA))
    }

    @Test
    fun `gba gameshark with plus normalizes to a space`() {
        assertEquals("01234567 89ABCDEF", CheatFormat.normalize("01234567+89abcdef", GameSystem.GBA))
    }

    @Test
    fun `gba codebreaker 8 plus 4 is valid`() {
        assertTrue(CheatFormat.isValid("01234567 89AB", GameSystem.GBA))
    }

    @Test
    fun `gba codebreaker 8 plus 8 is gameshark not codebreaker but still valid`() {
        assertTrue(CheatFormat.isValid("01234567 89ABCDEF", GameSystem.GBA))
    }

    @Test
    fun `gba rejects game genie dashes`() {
        assertFalse(CheatFormat.isValid("012-345-678", GameSystem.GBA))
    }

    @Test
    fun `gba rejects short and long codes`() {
        assertFalse(CheatFormat.isValid("0123456 89ABCDEF", GameSystem.GBA))
        assertFalse(CheatFormat.isValid("012345678 89ABCDEF", GameSystem.GBA))
        assertFalse(CheatFormat.isValid("01234567 89ABCDE", GameSystem.GBA))
    }

    @Test
    fun `gba rejects non-hex letters`() {
        assertFalse(CheatFormat.isValid("01234567 89ABCDEG", GameSystem.GBA))
    }

    @Test
    fun `gb game genie 6 and 9 digits are valid`() {
        assertTrue(CheatFormat.isValid("012-345", GameSystem.GB))
        assertTrue(CheatFormat.isValid("012-345-678", GameSystem.GB))
        assertTrue(CheatFormat.isValid("012-345-678", GameSystem.GBC))
    }

    @Test
    fun `gb gameshark 8 hex is valid`() {
        assertTrue(CheatFormat.isValid("01234567", GameSystem.GB))
    }

    @Test
    fun `gb vba colon code is valid`() {
        assertTrue(CheatFormat.isValid("0123:AB", GameSystem.GB))
        assertTrue(CheatFormat.isValid("0123:AB", GameSystem.GBC))
    }

    @Test
    fun `gb codebreaker is rejected because the converter truncates it`() {
        // "0123-4567-89" is 12 chars; the libretro converter splits
        // GB codes into 11-char lines, so this would be corrupted.
        assertNull(CheatFormat.normalize("0123-4567-89", GameSystem.GB))
        assertFalse(CheatFormat.isValid("0123-4567-89", GameSystem.GBC))
    }

    @Test
    fun `gb rejects a gba-style spaced code`() {
        assertFalse(CheatFormat.isValid("01234567 89ABCDEF", GameSystem.GB))
    }

    @Test
    fun `normalize uppercases and trims`() {
        assertEquals("012-345-678", CheatFormat.normalize("  012-345-678  ", GameSystem.GB))
    }

    @Test
    fun `hint names the formats for the system`() {
        assertTrue(CheatFormat.hint(GameSystem.GBA).contains("GameShark"))
        assertTrue(CheatFormat.hint(GameSystem.GB).contains("Game Genie"))
    }
}
