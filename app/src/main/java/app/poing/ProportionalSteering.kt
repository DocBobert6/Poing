package app.poing

import kotlin.math.roundToInt
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
     * at 60 fps. Fixed by design — no period setting, no curve:
     * the duty cycle carries the gradation, linear, and the
     * device verdict decides whether that needs revisiting.
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

    /** Frames of the period the direction is ON at deflection t: round(6 * t). */
    fun onFrames(t: Float): Int =
        (PERIOD_FRAMES * t.coerceIn(0f, 1f)).roundToInt().coerceIn(0, PERIOD_FRAMES)

    /** True when the pulse is ON at this step of the period (steps cycle 0..5). */
    fun isOn(step: Int, t: Float): Boolean =
        (step % PERIOD_FRAMES) < onFrames(t)

    /**
     * The mask the core is handed this step. The stick's
     * direction bits ([stickMask], as reported by StickControl)
     * are gated by the pulse; every other bit — the face
     * buttons, the gamepad, the D-pad when it is in the slot —
     * passes through untouched. Disabled, or with no stick
     * direction held, the mask reaches the core exactly as it
     * always did (today's behaviour byte-for-byte).
     */
    fun pulsedMask(rawMask: Int, stickMask: Int, step: Int, t: Float, enabled: Boolean): Int {
        if (!enabled) return rawMask
        val stickBits = stickMask and ActuationDetector.DIRECTION_BITS
        if (stickBits == 0) return rawMask
        return if (isOn(step, t)) rawMask else rawMask and stickBits.inv()
    }
}
