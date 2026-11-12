package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSystemTest {
    @Test
    fun extensionDetectsAllThree() {
        assertEquals(GameSystem.GB, GameSystem.fromExtension("game.gb"))
        assertEquals(GameSystem.GBC, GameSystem.fromExtension("game.GBC"))
        assertEquals(GameSystem.GBA, GameSystem.fromExtension("game.gba"))
        assertNull(GameSystem.fromExtension("game.zip"))
        assertNull(GameSystem.fromExtension("game"))
    }

    @Test
    fun headerDetectsGbAndGbc() {
        assertEquals(GameSystem.GB, GameSystem.fromHeader(fakeGbHeader(cgbFlag = 0x00)))
        assertEquals(GameSystem.GBC, GameSystem.fromHeader(fakeGbHeader(cgbFlag = 0x80)))
        assertEquals(GameSystem.GBC, GameSystem.fromHeader(fakeGbHeader(cgbFlag = 0xC0)))
    }

    @Test
    fun headerDetectsGba() {
        assertEquals(GameSystem.GBA, GameSystem.fromHeader(fakeGbaHeader("GOLDEN SUN")))
    }

    @Test
    fun headerRejectsNonRomBytes() {
        assertNull(GameSystem.fromHeader(ByteArray(0x200)))
        assertNull(GameSystem.fromHeader("not a game at all, just text".toByteArray()))
        // GBA logo prefix but the wrong fixed byte is not a GBA ROM.
        val almost = fakeGbaHeader("TEST")
        almost[0xB2] = 0x00
        assertNull(GameSystem.fromHeader(almost))
    }

    @Test
    fun internalTitleComesFromHeader() {
        assertEquals(
            "TETRIS",
            GameSystem.internalTitle(fakeGbHeader(cgbFlag = 0x00, title = "TETRIS"), GameSystem.GB),
        )
        assertEquals(
            "GOLDEN SUN",
            GameSystem.internalTitle(fakeGbaHeader("GOLDEN SUN"), GameSystem.GBA),
        )
    }

    @Test
    fun internalTitleFallsBackWhenBlank() {
        assertNull(
            GameSystem.internalTitle(fakeGbHeader(cgbFlag = 0x00, title = ""), GameSystem.GB),
        )
    }

    companion object {
        /** 48-byte Nintendo logo, verified against the tree's own test ROMs (cinema/gb/acid). */
        private const val GB_LOGO_HEX =
            "ceed6666cc0d000b03730083000c000d0008111f8889000edccc6ee6ddddd999bbbb67636e0eecccdddc999fbbb9333e"

        fun fakeGbHeader(cgbFlag: Int, title: String = "TEST GAME"): ByteArray {
            val bytes = ByteArray(0x8000)
            val logo = GB_LOGO_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            logo.copyInto(bytes, 0x104)
            val titleBytes = title.toByteArray(Charsets.US_ASCII)
            titleBytes.copyInto(bytes, 0x134, 0, minOf(titleBytes.size, 10))
            bytes[0x143] = cgbFlag.toByte()
            return bytes
        }

        fun fakeGbaHeader(title: String): ByteArray {
            val bytes = ByteArray(0xC0)
            bytes[0x04] = 0x24
            bytes[0x05] = 0xFF.toByte()
            bytes[0x06] = 0xAE.toByte()
            bytes[0x07] = 0x51
            bytes[0xB2] = 0x96.toByte()
            val titleBytes = title.toByteArray(Charsets.US_ASCII)
            titleBytes.copyInto(bytes, 0xA0, 0, minOf(titleBytes.size, 12))
            return bytes
        }
    }
}
