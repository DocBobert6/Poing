package app.poing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Worm Light rework (Robert, 2026-11-12): the lamp is bigger
 * and sits ABOVE the picture, angling its cone down onto the
 * game — the old small lens floating mid-picture with a radial
 * glow circle is gone. The geometry is pure, so these run on
 * the plain JVM.
 *
 * The box here is 1000x600 px at density 2.625 (a Pixel 10a):
 * a GBA picture at that size, so the numbers below are the
 * real ones, not toy values.
 */
class WormLightGeometryTest {

    private val width = 1000f
    private val height = 600f
    private val density = 2.625f

    private fun geometry(head: Offset = WORM_LIGHT_DEFAULT_HEAD) =
        wormLightGeometry(width, height, head, density)

    @Test
    fun defaultHeadSitsOnTheTopEdgeCentred() {
        val g = geometry()
        assertEquals(width / 2f, g.headPx.x, 0.01f)
        // The head is on the top edge (12% down), not
        // mid-picture (the old default was 35% down).
        assertEquals(height * 0.12f, g.headPx.y, 0.01f)
        assertTrue("head should sit near the top edge", g.headPx.y < height * 0.2f)
        assertEquals(WORM_LIGHT_DEFAULT_HEAD, Offset(0.5f, 0.12f))
    }

    @Test
    fun lensIsThirtyTwoDpAndNeckTenDp() {
        val g = geometry()
        assertEquals(32f * density, g.lensRadiusPx, 0.01f)
        assertEquals(10f * density, g.neckWidthPx, 0.01f)
        // About three times the old lens (a 26f px disc) and
        // four times the old neck (a 6f px stroke).
        assertTrue("lens should be ~3x the old 26px disc", g.lensRadiusPx > 3f * 26f)
        assertTrue("neck should be ~4x the old 6px stroke", g.neckWidthPx > 4f * 6f)
    }

    @Test
    fun coneOpensDownwardOntoThePicture() {
        val g = geometry()
        // The base spans 84% of the picture width at 90% of
        // its height, centred — the light pools on the game.
        assertEquals(height * 0.9f, g.coneBaseLeft.y, 0.01f)
        assertEquals(height * 0.9f, g.coneBaseRight.y, 0.01f)
        assertEquals(width * 0.84f, g.coneBaseRight.x - g.coneBaseLeft.x, 0.01f)
        assertEquals(width / 2f, g.coneBaseCenter.x, 0.01f)
        // The base stays inside the picture: the cone never
        // spills onto the flank controls.
        assertTrue(g.coneBaseLeft.x >= 0f)
        assertTrue(g.coneBaseRight.x <= width)
        assertTrue(g.coneBaseLeft.y <= height)
        // The cone opens downward: the apex is above the base
        // and the base is far wider than the lens.
        assertTrue(g.coneApex.y < g.coneBaseLeft.y)
        assertTrue(g.coneBaseRight.x - g.coneBaseLeft.x > g.lensRadiusPx * 2f)
        // The outer cone feathers beyond the inner one: wider
        // and lower, still inside the picture.
        assertTrue(g.outerBaseRight.x - g.outerBaseLeft.x > g.coneBaseRight.x - g.coneBaseLeft.x)
        assertTrue(g.outerBaseLeft.y > g.coneBaseLeft.y)
        assertTrue(g.outerBaseLeft.x >= 0f)
        assertTrue(g.outerBaseRight.x <= width)
    }

    @Test
    fun coneBaseStaysCentredWhenTheHeadMoves() {
        // Dragging the head sideways angles the cone: the apex
        // follows the head, the base stays centred on the
        // picture, so the light falls across the game.
        val g = geometry(Offset(0.2f, 0.12f))
        assertEquals(width * 0.2f, g.coneApex.x, 0.01f)
        assertEquals(width / 2f, g.coneBaseCenter.x, 0.01f)
        assertTrue("a head left of centre should angle the cone right", g.coneApex.x < g.coneBaseCenter.x)
    }

    @Test
    fun coneContainsThePictureCentre() {
        val g = geometry()
        // At the picture centre's height, the cone's left edge
        // is left of centre and its right edge right of it —
        // the pool covers the middle of the game.
        val cy = height * 0.55f
        val t = (cy - g.coneApex.y) / (g.coneBaseLeft.y - g.coneApex.y)
        val leftEdge = g.coneApex.x + (g.coneBaseLeft.x - g.coneApex.x) * t
        val rightEdge = g.coneApex.x + (g.coneBaseRight.x - g.coneApex.x) * t
        assertTrue("cone left edge should be left of centre", leftEdge < width / 2f)
        assertTrue("cone right edge should be right of centre", rightEdge > width / 2f)
    }
}
