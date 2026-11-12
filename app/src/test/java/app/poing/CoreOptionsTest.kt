package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 — the core-options model pins the audit: 17 keys, the
 * documented defaults, the value lists from the core header, and
 * the restart flags. If the core ever changes an option, this
 * test is the tripwire — update the model and the bridge table
 * together.
 */
class CoreOptionsTest {

    @Test
    fun seventeenOptions() {
        assertEquals(17, CoreOptions.ALL.size)
        assertEquals(17, CoreOptions.ALL.map { it.key }.toSet().size)
    }

    @Test
    fun defaultsMatchTheBridgeTable() {
        // The bridge (bridge.cpp) answers GET_VARIABLE with these
        // defaults, copied from libretro_core_options.h. Both tables
        // must agree or a fresh install behaves differently from a
        // changed setting.
        val expected = mapOf(
            "mgba_gb_model" to "Autodetect",
            "mgba_use_bios" to "ON",
            "mgba_skip_bios" to "OFF",
            "mgba_gb_colors" to "Grayscale",
            "mgba_gb_colors_preset" to "0",
            "mgba_sgb_borders" to "ON",
            "mgba_color_correction" to "OFF",
            "mgba_interframe_blending" to "OFF",
            "mgba_audio_low_pass_filter" to "disabled",
            "mgba_audio_low_pass_range" to "60",
            "mgba_allow_opposing_directions" to "no",
            "mgba_solar_sensor_level" to "0",
            "mgba_force_gbp" to "OFF",
            "mgba_idle_optimization" to "Remove Known",
            "mgba_frameskip" to "disabled",
            "mgba_frameskip_threshold" to "33",
            "mgba_frameskip_interval" to "0",
        )
        assertEquals(expected, CoreOptions.defaults())
        for ((key, default) in expected) {
            val option = CoreOptions.byKey(key)
            assertNotNull("missing option $key", option)
            assertTrue("default $default not in values for $key", default in option!!.values)
        }
    }

    @Test
    fun restartFlagsMatchTheHeader() {
        // The "(Restart)" labels in libretro_core_options.h.
        val restart = CoreOptions.ALL.filter { it.restart }.map { it.key }.toSet()
        assertEquals(
            setOf(
                "mgba_gb_model", "mgba_use_bios", "mgba_skip_bios",
                "mgba_gb_colors_preset", "mgba_sgb_borders", "mgba_force_gbp",
            ),
            restart,
        )
    }

    @Test
    fun gbPaletteListIsTheCoreList() {
        val palettes = CoreOptions.GB_PALETTES
        assertEquals(48, palettes.size)
        assertEquals("Grayscale", palettes.first())
        assertTrue("DMG Green" in palettes)
        assertTrue("GB Pocket" in palettes)
        assertTrue("GB Light" in palettes)
        assertTrue("GBC Brown ↑" in palettes)
        assertTrue("GBC Reverse →B" in palettes)
        assertTrue("SGB 1-A" in palettes)
        assertTrue("SGB 4-H" in palettes)
        val option = CoreOptions.byKey("mgba_gb_colors")!!
        assertEquals(palettes, option.values)
    }

    @Test
    fun valueListsMatchTheHeader() {
        assertEquals(
            listOf("Autodetect", "Game Boy", "Super Game Boy", "Game Boy Color", "Super Game Boy Color", "Game Boy Advance"),
            CoreOptions.byKey("mgba_gb_model")!!.values,
        )
        assertEquals(
            listOf("OFF", "GBA", "GBC", "Auto"),
            CoreOptions.byKey("mgba_color_correction")!!.values,
        )
        assertEquals(
            listOf("OFF", "mix", "mix_smart", "lcd_ghosting", "lcd_ghosting_fast"),
            CoreOptions.byKey("mgba_interframe_blending")!!.values,
        )
        assertEquals(19, CoreOptions.byKey("mgba_audio_low_pass_range")!!.values.size)
        assertEquals("5", CoreOptions.byKey("mgba_audio_low_pass_range")!!.values.first())
        assertEquals("95", CoreOptions.byKey("mgba_audio_low_pass_range")!!.values.last())
        assertEquals(12, CoreOptions.byKey("mgba_solar_sensor_level")!!.values.size)
        assertEquals("sensor", CoreOptions.byKey("mgba_solar_sensor_level")!!.values.first())
        assertEquals(16, CoreOptions.byKey("mgba_frameskip_threshold")!!.values.size)
        assertEquals(11, CoreOptions.byKey("mgba_frameskip_interval")!!.values.size)
        assertEquals(
            listOf("Remove Known", "Detect and Remove", "Don't Remove"),
            CoreOptions.byKey("mgba_idle_optimization")!!.values,
        )
    }

    @Test
    fun categoriesCoverAllFiveGroups() {
        val categories = CoreOptions.ALL.map { it.category }.toSet()
        assertEquals(CoreOptionCategory.entries.toSet(), categories)
    }

    @Test
    fun everyOptionHasALabelAndDescription() {
        for (option in CoreOptions.ALL) {
            assertTrue(option.label.isNotBlank())
            assertTrue(option.description.isNotBlank())
            assertTrue(option.values.isNotEmpty())
            assertTrue(option.values.all { it.isNotBlank() })
        }
    }
}
