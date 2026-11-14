package app.poing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Worm Light realism (Robert, 2026-11-12, after three
 * reference photos of the real accessory): the lamp is a
 * real Worm Light now, not a spotlight cone on the picture.
 * The cord plugs in JUST LEFT of the display area (like
 * the real EXT port on the side of the device), coils up
 * the left side of the game, arches over the top, and the
 * head hangs above the screen — and, since full travel
 * (Robert, 2026-11-13), the head can be pulled DOWN the
 * left and right sides of the game too, to light any part
 * of the picture: it travels the perimeter of the display
 * area, never on top of the game itself, and stays where
 * it is left. The head is a CLEAR OVAL LENS with the bulb
 * visible inside as a bright point, plus a soft glow
 * around it; the neck is a TIGHT HELIX COIL (a telephone
 * cord), not a straight line. The light is ADDITIVE: an
 * ELLIPTICAL warm-white pool, brightest at the head,
 * smooth falloff to nothing at the edges, with a hotspot
 * where the beam lands most directly. When the head is
 * dragged, the pool moves and skews with it, and the coil
 * flexes (stretch on one side, bunch on the other — the
 * cord has a fixed number of coils, so a longer spine
 * spreads them) with a tiny settle wobble when released.
 *
 * The hardware (cord coil, head plastic, plug) wears the
 * colour the player picked in Settings (dark medium gray
 * by default); the LIGHT itself stays warm white, always,
 * and is never a setting.
 *
 * The old cone overlay is gone — replaced by the pool,
 * which is NOT an overlay wash. The game view is a
 * GLSurfaceView, whose pixels live behind the window: an
 * overlay drawn with BlendMode.Screen would blend against
 * the window's black background and composite as a
 * translucent tint wash — the exact look Robert already
 * rejected. So the pool is applied in the chain's pass-2
 * shader (DisplayShaders.GRID_FRAGMENT, and the plain
 * program too so the lamp works with the chain off), as
 * uniforms added AFTER the optics lines, so the room dim
 * never cancels the lamp. Intensity 0 adds exactly 0.0, so
 * the chains are pixel-identical with the lamp off. This
 * stretches the brief's "no chain change" (its intent: do
 * not rework the chains' look) — the pool is a lamp
 * uniform, not a chain rework; the chains' colour and grid
 * are untouched. On the fallback path (a plain Compose
 * Image, no GL), the overlay draws the same pool with
 * BlendMode.Screen, which works there because the Image
 * lives in the window canvas.
 *
 * Nothing when off: the overlay is not composed at all, so
 * it cannot swallow a touch, and the GL pool resets to
 * intensity 0 (see GameDisplayView).
 */

// The lamp's strip above the picture (Worm Light realism,
// Robert, 2026-11-12): the head hangs in this strip, so the
// game gives up this height while the lamp is on and takes
// the full height back when it is off. 56dp holds the oval
// lens (~30-40dp tall) with clearance above and below.
const val WORM_LIGHT_STRIP_DP = 56f

// The default head: top edge, centred — clipped on, like
// the real accessory. 6% down sits mid-strip on a phone in
// landscape (~25dp of a 56dp strip); the geometry clamps
// the head into the strip on any height, so the default is
// safe on a tall or a short player area alike.
val WORM_LIGHT_DEFAULT_HEAD = Offset(0.5f, 0.06f)

// The oval lens, in dp: a clear oval, longer than tall,
// rotated 35 degrees so the head angles down at the game
// the way the real Worm Light's head does.
const val WORM_LIGHT_LENS_RX_DP = 22f
const val WORM_LIGHT_LENS_RY_DP = 14f
const val WORM_LIGHT_LENS_ROTATION_DEG = 35f

// The bulb inside the lens, in dp: a small bright oval,
// offset toward the top of the lens (the bulb sits at the
// back of the head, shining down).
const val WORM_LIGHT_BULB_RX_DP = 8f
const val WORM_LIGHT_BULB_RY_DP = 6f

// The plug just left of the display area, in dp: the EXT
// port's plug, where the cord enters the player. The gap
// is the small space between the plug and the game rect's
// left edge — the plug sits beside the picture, never at
// the device screen's edge and never over the picture.
const val WORM_LIGHT_PLUG_W_DP = 10f
const val WORM_LIGHT_PLUG_H_DP = 24f
const val WORM_LIGHT_PLUG_GAP_DP = 4f

// The coil: a telephone cord has a fixed number of loops,
// so a longer spine spreads them (stretch) and a shorter
// one crowds them (bunch). 14 loops over the default spine
// reads as a tight helix; the amplitude is the loop's
// half-width, the stroke the cord's thickness.
const val WORM_LIGHT_COIL_TURNS = 14
const val WORM_LIGHT_COIL_AMP_DP = 5f
const val WORM_LIGHT_COIL_STROKE_DP = 3f
const val WORM_LIGHT_COIL_SAMPLES = 64

// The flank bow, in dp: on a flank the coil's control
// points bow AWAY from the game by this much, so the cord
// hangs beside the picture instead of hugging its edge.
const val WORM_LIGHT_COIL_BOW_DP = 8f

// The pool, in frame UV (y = 0 at the top of the game):
// brightest at the head, an ellipse wider than tall, its
// centre the head's position projected onto the game —
// clamped so the pool stays on the picture — and its long
// axis tilting toward the head's side as the head is
// dragged. Full travel (Robert, 2026-11-13): the head
// travels the perimeter, so the centre tracks it in both
// axes — down the flanks as well as across the strip.
const val WORM_LIGHT_POOL_CENTER_Y = 0.20f
const val WORM_LIGHT_POOL_CENTER_Y_MAX = 0.80f
const val WORM_LIGHT_POOL_RX = 0.55f
const val WORM_LIGHT_POOL_RY = 0.42f
const val WORM_LIGHT_POOL_CENTER_X_MIN = 0.15f
const val WORM_LIGHT_POOL_CENTER_X_MAX = 0.85f
const val WORM_LIGHT_POOL_ROTATION_PER_OFFSET = 0.6f
// The tilt's vertical blend (in game heights): the long
// axis tilts toward the head's side while the head is
// above the pool's centre, and mirrors when the head is
// below it (a low head lights from below, so the slant
// flips). The blend is 1 while the head is clearly above
// the centre, -1 clearly below, 0 level with it — the
// half-width is the smooth zone around level.
const val WORM_LIGHT_POOL_TILT_BLEND = 0.10f

// The settle wobble: while dragging, the coil trails the
// head by a quarter of the drag (it lags behind the hand);
// on release a spring carries the trail back to zero, so
// the lamp settles with a small wobble.
const val WORM_LIGHT_SWAY_DRAG_FACTOR = 0.25f

// The pool's warm white (0xFFFFD18C): warm, never the
// hardware colour, never a setting.
val WORM_LIGHT_WARM = Color(0xFFFFD18C)

/**
 * The lamp's pool of light, in frame UV (y = 0 at the top
 * of the game) — the same values the shader takes as
 * uniforms (uWlCenter, uWlRadii, uWlRot, uWlIntensity) and
 * the overlay draws on the fallback path. Intensity 0 is
 * the lamp off: the shader adds exactly 0.0.
 */
data class WormLightPool(
    val center: Offset,
    val radii: Offset,
    val rotationRad: Float,
    val intensity: Float,
) {
    companion object {
        val OFF = WormLightPool(
            center = Offset(0.5f, WORM_LIGHT_POOL_CENTER_Y),
            radii = Offset(WORM_LIGHT_POOL_RX, WORM_LIGHT_POOL_RY),
            rotationRad = 0f,
            intensity = 0f,
        )
    }
}

/**
 * The lamp's geometry in the player area's px space, pure
 * so it is unit-testable without a Canvas: the game rect
 * (the largest aspect box in the row below the strip), the
 * plug, the coil's spine (one cubic, or two when the head
 * is on the right flank), the lens and its bulb, and the
 * pool in frame UV (plus its px form, for the fallback
 * path). The head is clamped to the perimeter of the
 * display area (the strip plus both flanks), so a stored
 * position from a taller or shorter player area still
 * lands on it, never on the game itself.
 */
/**
 * One leg of the coil's spine: a cubic from the plug (or
 * the previous leg's end) to the head (or the corner).
 */
data class CoilCubic(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val p3: Offset,
)

data class WormLightGeometry(
    val gameRect: Rect,
    val plugRect: Rect,
    val coilP0: Offset,
    val coilP1: Offset,
    val coilP2: Offset,
    val coilP3: Offset,
    val coilAmplitudePx: Float,
    val coilStrokePx: Float,
    val lensCenter: Offset,
    val lensRadii: Offset,
    val lensRotationDeg: Float,
    val lensHalfWidthPx: Float,
    val lensHalfHeightPx: Float,
    val bulbCenter: Offset,
    val bulbRadii: Offset,
    val pool: WormLightPool,
    val poolCenterPx: Offset,
    val poolRadiiPx: Offset,
    val poolRotationDeg: Float,
    // The coil's second leg (right-flank heads only): the
    // cord routes over the top and down that flank, so the
    // spine has a corner at the top of the flank. Null for
    // strip and left-flank heads, whose cord is one cubic.
    val coilSecond: CoilCubic? = null,
    // The cord's 14 loops, split across the legs by length
    // when there are two — so the loops stay evenly spaced
    // however long each leg is.
    val coilTurnsFirst: Int = WORM_LIGHT_COIL_TURNS,
    val coilTurnsSecond: Int = 0,
)

/**
 * Full travel (Robert, 2026-11-13): the head travels the
 * perimeter of the display area — the strip above the
 * game plus the flanks beside it — never on top of the
 * game itself. The clamp is zone-based: a raw position
 * at or above the strip's bottom edge stays in the strip;
 * below it, the head drops into the flank under the
 * finger, or sticks to the strip's bottom edge while the
 * finger is over the game. (A nearest-point clamp would
 * be wrong here: a low drag over the centre of the game
 * is nearly equidistant to the strip bottom and a flank,
 * and would jump the head sideways.) A flank that is too
 * narrow to hold the lens never takes the head — it stays
 * in the strip, exactly as before full travel.
 */
private fun clampHeadToPerimeter(
    raw: Offset,
    boxWidth: Float,
    boxHeight: Float,
    stripPx: Float,
    gameRect: Rect,
    halfW: Float,
    halfH: Float,
): Offset {
    if (raw.y <= stripPx) {
        return Offset(
            raw.x.coerceIn(halfW, boxWidth - halfW),
            raw.y.coerceIn(0f, stripPx),
        )
    }
    val leftMaxX = gameRect.left - halfW
    val rightMinX = gameRect.right + halfW
    val flankBottom = boxHeight - halfH
    return when {
        raw.x <= leftMaxX && leftMaxX > halfW && flankBottom > stripPx -> Offset(
            raw.x.coerceIn(halfW, leftMaxX),
            raw.y.coerceIn(stripPx, flankBottom),
        )
        raw.x >= rightMinX && rightMinX < boxWidth - halfW && flankBottom > stripPx -> Offset(
            raw.x.coerceIn(rightMinX, boxWidth - halfW),
            raw.y.coerceIn(stripPx, flankBottom),
        )
        else -> Offset(raw.x.coerceIn(halfW, boxWidth - halfW), stripPx)
    }
}

private fun coilCubicLength(c: CoilCubic): Float {
    // Control-polygon length: an overestimate of the
    // curve, but proportional — enough to split the loops
    // evenly between the legs.
    fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
    return dist(c.p0, c.p1) + dist(c.p1, c.p2) + dist(c.p2, c.p3)
}

private fun coilTurnSplit(first: CoilCubic, second: CoilCubic?): Pair<Int, Int> {
    if (second == null) return WORM_LIGHT_COIL_TURNS to 0
    val len1 = coilCubicLength(first)
    val len2 = coilCubicLength(second)
    val total = len1 + len2
    if (total <= 0f) return WORM_LIGHT_COIL_TURNS to 0
    val t1 = (WORM_LIGHT_COIL_TURNS * len1 / total)
        .roundToInt().coerceIn(1, WORM_LIGHT_COIL_TURNS - 1)
    return t1 to (WORM_LIGHT_COIL_TURNS - t1)
}

fun wormLightGeometry(
    boxWidth: Float,
    boxHeight: Float,
    head: Offset,
    density: Float,
    gameAspect: Float,
    stripDp: Float,
    swayPx: Float,
): WormLightGeometry {
    val stripPx = stripDp * density
    // The game rect: the largest aspect box in the row below
    // the strip, centred in that row — exactly the frame
    // GameDisplayView lays out (see its strip tests).
    val rowH = boxHeight - stripPx
    val gameW = minOf(boxWidth, rowH * gameAspect)
    val gameH = gameW / gameAspect
    val gameLeft = (boxWidth - gameW) / 2f
    val gameTop = stripPx + (rowH - gameH) / 2f
    val gameRect = Rect(gameLeft, gameTop, gameLeft + gameW, gameTop + gameH)
    // The head, clamped to the perimeter of the display
    // area (see clampHeadToPerimeter): its centre stays in
    // the strip or on a flank, and the lens stays on
    // screen. The sway trails the head — the coil's apex
    // moves with the full sway, the head itself with half
    // of it, so the lamp settles as one piece.
    val lensRx = WORM_LIGHT_LENS_RX_DP * density
    val lensRy = WORM_LIGHT_LENS_RY_DP * density
    val rot = WORM_LIGHT_LENS_ROTATION_DEG * PI.toFloat() / 180f
    val cosR = cos(rot)
    val sinR = sin(rot)
    // The rotated lens's half-extents (its bounding box):
    // sqrt((rx cos)^2 + (ry sin)^2) across, and the same
    // with sin/cos swapped down.
    val halfW = sqrt(lensRx * lensRx * cosR * cosR + lensRy * lensRy * sinR * sinR)
    val halfH = sqrt(lensRx * lensRx * sinR * sinR + lensRy * lensRy * cosR * cosR)
    // The head's unswayed x: the coil's control points are
    // measured from it, so the apex moves with exactly the
    // full sway — the head itself only with half of it (see
    // swayTrailsTheHeadAndTheCoilApex). Measuring from the
    // swayed head would double-count: the sway would reach
    // the apex through the interpolation too.
    val headBaseX = (head.x * boxWidth).coerceIn(halfW, boxWidth - halfW)
    // Full travel (Robert, 2026-11-13): the sway trails the
    // head by half, as before — but the swayed position is
    // clamped to the PERIMETER of the display area (the
    // strip plus both flanks), so a fast drag's sway can
    // never push the head onto the game itself.
    val rawHead = Offset(headBaseX + swayPx * 0.5f, head.y * boxHeight)
    val headPx = clampHeadToPerimeter(
        rawHead, boxWidth, boxHeight, stripPx, gameRect, halfW, halfH,
    )
    // The plug: just left of the display area — the game
    // rect's left edge minus a small gap — centred in the
    // strip. The old anchor was the player area's left edge
    // (x = 0), so the cord ran in from the device screen's
    // edge and along the top edge (Robert, 2026-11-13:
    // "still attached to top and it's too far left"). The
    // plug never leaves the screen: when the game nearly
    // fills the width there is no room left of it, so the
    // plug parks at the edge instead of going off-screen.
    val plugW = WORM_LIGHT_PLUG_W_DP * density
    val plugH = WORM_LIGHT_PLUG_H_DP * density
    val plugGap = WORM_LIGHT_PLUG_GAP_DP * density
    val plugRight = (gameLeft - plugGap).coerceAtLeast(plugW)
    val plugRect = Rect(
        plugRight - plugW, stripPx / 2f - plugH / 2f,
        plugRight, stripPx / 2f + plugH / 2f,
    )
    // The coil's spine, by the head's zone (full travel,
    // Robert, 2026-11-13). A head in the strip keeps the
    // arch over the top, exactly as before full travel (the
    // earlier tests pin these control points). A head on
    // the left flank gets one cubic down that flank, bowed
    // away from the game. A head on the right flank needs
    // TWO cubics — one arching over the top to the corner
    // at the top of that flank, one dropping down it. A
    // single cubic cannot hug the top-right corner: its x
    // lags behind its y as it descends, so the cord would
    // cut across the game's top-right corner. Both legs
    // keep the cord out of the picture: the top leg stays
    // in the strip, the drop stays in the flank (its
    // control points are clamped there, so even a hard
    // sway cannot push it over the game). The sway moves
    // the apex with the head's trail, as before.
    val p0 = Offset(plugRight, stripPx / 2f)
    val bowPx = WORM_LIGHT_COIL_BOW_DP * density
    val ampPx = WORM_LIGHT_COIL_AMP_DP * density
    val strokePx = WORM_LIGHT_COIL_STROKE_DP * density
    val headInStrip = headPx.y <= stripPx
    val headOnLeftFlank = !headInStrip && headPx.x < gameRect.left
    val coilFirst: CoilCubic
    val coilSecond: CoilCubic?
    if (headInStrip) {
        coilFirst = CoilCubic(
            p0,
            Offset(p0.x + (headBaseX - p0.x) * 0.25f + swayPx, stripPx * 0.06f),
            Offset(p0.x + (headBaseX - p0.x) * 0.75f + swayPx, stripPx * 0.06f),
            headPx,
        )
        coilSecond = null
    } else if (headOnLeftFlank) {
        coilFirst = CoilCubic(
            p0,
            Offset(
                (p0.x + (headBaseX - p0.x) * 0.25f - bowPx + swayPx)
                    .coerceIn(strokePx, gameRect.left - ampPx - strokePx),
                p0.y + (headPx.y - p0.y) * 0.25f,
            ),
            Offset(
                (p0.x + (headBaseX - p0.x) * 0.75f - bowPx + swayPx)
                    .coerceIn(strokePx, gameRect.left - ampPx - strokePx),
                p0.y + (headPx.y - p0.y) * 0.75f,
            ),
            headPx,
        )
        coilSecond = null
    } else {
        // The right flank: arch over the top to the corner
        // at the top of that flank, then drop down it.
        val cornerX = (headBaseX + swayPx)
            .coerceIn(gameRect.right + halfW, boxWidth - halfW)
        val corner = Offset(cornerX, stripPx / 2f)
        coilFirst = CoilCubic(
            p0,
            Offset(p0.x + (headBaseX - p0.x) * 0.25f + swayPx, stripPx * 0.06f),
            Offset(p0.x + (headBaseX - p0.x) * 0.75f + swayPx, stripPx * 0.06f),
            corner,
        )
        coilSecond = CoilCubic(
            corner,
            Offset(
                (corner.x + bowPx).coerceIn(
                    gameRect.right + ampPx + strokePx, boxWidth - strokePx,
                ),
                corner.y + (headPx.y - corner.y) * 0.25f,
            ),
            Offset(
                (headPx.x + bowPx).coerceIn(
                    gameRect.right + ampPx + strokePx, boxWidth - strokePx,
                ),
                corner.y + (headPx.y - corner.y) * 0.75f,
            ),
            headPx,
        )
    }
    // The cord's 14 loops, split across the two legs by
    // length when there are two, so the loops stay evenly
    // spaced however long each leg is.
    val (turnsFirst, turnsSecond) = coilTurnSplit(coilFirst, coilSecond)
    // The bulb: inside the lens, toward its top — the bulb
    // sits at the back of the head, shining down. Its local
    // offset is rotated with the lens, so the bulb stays
    // put in the head as the lens angles.
    val bulbLocal = Offset(-lensRx * 0.15f, -lensRy * 0.35f)
    val bulbCenter = Offset(
        headPx.x + bulbLocal.x * cosR - bulbLocal.y * sinR,
        headPx.y + bulbLocal.x * sinR + bulbLocal.y * cosR,
    )
    // The pool: the head's position projected onto the
    // game, clamped so the pool stays on the picture —
    // full travel means the centre tracks the head in both
    // axes now, not just across the strip: drag it low on
    // a flank and the light pools low on that side. The
    // centre's y is the head's height in the game, clamped
    // to [0.20, 0.80] (the default head, above the game,
    // still lands at 0.20, exactly as before). Its long
    // axis tilts toward the head's side (the beam slants
    // from the head) — and mirrors when the head is below
    // the pool's centre, so a low head's slant reads the
    // right way round; the blend is 1 while the head is
    // clearly above the centre, -1 clearly below, 0 level
    // with it.
    val gameCenterX = gameLeft + gameW / 2f
    val poolCenterX = ((headPx.x - gameLeft) / gameW)
        .coerceIn(WORM_LIGHT_POOL_CENTER_X_MIN, WORM_LIGHT_POOL_CENTER_X_MAX)
    val headYinGame = (headPx.y - gameTop) / gameH
    val poolCenterY = headYinGame
        .coerceIn(WORM_LIGHT_POOL_CENTER_Y, WORM_LIGHT_POOL_CENTER_Y_MAX)
    val tiltBlend = ((poolCenterY - headYinGame) / WORM_LIGHT_POOL_TILT_BLEND)
        .coerceIn(-1f, 1f)
    val poolRotation = (gameCenterX - headPx.x) / gameW *
        WORM_LIGHT_POOL_ROTATION_PER_OFFSET * tiltBlend
    val pool = WormLightPool(
        center = Offset(poolCenterX, poolCenterY),
        radii = Offset(WORM_LIGHT_POOL_RX, WORM_LIGHT_POOL_RY),
        rotationRad = poolRotation,
        intensity = 1f,
    )
    return WormLightGeometry(
        gameRect = gameRect,
        plugRect = plugRect,
        coilP0 = coilFirst.p0,
        coilP1 = coilFirst.p1,
        coilP2 = coilFirst.p2,
        coilP3 = coilFirst.p3,
        coilAmplitudePx = WORM_LIGHT_COIL_AMP_DP * density,
        coilStrokePx = WORM_LIGHT_COIL_STROKE_DP * density,
        lensCenter = headPx,
        lensRadii = Offset(lensRx, lensRy),
        lensRotationDeg = WORM_LIGHT_LENS_ROTATION_DEG,
        lensHalfWidthPx = halfW,
        lensHalfHeightPx = halfH,
        bulbCenter = bulbCenter,
        bulbRadii = Offset(WORM_LIGHT_BULB_RX_DP * density, WORM_LIGHT_BULB_RY_DP * density),
        pool = pool,
        poolCenterPx = Offset(
            gameLeft + pool.center.x * gameW,
            gameTop + pool.center.y * gameH,
        ),
        poolRadiiPx = Offset(pool.radii.x * gameW, pool.radii.y * gameH),
        poolRotationDeg = poolRotation * 180f / PI.toFloat(),
        coilSecond = coilSecond,
        coilTurnsFirst = turnsFirst,
        coilTurnsSecond = turnsSecond,
    )
}

/**
 * The coil as a sine ribbon along a cubic spine: the
 * telephone cord's helix, drawn as a wave straddling the
 * spine. The sine is zero at both ends, so the cord
 * attaches cleanly at the plug and at the head, and the
 * turn count is fixed — a longer spine spreads the loops
 * (stretch), a shorter one crowds them (bunch). Pure, so
 * it runs under plain JVM unit tests.
 */
fun coilRibbonPoints(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    turns: Int,
    amplitudePx: Float,
    samples: Int = WORM_LIGHT_COIL_SAMPLES,
): List<Offset> {
    val pts = ArrayList<Offset>(samples + 1)
    for (i in 0..samples) {
        val t = i.toFloat() / samples
        val mt = 1f - t
        // The cubic point.
        val bx = mt * mt * mt * p0.x + 3f * mt * mt * t * p1.x +
            3f * mt * t * t * p2.x + t * t * t * p3.x
        val by = mt * mt * mt * p0.y + 3f * mt * mt * t * p1.y +
            3f * mt * t * t * p2.y + t * t * t * p3.y
        // Its tangent, and the unit normal to it.
        val tx = 3f * mt * mt * (p1.x - p0.x) + 6f * mt * t * (p2.x - p1.x) +
            3f * t * t * (p3.x - p2.x)
        val ty = 3f * mt * mt * (p1.y - p0.y) + 6f * mt * t * (p2.y - p1.y) +
            3f * t * t * (p3.y - p2.y)
        val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
        val nx = -ty / len
        val ny = tx / len
        val s = sin(t * turns * 2f * PI.toFloat())
        pts.add(Offset(bx + nx * amplitudePx * s, by + ny * amplitudePx * s))
    }
    return pts
}

/**
 * The pool's scalar factor at a frame UV, mirroring
 * GRID_FRAGMENT's pool lines exactly (same constants), so
 * the falloff is unit-testable on the JVM: 0.65 at the pool
 * centre (0.30 fall + 0.35 hotspot), 0.0 at and outside
 * the ellipse edge. The shader is the authority; this is
 * its test oracle, not a second implementation — keep the
 * constants in step with the GLSL. The inputs are in frame
 * UV (y = 0 at the top); the rotation is applied in vTex
 * space (y = 0 at the bottom), the same conversion the
 * grid shader makes, so a rotated pool's tilt reads the
 * same way here as on the picture.
 */
fun wormLightPoolFactor(
    center: Offset,
    radii: Offset,
    rotationRad: Float,
    uv: Offset,
): Float {
    val dx = uv.x - center.x
    // Flip y into vTex space (y = 0 at the bottom): the
    // shader's rotation is applied there, so the same flip
    // belongs here or a rotated pool's tilt reads mirrored
    // against the shader. (The grid shader flips the
    // centre's y the same way; flipping both points is the
    // same conversion.)
    val dy = center.y - uv.y
    val c = cos(rotationRad)
    val s = sin(rotationRad)
    val rx = dx * c - dy * s
    val ry = dx * s + dy * c
    val ex = rx / radii.x
    val ey = ry / radii.y
    val e = ex * ex + ey * ey
    val fall = wormLightSmoothstep(1f, 0f, e)
    val hot = wormLightSmoothstep(0.45f, 0f, e)
    return fall * 0.30f + hot * 0.35f
}

private fun wormLightSmoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
fun WormLightOverlay(
    enabled: Boolean,
    // Worm Light realism (Robert, 2026-11-12): the colour of
    // the lamp's hardware — cord, head plastic, plug. Dark
    // medium gray by default; the light itself stays warm
    // white and is never a setting.
    hardwareColor: WormLightColor = WormLightColor.DEFAULT,
    // The game the lamp hangs over: its aspect sets the game
    // rect the pool is measured against (the frame is the
    // largest aspect box in the row below the strip).
    gameAspect: Float = 1.5f,
    // The fallback path (no GL view): the overlay draws the
    // pool itself with BlendMode.Screen. On the GL path the
    // shader adds the pool, and the overlay must not draw
    // it too — the pool would double.
    drawPool: Boolean = false,
    // The pool, reported up to the GL view (GameDisplayView
    // hands it to GameGlView.setWormLight) whenever the head
    // moves, so dragging while paused still moves the pool.
    onPoolChange: (WormLightPool) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var position by remember { mutableStateOf(WORM_LIGHT_DEFAULT_HEAD) }
    var sway by remember { mutableStateOf(0f) }
    var dragStartX by remember { mutableStateOf(0f) }
    var wobbleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.testTag("worm-light")) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // The density for the geometry: BoxWithConstraints
        // does not expose it as a property here, so it comes
        // from the ambient LocalDensity (the same value the
        // Canvas draw scope sees as its own density).
        val density = LocalDensity.current.density
        val g = wormLightGeometry(
            width, height, position, density, gameAspect,
            WORM_LIGHT_STRIP_DP, sway,
        )
        // The pool reaches the GL view on every move — the
        // shader is what lights the picture (see the file
        // header); the overlay only draws the pool itself on
        // the fallback path.
        LaunchedEffect(g.pool) { onPoolChange(g.pool) }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, height) {
                    detectDragGestures(
                        onDragStart = {
                            // A fresh grab cancels any settle
                            // wobble still running.
                            wobbleJob?.cancel()
                            sway = 0f
                            dragStartX = position.x * width
                        },
                        onDragEnd = {
                            // The coil trails the head; on
                            // release a spring carries the
                            // trail back to zero, so the lamp
                            // settles with a small wobble.
                            wobbleJob?.cancel()
                            wobbleJob = scope.launch {
                                val anim = Animatable(sway)
                                anim.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.25f, stiffness = 300f),
                                ) { sway = value }
                            }
                        },
                        onDragCancel = {
                            wobbleJob?.cancel()
                            sway = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Full travel: the raw position is
                            // only clamped to the box here — the
                            // geometry clamps the head to the
                            // perimeter of the display area (the
                            // strip plus both flanks, never onto
                            // the game itself), so the drag and
                            // the clamp stay in one place.
                            val x = (position.x * width + dragAmount.x)
                                .coerceIn(0f, width)
                            val y = (position.y * height + dragAmount.y)
                                .coerceIn(0f, height)
                            position = Offset(x / width, y / height)
                            sway = (x - dragStartX) * WORM_LIGHT_SWAY_DRAG_FACTOR
                        },
                    )
                },
        ) {
            val hw = Color(hardwareColor.argb).copy(
                alpha = if (hardwareColor.translucent) 0.6f else 1f,
            )
            // The plug just left of the game rect, in the
            // strip zone — the EXT port, where the cord
            // enters the player.
            drawRoundRect(
                color = hw,
                topLeft = Offset(g.plugRect.left, g.plugRect.top),
                size = Size(g.plugRect.width, g.plugRect.height),
                cornerRadius = CornerRadius(3f * density, 3f * density),
            )
            // The coil: the sine ribbon along the cubic spine
            // — a telephone cord, not a straight line. A head
            // on the right flank routes over the top and down
            // that flank, so the spine has a second cubic
            // there (the loops split across the two by
            // length, so they stay evenly spaced).
            fun drawCoil(cubic: CoilCubic, turns: Int) {
                val ribbon = Path().apply {
                    val pts = coilRibbonPoints(
                        cubic.p0, cubic.p1, cubic.p2, cubic.p3,
                        turns, g.coilAmplitudePx,
                    )
                    moveTo(pts.first().x, pts.first().y)
                    for (p in pts.drop(1)) lineTo(p.x, p.y)
                }
                drawPath(
                    ribbon,
                    hw,
                    style = Stroke(width = g.coilStrokePx, cap = StrokeCap.Round),
                )
            }
            drawCoil(
                CoilCubic(g.coilP0, g.coilP1, g.coilP2, g.coilP3),
                g.coilTurnsFirst,
            )
            g.coilSecond?.let { drawCoil(it, g.coilTurnsSecond) }
            // A soft glow around the head itself — the lens
            // spilling a little light where it sits.
            val glowRadius = g.lensRadii.x * 1.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        WORM_LIGHT_WARM.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    center = g.lensCenter,
                    radius = glowRadius,
                ),
                center = g.lensCenter,
                radius = glowRadius,
            )
            // The lens: a clear oval, rotated so the head
            // angles down at the game — a faint fill (the
            // plastic) with its rim in the hardware colour.
            // The power light draws over the player area's
            // top-left corner after this overlay, so the coil
            // passing near it sits behind the light, not over
            // it.
            rotate(g.lensRotationDeg, pivot = g.lensCenter) {
                drawOval(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(
                        g.lensCenter.x - g.lensRadii.x,
                        g.lensCenter.y - g.lensRadii.y,
                    ),
                    size = Size(g.lensRadii.x * 2f, g.lensRadii.y * 2f),
                )
                drawOval(
                    color = hw,
                    topLeft = Offset(
                        g.lensCenter.x - g.lensRadii.x,
                        g.lensCenter.y - g.lensRadii.y,
                    ),
                    size = Size(g.lensRadii.x * 2f, g.lensRadii.y * 2f),
                    style = Stroke(width = 2f * density),
                )
            }
            // The bulb: bright white, visible inside the clear
            // lens, with a hot core (the filament).
            drawOval(
                color = Color.White,
                topLeft = Offset(
                    g.bulbCenter.x - g.bulbRadii.x,
                    g.bulbCenter.y - g.bulbRadii.y,
                ),
                size = Size(g.bulbRadii.x * 2f, g.bulbRadii.y * 2f),
            )
            drawCircle(
                color = Color(0xFFFFF6D8),
                radius = g.bulbRadii.x * 0.45f,
                center = g.bulbCenter,
            )
            // Fallback path only: the pool, added with Screen
            // so it lights the picture — the same falloff the
            // shader adds on the GL path. Clipped to the game
            // rect, so it never spills onto the black bars or
            // the flank controls.
            if (drawPool) {
                clipRect(
                    g.gameRect.left, g.gameRect.top,
                    g.gameRect.right, g.gameRect.bottom,
                ) {
                    rotate(g.poolRotationDeg, pivot = g.poolCenterPx) {
                        val r = maxOf(g.poolRadiiPx.x, g.poolRadiiPx.y)
                        scale(
                            g.poolRadiiPx.x / r,
                            g.poolRadiiPx.y / r,
                            pivot = g.poolCenterPx,
                        ) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to WORM_LIGHT_WARM.copy(alpha = 0.65f),
                                        0.4f to WORM_LIGHT_WARM.copy(alpha = 0.45f),
                                        0.75f to WORM_LIGHT_WARM.copy(alpha = 0.15f),
                                        1f to Color.Transparent,
                                    ),
                                    center = g.poolCenterPx,
                                    radius = r,
                                ),
                                center = g.poolCenterPx,
                                radius = r,
                                blendMode = BlendMode.Screen,
                            )
                        }
                    }
                }
            }
        }
    }
}
