package app.poing

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 4 — gamepad mapping, state, and event handling.
 * The mapping/axis math is pure; the event-source checks need real
 * KeyEvent/MotionEvent objects, so this runs under Robolectric.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GamepadInputTest {

    @Before
    fun setUp() {
        GamepadInput.enabled = false
        GamepadInput.state.clear()
        GamepadInput.onMaskChanged = null
    }

    @After
    fun tearDown() {
        GamepadInput.enabled = false
        GamepadInput.state.clear()
        GamepadInput.onMaskChanged = null
    }

    private fun keyEvent(action: Int, code: Int, source: Int): KeyEvent =
        KeyEvent(0, 0, action, code, 0, 0, 0, 0, 0, source)

    private fun motionEvent(x: Float, y: Float, source: Int): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = 0f
                this.y = 0f
                setAxisValue(MotionEvent.AXIS_X, x)
                setAxisValue(MotionEvent.AXIS_Y, y)
                setAxisValue(MotionEvent.AXIS_HAT_X, 0f)
                setAxisValue(MotionEvent.AXIS_HAT_Y, 0f)
            },
        )
        return MotionEvent.obtain(
            0, 0, MotionEvent.ACTION_MOVE, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, source, 0,
        )
    }

    @Test
    fun keyCodesMapToJoypadBits() {
        assertEquals(InputMask.JOY_A, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(InputMask.JOY_B, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(InputMask.JOY_X, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(InputMask.JOY_Y, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(InputMask.JOY_START, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(InputMask.JOY_SELECT, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(InputMask.JOY_L, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(InputMask.JOY_R, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(InputMask.JOY_L2, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(InputMask.JOY_R2, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(InputMask.JOY_UP, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(InputMask.JOY_DOWN, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(InputMask.JOY_LEFT, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(InputMask.JOY_RIGHT, GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun unmappedKeysReturnNull() {
        assertNull(GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_ENTER))
        assertNull(GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BACK))
        assertNull(GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(GamepadInput.keyCodeToBit(KeyEvent.KEYCODE_BUTTON_MODE))
    }

    @Test
    fun axisDirectionsNeedDeflection() {
        assertEquals(0, GamepadInput.axisDirections(0f, 0f))
        assertEquals(0, GamepadInput.axisDirections(0.4f, -0.4f))
        assertEquals(1 shl InputMask.JOY_RIGHT, GamepadInput.axisDirections(0.8f, 0f))
        assertEquals(1 shl InputMask.JOY_LEFT, GamepadInput.axisDirections(-0.8f, 0f))
        assertEquals(1 shl InputMask.JOY_DOWN, GamepadInput.axisDirections(0f, 0.8f))
        assertEquals(1 shl InputMask.JOY_UP, GamepadInput.axisDirections(0f, -0.8f))
        val diagonal = GamepadInput.axisDirections(0.8f, -0.8f)
        assertTrue(diagonal and (1 shl InputMask.JOY_RIGHT) != 0)
        assertTrue(diagonal and (1 shl InputMask.JOY_UP) != 0)
    }

    @Test
    fun stateTracksButtonsAndAxes() {
        val state = GamepadState()
        assertEquals(0, state.mask)
        state.onKey(KeyEvent.KEYCODE_BUTTON_A, true)
        assertEquals(1 shl InputMask.JOY_A, state.mask)
        state.onKey(KeyEvent.KEYCODE_BUTTON_B, true)
        assertTrue(state.mask and (1 shl InputMask.JOY_B) != 0)
        state.onKey(KeyEvent.KEYCODE_BUTTON_A, false)
        assertEquals(1 shl InputMask.JOY_B, state.mask)
        state.onAxes(0.9f, 0f, 0f, 0f)
        assertTrue(state.mask and (1 shl InputMask.JOY_RIGHT) != 0)
        assertTrue(state.mask and (1 shl InputMask.JOY_B) != 0)
        state.onAxes(0f, 0f, 0f, 0f)
        assertEquals(1 shl InputMask.JOY_B, state.mask)
        state.clear()
        assertEquals(0, state.mask)
    }

    @Test
    fun unmappedKeyDoesNotChangeState() {
        val state = GamepadState()
        state.onKey(KeyEvent.KEYCODE_ENTER, true)
        assertEquals(0, state.mask)
    }

    @Test
    fun gamepadSourceDetection() {
        assertTrue(
            GamepadInput.isGamepadEvent(
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD),
            ),
        )
        assertTrue(
            GamepadInput.isGamepadEvent(
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_JOYSTICK),
            ),
        )
        assertFalse(
            GamepadInput.isGamepadEvent(
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_KEYBOARD),
            ),
        )
        assertFalse(
            GamepadInput.isGamepadEvent(
                keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, InputDevice.SOURCE_KEYBOARD),
            ),
        )
    }

    @Test
    fun handleKeyEventOnlyWhenEnabledAndGamepad() {
        val down = keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD)
        // Player not showing: nothing consumed, state untouched.
        assertFalse(GamepadInput.handleKeyEvent(down))
        assertEquals(0, GamepadInput.state.mask)

        GamepadInput.enabled = true
        var notified = -1
        GamepadInput.onMaskChanged = { notified = it }
        assertTrue(GamepadInput.handleKeyEvent(down))
        assertEquals(1 shl InputMask.JOY_A, GamepadInput.state.mask)
        assertEquals(1 shl InputMask.JOY_A, notified)

        val up = keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD)
        assertTrue(GamepadInput.handleKeyEvent(up))
        assertEquals(0, GamepadInput.state.mask)

        // Keyboard source: never consumed, even when enabled.
        val kb = keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_KEYBOARD)
        assertFalse(GamepadInput.handleKeyEvent(kb))
        assertEquals(0, GamepadInput.state.mask)

        // Unmapped gamepad key: never consumed.
        val mode = keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_MODE, InputDevice.SOURCE_GAMEPAD)
        assertFalse(GamepadInput.handleKeyEvent(mode))
    }

    @Test
    fun handleMotionEventOnlyWhenEnabledAndJoystick() {
        val move = motionEvent(0.9f, 0f, InputDevice.SOURCE_JOYSTICK)
        assertFalse(GamepadInput.handleMotionEvent(move))
        assertEquals(0, GamepadInput.state.mask)

        GamepadInput.enabled = true
        assertTrue(GamepadInput.handleMotionEvent(move))
        assertEquals(1 shl InputMask.JOY_RIGHT, GamepadInput.state.mask)

        // Touchscreen move: never consumed.
        val touch = motionEvent(0.9f, 0f, InputDevice.SOURCE_TOUCHSCREEN)
        assertFalse(GamepadInput.handleMotionEvent(touch))
    }
}
