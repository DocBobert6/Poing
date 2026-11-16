package app.poing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Alpha.10 — the joystick option for the left-control slot,
 * ported from HowlingDog's PsStick
 * (app/howlingdog/MainActivity.kt:6028), which itself keeps
 * Notscape Mariner's proportions exact.
 *
 * PlayStation-style: a filled base disc with a smaller filled
 * nub riding inside it. Mariner proportions, kept exact: the
 * nub is 0.55x the base diameter and rides at 0.45x the finger
 * offset (clamped to the base radius), so at full deflection
 * its edge sits flush with the base edge (0.45 + 0.55 = 1.0).
 * The discs are flat colours in the per-system theme — the
 * base in the well colour, the nub in the cross colour, the
 * pressed colour as a hairline — where HowlingDog uses
 * vertical gradients; Poing's hardware look is flat, so the
 * shape is the port and the shading follows Poing.
 *
 * Behaviour: fixed in place (it never follows the thumb).
 * Touch down inside the base (plus the grab margin) and drag;
 * the nub follows and the direction comes from StickInput's
 * square-gate rule. Release snaps the nub back to centre and
 * clears the direction. A cancelled gesture clears it too, in
 * the finally block — a stuck direction is never left behind.
 * Only one pointer is tracked, so a second thumb on the
 * buttons cannot steal the stick. A drag that runs off the
 * edge keeps its direction, clamped by updateKnob.
 *
 * It IS a D-pad at its core: the direction mask it reports
 * is the same joypad mask the D-pad reports. The click
 * follows HowlingDog's ActuationDetector — it fires only
 * when the stick crosses an actuation threshold (neutral ->
 * a direction, or one direction -> another). A release back
 * to neutral stays silent.
 *
 * HowlingDog's DragTracker is deliberately not ported: the
 * single-pointer awaitFirstDown loop below gives the same
 * semantics — the tracked pointer is followed by id, so a
 * face button lifting cannot drop the stick mid-drag, and
 * exit is ignored (a drag off the edge keeps its direction,
 * clamped) — with Poing's own gesture idiom, the one
 * DpadControl already uses.
 *
 * The input travel is tuned against the old knob radius and
 * must not move: the dead zone and the diagonal band are
 * measured in units of that travel (see StickInput).
 */
@Composable
fun StickControl(
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    // Alpha.10 — haptics, the ActuationDetector rule: fires
    // only on neutral -> direction or direction -> direction,
    // never on release. The D-pad's onDirectionChange has the
    // same meaning; the caller passes the same click.
    onDirectionChange: (() -> Unit)? = null,
    // Per-system style (ControlThemes): the base is the well
    // colour, the nub is the cross colour, the hairline is the
    // pressed colour. Defaults are the Phase 5 silver-on-dark
    // look, so the stick sits in the theme like the D-pad.
    baseColor: Color = Color(0xFF1A1A1A),
    nubColor: Color = PoingSilver,
    accentColor: Color = PoingPurple,
    sizeDp: Dp = 144.dp,
    grabMarginDp: Dp = 8.dp,
) {
    var sizePx by remember { mutableStateOf(0f) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val grabPx = with(density) { grabMarginDp.toPx() }

    // Geometry in px, from the measured touch square: the base
    // radius, the maximum travel, and the nub radius. Null
    // until the first layout pass.
    fun geometry(): Triple<Float, Float, Float>? {
        if (sizePx <= 0f) return null
        val baseR = sizePx / 2f - grabPx
        if (baseR <= 0f) return null
        val travelKnobR = baseR * 0.38f
        return Triple(baseR, baseR - travelKnobR, baseR * 0.55f)
    }

    Box(
        modifier = Modifier
            .testTag("stick")
            .size(sizeDp)
            .onSizeChanged { sizePx = it.width.toFloat() }
            // Keyed on [paused]: pausing restarts the loop, and
            // the finally block below clears the direction, so a
            // stick held when the pause overlay appears cannot
            // stay stuck — the same rule as the D-pad's loop.
            .pointerInput(paused) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val id = down.id
                    var lastMask = 0
                    fun setDirection(mask: Int) {
                        if (mask != lastMask) {
                            val previous = lastMask
                            lastMask = mask
                            held.setDirections(mask)
                            onInputChanged()
                            if (ActuationDetector.isActuation(previous, mask)) {
                                onDirectionChange?.invoke()
                            }
                        }
                    }
                    fun updateKnob(position: Offset) {
                        val (baseR, maxTravel, _) = geometry() ?: return
                        // The touch area is the sizePx square, so
                        // its centre is sizePx / 2. Do NOT use an
                        // unqualified `size` here: it would resolve
                        // to the Dp parameter, not the measured
                        // pixels (the HowlingDog comment that broke
                        // CI twice).
                        val centre = Offset(sizePx / 2f, sizePx / 2f)
                        val raw = position - centre
                        // Direction: the drag normalised by the
                        // input travel, so the dead zone and the
                        // 0.35 square-gate band behave exactly as
                        // the stick's math defines them.
                        val (cx, cy) = StickInput.clamp(
                            raw.x / maxTravel,
                            raw.y / maxTravel,
                        )
                        // Nub visual (Mariner StickSurface): the
                        // nub rides at 0.45x the finger offset,
                        // clamped to the base radius, so at full
                        // deflection its edge sits flush with the
                        // base edge (0.45 + 0.55 = 1.0).
                        val rawMag = sqrt(raw.x * raw.x + raw.y * raw.y)
                        val clamped = if (rawMag > baseR) {
                            raw * (baseR / rawMag)
                        } else {
                            raw
                        }
                        knob = clamped * 0.45f
                        setDirection(StickInput.direction(cx, cy))
                    }
                    try {
                        updateKnob(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == id }
                                ?: continue
                            if (!change.pressed) break
                            updateKnob(change.position)
                        }
                    } finally {
                        // Release or cancel: the nub snaps back
                        // and the direction clears. A Cancel ends
                        // the drag through here; a scope restart
                        // with no drag is a no-op (the direction
                        // is already 0, so setDirection stays
                        // silent).
                        knob = Offset.Zero
                        setDirection(0)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val (baseR, _, knobR) = geometry() ?: Triple(0f, 0f, 0f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (baseR <= 0f) return@Canvas
            val centre = Offset(size.width / 2f, size.height / 2f)
            // Soft drop shadow under the base (Mariner's frost
            // bar carries a 6dp elevation; the game area is dark,
            // so this only reads on bright game art).
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = baseR,
                center = centre + Offset(0f, 2.dp.toPx()),
            )
            // Base: a filled disc in the well colour, with the
            // pressed colour as a hairline.
            drawCircle(color = baseColor, radius = baseR, center = centre)
            drawCircle(
                color = accentColor.copy(alpha = 0.6f),
                radius = baseR,
                center = centre,
                style = Stroke(width = 1.dp.toPx()),
            )
            // Nub: a smaller filled disc riding inside the base,
            // in the cross colour, with the same hairline.
            val nubCentre = centre + knob
            drawCircle(color = nubColor, radius = knobR, center = nubCentre)
            drawCircle(
                color = accentColor.copy(alpha = 0.8f),
                radius = knobR,
                center = nubCentre,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
