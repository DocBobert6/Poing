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
 * Phase 6 (item 7) — haptics, light version only: one short click.
 * The Vibrator is the Robolectric one, and the assertion is the
 * shadow's vibrating flag: this version's ShadowVibrator has no
 * vibration list (shadowOf(v).vibrations does not resolve), and
 * the Vibrator constructor is package-private, so no fake
 * subclass is possible either.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticsTest {

    private fun vibrator(): Vibrator =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Vibrator::class.java)

    @Test
    fun clickVibrates() {
        val v = vibrator()
        Haptics(v).click()
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun disabledClickDoesNothing() {
        val v = vibrator()
        Haptics(v, enabled = false).click()
        assertFalse(shadowOf(v).isVibrating)
    }

    @Test
    fun nullVibratorIsASilentNoOp() {
        // A device without a vibrator must never crash.
        Haptics(null).click()
    }

    @Test
    fun clickIsAShortEffect() {
        // The click is one 12ms effect: a light tap, not a buzz.
        assertEquals(12L, Haptics.CLICK_MS)
    }
}
