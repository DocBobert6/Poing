package app.poing

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 (item 3) — CheatStore: per-game cheat files, one line
 * per cheat, "1|" enabled / "0|" disabled.
 */
class CheatStoreTest {
    private lateinit var dir: java.io.File
    private lateinit var store: CheatStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cheats").toFile()
        store = CheatStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `unknown game has no cheats`() {
        assertTrue(store.list("abc123").isEmpty())
    }

    @Test
    fun `save and list round-trip with enabled flags`() {
        store.save("abc123", listOf(Cheat("01234567 89ABCDEF", true), Cheat("012-345-678", false)))
        assertEquals(
            listOf(Cheat("01234567 89ABCDEF", true), Cheat("012-345-678", false)),
            store.list("abc123"),
        )
    }

    @Test
    fun `saving an empty list deletes the file`() {
        store.save("abc123", listOf(Cheat("01234567", true)))
        assertTrue(store.file("abc123").exists())
        store.save("abc123", emptyList())
        assertFalse(store.file("abc123").exists())
        assertTrue(store.list("abc123").isEmpty())
    }

    @Test
    fun `games do not share cheat files`() {
        store.save("abc123", listOf(Cheat("01234567", true)))
        assertTrue(store.list("def456").isEmpty())
    }

    @Test
    fun `corrupt lines are skipped not fatal`() {
        store.file("abc123").writeText("1|01234567\nnot-a-cheat\n2|01234567\n|01234567\n0|\n")
        assertEquals(listOf(Cheat("01234567", true)), store.list("abc123"))
    }
}
