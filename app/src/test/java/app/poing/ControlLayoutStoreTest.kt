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
    fun `a saved layout without ff keys loads the trio at their defaults`() {
        // Alpha.10 (Part D) — migration: an existing install's
        // saved layout has no ff/slow/rw keys, so the trio
        // loads at the defaults — the fixed Row's spot, bottom-
        // right — and the buttons stay exactly where they were.
        store.file("abc123").writeText("dpad.cx=0.5\ndpad.cy=0.5\n")
        val loaded = store.load("abc123")
        assertEquals(ControlLayout.DEFAULT.control(ControlId.FF), loaded.control(ControlId.FF))
        assertEquals(ControlLayout.DEFAULT.control(ControlId.SLOW), loaded.control(ControlId.SLOW))
        assertEquals(ControlLayout.DEFAULT.control(ControlId.RW), loaded.control(ControlId.RW))
    }

    @Test
    fun `ff moved to the centre round-trips and the per-game override wins`() {
        val centred = ControlLayout.DEFAULT.moved(ControlId.FF, 0.5f, 0.5f, grouped = false)
        store.save("abc123", centred)
        store.saveDefault(
            GameSystem.GB,
            ControlLayout.DEFAULT.moved(ControlId.FF, 0.1f, 0.1f, grouped = false),
        )
        val loaded = store.loadForGame("abc123", GameSystem.GB)
        assertEquals(0.5f, loaded.control(ControlId.FF).cx, 0f)
        assertEquals(0.5f, loaded.control(ControlId.FF).cy, 0f)
        // And a game with no override gets the handheld default.
        val other = store.loadForGame("def456", GameSystem.GB)
        assertEquals(0.1f, other.control(ControlId.FF).cx, 0f)
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

    // Alpha.10 — the per-game left-control choice, in its own
    // small file beside the layout file: the layout's
    // save/clear rules must never wipe it, or be wiped by it.
    @Test
    fun `left-control override is null by default`() {
        assertEquals(null, store.leftControlOverride("abc123"))
    }

    @Test
    fun `left-control override round-trips and clears`() {
        store.setLeftControlOverride("abc123", LeftControl.STICK)
        assertEquals(LeftControl.STICK, store.leftControlOverride("abc123"))
        assertEquals(LeftControl.STICK, ControlLayoutStore(dir).leftControlOverride("abc123"))
        store.setLeftControlOverride("abc123", LeftControl.DPAD)
        assertEquals(LeftControl.DPAD, store.leftControlOverride("abc123"))
        store.setLeftControlOverride("abc123", null)
        assertEquals(null, store.leftControlOverride("abc123"))
        // Clearing deletes the file: a later load must not
        // resurrect the choice.
        assertFalse(java.io.File(dir, "abc123.leftcontrol").exists())
    }

    @Test
    fun `left-control override survives a layout save and clear`() {
        store.setLeftControlOverride("abc123", LeftControl.STICK)
        store.save("abc123", ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false))
        assertEquals(LeftControl.STICK, store.leftControlOverride("abc123"))
        store.clearOverride("abc123")
        assertEquals(LeftControl.STICK, store.leftControlOverride("abc123"))
    }

    @Test
    fun `left-control override does not leak between games`() {
        store.setLeftControlOverride("abc123", LeftControl.STICK)
        assertEquals(null, store.leftControlOverride("def456"))
    }

    @Test
    fun `an unreadable left-control choice reads as no override`() {
        java.io.File(dir, "abc123.leftcontrol").writeText("banana")
        assertEquals(null, store.leftControlOverride("abc123"))
    }

    // Alpha.14 — the per-game joystick dead-zone override, in
    // its own small file beside the layout file: the layout's
    // save/clear rules must never wipe it, or be wiped by it.
    @Test
    fun `stick dead-zone override is null by default`() {
        assertEquals(null, store.stickDeadZoneOverridePct("abc123"))
    }

    @Test
    fun `stick dead-zone override round-trips and clears`() {
        store.setStickDeadZoneOverridePct("abc123", 30)
        assertEquals(30, store.stickDeadZoneOverridePct("abc123"))
        assertEquals(30, ControlLayoutStore(dir).stickDeadZoneOverridePct("abc123"))
        store.setStickDeadZoneOverridePct("abc123", 0)
        assertEquals(0, store.stickDeadZoneOverridePct("abc123"))
        store.setStickDeadZoneOverridePct("abc123", null)
        assertEquals(null, store.stickDeadZoneOverridePct("abc123"))
        // Clearing deletes the file: a later load must not
        // resurrect the override.
        assertFalse(java.io.File(dir, "abc123.stickdeadzone").exists())
    }

    @Test
    fun `stick dead-zone override survives a layout save and clear`() {
        store.setStickDeadZoneOverridePct("abc123", 35)
        store.save("abc123", ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false))
        assertEquals(35, store.stickDeadZoneOverridePct("abc123"))
        store.clearOverride("abc123")
        assertEquals(35, store.stickDeadZoneOverridePct("abc123"))
    }

    @Test
    fun `stick dead-zone override does not leak between games`() {
        store.setStickDeadZoneOverridePct("abc123", 30)
        assertEquals(null, store.stickDeadZoneOverridePct("def456"))
    }

    @Test
    fun `an unreadable stick dead-zone override reads as no override`() {
        java.io.File(dir, "abc123.stickdeadzone").writeText("banana")
        assertEquals(null, store.stickDeadZoneOverridePct("abc123"))
        java.io.File(dir, "abc123.stickdeadzone").writeText("99")
        assertEquals(null, store.stickDeadZoneOverridePct("abc123"))
    }

    @Test
    fun `resolved dead zone prefers the override over the global value`() {
        // No override: the global value wins.
        assertEquals(22, store.resolvedStickDeadZonePct("abc123", 22))
        // An override wins over the global value.
        store.setStickDeadZoneOverridePct("abc123", 35)
        assertEquals(35, store.resolvedStickDeadZonePct("abc123", 22))
        // Clearing the override falls back to the global value.
        store.setStickDeadZoneOverridePct("abc123", null)
        assertEquals(30, store.resolvedStickDeadZonePct("abc123", 30))
    }

    // Alpha.16 — the per-game proportional-steering override,
    // in its own small file beside the layout file: the
    // layout's save/clear rules must never wipe it, or be
    // wiped by it.
    @Test
    fun `proportional steering override is null by default`() {
        assertEquals(null, store.proportionalSteeringOverride("abc123"))
    }

    @Test
    fun `proportional steering override round-trips and clears`() {
        store.setProportionalSteeringOverride("abc123", true)
        assertEquals(true, store.proportionalSteeringOverride("abc123"))
        assertEquals(true, ControlLayoutStore(dir).proportionalSteeringOverride("abc123"))
        store.setProportionalSteeringOverride("abc123", false)
        assertEquals(false, store.proportionalSteeringOverride("abc123"))
        store.setProportionalSteeringOverride("abc123", null)
        assertEquals(null, store.proportionalSteeringOverride("abc123"))
        // Clearing deletes the file: a later load must not
        // resurrect the override.
        assertFalse(java.io.File(dir, "abc123.propsteering").exists())
    }

    @Test
    fun `proportional steering override survives a layout save and clear`() {
        store.setProportionalSteeringOverride("abc123", true)
        store.save("abc123", ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false))
        assertEquals(true, store.proportionalSteeringOverride("abc123"))
        store.clearOverride("abc123")
        assertEquals(true, store.proportionalSteeringOverride("abc123"))
    }

    @Test
    fun `proportional steering override does not leak between games`() {
        store.setProportionalSteeringOverride("abc123", true)
        assertEquals(null, store.proportionalSteeringOverride("def456"))
    }

    @Test
    fun `an unreadable proportional steering override reads as no override`() {
        java.io.File(dir, "abc123.propsteering").writeText("banana")
        assertEquals(null, store.proportionalSteeringOverride("abc123"))
    }

    @Test
    fun `resolved proportional steering prefers the override over the global value`() {
        // No override: the global value wins.
        assertFalse(store.resolvedProportionalSteering("abc123", false))
        assertTrue(store.resolvedProportionalSteering("abc123", true))
        // An override wins over the global value, either way.
        store.setProportionalSteeringOverride("abc123", true)
        assertTrue(store.resolvedProportionalSteering("abc123", false))
        store.setProportionalSteeringOverride("abc123", false)
        assertFalse(store.resolvedProportionalSteering("abc123", true))
        // Clearing the override falls back to the global value.
        store.setProportionalSteeringOverride("abc123", null)
        assertTrue(store.resolvedProportionalSteering("abc123", true))
    }
}
