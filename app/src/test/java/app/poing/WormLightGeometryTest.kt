package app.poing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Worm Light realism (Robert, 2026-11-12, after three
 * reference photos of the real accessory): the cord plugs in
 * at the left edge, coils up the left side, arches over the
 * top, and the head — a clear oval lens with the bulb inside
 * — hangs above the screen in the strip the game gives up,
 * pooling warm light on the game from above. The old cone
 * geometry is gone. The geometry is pure, so these run on
 * the plain JVM.
 *
 * The box here is 1000x600 px at density 2.625 (a Pixel 10a):
 * a GBA picture at that size, so the numbers below are the
 * real ones, not toy values. Strip 56dp = 147px, row 453px,
 * game 679.5x453px at left 160.25, top 147.
 */
class WormLightGeometryTest {

    private val width = 1000f
    private val height = 600f
    private val density = 2.625f
    private val stripPx = 56f * density // 147px
    private val gameAspect = 1.5f

    private fun geometry(
        head: Offset = WORM_LIGHT_DEFAULT_HEAD,
        aspect: Float = gameAspect,
        swayPx: Float = 0f,
    ) = wormLightGeometry(width, height, head, density, aspect, WORM_LIGHT_STRIP_DP, swayPx)

    @Test
    fun defaultHeadSitsMidStripCentred() {
        val g = geometry()
        assertEquals(width / 2f, g.lensCenter.x, 0.01f)
        // 6% down is mid-strip on a phone in landscape
        // (~25dp of a 56dp strip), not on the picture.
        assertEquals(height * 0.06f, g.lensCenter.y, 0.01f)
        assertTrue("head should sit inside the strip", g.lensCenter.y < stripPx)
        assertEquals(WORM_LIGHT_DEFAULT_HEAD, Offset(0.5f, 0.06f))
    }

    @Test
    fun headIsClampedIntoTheStrip() {
        // A stored position from a taller or shorter player
        // area still lands in the strip, and the lens stays
        // on screen.
        val low = geometry(Offset(0.5f, 0.9f))
        assertEquals(stripPx, low.lensCenter.y, 0.01f)
        val high = geometry(Offset(0.5f, -0.1f))
        assertEquals(0f, high.lensCenter.y, 0.01f)
        val left = geometry(Offset(-0.5f, 0.06f))
        assertEquals(left.lensHalfWidthPx, left.lensCenter.x, 0.01f)
        val right = geometry(Offset(1.5f, 0.06f))
        assertEquals(width - right.lensHalfWidthPx, right.lensCenter.x, 0.01f)
    }

    @Test
    fun gameRectIsTheLargestAspectBoxBelowTheStrip() {
        val g = geometry()
        // Row 453px, GBA 3:2: 679.5x453, centred, below the
        // 147px strip — the frame GameDisplayView lays out.
        assertEquals(160.25f, g.gameRect.left, 0.01f)
        assertEquals(stripPx, g.gameRect.top, 0.01f)
        assertEquals(679.5f, g.gameRect.width, 0.01f)
        assertEquals(453f, g.gameRect.height, 0.01f)
        // A GB game (10:9) is narrower in the same row.
        val gb = geometry(aspect = 160f / 144f)
        assertEquals(503.33f, gb.gameRect.width, 0.01f)
        assertEquals(453f, gb.gameRect.height, 0.01f)
        assertEquals(248.33f, gb.gameRect.left, 0.01f)
        assertEquals(stripPx, gb.gameRect.top, 0.01f)
    }

    @Test
    fun plugSitsAtTheLeftEdgeInTheStrip() {
        val g = geometry()
        // The EXT port's plug: 10x24dp at the left edge,
        // centred in the strip.
        assertEquals(0f, g.plugRect.left, 0.01f)
        assertEquals(10f * density, g.plugRect.width, 0.01f)
        assertEquals(24f * density, g.plugRect.height, 0.01f)
        assertEquals(stripPx / 2f, g.plugRect.center.y, 0.01f)
    }

    @Test
    fun coilSpineArchesOverTheTopFromPlugToHead() {
        val g = geometry()
        // The spine starts at the plug's right edge and ends
        // at the head, and both control points sit near the
        // top of the strip — above both ends — so the cord
        // rises from the plug, arches over the top, and
        // descends to the head.
        assertEquals(10f * density, g.coilP0.x, 0.01f)
        assertEquals(stripPx / 2f, g.coilP0.y, 0.01f)
        assertEquals(g.lensCenter, g.coilP3)
        assertTrue("first control should be above the plug", g.coilP1.y < g.coilP0.y)
        assertTrue("second control should be above the head", g.coilP2.y < g.coilP3.y)
        assertTrue("controls should run plug to head", g.coilP0.x < g.coilP1.x)
        assertTrue("controls should run plug to head", g.coilP1.x < g.coilP2.x)
        assertTrue("controls should run plug to head", g.coilP2.x < g.coilP3.x)
        assertEquals(stripPx * 0.06f, g.coilP1.y, 0.01f)
        assertEquals(stripPx * 0.06f, g.coilP2.y, 0.01f)
    }

    @Test
    fun lensIsAnOvalAngledDownAtTheGame() {
        val g = geometry()
        // A clear oval, longer than tall (22x14dp), rotated
        // 35 degrees so the head angles down at the game.
        assertEquals(22f * density, g.lensRadii.x, 0.01f)
        assertEquals(14f * density, g.lensRadii.y, 0.01f)
        assertEquals(35f, g.lensRotationDeg, 0.01f)
        // The rotated lens's bounding half-extents: wider
        // than the unrotated x radius would be alone, and
        // taller than the y radius alone.
        assertTrue(g.lensHalfWidthPx > g.lensRadii.x * 0.8f)
        assertTrue(g.lensHalfHeightPx > g.lensRadii.y)
    }

    @Test
    fun bulbSitsInsideTheLensTowardItsTop() {
        val g = geometry()
        // The bulb is visible inside the clear lens, toward
        // its top (it sits at the back of the head, shining
        // down) — above the lens centre, and inside the
        // lens's ellipse.
        assertTrue("bulb should sit above the lens centre", g.bulbCenter.y < g.lensCenter.y)
        val dx = g.bulbCenter.x - g.lensCenter.x
        val dy = g.bulbCenter.y - g.lensCenter.y
        val rot = 35f * PI.toFloat() / 180f
        val c = kotlin.math.cos(rot)
        val s = kotlin.math.sin(rot)
        val rx = dx * c + dy * s
        val ry = -dx * s + dy * c
        val e = (rx / g.lensRadii.x) * (rx / g.lensRadii.x) +
            (ry / g.lensRadii.y) * (ry / g.lensRadii.y)
        assertTrue("bulb should sit inside the lens", e < 1f)
        assertEquals(8f * density, g.bulbRadii.x, 0.01f)
        assertEquals(6f * density, g.bulbRadii.y, 0.01f)
    }

    @Test
    fun poolIsBrightestAtTheTopCentreOfTheGame() {
        val g = geometry()
        // The pool's centre is the top-centre of the game,
        // an ellipse wider than tall, full intensity.
        assertEquals(Offset(0.5f, 0.20f), g.pool.center)
        assertEquals(Offset(0.55f, 0.42f), g.pool.radii)
        assertEquals(0f, g.pool.rotationRad, 0.001f)
        assertEquals(1f, g.pool.intensity, 0.001f)
        // In px: centred on the game, at 20% of its height.
        assertEquals(500f, g.poolCenterPx.x, 0.01f)
        assertEquals(147f + 453f * 0.20f, g.poolCenterPx.y, 0.01f)
        assertEquals(0.55f * 679.5f, g.poolRadiiPx.x, 0.01f)
        assertEquals(0.42f * 453f, g.poolRadiiPx.y, 0.01f)
        // Off is intensity 0 — the shader adds exactly 0.0.
        assertEquals(0f, WormLightPool.OFF.intensity, 0.001f)
    }

    @Test
    fun poolSkewsWithTheHeadAndStaysOnThePicture() {
        // A head dragged left moves the pool left and tilts
        // its long axis toward the head's side — but the
        // pool never leaves the picture.
        val g = geometry(Offset(0.2f, 0.06f))
        assertEquals(0.15f, g.pool.center.x, 0.01f)
        assertEquals(0.20f, g.pool.center.y, 0.01f)
        assertTrue("a head left of centre should tilt the pool", g.pool.rotationRad > 0f)
        // A head dragged right mirrors it.
        val r = geometry(Offset(0.8f, 0.06f))
        assertEquals(0.85f, r.pool.center.x, 0.01f)
        assertTrue("a head right of centre should tilt the pool the other way", r.pool.rotationRad < 0f)
    }

    @Test
    fun swayTrailsTheHeadAndTheCoilApex() {
        // The settle wobble: the coil's apex moves with the
        // full sway, the head itself with half of it, so the
        // lamp settles as one piece — and the pool follows
        // the head.
        val g = geometry(swayPx = 40f)
        assertEquals(520f, g.lensCenter.x, 0.01f)
        assertEquals(144.69f + 40f, g.coilP1.x, 0.01f)
        assertEquals(381.56f + 40f, g.coilP2.x, 0.01f)
        assertEquals((520f - 160.25f) / 679.5f, g.pool.center.x, 0.01f)
    }

    @Test
    fun coilRibbonStraddlesTheSpineAndAttachesAtBothEnds() {
        val g = geometry()
        val pts = coilRibbonPoints(
            g.coilP0, g.coilP1, g.coilP2, g.coilP3,
            WORM_LIGHT_COIL_TURNS, g.coilAmplitudePx,
        )
        // One point per sample plus the start.
        assertEquals(WORM_LIGHT_COIL_SAMPLES + 1, pts.size)
        // The sine is zero at both ends, so the cord
        // attaches cleanly at the plug and at the head.
        assertEquals(g.coilP0.x, pts.first().x, 0.01f)
        assertEquals(g.coilP0.y, pts.first().y, 0.01f)
        assertEquals(g.coilP3.x, pts.last().x, 0.01f)
        assertEquals(g.coilP3.y, pts.last().y, 0.01f)
        // The ribbon is a sine around the spine itself (not
        // around the straight plug-to-head line — the spine
        // arches): each point sits off the cubic by the
        // amplitude times the sine at its t, so the ribbon
        // swings to both sides of the spine and never leaves
        // the amplitude band around it.
        var minSigned = Float.MAX_VALUE
        var maxSigned = Float.MIN_VALUE
        var maxAbs = 0f
        for (i in pts.indices) {
            val t = i.toFloat() / WORM_LIGHT_COIL_SAMPLES
            val mt = 1f - t
            val bx = mt * mt * mt * g.coilP0.x + 3f * mt * mt * t * g.coilP1.x +
                3f * mt * t * t * g.coilP2.x + t * t * t * g.coilP3.x
            val by = mt * mt * mt * g.coilP0.y + 3f * mt * mt * t * g.coilP1.y +
                3f * mt * t * t * g.coilP2.y + t * t * t * g.coilP3.y
            val tx = 3f * mt * mt * (g.coilP1.x - g.coilP0.x) +
                6f * mt * t * (g.coilP2.x - g.coilP1.x) + 3f * t * t * (g.coilP3.x - g.coilP2.x)
            val ty = 3f * mt * mt * (g.coilP1.y - g.coilP0.y) +
                6f * mt * t * (g.coilP2.y - g.coilP1.y) + 3f * t * t * (g.coilP3.y - g.coilP2.y)
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
            val nx = -ty / len
            val ny = tx / len
            val signed = (pts[i].x - bx) * nx + (pts[i].y - by) * ny
            minSigned = minOf(minSigned, signed)
            maxSigned = maxOf(maxSigned, signed)
            maxAbs = maxOf(maxAbs, abs(signed))
        }
        assertTrue("the ribbon should swing to both sides of the spine", minSigned < 0f)
        assertTrue("the ribbon should swing to both sides of the spine", maxSigned > 0f)
        assertTrue("the widest swing should be near the amplitude", maxAbs > g.coilAmplitudePx * 0.8f)
        assertTrue("the widest swing should not exceed the amplitude", maxAbs <= g.coilAmplitudePx * 1.01f)
    }

    @Test
    fun poolFactorIsBrightestAtTheCentreAndZeroAtTheEdge() {
        val center = Offset(0.5f, 0.20f)
        val radii = Offset(0.55f, 0.42f)
        // At the centre: 0.30 fall + 0.35 hotspot = 0.65.
        assertEquals(0.65f, wormLightPoolFactor(center, radii, 0f, center), 0.001f)
        // At the ellipse edge (e = 1): nothing.
        assertEquals(0f, wormLightPoolFactor(center, radii, 0f, Offset(1.05f, 0.20f)), 0.001f)
        // Outside the ellipse: nothing.
        assertEquals(0f, wormLightPoolFactor(center, radii, 0f, Offset(1.30f, 0.20f)), 0.001f)
        // Halfway out along the long axis (e = 0.25): 0.399.
        assertEquals(0.399f, wormLightPoolFactor(center, radii, 0f, Offset(0.775f, 0.20f)), 0.01f)
    }

    @Test
    fun poolFactorRotatesWithThePool() {
        val center = Offset(0.5f, 0.20f)
        val radii = Offset(0.55f, 0.42f)
        // A quarter-turn swaps the axes: the same e = 0.25
        // down the short axis scores the same as along the
        // long axis unrotated.
        val alongLong = wormLightPoolFactor(center, radii, 0f, Offset(0.775f, 0.20f))
        val downShort = wormLightPoolFactor(center, radii, PI.toFloat() / 2f, Offset(0.5f, 0.475f))
        assertEquals(alongLong, downShort, 0.001f)
    }
}
