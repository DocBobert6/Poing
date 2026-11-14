package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 — edge smoothing, on the JVM: the exclusivity
 * rule (edge on means the chain is off), the per-system
 * parameters, the motion math, and the palette scan. The
 * look itself is a device check for Robert — the
 * side-by-side against xBR / ScaleFX, not these tests.
 */
class EdgeSmoothingTest {

    @Test
    fun resolveKeepsTheChainWhenEdgeIsOff() {
        val r = EdgeSmoothing.resolve(DisplayChain.JADE, true, EdgeMode.OFF)
        assertEquals(DisplayChain.JADE, r.chain)
        assertEquals(EdgeMode.OFF, r.edge)
    }

    @Test
    fun resolveTurnsTheChainOffWhenEdgeIsOn() {
        // Edge on means the chain is off — the two are
        // alternatives, never stacked.
        val edge = EdgeSmoothing.resolve(DisplayChain.PRISM, true, EdgeMode.EDGE)
        assertEquals(DisplayChain.CLEAN, edge.chain)
        assertEquals(EdgeMode.EDGE, edge.edge)
        val combined = EdgeSmoothing.resolve(DisplayChain.DUSK, true, EdgeMode.EDGE_GRID)
        assertEquals(DisplayChain.CLEAN, combined.chain)
        assertEquals(EdgeMode.EDGE_GRID, combined.edge)
    }

    @Test
    fun resolveIsCleanWhenTheChainToggleIsOff() {
        val r = EdgeSmoothing.resolve(DisplayChain.JADE, false, EdgeMode.OFF)
        assertEquals(DisplayChain.CLEAN, r.chain)
        assertEquals(EdgeMode.OFF, r.edge)
    }

    @Test
    fun resolveKeepsTheEdgeModeWhenTheChainIsAlreadyOff() {
        // The chain toggle does not gate the edge mode: edge
        // on with the chain off is still edge on.
        val r = EdgeSmoothing.resolve(DisplayChain.DUSK, false, EdgeMode.EDGE)
        assertEquals(DisplayChain.CLEAN, r.chain)
        assertEquals(EdgeMode.EDGE, r.edge)
    }

    @Test
    fun paramsDifferPerSystem() {
        // GB's 4 shades differ subtly, so its thresholds sit
        // lower and its blend cap is the gentlest; GBA has
        // the thinnest outlines, so it gets the strongest
        // blend and the only subpixel pass.
        val gb = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 2.0f)
        val gbc = EdgeSmoothing.edgeParamsFor(GameSystem.GBC, 2.0f)
        val gba = EdgeSmoothing.edgeParamsFor(GameSystem.GBA, 2.0f)
        assertEquals(0.60f, gb.blendMax, 0.001f)
        assertEquals(0.65f, gbc.blendMax, 0.001f)
        assertEquals(0.70f, gba.blendMax, 0.001f)
        assertTrue(gb.snapGb)
        assertFalse(gbc.snapGb)
        assertFalse(gba.snapGb)
        assertFalse(gb.subpixel)
        assertFalse(gbc.subpixel)
        assertTrue(gba.subpixel)
        assertEquals(0.30f, gb.edgeHi, 0.001f)
        assertEquals(0.35f, gbc.edgeHi, 0.001f)
        assertEquals(0.35f, gba.edgeHi, 0.001f)
    }

    @Test
    fun blendCapTapersAboveThreeTimesScale() {
        // Full at 3x and below; down to 0.8x at 5x and
        // above — the same reasoning as the grid taper.
        val gb2 = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 2.0f)
        val gb3 = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 3.0f)
        val gb45 = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 4.5f)
        val gb8 = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 8.0f)
        assertEquals(0.60f, gb2.blendMax, 0.001f)
        assertEquals(0.60f, gb3.blendMax, 0.001f)
        assertEquals(0.4899f, gb45.blendMax, 0.001f)
        assertEquals(0.48f, gb8.blendMax, 0.001f)
    }

    @Test
    fun motionRampsBetweenStillAndScrolling() {
        assertEquals(0f, EdgeSmoothing.motionFromMeanDiff(0f), 0.001f)
        assertEquals(0f, EdgeSmoothing.motionFromMeanDiff(0.02f), 0.001f)
        assertEquals(0.5f, EdgeSmoothing.motionFromMeanDiff(0.135f), 0.001f)
        assertEquals(1f, EdgeSmoothing.motionFromMeanDiff(0.25f), 0.001f)
        assertEquals(1f, EdgeSmoothing.motionFromMeanDiff(1.0f), 0.001f)
    }

    @Test
    fun meanAbsDiffIsZeroForIdenticalGrids() {
        val a = floatArrayOf(0.1f, 0.5f, 0.9f, 0.2f)
        assertEquals(0f, EdgeSmoothing.meanAbsDiff(a, a.copyOf()), 0.001f)
        val b = floatArrayOf(0.2f, 0.5f, 0.9f, 0.2f)
        assertEquals(0.025f, EdgeSmoothing.meanAbsDiff(a, b), 0.001f)
    }

    @Test
    fun lumaGridSamplesCellCentres() {
        // A 2x2 frame, one sample per cell: white, black,
        // red (0.299) and green (0.587).
        val pixels = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
        )
        val grid = EdgeSmoothing.lumaGrid(pixels, 2, 2, 2, 2)
        assertEquals(4, grid.size)
        assertEquals(1.0f, grid[0], 0.001f)
        assertEquals(0.0f, grid[1], 0.001f)
        assertEquals(0.299f, grid[2], 0.001f)
        assertEquals(0.587f, grid[3], 0.001f)
    }

    @Test
    fun topPaletteColorsAreTheMostFrequentFirst() {
        // Four shades, 4 / 3 / 2 / 1 pixels: the order is
        // by frequency, most frequent first.
        val dark = 0xFF181818.toInt()
        val midDark = 0xFF4A4A4A.toInt()
        val midLight = 0xFF848484.toInt()
        val light = 0xFFA5A5A5.toInt()
        val pixels = intArrayOf(
            dark, dark, dark, dark,
            midDark, midDark, midDark,
            midLight, midLight,
            light,
        )
        val top = EdgeSmoothing.topPaletteColors(pixels)
        assertEquals(4, top.size)
        assertEquals(dark, top[0])
        assertEquals(midDark, top[1])
        assertEquals(midLight, top[2])
        assertEquals(light, top[3])
    }

    @Test
    fun topPaletteColorsPadsByRepeatingTheLast() {
        // A two-colour frame still yields a full set of 4
        // — the shader always gets four shades.
        val pixels = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
        )
        val top = EdgeSmoothing.topPaletteColors(pixels)
        assertEquals(4, top.size)
        assertEquals(0xFF000000.toInt(), top[0])
        assertEquals(0xFFFFFFFF.toInt(), top[1])
        assertEquals(0xFFFFFFFF.toInt(), top[2])
        assertEquals(0xFFFFFFFF.toInt(), top[3])
    }

    @Test
    fun topPaletteColorsIsBlackForAnEmptyFrame() {
        val top = EdgeSmoothing.topPaletteColors(intArrayOf())
        assertEquals(4, top.size)
        for (c in top) assertEquals(0xFF000000.toInt(), c)
    }
}
