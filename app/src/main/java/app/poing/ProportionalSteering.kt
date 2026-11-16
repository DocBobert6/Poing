package app.poing

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Alpha.16 — proportional steering: the joystick's deflection
 * controls HOW MUCH steering the game gets, not just on or
 * off. Past the dead zone the stick's output is still a
 * digital direction mask, so the deflection is turned into a
 * duty cycle: a slight push pulses the direction in rapid
 * gentle taps — like tapping the D-pad on real hardware, which
 * is how you steer precisely on a GBA — and full deflection
 * holds it continuously.
 *
 * Alpha.18 — the curve and the counting both changed on
 * device evidence (Robert, 2026-11-16): the linear duty's
 * smallest step was one full-steering frame in six (~17%)
 * and stayed there until t ≈ 0.25, so a slight push steered
 * too much. The duty now follows t-squared, so slight
 * pushes count for much less, and the fractional remainder
 * carries forward across periods (error diffusion) instead
 * of rounding per period, so a tiny push gives one
 * full-steering frame every N periods rather than
 * nothing-or-a-sixth. The period stays 6 frames as the
 * window; the accumulator is the only new state, and it
 * lives in PlayerSession next to the step counter.
 *
 * Pure Kotlin, no Android dependencies, unit-tested on the
 * plain JVM (see ProportionalSteeringTest). The pulse itself
 * lives in PlayerSession's emu loop, one step per emulated
 * frame, so fast-forward keeps the steering consistent in
 * game time; the haptics and the input display read the raw
 * direction from the controls and never see the pulsing.
 */
object ProportionalSteering {

    /**
     * The pulse period, in emulated frames: 6 frames is 100 ms
     * at 60 fps. Fixed by design — no period setting: the duty
     * cycle carries the gradation, and the device verdict
     * decides whether that needs revisiting.
     */
    const val PERIOD_FRAMES = 6

    /**
     * The normalised deflection t in 0..1, from the knob offset
     * (cx, cy) in units of full travel and the dead zone in the
     * same units. The dead zone applies first: at or below it
     * t is 0 (nothing), and t reaches 1 at full travel.
     */
    fun deflection(dx: Float, dy: Float, deadZone: Float): Float {
        val mag = sqrt(dx * dx + dy * dy)
        if (mag <= deadZone) return 0f
        return ((mag - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
    }

    /**
     * Alpha.18 — the duty at deflection t: t-squared, not t.
     * A slight push counts for much less than it did under
     * the linear duty (t = 0.5 is a quarter, not a half;
     * t = 0.1 is a hundredth, not a tenth), while full
     * deflection is still exactly 1 — held continuously.
     */
    fun duty(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c
    }

    /**
     * Alpha.18 — the frames of the period the direction is ON
     * at deflection t, with the fractional remainder [carry]
     * carried in from earlier periods: floor(6 * duty + carry),
     * clamped to the period. The remainder is what makes a
     * duty below one frame per period still fire eventually
     * instead of rounding down to nothing every period.
     */
    fun onFrames(t: Float, carry: Float): Int =
        floor(PERIOD_FRAMES * duty(t) + carry).toInt().coerceIn(0, PERIOD_FRAMES)

    /**
     * Alpha.18 — the remainder left after this period's ON
     * frames, to carry into the next period: (6 * duty + carry)
     * minus the frames just counted, always in [0, 1). Paired
     * with [onFrames]: over many periods the ON frames add up
     * to exactly the duty, with no dead band just above the
     * dead zone — any t above 0 fires sooner or later.
     */
    fun nextCarry(t: Float, carry: Float): Float {
        val total = PERIOD_FRAMES * duty(t) + carry
        return (total - floor(total)).coerceIn(0f, 1f)
    }

    /** True when the pulse is ON at this step of the period (steps cycle 0..5). */
    fun isOn(step: Int, onFrames: Int): Boolean =
        (step % PERIOD_FRAMES) < onFrames.coerceIn(0, PERIOD_FRAMES)

    /**
     * The mask the core is handed this step. The stick's
     * direction bits ([stickMask], as reported by StickControl)
     * are gated by the pulse — [onFrames] is the current
     * period's ON count, computed once per period by the
     * caller (see [onFrames]); every other bit — the face
     * buttons, the gamepad, the D-pad when it is in the slot —
     * passes through untouched. Disabled, or with no stick
     * direction held, the mask reaches the core exactly as it
     * always did (today's behaviour byte-for-byte).
     */
    fun pulsedMask(rawMask: Int, stickMask: Int, step: Int, onFrames: Int, enabled: Boolean): Int {
        if (!enabled) return rawMask
        val stickBits = stickMask and ActuationDetector.DIRECTION_BITS
        if (stickBits == 0) return rawMask
        return if (isOn(step, onFrames)) rawMask else rawMask and stickBits.inv()
    }
}
