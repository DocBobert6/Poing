package app.poing

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Overlay rework (Robert, 2026-11-13) — ControlLayoutStore:
 * per-game .properties files with one fraction triple per
 * control plus opacity, and the one-time migration from the
 * old Phase 6 dp-offset format.
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
    fun `save and load round-trip every control and the opacity`() {
        val layout = ControlLayout.DEFAULT
            .moved(ControlId.A, 0.9f, 0.4f, grouped = false)
            .resized(ControlId.DPAD, 120, grouped = false)
            .withOpacity(75)
        store.save("abc123", layout)
        assertEquals(layout, store.load("abc123"))
    }

    @Test
    fun `games do not share layout files`() {
        store.save(
            "abc123",
            ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false),
        )
        assertEquals(ControlLayout.DEFAULT, store.load("def456"))
    }

    @Test
    fun `saving twice overwrites`() {
        store.save(
            "abc123",
            ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false),
        )
        store.save("abc123", ControlLayout.DEFAULT)
        assertEquals(ControlLayout.DEFAULT, store.load("abc123"))
    }

    @Test
    fun `corrupt values fall back per field`() {
        store.file("abc123").writeText(
            "dpad.cx=banana\ndpad.cy=0.25\ndpad.size=banana\na.cx=NaN\nopacity=banana\n",
        )
        val loaded = store.load("abc123")
        val dpadDef = ControlLayout.DEFAULT.control(ControlId.DPAD)
        assertEquals(dpadDef.cx, loaded.control(ControlId.DPAD).cx, 0f)
        assertEquals(0.25f, loaded.control(ControlId.DPAD).cy, 0f)
        assertEquals(dpadDef.sizeDp, loaded.control(ControlId.DPAD).sizeDp)
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.A).cx,
            loaded.control(ControlId.A).cx,
            0f,
        )
        assertEquals(100, loaded.opacity)
    }

    @Test
    fun `out-of-range values are clamped on load`() {
        store.file("abc123").writeText("dpad.cx=9\ndpad.cy=-9\ndpad.size=999\nopacity=999\n")
        val loaded = store.load("abc123")
        assertEquals(0.97f, loaded.control(ControlId.DPAD).cx, 0f)
        assertEquals(0.03f, loaded.control(ControlId.DPAD).cy, 0f)
        assertEquals(220, loaded.control(ControlId.DPAD).sizeDp)
        assertEquals(100, loaded.opacity)
    }

    @Test
    fun `a missing file after a save is the defaults again`() {
        store.save(
            "abc123",
            ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false),
        )
        assertTrue(store.file("abc123").delete())
        assertFalse(store.file("abc123").exists())
        assertEquals(ControlLayout.DEFAULT, store.load("abc123"))
    }

    @Test
    fun `an old dp-offset file migrates once and keeps the tuned spots`() {
        // Old Phase 6 format: one dp-offset pair per group.
        store.file("abc123").writeText(
            "dpad.x=50\ndpad.y=-40\nab.x=-30\nab.y=10\nlr.x=0\nlr.y=0\nstartselect.x=20\nstartselect.y=-8\n",
        )
        val loaded = store.load("abc123")
        val def = ControlLayout.DEFAULT
        // Each group's offset becomes a fraction delta from its
        // default spot on the reference canvas.
        assertEquals(
            def.control(ControlId.DPAD).cx + 50f / 923f,
            loaded.control(ControlId.DPAD).cx,
            0.001f,
        )
        assertEquals(
            def.control(ControlId.DPAD).cy - 40f / 411f,
            loaded.control(ControlId.DPAD).cy,
            0.001f,
        )
        // Both members of a pair carry the group's offset.
        assertEquals(
            def.control(ControlId.A).cx - 30f / 923f,
            loaded.control(ControlId.A).cx,
            0.001f,
        )
        assertEquals(
            def.control(ControlId.B).cx - 30f / 923f,
            loaded.control(ControlId.B).cx,
            0.001f,
        )
        assertEquals(
            def.control(ControlId.B).cy + 10f / 411f,
            loaded.control(ControlId.B).cy,
            0.001f,
        )
        assertEquals(
            def.control(ControlId.START).cx + 20f / 923f,
            loaded.control(ControlId.START).cx,
            0.001f,
        )
        // Sizes and opacity are the defaults — the old format
        // had neither.
        assertEquals(144, loaded.control(ControlId.DPAD).sizeDp)
        assertEquals(100, loaded.opacity)
        // The file is rewritten in the new format, so a second
        // load reads the same layout back without migrating again.
        val text = store.file("abc123").readText()
        assertTrue(text.contains("dpad.cx="))
        assertTrue(text.contains("a.cx="))
        assertFalse(text.contains("dpad.x="))
        assertEquals(loaded, store.load("abc123"))
    }

    @Test
    fun `migration of one group leaves the others at their defaults`() {
        // Left, not right: A sits at 0.948 already, so a +0.1
        // offset would clamp at 0.97 and the test would pin the
        // clamp, not the migration.
        store.file("abc123").writeText("ab.x=-92.3\nab.y=0\n")
        val loaded = store.load("abc123")
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.A).cx - 0.1f,
            loaded.control(ControlId.A).cx,
            0.001f,
        )
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.DPAD),
            loaded.control(ControlId.DPAD),
        )
    }

    // Overlay defaults (Robert, 2026-11-14): one adjustable
    // default layout per handheld, with the resolution order
    // per-game override -> handheld default -> factory
    // default. The three customs below are distinct in
    // position, size and opacity, so a leak between systems
    // or games is visible.
    private val gbCustom = ControlLayout.DEFAULT
        .moved(ControlId.A, 0.2f, 0.3f, grouped = false)
        .withOpacity(80)
    private val gbcCustom = ControlLayout.DEFAULT
        .resized(ControlId.DPAD, 120, grouped = false)
        .withOpacity(70)
    private val gbaCustom = ControlLayout.DEFAULT
        .moved(ControlId.A, 0.2f, 0.3f, grouped = false)
        .resized(ControlId.L, 120, grouped = false)
        .withOpacity(60)

    @Test
    fun `handheld default applies to a game with no override`() {
        assertEquals(ControlLayout.DEFAULT, store.loadForGame("game1", GameSystem.GB))
        store.saveDefault(GameSystem.GB, gbCustom)
        assertEquals(gbCustom, store.loadForGame("game1", GameSystem.GB))
    }

    @Test
    fun `handheld defaults do not leak between systems`() {
        store.saveDefault(GameSystem.GB, gbCustom)
        assertEquals(ControlLayout.DEFAULT, store.loadDefault(GameSystem.GBC))
        assertEquals(ControlLayout.DEFAULT, store.loadDefault(GameSystem.GBA))
        assertEquals(ControlLayout.DEFAULT, store.loadForGame("game1", GameSystem.GBC))
        assertEquals(ControlLayout.DEFAULT, store.loadForGame("game1", GameSystem.GBA))
        assertEquals(gbCustom, store.loadForGame("game1", GameSystem.GB))
    }

    @Test
    fun `per-game override wins over the handheld default`() {
        store.saveDefault(GameSystem.GB, gbCustom)
        store.save("game1", gbcCustom)
        assertEquals(gbcCustom, store.loadForGame("game1", GameSystem.GB))
    }

    @Test
    fun `overrides do not leak between games`() {
        store.saveDefault(GameSystem.GB, gbCustom)
        store.save("game1", gbaCustom)
        assertEquals(gbaCustom, store.loadForGame("game1", GameSystem.GB))
        assertEquals(gbCustom, store.loadForGame("game2", GameSystem.GB))
    }

    @Test
    fun `clearing the override restores the handheld default`() {
        store.saveDefault(GameSystem.GB, gbCustom)
        store.save("game1", gbaCustom)
        assertTrue(store.hasOverride("game1"))
        store.clearOverride("game1")
        assertFalse(store.hasOverride("game1"))
        assertEquals(gbCustom, store.loadForGame("game1", GameSystem.GB))
    }

    @Test
    fun `defaults survive a new store instance`() {
        // A restart is a new store over the same directory:
        // the saved default must still be there.
        store.saveDefault(GameSystem.GBC, gbcCustom)
        val reopened = ControlLayoutStore(dir)
        assertEquals(gbcCustom, reopened.loadDefault(GameSystem.GBC))
        assertEquals(gbcCustom, reopened.loadForGame("game1", GameSystem.GBC))
        assertEquals(ControlLayout.DEFAULT, reopened.loadDefault(GameSystem.GB))
    }

    @Test
    fun `clearing a handheld default restores the factory default`() {
        store.saveDefault(GameSystem.GBA, gbaCustom)
        assertTrue(store.hasDefault(GameSystem.GBA))
        store.clearDefault(GameSystem.GBA)
        assertFalse(store.hasDefault(GameSystem.GBA))
        assertEquals(ControlLayout.DEFAULT, store.loadDefault(GameSystem.GBA))
        assertEquals(ControlLayout.DEFAULT, store.loadForGame("game1", GameSystem.GBA))
    }
}
