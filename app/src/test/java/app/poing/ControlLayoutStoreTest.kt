package app.poing

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 (item 4) — ControlLayoutStore: per-game .properties
 * files with one x/y pair per control group.
 */
class ControlLayoutStoreTest {
    private lateinit var dir: java.io.File
    private lateinit var store: ControlLayoutStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("layouts").toFile()
        store = ControlLayoutStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `unknown game loads the defaults`() {
        assertEquals(ControlLayout.DEFAULT, store.load("abc123"))
    }

    @Test
    fun `save and load round-trip all four groups`() {
        val layout = ControlLayout(
            dpad = ControlPosition(10f, -20f),
            startSelect = ControlPosition(-5f, 3f),
            ab = ControlPosition(40f, -10f),
            lr = ControlPosition(0f, 12f),
        )
        store.save("abc123", layout)
        assertEquals(layout, store.load("abc123"))
    }

    @Test
    fun `games do not share layout files`() {
        store.save("abc123", ControlLayout(dpad = ControlPosition(10f, 0f)))
        assertEquals(ControlLayout.DEFAULT, store.load("def456"))
    }

    @Test
    fun `saving twice overwrites`() {
        store.save("abc123", ControlLayout(dpad = ControlPosition(10f, 0f)))
        store.save("abc123", ControlLayout.DEFAULT)
        assertEquals(ControlLayout.DEFAULT, store.load("abc123"))
    }

    @Test
    fun `corrupt values fall back per axis`() {
        store.file("abc123").writeText("dpad.x=banana\ndpad.y=-20\nab.x=NaN\nab.y=Infinity\n")
        val loaded = store.load("abc123")
        assertEquals(ControlPosition(0f, -20f), loaded.dpad)
        assertEquals(ControlPosition.ZERO, loaded.ab)
        assertEquals(ControlPosition.ZERO, loaded.lr)
    }

    @Test
    fun `out-of-range values are clamped on load`() {
        store.file("abc123").writeText("dpad.x=999\ndpad.y=999\n")
        assertEquals(ControlPosition(160f, 12f), store.load("abc123").dpad)
    }

    @Test
    fun `a missing file after a save is the defaults again`() {
        store.save("abc123", ControlLayout(dpad = ControlPosition(10f, 0f)))
        assertTrue(store.file("abc123").delete())
        assertFalse(store.file("abc123").exists())
        assertEquals(ControlLayout.DEFAULT, store.load("abc123"))
    }
}
