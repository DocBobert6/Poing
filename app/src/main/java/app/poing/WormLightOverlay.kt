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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.abs
import kotlin.math.atan2
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
 * of the picture. Since the improvements (Robert,
 * 2026-11-13), the head is FREE: it can sit anywhere in
 * the player area, including over the middle of the game
 * — that reverses the "never on top of the game" rule,
 * by his word — and the pool's brightest point can sit
 * at the centre of the picture. The head is a CLEAR OVAL
 * LENS with the bulb visible inside as a bright point,
 * plus a soft glow around it; the neck is a TIGHT HELIX
 * COIL (a telephone cord), not a straight line. The light
 * is ADDITIVE: an ELLIPTICAL warm-white pool, brightest
 * at the head, smooth falloff to nothing at the edges,
 * with a hotspot where the beam lands most directly.
 * When the head is dragged, the pool moves and skews
 * with it, and the coil flexes (stretch on one side,
 * bunch on the other — the cord has a fixed number of
 * coils, so a longer spine spreads them) with a tiny
 * settle wobble when released. The lens is not fixed any
 * more: it tilts toward where the light lands, so the
 * head and its pool always agree — and a tap on the head
 * switches it into aim mode, where a drag rotates the
 * head and the pool instead of moving the head. While a
 * drag is in progress the head draws above the controls,
 * so it never disappears behind the D-pad mid-drag.
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

// The oval lens, in dp: a clear oval, longer than tall.
// Its rotation follows where the light lands (see
// wormLightGeometry) — it is not a fixed tilt any more:
// the fixed 35 degrees pointed away from the game on the
// right side while the pool shone toward it (Robert's
// 2026-11-13 video, 00:14).
const val WORM_LIGHT_LENS_RX_DP = 22f
const val WORM_LIGHT_LENS_RY_DP = 14f

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
// Free head (Robert, 2026-11-13): the head can sit over
// the game itself now, so the centre can reach (0.5, 0.5)
// — the middle of the picture, which the perimeter lock
// could never reach.
const val WORM_LIGHT_POOL_CENTER_Y = 0.20f
const val WORM_LIGHT_POOL_CENTER_Y_MAX = 0.80f
const val WORM_LIGHT_POOL_RX = 0.55f
const val WORM_LIGHT_POOL_RY = 0.42f
const val WORM_LIGHT_POOL_CENTER_X_MIN = 0.15f
const val WORM_LIGHT_POOL_CENTER_X_MAX = 0.85f
const val WORM_LIGHT_POOL_ROTATION_PER_OFFSET = 0.6f
// The pool's peak (retune, Robert, 2026-11-13): 0.30 —
// 0.14 fall + 0.16 hotspot — down from 0.65. At 0.65 the
// lightest two Jade shades clipped to white at the pool
// core (his video: the top of Super Mario Land 2 bleached
// to cream, core measured R 0.97). The lightest Jade shade
// clips above a peak of 0.353, so 0.30 keeps it at 0.95
// with all four shades distinct. The fall/hot balance is
// unchanged, and the shader (DisplayShaders) carries the
// same two numbers — keep them in step.
const val WORM_LIGHT_POOL_FALL = 0.14f
const val WORM_LIGHT_POOL_HOT = 0.16f
const val WORM_LIGHT_POOL_PEAK = WORM_LIGHT_POOL_FALL + WORM_LIGHT_POOL_HOT
// Tap-to-aim (Robert, 2026-11-13): a press on the head
// with less than this much movement is a tap, and toggles
// aim mode. The tap must start on the lens itself, within
// the lens's half-extents plus this grab margin.
const val WORM_LIGHT_TAP_MAX_DP = 12f
const val WORM_LIGHT_TAP_GRAB_EXTRA_DP = 12f
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
 * is on the right flank — or a direct drape when the head
 * is over the game itself), the lens and its bulb, and the
 * pool in frame UV (plus its px form, for the fallback
 * path). The head is FREE (Robert, 2026-11-13 — the
 * "never on top of the game" rule is reversed): it can sit
 * anywhere in the player area, clamped only to the box,
 * so a stored position from a taller or shorter player
 * area still lands on screen. The lens rotation follows
 * the pool's tilt, so the head and its light always agree;
 * in aim mode both follow the aim angle instead.
 *
 * @param aimAngleRad the head's angle in aim mode (the
 * direction from the head to the finger, radians, screen
 * space), or null to follow the head's position.
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
 * The lens's bounding half-extents for a rotation: the
 * rotated oval's half-width across and half-height down.
 */
private fun lensHalfExtents(rotationRad: Float, density: Float): Pair<Float, Float> {
    val lensRx = WORM_LIGHT_LENS_RX_DP * density
    val lensRy = WORM_LIGHT_LENS_RY_DP * density
    val cosR = cos(rotationRad)
    val sinR = sin(rotationRad)
    val halfW = sqrt(lensRx * lensRx * cosR * cosR + lensRy * lensRy * sinR * sinR)
    val halfH = sqrt(lensRx * lensRx * sinR * sinR + lensRy * lensRy * cosR * cosR)
    return halfW to halfH
}

/**
 * The pool for a head position: its centre is the head's
 * position projected onto the game, clamped so the pool
 * stays on the picture, and its long axis tilts toward the
 * head's side — mirroring when the head is below the
 * pool's centre, so a low head's slant reads the right way
 * round; the blend is 1 while the head is clearly above
 * the centre, -1 clearly below, 0 level with it. In aim
 * mode the tilt follows the aim angle instead of the
 * position — the centre still follows the head.
 */
private fun poolForHead(
    headPx: Offset,
    gameRect: Rect,
    aimAngleRad: Float?,
): WormLightPool {
    val gameCenterX = gameRect.center.x
    val poolCenterX = ((headPx.x - gameRect.left) / gameRect.width)
        .coerceIn(WORM_LIGHT_POOL_CENTER_X_MIN, WORM_LIGHT_POOL_CENTER_X_MAX)
    val headYinGame = (headPx.y - gameRect.top) / gameRect.height
    val poolCenterY = headYinGame
        .coerceIn(WORM_LIGHT_POOL_CENTER_Y, WORM_LIGHT_POOL_CENTER_Y_MAX)
    val rotationRad = if (aimAngleRad != null) {
        aimAngleRad
    } else {
        val tiltBlend = ((poolCenterY - headYinGame) / WORM_LIGHT_POOL_TILT_BLEND)
            .coerceIn(-1f, 1f)
        (gameCenterX - headPx.x) / gameRect.width *
            WORM_LIGHT_POOL_ROTATION_PER_OFFSET * tiltBlend
    }
    return WormLightPool(
        center = Offset(poolCenterX, poolCenterY),
        radii = Offset(WORM_LIGHT_POOL_RX, WORM_LIGHT_POOL_RY),
        rotationRad = rotationRad,
        intensity = 1f,
    )
}

/**
 * Free head (Robert, 2026-11-13 — the "never on top of
 * the game" rule is reversed): the head can sit anywhere
 * in the player area. The clamp keeps the lens on screen
 * sideways — its centre stays a half-width from the edges
 * — and lets its centre reach the top and bottom edges
 * (as it always could in the strip: the lens may poke out
 * a few px there, exactly as the default head already
 * does). The rotation follows the head, so the half-width
 * moves a few px as the head moves — iterate to a fixed
 * point (three passes converge: each pass moves the head
 * less). In aim mode the rotation is the aim angle, not
 * the position, so the half-width is fixed and one pass
 * suffices — the loop covers both.
 */
private fun clampHeadToBox(
    raw: Offset,
    boxWidth: Float,
    boxHeight: Float,
    gameRect: Rect,
    density: Float,
    aimAngleRad: Float?,
): Offset {
    var head = raw
    repeat(3) {
        val rot = poolForHead(head, gameRect, aimAngleRad).rotationRad
        val (halfW, _) = lensHalfExtents(rot, density)
        val clamped = Offset(
            head.x.coerceIn(halfW, boxWidth - halfW),
            head.y.coerceIn(0f, boxHeight),
        )
        if (clamped == head) return clamped
        head = clamped
    }
    return head
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
    aimAngleRad: Float? = null,
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
    // The head, free (Robert, 2026-11-13): anywhere in the
    // player area, clamped only to the box (see
    // clampHeadToBox). The sway trails the head — the
    // coil's apex moves with the full sway, the head itself
    // with half of it, so the lamp settles as one piece.
    // headBase is the unswayed head: the coil's control
    // points are measured from it, so the apex moves with
    // exactly the full sway (see
    // swayTrailsTheHeadAndTheCoilApex). Measuring from the
    // swayed head would double-count: the sway would reach
    // the apex through the interpolation too.
    val headBase = clampHeadToBox(
        Offset(head.x * boxWidth, head.y * boxHeight),
        boxWidth, boxHeight, gameRect, density, aimAngleRad,
    )
    val headPx = clampHeadToBox(
        Offset(headBase.x + swayPx * 0.5f, head.y * boxHeight),
        boxWidth, boxHeight, gameRect, density, aimAngleRad,
    )
    // The lens rotation follows the pool's tilt (see
    // poolForHead): the head and its light always agree.
    // In aim mode both follow the aim angle instead.
    val pool = poolForHead(headPx, gameRect, aimAngleRad)
    val rot = pool.rotationRad
    val lensRx = WORM_LIGHT_LENS_RX_DP * density
    val lensRy = WORM_LIGHT_LENS_RY_DP * density
    val cosR = cos(rot)
    val sinR = sin(rot)
    val (halfW, halfH) = lensHalfExtents(rot, density)
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
    // sway cannot push it over the game). A head OVER the
    // game (free head, Robert, 2026-11-13) gets ONE cubic
    // straight from the plug to the head — a cord draped
    // over the screen, accepted by his word. The sway
    // moves the apex with the head's trail, as before.
    val p0 = Offset(plugRight, stripPx / 2f)
    val bowPx = WORM_LIGHT_COIL_BOW_DP * density
    val ampPx = WORM_LIGHT_COIL_AMP_DP * density
    val strokePx = WORM_LIGHT_COIL_STROKE_DP * density
    val headInStrip = headPx.y <= stripPx
    val headOnLeftFlank = !headInStrip && headPx.x < gameRect.left
    val headOnRightFlank = !headInStrip && headPx.x > gameRect.right
    val coilFirst: CoilCubic
    val coilSecond: CoilCubic?
    if (headInStrip) {
        coilFirst = CoilCubic(
            p0,
            Offset(p0.x + (headBase.x - p0.x) * 0.25f + swayPx, stripPx * 0.06f),
            Offset(p0.x + (headBase.x - p0.x) * 0.75f + swayPx, stripPx * 0.06f),
            headPx,
        )
        coilSecond = null
    } else if (headOnLeftFlank) {
        coilFirst = CoilCubic(
            p0,
            Offset(
                (p0.x + (headBase.x - p0.x) * 0.25f - bowPx + swayPx)
                    .coerceIn(strokePx, gameRect.left - ampPx - strokePx),
                p0.y + (headPx.y - p0.y) * 0.25f,
            ),
            Offset(
                (p0.x + (headBase.x - p0.x) * 0.75f - bowPx + swayPx)
                    .coerceIn(strokePx, gameRect.left - ampPx - strokePx),
                p0.y + (headPx.y - p0.y) * 0.75f,
            ),
            headPx,
        )
        coilSecond = null
    } else if (headOnRightFlank) {
        // The right flank: arch over the top to the corner
        // at the top of that flank, then drop down it.
        val cornerX = (headBase.x + swayPx)
            .coerceIn(gameRect.right + halfW, boxWidth - halfW)
        val corner = Offset(cornerX, stripPx / 2f)
        coilFirst = CoilCubic(
            p0,
            Offset(p0.x + (headBase.x - p0.x) * 0.25f + swayPx, stripPx * 0.06f),
            Offset(p0.x + (headBase.x - p0.x) * 0.75f + swayPx, stripPx * 0.06f),
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
    } else {
        // Over the game: one cubic straight from the plug
        // to the head — the cord drapes over the screen.
        coilFirst = CoilCubic(
            p0,
            Offset(p0.x + (headBase.x - p0.x) * 0.25f + swayPx, p0.y + (headPx.y - p0.y) * 0.25f),
            Offset(p0.x + (headBase.x - p0.x) * 0.75f + swayPx, p0.y + (headPx.y - p0.y) * 0.75f),
            headPx,
        )
        coilSecond = null
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
    // The pool is computed above, from the head (see
    // poolForHead): its centre tracks the head in both
    // axes, its tilt follows the head's side — or the aim
    // angle in aim mode.
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
        lensRotationDeg = rot * 180f / PI.toFloat(),
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
        poolRotationDeg = pool.rotationRad * 180f / PI.toFloat(),
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
 * the falloff is unit-testable on the JVM: 0.30 at the pool
 * centre (0.14 fall + 0.16 hotspot), 0.0 at and outside
 * the ellipse edge. Retune (Robert, 2026-11-13): the peak
 * was 0.65 (0.30 fall + 0.35 hotspot) and bleached the
 * lightest two Jade shades to white at the pool core. The
 * shader is the authority; this is its test oracle, not a
 * second implementation — keep the constants in step with
 * the GLSL. The inputs are in frame UV (y = 0 at the
 * top); the rotation is applied in vTex space (y = 0 at
 * the bottom), the same conversion the grid shader makes,
 * so a rotated pool's tilt reads the same way here as on
 * the picture.
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
    return fall * WORM_LIGHT_POOL_FALL + hot * WORM_LIGHT_POOL_HOT
}

private fun wormLightSmoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The lamp's head as drawn on a Canvas, so the overlay and
 * the drag layer in GameScreen can never drift: one set of
 * numbers, one drawing helper ([DrawScope.drawWormLightHead]).
 * Everything is in the player area's px space.
 */
data class WormLightDragHead(
    val lensCenter: Offset,
    val lensRadii: Offset,
    val lensRotationDeg: Float,
    val bulbCenter: Offset,
    val bulbRadii: Offset,
    val glowRadiusPx: Float,
    val rimStrokePx: Float,
    val aimTickPx: Float,
    val hardwareColorArgb: Int,
    val hardwareTranslucent: Boolean,
    val aimMode: Boolean,
)

/**
 * Draws the lamp's head: the soft glow, the lens fill (a
 * touch brighter in aim mode, so the mode is visible),
 * the rim, the bulb with its hot core, and — in aim mode
 * only — a short tick from the lens edge outward along
 * the lens's long axis, pointing where the head is aimed.
 */
fun DrawScope.drawWormLightHead(head: WormLightDragHead) {
    val hardware = Color(head.hardwareColorArgb)
    rotate(head.lensRotationDeg, pivot = head.lensCenter) {
        // The glow: a wide, faint halo of the same warm
        // white as the pool.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to WORM_LIGHT_WARM.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                ),
                center = head.lensCenter,
                radius = head.glowRadiusPx,
            ),
            center = head.lensCenter,
            radius = head.glowRadiusPx,
        )
        // The lens: clear plastic — a faint white fill, a
        // little brighter in aim mode so the mode reads,
        // with its rim in the hardware colour (the bulb
        // stays visible through the fill).
        drawOval(
            color = Color.White.copy(alpha = if (head.aimMode) 0.30f else 0.14f),
            topLeft = head.lensCenter - head.lensRadii,
            size = Size(head.lensRadii.x * 2f, head.lensRadii.y * 2f),
        )
        // The rim.
        drawOval(
            color = if (head.hardwareTranslucent) Color.White.copy(alpha = 0.5f) else hardware,
            topLeft = head.lensCenter - head.lensRadii,
            size = Size(head.lensRadii.x * 2f, head.lensRadii.y * 2f),
            style = Stroke(width = head.rimStrokePx),
        )
    }
    // The bulb: a bright warm point with a hot core, drawn
    // unrotated (it is a point, not a direction).
    drawCircle(
        color = Color(0xFFFFF3C4),
        radius = head.bulbRadii.x,
        center = head.bulbCenter,
    )
    drawCircle(
        color = Color.White,
        radius = head.bulbRadii.x * 0.5f,
        center = head.bulbCenter,
    )
    if (head.aimMode) {
        // The aim tick: a short warm line from the lens edge
        // outward along the lens's long axis.
        val rotRad = head.lensRotationDeg * PI.toFloat() / 180f
        val dir = Offset(cos(rotRad), sin(rotRad))
        val start = head.lensCenter + dir * head.lensRadii.x
        val end = start + dir * head.aimTickPx
        drawLine(
            color = WORM_LIGHT_WARM.copy(alpha = 0.7f),
            start = start,
            end = end,
            strokeWidth = head.rimStrokePx,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Builds the drag head from a geometry: the same numbers
 * the overlay's own Canvas draws, so the drag layer in
 * GameScreen can never drift from it. One builder serves
 * both the composable body and the gesture handler (which
 * reports synchronously from the live position — see
 * below).
 */
private fun wormLightDragHead(
    g: WormLightGeometry,
    density: Float,
    hardwareColor: WormLightColor,
    aimMode: Boolean,
) = WormLightDragHead(
    lensCenter = g.lensCenter,
    lensRadii = g.lensRadii,
    lensRotationDeg = g.lensRotationDeg,
    bulbCenter = g.bulbCenter,
    bulbRadii = g.bulbRadii,
    glowRadiusPx = g.lensRadii.x * 1.8f,
    rimStrokePx = 2f * density,
    aimTickPx = g.lensRadii.x * 0.55f,
    hardwareColorArgb = hardwareColor.argb,
    hardwareTranslucent = hardwareColor.translucent,
    aimMode = aimMode,
)

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
    // The head, reported up to the player while a drag is
    // in progress (null otherwise), so the player can draw
    // it above the controls — a dragged head never
    // disappears behind the D-pad.
    onDragHeadChange: (WormLightDragHead?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var position by remember { mutableStateOf(WORM_LIGHT_DEFAULT_HEAD) }
    var sway by remember { mutableStateOf(0f) }
    var dragStartX by remember { mutableStateOf(0f) }
    var wobbleJob by remember { mutableStateOf<Job?>(null) }
    // Tap-to-aim (Robert, 2026-11-13): a tap on the head
    // toggles aim mode, where a drag rotates the head and
    // the pool instead of moving the head. The aim angle is
    // the direction from the head to the finger.
    var aimMode by remember { mutableStateOf(false) }
    var aimAngleRad by remember { mutableStateOf(0f) }
    var pressOnHead by remember { mutableStateOf(false) }
    var dragDistancePx by remember { mutableStateOf(0f) }
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
            aimAngleRad = if (aimMode) aimAngleRad else null,
        )
        // The pool reaches the GL view on every move — the
        // shader is what lights the picture (see the file
        // header); the overlay only draws the pool itself on
        // the fallback path.
        LaunchedEffect(g.pool) { onPoolChange(g.pool) }
        // The head, for the drag layer and for this Canvas:
        // the same numbers both places (see
        // wormLightDragHead). It is reported to the player
        // SYNCHRONOUSLY from the gesture callbacks below, not
        // through a LaunchedEffect on a dragging flag — a fast
        // drag can end before such an effect runs, and the
        // non-null report would be swallowed. A tap never
        // calls onDrag, so it never reports — no flash.
        val dragHead = wormLightDragHead(g, density, hardwareColor, aimMode)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, height) {
                    detectDragGestures(
                        onDragStart = { press ->
                            // A fresh grab cancels any settle
                            // wobble still running.
                            wobbleJob?.cancel()
                            sway = 0f
                            dragStartX = position.x * width
                            // Was this press on the head? The
                            // geometry here is computed from
                            // the live position state — never
                            // the g captured by this closure,
                            // which is stale by the time a
                            // drag starts.
                            val pressGeom = wormLightGeometry(
                                width, height, position, density,
                                gameAspect, WORM_LIGHT_STRIP_DP, 0f,
                                aimAngleRad = if (aimMode) aimAngleRad else null,
                            )
                            val (halfW, halfH) = lensHalfExtents(
                                pressGeom.lensRotationDeg * PI.toFloat() / 180f,
                                density,
                            )
                            val grab = WORM_LIGHT_TAP_GRAB_EXTRA_DP * density
                            pressOnHead =
                                abs(press.x - pressGeom.lensCenter.x) <= halfW + grab &&
                                abs(press.y - pressGeom.lensCenter.y) <= halfH + grab
                            dragDistancePx = 0f
                        },
                        onDragEnd = {
                            // A tap on the head toggles aim
                            // mode; a real move drag settles
                            // with a wobble. An aim-mode drag
                            // neither toggles nor wobbles.
                            if (pressOnHead &&
                                dragDistancePx <= WORM_LIGHT_TAP_MAX_DP * density
                            ) {
                                if (!aimMode) {
                                    // Entering aim mode: start
                                    // from where the lens
                                    // already points, so it
                                    // does not jump — computed
                                    // fresh from the live
                                    // position, never the g
                                    // captured by this closure.
                                    aimAngleRad = wormLightGeometry(
                                        width, height, position, density,
                                        gameAspect, WORM_LIGHT_STRIP_DP, 0f,
                                    ).pool.rotationRad
                                }
                                aimMode = !aimMode
                            } else if (!aimMode &&
                                dragDistancePx > WORM_LIGHT_TAP_MAX_DP * density
                            ) {
                                // The coil trails the head; on
                                // release a spring carries the
                                // trail back to zero, so the
                                // lamp settles with a small
                                // wobble.
                                wobbleJob?.cancel()
                                wobbleJob = scope.launch {
                                    val anim = Animatable(sway)
                                    anim.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.25f, stiffness = 300f),
                                    ) { sway = value }
                                }
                            }
                            // The drag is over: the player
                            // clears its drag-layer head, so
                            // the overlay's own head (below
                            // the controls) is the only one.
                            onDragHeadChange(null)
                        },
                        onDragCancel = {
                            wobbleJob?.cancel()
                            sway = 0f
                            onDragHeadChange(null)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDistancePx += dragAmount.getDistance()
                            if (aimMode) {
                                // Aim mode: the drag rotates
                                // the head and the pool — the
                                // head never moves. The angle
                                // is the direction from the
                                // head to the finger; a press
                                // need not start on the head.
                                // The head's centre is computed
                                // fresh from the live position
                                // — never the g captured by
                                // this closure, which is stale.
                                val geom = wormLightGeometry(
                                    width, height, position, density,
                                    gameAspect, WORM_LIGHT_STRIP_DP, sway,
                                    aimAngleRad = aimAngleRad,
                                )
                                val finger = change.position
                                val dx = finger.x - geom.lensCenter.x
                                val dy = finger.y - geom.lensCenter.y
                                if (sqrt(dx * dx + dy * dy) >= 1f) {
                                    aimAngleRad = atan2(dy, dx)
                                }
                            } else {
                                // The raw position is only
                                // clamped to the box here — the
                                // geometry clamps the head to
                                // the box (free head, Robert,
                                // 2026-11-13), so the drag and
                                // the clamp stay in one place.
                                val x = (position.x * width + dragAmount.x)
                                    .coerceIn(0f, width)
                                val y = (position.y * height + dragAmount.y)
                                    .coerceIn(0f, height)
                                position = Offset(x / width, y / height)
                                sway = (x - dragStartX) * WORM_LIGHT_SWAY_DRAG_FACTOR
                            }
                            // Report the head synchronously, on
                            // the first real movement — not in
                            // onDragStart, so a tap never flashes
                            // the drag layer, and not through a
                            // LaunchedEffect, which a fast drag
                            // can outrun. The geometry is
                            // computed fresh from the live
                            // position and the new aim angle, so
                            // the reported head is the head as
                            // drawn after this movement.
                            val geom = wormLightGeometry(
                                width, height, position, density,
                                gameAspect, WORM_LIGHT_STRIP_DP, sway,
                                aimAngleRad = if (aimMode) aimAngleRad else null,
                            )
                            onDragHeadChange(
                                wormLightDragHead(geom, density, hardwareColor, aimMode),
                            )
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
            // The head itself — drawn by the shared helper,
            // so the drag layer in GameScreen draws the same
            // head. The power light draws over the player
            // area's top-left corner after this overlay, so
            // the coil passing near it sits behind the light,
            // not over it.
            drawWormLightHead(dragHead)
            // Fallback path only: the pool, added with Screen
            // so it lights the picture — the same falloff the
            // shader adds on the GL path (peak 0.30, retuned
            // 2026-11-13 — stops scaled from the old 0.65 peak
            // by 0.30/0.65: 0.30, 0.21, 0.07). Clipped to the
            // game rect, so it never spills onto the black
            // bars or the flank controls.
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
                                        0f to WORM_LIGHT_WARM.copy(alpha = 0.30f),
                                        0.4f to WORM_LIGHT_WARM.copy(alpha = 0.21f),
                                        0.75f to WORM_LIGHT_WARM.copy(alpha = 0.07f),
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
