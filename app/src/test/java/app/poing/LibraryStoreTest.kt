package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = LibraryStore(temp.root)

    @Test
    fun addAndListRoundTrip() {
        val store = store()
        val game = store.addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
        assertEquals("TETRIS", game?.title)
        assertEquals(GameSystem.GB, game?.system)
        assertTrue(store.gameFile(game!!).exists())
        // A fresh store on the same root reads the same index.
        assertEquals(listOf(game), LibraryStore(temp.root).list())
    }

    @Test
    fun reimportReplacesInsteadOfDuplicating() {
        val store = store()
        val bytes = GameSystemTest.fakeGbHeader(0x00, "TETRIS")
        val first = store.addRom(bytes, "tetris.gb")!!
        val second = store.addRom(bytes, "tetris-copy.gb")!!
        assertEquals(first.id, second.id)
        assertEquals(1, store.list().size)
    }

    @Test
    fun gbcInGbFileStillPlaysAsGbc() {
        val store = store()
        val game = store.addRom(GameSystemTest.fakeGbHeader(0xC0, "POKEMON"), "poke.gb")
        assertEquals(GameSystem.GBC, game?.system)
    }

    @Test
    fun nonRomBytesAreRejected() {
        val store = store()
        assertNull(store.addRom("hello world".toByteArray(), "hello.txt"))
        assertNull(store.addRom(ByteArray(0x200), "blank.gb"))
        assertEquals(0, store.list().size)
    }

    @Test
    fun zipUnwrapsFirstRomEntry() {
        val store = store()
        val rom = GameSystemTest.fakeGbaHeader("GOLDEN SUN")
        val zip = zipBytes(
            mapOf("readme.txt" to "hi".toByteArray(), "golden.gba" to rom),
        )
        val game = store.addZip(zip, "games.zip")
        assertEquals("GOLDEN SUN", game?.title)
        assertEquals(GameSystem.GBA, game?.system)
    }

    @Test
    fun zipWithNoRomReturnsNull() {
        val store = store()
        val zip = zipBytes(mapOf("readme.txt" to "hi".toByteArray()))
        assertNull(store.addZip(zip, "empty.zip"))
    }

    @Test
    fun titleFallsBackToFileName() {
        val store = store()
        val game = store.addRom(GameSystemTest.fakeGbHeader(0x00, ""), "my-cool-game.gb")
        assertEquals("my-cool-game", game?.title)
    }

    @Test
    fun removeDeletesCopySavesAndCover() {
        val store = store()
        val game = store.addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")!!
        // Fake a battery save and a cached cover for this game.
        val saves = temp.root.resolve("saves").apply { mkdirs() }
        saves.resolve("${game.id}.sav").writeText("save")
        val covers = temp.root.resolve("covers").apply { mkdirs() }
        covers.resolve("${game.id}.png").writeText("png")
        store.remove(game)
        assertFalse(store.gameFile(game).exists())
        assertFalse(saves.resolve("${game.id}.sav").exists())
        assertFalse(covers.resolve("${game.id}.png").exists())
        assertEquals(0, store.list().size)
    }

    @Test
    fun malformedIndexLinesAreSkipped() {
        val store = store()
        store.addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
        temp.root.resolve("library.txt").appendText("garbage|line\n")
        assertEquals(1, store.list().size)
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
