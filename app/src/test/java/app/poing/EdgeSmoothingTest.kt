package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge smoothing, REWORKED 2026-11-14, on the JVM: the
 * exclusivity rule (edge on means the chain is off) and
 * the per-system parameters. The look itself is a device
 * check for Robert — the side-by-side against xBR /
 * ScaleFX, not these tests. The numeric replica that
 * proves the design on real frames is
 * ~/workspace/poing/verify/edge-rework/sim2.py.
 *
 * What the rework deleted (so these tests assert their
 * ABSENCE): motion-adaptive (the frame-differencing
 * luma grid, the motion factor, the palette scan) and
 * the blend-cap taper. If any of those names reappear
 * in EdgeSmoothing, the compile fails here first.
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
        // GB: the gentlest blend, no snap at all (on 4
        // shades any grey is harmonious), the broadest text
        // bypass (chunky 8x8). GBC: the 15-bit snap, a
        // narrower text bypass. GBA: the strongest blend,
        // the 15-bit snap, the narrowest text bypass — and
        // (until item 3) the only subpixel pass.
        val gb = EdgeSmoothing.edgeParamsFor(GameSystem.GB)
        val gbc = EdgeSmoothing.edgeParamsFor(GameSystem.GBC)
        val gba = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        assertEquals(0.60f, gb.blendMax, 0.001f)
        assertEquals(0.65f, gbc.blendMax, 0.001f)
        assertEquals(0.70f, gba.blendMax, 0.001f)
        assertFalse(gb.snap15)
        assertTrue(gbc.snap15)
        assertTrue(gba.snap15)
        assertFalse(gb.subpixel)
        assertFalse(gbc.subpixel)
        assertTrue(gba.subpixel)
        assertEquals(0.15f, gb.textAlign, 0.001f)
        assertEquals(0.08f, gbc.textAlign, 0.001f)
        assertEquals(0.05f, gba.textAlign, 0.001f)
        assertEquals(0.30f, gb.edgeHi, 0.001f)
        assertEquals(0.35f, gbc.edgeHi, 0.001f)
        assertEquals(0.35f, gba.edgeHi, 0.001f)
    }

    @Test
    fun blendCapDoesNotTaperWithDisplayScale() {
        // The taper was deleted in the rework: the sim
        // measured its effect at under 3% on real edges
        // (identical 7px transition width tapered or not),
        // so the cap is the same at every scale.
        val gb2 = EdgeSmoothing.edgeParamsFor(GameSystem.GB)
        val gba8 = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        assertEquals(0.60f, gb2.blendMax, 0.001f)
        assertEquals(0.70f, gba8.blendMax, 0.001f)
    }
}
