package app.poing

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Alpha.33 — Achievement Center build 2: the HUD pin store.
 * One pinned achievement id per game, in one small file per
 * game (`<base>.pin`); a missing, unreadable or non-numeric
 * file means no pin.
 */
class RaPinStoreTest {
    private lateinit var dir: java.io.File
    private lateinit var store: RaPinStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ra-pins").toFile()
        store = RaPinStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `missing file means no pin`() {
        assertNull(store.pinnedId("abc123"))
    }

    @Test
    fun `set and read round-trip`() {
        store.setPinnedId("abc123", 7)
        assertEquals(7, store.pinnedId("abc123"))
    }

    @Test
    fun `clearing the pin deletes the file`() {
        store.setPinnedId("abc123", 7)
        store.setPinnedId("abc123", null)
        assertNull(store.pinnedId("abc123"))
        assertEquals(false, store.file("abc123").exists())
    }

    @Test
    fun `pinning a second achievement replaces the first`() {
        store.setPinnedId("abc123", 7)
        store.setPinnedId("abc123", 9)
        assertEquals(9, store.pinnedId("abc123"))
    }

    @Test
    fun `games are independent`() {
        store.setPinnedId("game-a", 7)
        assertNull(store.pinnedId("game-b"))
    }

    @Test
    fun `a non-numeric file means no pin`() {
        store.file("abc123").apply {
            parentFile?.mkdirs()
            writeText("not-a-number")
        }
        assertNull(store.pinnedId("abc123"))
    }
}
