package app.poing

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Phase 4 — standard Android gamepad input, player 1 only
 * (Bluetooth or USB). Buttons and the D-pad arrive as [KeyEvent]s,
 * the left stick and the D-pad hat as [MotionEvent]s; both are
 * folded into one mask and OR-ed with the touch mask by the player
 * screen before it reaches the session.
 *
 * Only events from a gamepad/joystick source are ever consumed, and
 * only while [enabled] — set by the player screen on entry, cleared
 * on exit — so the pad never hijacks keys on the library screen and
 * the Back key is never swallowed.
 *
 * The mapping is the standard Android one: A/B/X/Y, Start/Select,
 * L1/R1, L2/R2 (mGBA reads X/Y as Turbo A/B and L2/R2 as Turbo
 * L/R). R3/L3 (solar sensor) are not wired.
 *
 * [keyCodeToBit] and [axisDirections] are pure and JVM-testable;
 * the event-source checks need a real [KeyEvent]/[MotionEvent] and
 * are covered under Robolectric.
 */
object GamepadInput {

    /** Beyond this deflection on a stick/hat axis, that direction counts. */
    const val AXIS_THRESHOLD = 0.5f

    /** True while the player screen is showing; gates all consumption. */
    @Volatile
    var enabled = false

    /** The current gamepad mask (buttons + stick/hat directions). */
    val state = GamepadState()

    /**
     * Called by the player screen whenever the mask changes, so it
     * can push the merged (touch OR gamepad) mask to the session.
     */
    @Volatile
    var onMaskChanged: ((Int) -> Unit)? = null

    /** Key code -> joypad bit ([InputMask]), or null when the key is not a game button. */
    fun keyCodeToBit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> InputMask.JOY_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> InputMask.JOY_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> InputMask.JOY_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> InputMask.JOY_RIGHT
        KeyEvent.KEYCODE_BUTTON_A -> InputMask.JOY_A
        KeyEvent.KEYCODE_BUTTON_B -> InputMask.JOY_B
        KeyEvent.KEYCODE_BUTTON_X -> InputMask.JOY_X
        KeyEvent.KEYCODE_BUTTON_Y -> InputMask.JOY_Y
        KeyEvent.KEYCODE_BUTTON_START -> InputMask.JOY_START
        KeyEvent.KEYCODE_BUTTON_SELECT -> InputMask.JOY_SELECT
        KeyEvent.KEYCODE_BUTTON_L1 -> InputMask.JOY_L
        KeyEvent.KEYCODE_BUTTON_R1 -> InputMask.JOY_R
        KeyEvent.KEYCODE_BUTTON_L2 -> InputMask.JOY_L2
        KeyEvent.KEYCODE_BUTTON_R2 -> InputMask.JOY_R2
        else -> null
    }

    /** True when this key event came from a gamepad/joystick (not the keyboard or a TV remote). */
    fun isGamepadEvent(event: KeyEvent): Boolean =
        event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    /** True when this motion event is a joystick stick/hat move. */
    fun isGamepadMotion(event: MotionEvent): Boolean =
        event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE

    /** Direction bits from a stick/hat deflection (x right, y down). */
    fun axisDirections(x: Float, y: Float): Int {
        var mask = 0
        if (x > AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_RIGHT)
        if (x < -AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_LEFT)
        if (y > AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_DOWN)
        if (y < -AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_UP)
        return mask
    }

    /**
     * Handle a key event from MainActivity.dispatchKeyEvent. Returns
     * true when the event was a mapped gamepad button (consumed);
     * false for everything else (keyboard keys, unmapped keys, and
     * all events while the player is not showing).
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || !isGamepadEvent(event)) return false
        if (keyCodeToBit(event.keyCode) == null) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP -> {
                // Repeat downs are idempotent: the button is already
                // in the pressed set.
                state.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
                onMaskChanged?.invoke(state.mask)
                return true
            }
            else -> return false
        }
    }

    /**
     * Handle a motion event from MainActivity.dispatchGenericMotionEvent.
     * Returns true when it was a joystick move while the player is
     * showing (consumed); false otherwise.
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!enabled || !isGamepadMotion(event)) return false
        state.onAxes(
            x = event.getAxisValue(MotionEvent.AXIS_X),
            y = event.getAxisValue(MotionEvent.AXIS_Y),
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        onMaskChanged?.invoke(state.mask)
        return true
    }
}

/** Mutable gamepad state: pressed buttons plus stick/hat directions, as one mask. */
class GamepadState {
    @Volatile
    var mask: Int = 0
        private set

    private val pressedBits = mutableSetOf<Int>()
    private var axisMask = 0

    /** Record a button press/release; returns the new mask. */
    @Synchronized
    fun onKey(keyCode: Int, down: Boolean): Int {
        val bit = GamepadInput.keyCodeToBit(keyCode) ?: return mask
        if (down) pressedBits.add(bit) else pressedBits.remove(bit)
        recompute()
        return mask
    }

    /** Record a stick + hat deflection; returns the new mask. */
    @Synchronized
    fun onAxes(x: Float, y: Float, hatX: Float, hatY: Float): Int {
        axisMask = GamepadInput.axisDirections(x, y) or
            GamepadInput.axisDirections(hatX, hatY)
        recompute()
        return mask
    }

    @Synchronized
    fun clear() {
        pressedBits.clear()
        axisMask = 0
        mask = 0
    }

    private fun recompute() {
        var m = axisMask
        for (bit in pressedBits) m = m or (1 shl bit)
        mask = m
    }
}
