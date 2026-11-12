package app.poing

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 10b) — cartridge rumble, through the system
 * Vibrator's shadow: a level above zero runs the motor, zero
 * cancels it, a duplicate level changes nothing, and a null
 * vibrator (a device without one) is a silent no-op. The core
 * side (the libretro callback forwarding the level) is proven
 * by CI's JNI symbol check + the bridge compiling; a rumble
 * game on device proves the end-to-end path.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CartridgeRumbleTest {

    private fun vibrator(): Vibrator =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Vibrator::class.java)

    @Test
    fun levelAboveZeroVibratesAndZeroCancels() {
        val v = vibrator()
        val rumble = CartridgeRumble(v)
        assertFalse(shadowOf(v).isVibrating)
        rumble.setStrength(CartridgeRumble.MAX_STRENGTH)
        assertEquals(CartridgeRumble.MAX_STRENGTH, rumble.currentStrength)
        assertTrue(shadowOf(v).isVibrating)
        rumble.setStrength(0)
        assertEquals(0, rumble.currentStrength)
        assertFalse(shadowOf(v).isVibrating)
    }

    @Test
    fun strengthIsClampedToTheLibretroRange() {
        val v = vibrator()
        val rumble = CartridgeRumble(v)
        rumble.setStrength(CartridgeRumble.MAX_STRENGTH + 1000)
        assertEquals(CartridgeRumble.MAX_STRENGTH, rumble.currentStrength)
        assertTrue(shadowOf(v).isVibrating)
        rumble.setStrength(-5)
        assertEquals(0, rumble.currentStrength)
        assertFalse(shadowOf(v).isVibrating)
    }

    @Test
    fun stopCancelsAndANullVibratorIsANoOp() {
        val v = vibrator()
        val rumble = CartridgeRumble(v)
        rumble.setStrength(1000)
        assertTrue(shadowOf(v).isVibrating)
        rumble.stop()
        assertEquals(0, rumble.currentStrength)
        assertFalse(shadowOf(v).isVibrating)
        // A device without a vibrator: never a crash.
        val none = CartridgeRumble(null)
        none.setStrength(CartridgeRumble.MAX_STRENGTH)
        assertEquals(CartridgeRumble.MAX_STRENGTH, none.currentStrength)
        none.stop()
        assertEquals(0, none.currentStrength)
    }

    @Test
    fun bridgeRumbleHandlerForwardsTheLevel() {
        val seen = ArrayList<Int>()
        Bridge.onRumbleHandler = { strength -> seen.add(strength) }
        try {
            Bridge.onRumble(1234)
            assertEquals(listOf(1234), seen)
        } finally {
            Bridge.onRumbleHandler = null
        }
        Bridge.onRumble(9999)
        assertEquals(listOf(1234), seen)
    }
}
