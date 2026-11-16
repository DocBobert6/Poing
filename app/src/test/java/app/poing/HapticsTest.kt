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

    @Test
    fun strengthZeroSilencesEveryEffect() {
        // Regression: the effect used to be built before
        // the guard ran, and createOneShot rejects amplitude
        // 0 — so strength 0 threw instead of staying silent.
        // Every method must stay silent, never throw.
        val v = vibrator()
        val haptics = Haptics(v, strength = 0)
        haptics.click()
        haptics.lightClick()
        haptics.directionTick()
        haptics.releaseTick()
        haptics.shoulderClick()
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
        // land at press strength). The choice is a pure
        // function, so the matrix pins here; the effect
        // itself has no readable duration on the public API.
        assertEquals(
            Haptics.TickEffectKind.PRIMITIVE_TICK,
            Haptics.tickEffectKind(35, tickSupported = true),
        )
        assertEquals(
            Haptics.TickEffectKind.ONE_SHOT,
            Haptics.tickEffectKind(35, tickSupported = false),
        )
        assertEquals(
            Haptics.TickEffectKind.ONE_SHOT,
            Haptics.tickEffectKind(24, tickSupported = false),
        )
        assertTrue(Haptics.TICK_MS < Haptics.CLICK_MS)
    }

    @Test
    fun shoulderFallbackIsThePlainClick() {
        // Without the thud the shoulder is the click alone;
        // without primitives at all it is the plain click
        // effect. The choice is a pure function, so the
        // matrix pins here.
        assertEquals(
            Haptics.ShoulderEffectKind.CLICK_THUD,
            Haptics.shoulderEffectKind(35, primitivesSupported = true, thudSupported = true),
        )
        assertEquals(
            Haptics.ShoulderEffectKind.CLICK_ONLY,
            Haptics.shoulderEffectKind(35, primitivesSupported = true, thudSupported = false),
        )
        assertEquals(
            Haptics.ShoulderEffectKind.PLAIN_CLICK,
            Haptics.shoulderEffectKind(35, primitivesSupported = false, thudSupported = false),
        )
        assertEquals(
            Haptics.ShoulderEffectKind.PLAIN_CLICK,
            Haptics.shoulderEffectKind(24, primitivesSupported = false, thudSupported = false),
        )
    }

    @Test
    fun characterScalesAreOrdered() {
        // The release is the lightest event (a spring-back),
        // the roll tick is an actuation so it ticks harder,
        // Start/Select are a lighter click, and the thud sits
        // low under the shoulder click. The release stays in
        // the 0.3–0.5 band Robert approved, and the thud lands
        // 15–25ms after the click. Alpha.15: the direction
        // tick rose to 0.6, the same scale as Start/Select —
        // that is fine, because the tick is a softer primitive
        // than the click, and the direction envelope's
        // sharpness is pulled down a quarter, so the roll
        // still lands clearly below a face button's click.
        assertTrue(Haptics.RELEASE_TICK_SCALE in 0.3f..0.5f)
        assertTrue(Haptics.RELEASE_TICK_SCALE < Haptics.DIRECTION_TICK_SCALE)
        assertTrue(Haptics.DIRECTION_TICK_SCALE <= Haptics.START_SELECT_SCALE)
        assertTrue(Haptics.START_SELECT_SCALE < 1f)
        assertTrue(Haptics.SHOULDER_THUD_SCALE < Haptics.DIRECTION_TICK_SCALE)
        assertTrue(Haptics.SHOULDER_THUD_DELAY_MS in 15..25)
    }

    // Alpha.13 — envelope click: the decision is a pure
    // function, so the matrix pins here. The envelope effect
    // itself cannot be built under Robolectric's SDK-35
    // harness (BasicEnvelopeBuilder does not exist there),
    // so it is proven on a real API 36 device instead — and
    // the behaviour tests above prove the pre-36 path still
    // clicks, since at SDK 35 the envelope is never taken.
    @Test
    fun useEnvelopeClickNeedsApi36AndSupport() {
        assertTrue(Haptics.useEnvelopeClick(36, envelopeSupported = true))
        assertFalse(Haptics.useEnvelopeClick(36, envelopeSupported = false))
        assertFalse(Haptics.useEnvelopeClick(35, envelopeSupported = true))
        assertFalse(Haptics.useEnvelopeClick(24, envelopeSupported = true))
    }

    @Test
    fun resonantSharpnessPlacesTheResonantFrequency() {
        // A resonant frequency a third of the way up the
        // supported range takes a sharpness a third of the
        // way up — the click lands in the resonant band,
        // not at an arbitrary extreme.
        assertEquals(140f / 460f, Haptics.resonantSharpness(40f, 500f, 180f), 0.001f)
        assertEquals(0f, Haptics.resonantSharpness(40f, 500f, 40f), 0.001f)
        assertEquals(1f, Haptics.resonantSharpness(40f, 500f, 500f), 0.001f)
        // Outside the range clamps instead of escaping it.
        assertEquals(1f, Haptics.resonantSharpness(40f, 500f, 600f), 0.001f)
        // An unknown resonant frequency, or a profile with
        // no range, takes the full sharpness.
        assertEquals(1f, Haptics.resonantSharpness(40f, 500f, 0f), 0.001f)
        assertEquals(1f, Haptics.resonantSharpness(40f, 500f, -10f), 0.001f)
        assertEquals(1f, Haptics.resonantSharpness(100f, 100f, 150f), 0.001f)
    }

    @Test
    fun envelopeClickDurationsStayInTheClickWindow() {
        // The attack is the device's fastest envelope step
        // and the decay takes the rest of the ~15ms total;
        // the decay is never shorter than the attack, since
        // a step shorter than the device's minimum would be
        // rejected.
        assertEquals(1L to 14L, Haptics.envelopeClickDurations(1L))
        assertEquals(5L to 10L, Haptics.envelopeClickDurations(5L))
        assertEquals(10L to 10L, Haptics.envelopeClickDurations(10L))
        assertEquals(1L to 14L, Haptics.envelopeClickDurations(0L))
    }

    @Test
    fun envelopeClickTotalIsARealClickLength() {
        // ~15ms, inside the 10–20ms a crisp click wants —
        // the same order as the 12ms click it refines.
        assertTrue(Haptics.ENVELOPE_CLICK_TOTAL_MS in 10L..20L)
    }

    // Alpha.15 — the retune (Robert's device verdict on
    // alpha.13): A/B are back on the alpha.12 click, no
    // envelope; Start/Select keep the envelope; the D-pad
    // and the stick try the envelope on a direction change,
    // softer than Start/Select's and slightly stronger than
    // the old 0.5x tick. The routing itself can only be
    // proven on a real API 36 device (at SDK 35 the
    // envelope is never taken, so the behaviour tests above
    // prove the fallback path still ticks) — what pins here
    // is the decision math: the raised fallback scale, the
    // direction envelope's strength, and its softer
    // sharpness. Nothing here claims the envelope on the
    // rocker feels better — that is Robert's device verdict.
    @Test
    fun directionTickScaleIsSlightlyStronger() {
        // 0.5 to 0.6: a small step up from the old tick —
        // clearly present, still obviously below A/B's full
        // click (scale 1.0) and inside the strength the
        // slider already maps.
        assertEquals(0.6f, Haptics.DIRECTION_TICK_SCALE)
        assertTrue(Haptics.DIRECTION_TICK_SCALE < 1f)
        assertTrue(Haptics.RELEASE_TICK_SCALE < Haptics.DIRECTION_TICK_SCALE)
    }

    @Test
    fun directionEnvelopeStrengthMatchesTheFallbackTick() {
        // The envelope lands at exactly the fallback tick's
        // amplitude — the envelope-or-tick choice changes
        // only the character of the roll, never how hard it
        // lands. The tick's amplitude is the primitive scale
        // times the direction scale; the envelope's strength
        // is the same product, as a 0–100 strength.
        assertEquals(60, Haptics.directionEnvelopeStrength(100))
        assertEquals(30, Haptics.directionEnvelopeStrength(50))
        assertEquals(0, Haptics.directionEnvelopeStrength(0))
        assertEquals(
            0.6f,
            Haptics.primitiveScale(Haptics.directionEnvelopeStrength(100)),
            0.001f,
        )
        assertEquals(
            Haptics.primitiveScale(100) * Haptics.DIRECTION_TICK_SCALE,
            Haptics.primitiveScale(Haptics.directionEnvelopeStrength(100)),
            0.001f,
        )
    }

    @Test
    fun directionEnvelopeSharpnessIsSofterThanThePress() {
        // The resonant sharpness pulled down a quarter: the
        // roll reads softer than Start/Select's envelope,
        // which takes the resonant sharpness whole, while
        // staying in the resonant neighbourhood.
        assertEquals(0.75f, Haptics.directionEnvelopeSharpness(1.0f), 0.001f)
        assertEquals(0.6f, Haptics.directionEnvelopeSharpness(0.8f), 0.001f)
        assertEquals(0f, Haptics.directionEnvelopeSharpness(0f), 0.001f)
        // Always softer than the resonant sharpness it starts
        // from — the pull-down never leaves the value alone.
        assertTrue(Haptics.directionEnvelopeSharpness(0.8f) < 0.8f)
    }
}
