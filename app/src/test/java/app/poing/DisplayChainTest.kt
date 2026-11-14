package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 8 — the display-chain mapping and the Jade palette rule. */
class DisplayChainTest {

    @Test
    fun gbRunsJade() {
        assertEquals(DisplayChain.JADE, DisplayChain.forSystem(GameSystem.GB))
    }

    @Test
    fun gbcRunsPrismAndGbaRunsDusk() {
        assertEquals(DisplayChain.PRISM, DisplayChain.forSystem(GameSystem.GBC))
        assertEquals(DisplayChain.DUSK, DisplayChain.forSystem(GameSystem.GBA))
    }

    @Test
    fun greyscalePalettesGetTheFstnLook() {
        assertEquals(JadeMode.FSTN, DisplayChain.jadeModeForPalette("Grayscale"))
        assertEquals(JadeMode.FSTN, DisplayChain.jadeModeForPalette("GB Pocket"))
        assertEquals(JadeMode.FSTN, DisplayChain.jadeModeForPalette("GB Light"))
    }

    @Test
    fun dmgGreenAndColouredPalettesPassThrough() {
        // That palette IS the look the picker promises.
        assertEquals(JadeMode.PASSTHROUGH, DisplayChain.jadeModeForPalette("DMG Green"))
        assertEquals(JadeMode.PASSTHROUGH, DisplayChain.jadeModeForPalette("GBC Brown ↑"))
        assertEquals(JadeMode.PASSTHROUGH, DisplayChain.jadeModeForPalette("SGB 1-A"))
    }

    @Test
    fun chainOnSwitchesTheCoreCorrectionOff() {
        val options = mapOf("mgba_color_correction" to "Auto", "mgba_gb_model" to "Autodetect")
        val effective = DisplayChain.effectiveCoreOptions(options, DisplayChain.JADE)
        assertEquals("OFF", effective["mgba_color_correction"])
        // Everything else passes through untouched.
        assertEquals("Autodetect", effective["mgba_gb_model"])
        // Prism owns the colour too, so the same rule holds.
        assertEquals(
            "OFF",
            DisplayChain.effectiveCoreOptions(options, DisplayChain.PRISM)["mgba_color_correction"],
        )
        // And Dusk.
        assertEquals(
            "OFF",
            DisplayChain.effectiveCoreOptions(options, DisplayChain.DUSK)["mgba_color_correction"],
        )
    }

    @Test
    fun cleanLeavesTheCoreSettingAlone() {
        val options = mapOf("mgba_color_correction" to "Auto")
        assertEquals(
            options,
            DisplayChain.effectiveCoreOptions(options, DisplayChain.CLEAN),
        )
    }

    /**
     * Performance fix 2 (2026-11-14): the edge mode resolves
     * to CLEAN, so the old chain-only rule never fired while
     * edge was on — the core corrected AND the edge pass
     * folded the colour, twice and wrong. Edge on forces the
     * core correction off even with a clean chain.
     */
    @Test
    fun edgeOnForcesTheCoreCorrectionOff() {
        val options = mapOf("mgba_color_correction" to "Auto")
        assertEquals(
            "OFF",
            DisplayChain.effectiveCoreOptions(
                options, DisplayChain.CLEAN, EdgeMode.EDGE,
            )["mgba_color_correction"],
        )
        assertEquals(
            "OFF",
            DisplayChain.effectiveCoreOptions(
                options, DisplayChain.CLEAN, EdgeMode.EDGE_GRID,
            )["mgba_color_correction"],
        )
    }

    /**
     * Performance fix 2 (2026-11-14): the reconstruction
     * carries the chains' colour itself, so it owns the
     * colour like the edge pass does — core correction off
     * while it is on, even with a clean chain.
     */
    @Test
    fun reconOnForcesTheCoreCorrectionOff() {
        val options = mapOf("mgba_color_correction" to "Auto")
        assertEquals(
            "OFF",
            DisplayChain.effectiveCoreOptions(
                options, DisplayChain.CLEAN, EdgeMode.OFF, ReconMode.GBC,
            )["mgba_color_correction"],
        )
    }

    /**
     * Performance fix 2 (2026-11-14): clean with everything
     * off still leaves the user's core setting exactly as set
     * — the new parameters change nothing when they are off.
     */
    @Test
    fun cleanWithEverythingOffStillLeavesTheCoreSettingAlone() {
        val options = mapOf("mgba_color_correction" to "Auto")
        assertEquals(
            options,
            DisplayChain.effectiveCoreOptions(
                options, DisplayChain.CLEAN, EdgeMode.OFF, ReconMode.OFF,
            ),
        )
    }

    @Test
    fun everyPaletteInTheCoreListHasAMode() {
        // No palette may crash the mapping: anything the core
        // offers gets a mode (unknown names pass through).
        for (palette in CoreOptions.GB_PALETTES) {
            assertTrue(DisplayChain.jadeModeForPalette(palette) in JadeMode.entries)
        }
        assertEquals(48, CoreOptions.GB_PALETTES.size)
    }
}
