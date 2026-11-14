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
 * just left of the display area, coils up the left side of
 * the game, arches over the top, and the head — a clear oval
 * lens with the bulb inside — hangs above the screen in the
 * strip the game gives up, pooling warm light on the game
 * from above. The old cone geometry is gone. The geometry is
 * pure, so these run on the plain JVM.
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
        aimAngleRad: Float? = null,
    ) = wormLightGeometry(
        width, height, head, density, aspect, WORM_LIGHT_STRIP_DP, swayPx,
        aimAngleRad = aimAngleRad,
    )

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
    fun headIsClampedToThePlayerBox() {
        // Free head (Robert, 2026-11-13 — the "never on top
        // of the game" rule is reversed): a stored position
        // from a taller or shorter player area still lands
        // in the player box, anywhere in it — low, high, far
        // left, far right — and the lens stays on screen.
        val low = geometry(Offset(0.5f, 0.9f))
        assertEquals(height * 0.9f, low.lensCenter.y, 0.01f)
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
    fun plugSitsJustLeftOfTheGameRect() {
        val g = geometry()
        // The EXT port's plug: 10x24dp, its right edge a 10dp
        // gap left of the game rect's left edge, centred in
        // the strip — beside the picture, in the flank, never
        // at the device screen's edge (Robert, 2026-11-13:
        // "still attached to top and it's too far left"; the
        // realism rework, Robert, 2026-11-14, moved it a
        // little further left so it reads as plugged into the
        // device's side, not into the screen).
        assertEquals(160.25f - 10f * density, g.plugRect.right, 0.01f)
        assertEquals(10f * density, g.plugRect.width, 0.01f)
        assertEquals(24f * density, g.plugRect.height, 0.01f)
        assertEquals(stripPx / 2f, g.plugRect.center.y, 0.01f)
        assertTrue("the plug should sit right of the screen edge", g.plugRect.left > 0f)
        assertTrue("the plug should sit left of the game", g.plugRect.right < g.gameRect.left)
        // A GB game is narrower, so its plug sits further
        // right — always beside its own picture.
        val gb = geometry(aspect = 160f / 144f)
        assertEquals(248.33f - 10f * density, gb.plugRect.right, 0.01f)
        assertTrue(gb.plugRect.left > 0f)
    }

    @Test
    fun portHousingWrapsThePlugAndCollarExitsRight() {
        // Realism rework (Robert, 2026-11-14): the port
        // housing wraps the plug on three sides — its right
        // edge flush with the plug's right face, where the
        // cord leaves — and the strain-relief collar is the
        // short sleeve at that face, the cord emerging from
        // it. The whole assembly lives in the strip, clear
        // of the flank controls below it.
        val g = geometry()
        val pad = WORM_LIGHT_PORT_PAD_DP * density
        assertEquals(g.plugRect.left - pad, g.portRect.left, 0.01f)
        assertEquals(g.plugRect.top - pad, g.portRect.top, 0.01f)
        assertEquals(g.plugRect.right, g.portRect.right, 0.01f)
        assertEquals(g.plugRect.bottom + pad, g.portRect.bottom, 0.01f)
        assertEquals(g.plugRect.right, g.collarRect.left, 0.01f)
        assertEquals(WORM_LIGHT_COLLAR_W_DP * density, g.collarRect.width, 0.01f)
        assertEquals(WORM_LIGHT_COLLAR_H_DP * density, g.collarRect.height, 0.01f)
        assertEquals(stripPx / 2f, g.collarRect.center.y, 0.01f)
        assertEquals(g.coilP0.x, g.collarRect.left, 0.01f)
        assertEquals(g.coilP0.y, g.collarRect.center.y, 0.01f)
        assertTrue("the port should stay in the strip", g.portRect.bottom <= stripPx)
    }

    @Test
    fun plugParksAtTheEdgeWhenTheGameFillsTheWidth() {
        // A game nearly as wide as the player leaves no room
        // left of it: the plug parks at the screen edge
        // instead of going off-screen.
        val g = geometry(aspect = 3.0f)
        assertEquals(0f, g.gameRect.left, 0.01f)
        assertEquals(0f, g.plugRect.left, 0.01f)
        assertEquals(10f * density, g.plugRect.right, 0.01f)
    }

    @Test
    fun coilSpineArchesOverTheTopFromPlugToHead() {
        val g = geometry()
        // The spine starts at the plug's right edge and ends
        // at the head, and both control points sit near the
        // top of the strip — above both ends — so the cord
        // rises from the plug beside the game's left edge,
        // arches over the top, and descends to the head.
        assertEquals(160.25f - 10f * density, g.coilP0.x, 0.01f)
        assertEquals(stripPx / 2f, g.coilP0.y, 0.01f)
        assertEquals(g.lensCenter, g.coilP3)
        assertTrue("first control should be above the plug", g.coilP1.y < g.coilP0.y)
        assertTrue("second control should be above the head", g.coilP2.y < g.coilP3.y)
        assertTrue("controls should run plug to head", g.coilP0.x < g.coilP1.x)
        assertTrue("controls should run plug to head", g.coilP1.x < g.coilP2.x)
        assertTrue("controls should run plug to head", g.coilP2.x < g.coilP3.x)
        assertEquals(237.31f, g.coilP1.x, 0.01f)
        assertEquals(412.44f, g.coilP2.x, 0.01f)
        assertEquals(stripPx * 0.06f, g.coilP1.y, 0.01f)
        assertEquals(stripPx * 0.06f, g.coilP2.y, 0.01f)
    }

    @Test
    fun lensFollowsThePoolsTilt() {
        // The lens rotation follows where the light lands
        // (Robert, 2026-11-13): level over the middle of the
        // game, tilting toward the game from each flank —
        // and always the pool's own tilt, in degrees.
        val g = geometry()
        assertEquals(22f * density, g.lensRadii.x, 0.01f)
        assertEquals(14f * density, g.lensRadii.y, 0.01f)
        assertEquals(0f, g.lensRotationDeg, 0.01f)
        assertEquals(g.pool.rotationRad * 180f / PI.toFloat(), g.lensRotationDeg, 0.01f)
        // At level the half-extents are the radii themselves.
        assertEquals(g.lensRadii.x, g.lensHalfWidthPx, 0.01f)
        assertEquals(g.lensRadii.y, g.lensHalfHeightPx, 0.01f)
        val left = geometry(Offset(0.2f, 0.06f))
        assertEquals(15.18f, left.lensRotationDeg, 0.01f)
        assertEquals(left.pool.rotationRad * 180f / PI.toFloat(), left.lensRotationDeg, 0.01f)
        val right = geometry(Offset(0.8f, 0.06f))
        assertEquals(-15.18f, right.lensRotationDeg, 0.01f)
        assertEquals(right.pool.rotationRad * 180f / PI.toFloat(), right.lensRotationDeg, 0.01f)
        // The rotated lens's bounding half-extents: wider
        // than the unrotated x radius would be alone, and
        // taller than the y radius alone.
        assertTrue(left.lensHalfWidthPx >= left.lensRadii.x * 0.8f)
        assertTrue(left.lensHalfHeightPx >= left.lensRadii.y)
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
        val rot = g.lensRotationDeg * PI.toFloat() / 180f
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
        assertEquals(277.31f, g.coilP1.x, 0.01f)
        assertEquals(452.44f, g.coilP2.x, 0.01f)
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
        // At the centre: 0.14 fall + 0.16 hotspot = 0.30
        // (retuned 2026-11-13, from 0.65 — the old peak
        // bleached the lightest Jade shades to white).
        assertEquals(0.30f, wormLightPoolFactor(center, radii, 0f, center), 0.001f)
        // At the ellipse edge (e = 1): nothing.
        assertEquals(0f, wormLightPoolFactor(center, radii, 0f, Offset(1.05f, 0.20f)), 0.001f)
        // Outside the ellipse: nothing.
        assertEquals(0f, wormLightPoolFactor(center, radii, 0f, Offset(1.30f, 0.20f)), 0.001f)
        // Halfway out along the long axis (e = 0.25): 0.185.
        assertEquals(0.185f, wormLightPoolFactor(center, radii, 0f, Offset(0.775f, 0.20f)), 0.01f)
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

    @Test
    fun poolFactorTiltsTowardTheHeadsSide() {
        // A head left of centre tilts the pool's long axis
        // down-right (the beam slants down from the head):
        // a point down-right of the centre scores higher
        // than its mirror up-right. The oracle runs the
        // shader's rotation in vTex space (y = 0 at the
        // bottom) — without that flip the tilt reads
        // mirrored against the picture.
        val center = Offset(0.5f, 0.20f)
        val radii = Offset(0.55f, 0.42f)
        val downRight = wormLightPoolFactor(center, radii, 0.6f, Offset(0.8f, 0.35f))
        val upRight = wormLightPoolFactor(center, radii, 0.6f, Offset(0.8f, 0.05f))
        assertTrue("down-right should score higher than up-right", downRight > upRight)
        assertEquals(0.107f, downRight, 0.01f)
        assertEquals(0.054f, upRight, 0.01f)
    }

    @Test
    fun headTravelsDownTheLeftFlank() {
        // Full travel (Robert, 2026-11-13): the head can be
        // pulled down the left flank to light the bottom of
        // the game — the lens stays beside the picture,
        // never on it, and the pool follows it low on the
        // left.
        val g = geometry(Offset(0.08f, 0.5f))
        assertEquals(80f, g.lensCenter.x, 0.01f)
        assertEquals(300f, g.lensCenter.y, 0.01f)
        assertTrue(
            "the lens should stay left of the game",
            g.lensCenter.x + g.lensHalfWidthPx <= g.gameRect.left,
        )
        assertEquals(0.15f, g.pool.center.x, 0.01f)
        assertEquals((300f - 147f) / 453f, g.pool.center.y, 0.01f)
        assertEquals(1f, g.pool.intensity, 0.001f)
    }

    @Test
    fun headTravelsDownTheRightFlank() {
        // The same, on the right: the head drops down the
        // right flank and the pool follows it low on the
        // right.
        val g = geometry(Offset(0.92f, 0.8f))
        assertEquals(920f, g.lensCenter.x, 0.01f)
        assertEquals(480f, g.lensCenter.y, 0.01f)
        assertTrue(
            "the lens should stay right of the game",
            g.lensCenter.x - g.lensHalfWidthPx >= g.gameRect.right,
        )
        assertEquals(0.85f, g.pool.center.x, 0.01f)
        assertEquals((480f - 147f) / 453f, g.pool.center.y, 0.01f)
        assertEquals(1f, g.pool.intensity, 0.001f)
    }

    @Test
    fun headCanBeDraggedOntoTheGame() {
        // Free head (Robert, 2026-11-13 — the "never on top
        // of the game" rule is reversed): a drag straight
        // down over the game puts the head ON the picture,
        // and the pool follows it down the middle.
        val mid = geometry(Offset(0.5f, 0.5f))
        assertEquals(500f, mid.lensCenter.x, 0.01f)
        assertEquals(300f, mid.lensCenter.y, 0.01f)
        assertEquals(0.5f, mid.pool.center.x, 0.01f)
        assertEquals((300f - 147f) / 453f, mid.pool.center.y, 0.01f)
        // ... and the same at the very bottom of the game.
        val low = geometry(Offset(0.5f, 0.95f))
        assertEquals(570f, low.lensCenter.y, 0.01f)
        assertEquals(0.80f, low.pool.center.y, 0.01f)
    }

    @Test
    fun poolRotationFlipsWhenTheHeadIsBelowThePool() {
        // A head at the bottom-left corner lights from
        // below, so its slant mirrors a head at the
        // top-left (which tilts down-right): the bottom-left
        // head tilts the pool the other way. A head level
        // with the pool's centre lights from the side, so
        // its pool is level.
        val bottomLeft = geometry(Offset(0.08f, 0.95f))
        assertTrue(
            "a low left head should tilt the pool the other way",
            bottomLeft.pool.rotationRad < 0f,
        )
        val bottomRight = geometry(Offset(0.92f, 0.95f))
        assertTrue(
            "a low right head should tilt the pool the other way",
            bottomRight.pool.rotationRad > 0f,
        )
        val midLeft = geometry(Offset(0.08f, 0.5f))
        assertEquals(0f, midLeft.pool.rotationRad, 0.001f)
    }

    @Test
    fun rightFlankCoilRoutesOverTheTopAndDownTheSide() {
        // A head low on the right flank gets a two-leg
        // spine: the first leg arches over the top in the
        // strip, the second drops down the right flank —
        // and the loops split across the two legs by length.
        val g = geometry(Offset(0.92f, 0.8f))
        val second = g.coilSecond
        assertTrue("a right-flank head should route the coil in two legs", second != null)
        second!!
        assertTrue("the top leg should stay in the strip", g.coilP1.y < stripPx)
        assertTrue("the top leg should stay in the strip", g.coilP2.y < stripPx)
        assertTrue("the drop should stay right of the game", second.p1.x > g.gameRect.right)
        assertTrue("the drop should stay right of the game", second.p2.x > g.gameRect.right)
        assertEquals(g.lensCenter, second.p3)
        assertEquals(WORM_LIGHT_COIL_TURNS, g.coilTurnsFirst + g.coilTurnsSecond)
        assertTrue("both legs should carry loops", g.coilTurnsFirst > 0)
        assertTrue("both legs should carry loops", g.coilTurnsSecond > 0)
        // A head in the strip has one leg, all 14 loops on it.
        val strip = geometry()
        assertTrue("a strip head should have one coil leg", strip.coilSecond == null)
        assertEquals(WORM_LIGHT_COIL_TURNS, strip.coilTurnsFirst)
        assertEquals(0, strip.coilTurnsSecond)
    }

    @Test
    fun coilNeverCrossesTheGameForPerimeterHeads() {
        // The cord routes around the display area — left
        // edge, top, right edge — never across the picture
        // itself. Sampled over the ribbon (both legs when
        // there are two), for heads in the strip and low on
        // each flank. (A head OVER the game drapes its cord
        // across the picture by design — free head, Robert,
        // 2026-11-13 — so only perimeter heads are pinned
        // here.)
        val heads = listOf(
            Offset(0.5f, 0.06f), Offset(0.1f, 0.06f), Offset(0.9f, 0.06f),
            Offset(0.08f, 0.5f), Offset(0.08f, 0.95f),
            Offset(0.92f, 0.5f), Offset(0.92f, 0.95f),
        )
        for (head in heads) {
            val g = geometry(head)
            val ribbons = mutableListOf<List<Offset>>()
            ribbons.add(
                coilRibbonPoints(
                    g.coilP0, g.coilP1, g.coilP2, g.coilP3,
                    g.coilTurnsFirst, g.coilAmplitudePx,
                ),
            )
            g.coilSecond?.let {
                ribbons.add(
                    coilRibbonPoints(
                        it.p0, it.p1, it.p2, it.p3,
                        g.coilTurnsSecond, g.coilAmplitudePx,
                    ),
                )
            }
            for (pts in ribbons) {
                for (p in pts) {
                    val inside = p.x > g.gameRect.left && p.x < g.gameRect.right &&
                        p.y > g.gameRect.top && p.y < g.gameRect.bottom
                    assertTrue(
                        "the coil should never cross the game (head $head, point $p)",
                        !inside,
                    )
                }
            }
        }
    }

    @Test
    fun headGoesOverTheGameWhenTheGameFillsTheWidth() {
        // Free head (Robert, 2026-11-13): a game nearly as
        // wide as the player leaves no flank to hold the
        // lens — the head goes OVER the game instead of
        // staying in the strip.
        val g = geometry(Offset(0.1f, 0.9f), aspect = 3.0f)
        assertEquals(100f, g.lensCenter.x, 0.01f)
        assertEquals(540f, g.lensCenter.y, 0.01f)
        assertEquals(0.80f, g.pool.center.y, 0.01f)
    }

    @Test
    fun headReachesTheCentreOfTheGame() {
        // The pool's brightest point can sit at the centre
        // of the picture (Robert, 2026-11-13): the head at
        // the game's centre (0.5, 0.6225) puts the pool
        // centre at exactly (0.5, 0.5).
        val g = geometry(Offset(0.5f, 0.6225f))
        assertEquals(500f, g.lensCenter.x, 0.01f)
        assertEquals(373.5f, g.lensCenter.y, 0.01f)
        assertEquals(0.5f, g.pool.center.x, 0.01f)
        assertEquals(0.5f, g.pool.center.y, 0.01f)
    }

    @Test
    fun lensTiltsTowardTheGameOnBothFlanks() {
        // The lens points at where the light lands, on both
        // flanks and at the corners: a head at the top-left
        // tilts down-right, at the top-right down-left, and
        // the low corners mirror — and the lens rotation is
        // the pool's tilt in every zone.
        val leftTop = geometry(Offset(0.08f, 0.25f))
        assertEquals(21.25f, leftTop.lensRotationDeg, 0.01f)
        assertEquals(
            leftTop.pool.rotationRad * 180f / PI.toFloat(),
            leftTop.lensRotationDeg, 0.01f,
        )
        val rightTop = geometry(Offset(0.92f, 0.25f))
        assertEquals(-21.25f, rightTop.lensRotationDeg, 0.01f)
        assertEquals(
            rightTop.pool.rotationRad * 180f / PI.toFloat(),
            rightTop.lensRotationDeg, 0.01f,
        )
        val leftBottom = geometry(Offset(0.08f, 0.95f))
        assertEquals(-21.25f, leftBottom.lensRotationDeg, 0.01f)
        assertEquals(
            leftBottom.pool.rotationRad * 180f / PI.toFloat(),
            leftBottom.lensRotationDeg, 0.01f,
        )
        val rightBottom = geometry(Offset(0.92f, 0.95f))
        assertEquals(21.25f, rightBottom.lensRotationDeg, 0.01f)
        assertEquals(
            rightBottom.pool.rotationRad * 180f / PI.toFloat(),
            rightBottom.lensRotationDeg, 0.01f,
        )
    }

    @Test
    fun aimAngleSetsTheLensAndThePool() {
        // Tap-to-aim (Robert, 2026-11-13): in aim mode the
        // lens and the pool follow the aim angle, not the
        // head's position — the pool's centre still follows
        // the head.
        val aimed = geometry(Offset(0.5f, 0.06f), aimAngleRad = -0.5f)
        assertEquals(-28.65f, aimed.lensRotationDeg, 0.01f)
        assertEquals(-0.5f, aimed.pool.rotationRad, 0.001f)
        assertEquals(0.5f, aimed.pool.center.x, 0.01f)
        assertEquals(0.20f, aimed.pool.center.y, 0.01f)
        // The aim overrides the position tilt, not just
        // where the position tilt is zero.
        val offCentre = geometry(Offset(0.2f, 0.06f), aimAngleRad = -0.5f)
        assertEquals(-0.5f, offCentre.pool.rotationRad, 0.001f)
        assertEquals(-28.65f, offCentre.lensRotationDeg, 0.01f)
    }

    @Test
    fun overGameHeadRoutesTheCoilDirectly() {
        // Free head (Robert, 2026-11-13): a head over the
        // game gets one cubic straight from the plug to the
        // head — a cord draped over the screen — with all 14
        // loops on it.
        val g = geometry(Offset(0.5f, 0.6225f))
        assertTrue("an over-game head should have one coil leg", g.coilSecond == null)
        assertEquals(WORM_LIGHT_COIL_TURNS, g.coilTurnsFirst)
        assertEquals(0, g.coilTurnsSecond)
        assertEquals(g.lensCenter, g.coilP3)
        assertEquals(237.31f, g.coilP1.x, 0.01f)
        assertEquals(412.44f, g.coilP2.x, 0.01f)
        assertEquals(148.5f, g.coilP1.y, 0.01f)
        assertEquals(298.5f, g.coilP2.y, 0.01f)
    }
}
