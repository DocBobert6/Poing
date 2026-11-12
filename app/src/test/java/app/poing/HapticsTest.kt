package app.poing

import android.os.VibrationEffect
import android.os.Vibrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Vibrator that only counts the effects it was asked to play.
 * The Robolectric ShadowVibrator offers no vibration list in
 * this version (shadowOf(v).vibrations does not resolve), so
 * the tests count through a fake instead of the shadow.
 */
class FakeVibrator : Vibrator() {
    var clicks = 0
        private set

    override fun hasVibrator(): Boolean = true
    override fun hasAmplitudeControl(): Boolean = true
    override fun cancel() {}

    override fun vibrate(effect: VibrationEffect) {
        clicks++
    }
}

/**
 * Phase 6 (item 7) — haptics, light version only: one short click.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticsTest {

    @Test
    fun clickVibratesOnce() {
        val v = FakeVibrator()
        Haptics(v).click()
        assertEquals(1, v.clicks)
    }

    @Test
    fun disabledClickDoesNothing() {
        val v = FakeVibrator()
        Haptics(v, enabled = false).click()
        assertEquals(0, v.clicks)
    }

    @Test
    fun nullVibratorIsASilentNoOp() {
        // A device without a vibrator must never crash.
        Haptics(null).click()
    }

    @Test
    fun clickUsesAShortEffect() {
        // The click is one 12ms effect at the default amplitude:
        // a light tap, not a buzz.
        assertTrue(Haptics.CLICK_MS in 1..20)
    }
}
