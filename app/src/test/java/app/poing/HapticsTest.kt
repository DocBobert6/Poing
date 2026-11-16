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

    // Alpha.11 — per-button character: every new effect
    // vibrates through the same guard as the click.
    @Test
    fun directionTickVibrates() {
        val v = vibrator()
        Haptics(v).directionTick()
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun releaseTickVibrates() {
        val v = vibrator()
        Haptics(v).releaseTick()
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun lightClickVibrates() {
        val v = vibrator()
        Haptics(v).lightClick()
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun shoulderClickVibrates() {
        val v = vibrator()
        Haptics(v).shoulderClick()
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun disabledTickDoesNothing() {
        val v = vibrator()
        Haptics(v, enabled = false).directionTick()
        assertFalse(shadowOf(v).isVibrating)
    }

    @Test
    fun strengthZeroTickDoesNothing() {
        val v = vibrator()
        Haptics(v, strength = 0).releaseTick()
        assertFalse(shadowOf(v).isVibrating)
    }

    @Test
    fun nullVibratorTickIsASilentNoOp() {
        Haptics(null).directionTick()
        Haptics(null).releaseTick()
        Haptics(null).shoulderClick()
    }

    @Test
    fun tickFallbackIsAShortOneShot() {
        // Without the tick primitive the tick is an 8ms
        // one-shot — shorter than the 12ms click, never the
        // predefined click (which has no amplitude and would
        // land at press strength).
        assertEquals(
            Haptics.TICK_MS,
            Haptics.buildTickEffect(0.5f, 100, 24, tickSupported = false).duration,
        )
        assertTrue(Haptics.TICK_MS < Haptics.CLICK_MS)
    }

    @Test
    fun shoulderFallbackIsThePlainClick() {
        // Without primitives at all the shoulder is the plain
        // click effect (a 12ms one-shot on an old device).
        assertEquals(
            Haptics.CLICK_MS,
            Haptics.buildShoulderEffect(100, 24, primitivesSupported = false, thudSupported = false).duration,
        )
    }

    @Test
    fun characterScalesAreOrdered() {
        // The release is the lightest event (a spring-back),
        // the roll tick is an actuation so it ticks harder,
        // Start/Select are a lighter click, and the thud sits
        // low under the shoulder click. The release stays in
        // the 0.3–0.5 band Robert approved, and the thud lands
        // 15–25ms after the click.
        assertTrue(Haptics.RELEASE_TICK_SCALE in 0.3f..0.5f)
        assertTrue(Haptics.RELEASE_TICK_SCALE < Haptics.DIRECTION_TICK_SCALE)
        assertTrue(Haptics.DIRECTION_TICK_SCALE < Haptics.START_SELECT_SCALE)
        assertTrue(Haptics.START_SELECT_SCALE < 1f)
        assertTrue(Haptics.SHOULDER_THUD_SCALE < Haptics.DIRECTION_TICK_SCALE)
        assertTrue(Haptics.SHOULDER_THUD_DELAY_MS in 15..25)
    }
}
