package app.poing

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Alpha.10 — the joystick's direction math (pure Kotlin, no
 * Android dependencies, unit-tested on the plain JVM).
 *
 * Ported from HowlingDog's StickInput
 * (app/howlingdog/StickInput.kt), which Poing's D-pad
 * research (2026-11-15) measured as the same numbers as
 * Poing's DpadInput: the same 0.22 dead zone and the same
 * 0.35 diagonal band, the same direction-mask output. The
 * gate SHAPE differs by one deliberate choice, kept from
 * the port: HowlingDog's stick uses a ratio rule (a
 * diagonal when the smaller axis is at least 0.35 of the
 * larger), while Poing's D-pad uses per-axis thresholds
 * (each axis past 0.35 counts). Both share the 0.22/0.35
 * numbers; the ratio rule is the stick's proven behaviour
 * on device (Metal Slug 5 diagonals), so the port keeps it
 * instead of re-deriving a gate. Do not retune either
 * number without device evidence.
 *
 * Coordinates: dx/dy are the knob offset from the base
 * centre, in units of the stick's maximum travel (1.0 = the
 * knob at the edge of the base). +x is right, +y is down
 * (Android touch convention). The returned value is a
 * direction mask using the InputMask joypad bits, so it
 * slots straight into the Held state the D-pad already
 * feeds.
 */
object StickInput {

    /** Inside this fraction of full travel, no direction is active (a thumb resting near the centre does nothing). */
    const val DEAD_ZONE = 0.22f

    /**
     * Alpha.14 — the adjustable dead zone, in percent of full
     * travel: 0–50 in steps of 5, 22 by default — the fixed
     * 0.22 that shipped in alpha.10, unchanged on install.
     * 0 is raw (no dead zone at all, like the real D-pad);
     * 50 is the practical max (half the travel neutral).
     * The Settings slider offers the steps; the stores clamp
     * any value into range.
     */
    const val DEAD_ZONE_MIN_PCT = 0
    const val DEAD_ZONE_MAX_PCT = 50
    const val DEAD_ZONE_DEFAULT_PCT = 22
    const val DEAD_ZONE_STEP_PCT = 5

    /** Ratio of the smaller axis to the larger at which a push
        counts as a diagonal (both directions on). 0.35 gives a
        diagonal band of about 19 to 71 degrees off horizontal
        (~51 degrees); a deliberate straight push keeps its
        cardinal (each cardinal keeps a ~19-degree band). */
    const val DIAGONAL_RATIO = 0.35f

    /**
     * Direction bits from the knob offset (dx, dy), each in units of full travel.
     * [deadZone] is the gate, in the same units — below it nothing,
     * above it the exact same ratio rule as always. It is a pure
     * gate: no rescaling, because the stick outputs directions,
     * not an analog value, so there is nothing to rescale. The
     * default is the shipped 0.22, so every existing caller
     * keeps the old behaviour unless it passes a value.
     */
    fun direction(dx: Float, dy: Float, deadZone: Float = DEAD_ZONE): Int {
        val ax = abs(dx)
        val ay = abs(dy)
        val mag = sqrt(dx * dx + dy * dy)
        if (mag < deadZone) return 0
        // Diagonal when both axes are substantially engaged: the
        // smaller at least DIAGONAL_RATIO of the larger (about
        // 19-71 degrees off horizontal). Otherwise the dominant
        // axis alone.
        return if (minOf(ax, ay) >= DIAGONAL_RATIO * maxOf(ax, ay)) {
            var mask = 0
            if (dy < 0) mask = mask or (1 shl InputMask.JOY_UP)
            else mask = mask or (1 shl InputMask.JOY_DOWN)
            if (dx < 0) mask = mask or (1 shl InputMask.JOY_LEFT)
            else mask = mask or (1 shl InputMask.JOY_RIGHT)
            mask
        } else if (ax >= ay) {
            if (dx < 0) 1 shl InputMask.JOY_LEFT else 1 shl InputMask.JOY_RIGHT
        } else {
            if (dy < 0) 1 shl InputMask.JOY_UP else 1 shl InputMask.JOY_DOWN
        }
    }

    /**
     * Clamp a knob offset to the stick's maximum travel, so the knob
     * never leaves the base ring. Returns the clamped (dx, dy), still
     * in units of full travel.
     */
    fun clamp(dx: Float, dy: Float): Pair<Float, Float> {
        val mag = sqrt(dx * dx + dy * dy)
        if (mag <= 1.0f) return dx to dy
        return (dx / mag) to (dy / mag)
    }
}

/**
 * Alpha.10 — the microswitch click rule, ported from
 * HowlingDog's ActuationDetector (app/howlingdog/Haptics.kt):
 * the click fires only when the stick crosses an actuation
 * threshold (neutral -> a direction, or one direction ->
 * another). A release back to neutral stays silent. Button
 * bits are ignored — only the direction set matters, so a
 * button press while holding a direction never re-clicks
 * the stick.
 */
object ActuationDetector {

    /** The direction bits of a joypad mask (up/down/left/right). */
    val DIRECTION_BITS: Int =
        (1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_DOWN) or
            (1 shl InputMask.JOY_LEFT) or (1 shl InputMask.JOY_RIGHT)

    /** True when the stick crossed an actuation threshold between these two masks. */
    fun isActuation(previousMask: Int, newMask: Int): Boolean {
        val previous = previousMask and DIRECTION_BITS
        val current = newMask and DIRECTION_BITS
        return current != 0 && current != previous
    }
}
