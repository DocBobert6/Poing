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

    // Alpha.8 — the realistic click: which effect a click is,
    // for the strength and the device. The decision is a pure
    // function, so the matrix pins here; the factory mapping
    // is three lines and the behaviour tests above pin that
    // a click actually vibrates.
    @Test
    fun effectKindIsPrimitiveClickOnModernDevices() {
        assertEquals(
            Haptics.EffectKind.PRIMITIVE_CLICK,
            Haptics.effectKind(100, 35, primitivesSupported = true),
        )
        assertEquals(
            Haptics.EffectKind.PRIMITIVE_CLICK,
            Haptics.effectKind(50, 35, primitivesSupported = true),
        )
    }

    @Test
    fun effectKindIsPredefinedClickAtFullStrengthOnApi29() {
        // API 29 has the prebaked click but no composition
        // API, so at full strength the click is prebaked.
        assertEquals(
            Haptics.EffectKind.PREDEFINED_CLICK,
            Haptics.effectKind(100, 29, primitivesSupported = false),
        )
    }

    @Test
    fun effectKindFallsBackToOneShotBelowFullStrengthOnApi29() {
        // The prebaked click has a fixed amplitude, so a
        // reduced strength on API 29 takes the scaled
        // one-shot instead — the strength must still work.
        assertEquals(
            Haptics.EffectKind.ONE_SHOT,
            Haptics.effectKind(50, 29, primitivesSupported = false),
        )
    }

    @Test
    fun effectKindFallsBackToOneShotOnOldDevices() {
        assertEquals(
            Haptics.EffectKind.ONE_SHOT,
            Haptics.effectKind(100, 24, primitivesSupported = false),
        )
    }

    @Test
    fun scaledAmplitudeMapsStrength() {
        assertEquals(255, Haptics.scaledAmplitude(100))
        assertEquals(127, Haptics.scaledAmplitude(50))
        assertEquals(2, Haptics.scaledAmplitude(1))
        assertEquals(0, Haptics.scaledAmplitude(0))
    }

    @Test
    fun primitiveScaleMapsStrength() {
        assertEquals(1f, Haptics.primitiveScale(100))
        assertEquals(0.5f, Haptics.primitiveScale(50))
        assertEquals(0f, Haptics.primitiveScale(0))
    }

    @Test
    fun defaultStrengthIsFull() {
        assertEquals(100, Haptics.DEFAULT_STRENGTH)
    }

    @Test
    fun strengthZeroClickDoesNothing() {
        // 0 on the slider silences the click, the same as
        // the toggle off.
        val v = vibrator()
        Haptics(v, strength = 0).click()
        assertFalse(shadowOf(v).isVibrating)
    }
}
