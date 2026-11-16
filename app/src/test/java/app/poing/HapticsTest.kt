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
        haptics.directionTick(0, 1 shl InputMask.JOY_UP)
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
        // than the click, so the roll still lands clearly
        // below a face button's click. Alpha.17: the release
        // rose to 0.5, the top of the band.
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
    // envelope; Start/Select keep the envelope. Alpha.17
    // (Robert's device verdict on alpha.15): the envelope
    // is off the rocker too — the direction change is the
    // plain PRIMITIVE_TICK at 0.6, no envelope, no fallback.
    // Alpha.27 (Robert, 2026-11-16 13:29): the envelope is
    // back on the rocker, but split — the press from
    // neutral and the cardinal roll take it, the diagonal
    // roll takes the plain tick. The no-arg tick below is
    // now the stick's tick (and the D-pad's fallback); the
    // routed overload carries the split. The routing itself
    // can only be proven on a real API 36 device (at SDK 35
    // the envelope is never taken, so the behaviour tests
    // above prove the plain-tick path) — what pins here is
    // the behaviour: the tick is the plain tick primitive
    // at 0.6 of full strength.
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
    fun directionTickIsThePlainTickAtPointSix() {
        // Alpha.17: the direction change is the plain tick
        // primitive — the envelope branch and its strength
        // and sharpness functions are gone, so there is no
        // envelope to take and no fallback to fall back to.
        // The tick lands at 0.6 of full strength: clearly
        // present, still obviously below A/B's full click,
        // and the effect builds through the same tick path
        // the behaviour tests exercise.
        assertEquals(0.6f, Haptics.DIRECTION_TICK_SCALE)
        assertEquals(
            Haptics.TickEffectKind.PRIMITIVE_TICK,
            Haptics.tickEffectKind(35, tickSupported = true),
        )
        assertEquals(
            0.6f,
            Haptics.primitiveScale(100) * Haptics.DIRECTION_TICK_SCALE,
            0.001f,
        )
        Haptics.buildTickEffect(Haptics.DIRECTION_TICK_SCALE, 100, 35, tickSupported = true)
    }

    // Alpha.27 — the D-pad envelope split (Robert,
    // 2026-11-16 13:29, design confirmed 13:31): press
    // from neutral (any direction) and roll to a cardinal
    // take the envelope; roll to a diagonal takes the plain
    // tick. The routing is a pure function over the
    // previous and new masks, so the matrix pins here; the
    // envelope effect itself can only be proven on a real
    // API 36 device (at SDK 35 the envelope is never taken,
    // so the behaviour tests prove the plain-tick path).
    @Test
    fun directionSplitRoutingMatrix() {
        val up = 1 shl InputMask.JOY_UP
        val down = 1 shl InputMask.JOY_DOWN
        val left = 1 shl InputMask.JOY_LEFT
        val right = 1 shl InputMask.JOY_RIGHT
        val upRight = up or right
        val downLeft = down or left
        // Press from neutral — any direction, envelope.
        assertEquals(
            Haptics.DirectionEffectKind.ENVELOPE,
            Haptics.directionEffectKind(0, up),
        )
        assertEquals(
            Haptics.DirectionEffectKind.ENVELOPE,
            Haptics.directionEffectKind(0, upRight),
        )
        assertEquals(
            Haptics.DirectionEffectKind.ENVELOPE,
            Haptics.directionEffectKind(0, downLeft),
        )
        // Roll to a cardinal — envelope.
        assertEquals(
            Haptics.DirectionEffectKind.ENVELOPE,
            Haptics.directionEffectKind(up, down),
        )
        assertEquals(
            Haptics.DirectionEffectKind.ENVELOPE,
            Haptics.directionEffectKind(upRight, left),
        )
        // Roll to a diagonal — plain tick.
        assertEquals(
            Haptics.DirectionEffectKind.PLAIN_TICK,
            Haptics.directionEffectKind(up, upRight),
        )
        assertEquals(
            Haptics.DirectionEffectKind.PLAIN_TICK,
            Haptics.directionEffectKind(upRight, downLeft),
        )
        assertEquals(
            Haptics.DirectionEffectKind.PLAIN_TICK,
            Haptics.directionEffectKind(left, upRight),
        )
    }

    @Test
    fun cardinalDirectionIsExactlyOneDirectionBit() {
        val up = 1 shl InputMask.JOY_UP
        val right = 1 shl InputMask.JOY_RIGHT
        assertTrue(Haptics.isCardinalDirection(up))
        assertTrue(Haptics.isCardinalDirection(right))
        assertFalse(Haptics.isCardinalDirection(up or right))
        assertFalse(Haptics.isCardinalDirection(0))
        // Non-direction bits never count: a cardinal plus
        // an unrelated bit is still a cardinal.
        assertTrue(Haptics.isCardinalDirection(up or (1 shl InputMask.JOY_A)))
    }

    @Test
    fun directionEnvelopeStrengthMatchesTheFallbackTick() {
        // The envelope lands at exactly the fallback tick's
        // amplitude — the envelope-or-tick choice changes
        // only the character of the roll, never how hard it
        // lands. The tick's amplitude is the primitive scale
        // times the direction scale; the envelope's strength
        // is the same product, as a 0–100 strength.
        // Restored from alpha.15 (alpha.17 removed it with
        // the rocker envelope); alpha.27 brings it back.
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
        // staying in the resonant neighbourhood. Restored
        // from alpha.15 (alpha.17 removed it); alpha.27
        // brings it back at the same 0.75x.
        assertEquals(0.75f, Haptics.DIRECTION_ENVELOPE_SHARPNESS_SCALE, 0.001f)
        assertEquals(0.75f, Haptics.directionEnvelopeSharpness(1.0f), 0.001f)
        assertEquals(0.6f, Haptics.directionEnvelopeSharpness(0.8f), 0.001f)
        assertEquals(0f, Haptics.directionEnvelopeSharpness(0f), 0.001f)
        // Always softer than the resonant sharpness it starts
        // from — the pull-down never leaves the value alone.
        assertTrue(Haptics.directionEnvelopeSharpness(0.8f) < 0.8f)
    }

    @Test
    fun routedDirectionTickVibrates() {
        // The routed overload plays through the same guard
        // as the plain tick — at SDK 35 the envelope is
        // never taken, so both routes land on the tick and
        // vibrate. The envelope branch itself is proven on
        // a real API 36 device.
        val v = vibrator()
        val haptics = Haptics(v)
        val up = 1 shl InputMask.JOY_UP
        val right = 1 shl InputMask.JOY_RIGHT
        haptics.directionTick(0, up)
        assertTrue(shadowOf(v).isVibrating)
        haptics.directionTick(up, up or right)
        assertTrue(shadowOf(v).isVibrating)
    }

    @Test
    fun releaseTickScaleIsHalf() {
        // Alpha.17 (Robert: the release tick was slightly
        // too faint): 0.4 to 0.5 — the top of the 0.3–0.5
        // band, still clearly below the 0.6 roll tick and
        // the full press click.
        assertEquals(0.5f, Haptics.RELEASE_TICK_SCALE)
        assertTrue(Haptics.RELEASE_TICK_SCALE in 0.3f..0.5f)
        assertTrue(Haptics.RELEASE_TICK_SCALE < Haptics.DIRECTION_TICK_SCALE)
    }

    // Alpha.15 fold-in (Robert, 2026-11-16 00:36): L/R take
    // the envelope too — only the CLICK part; the thud stays
    // exactly as it is (PRIMITIVE_THUD 0.3x at 20ms). The
    // routing is a pure function, so the matrix pins here.
    // The delayed thud firing itself cannot be observed
    // under Robolectric's SDK-35 harness (the envelope
    // branch is never taken there) — it is proven on a real
    // API 36 device, like the envelope itself. What pins
    // here is the route, the thud's scale, and its timing.
    @Test
    fun shoulderClickRouteMatrix() {
        assertEquals(
            Haptics.ShoulderClickRoute.ENVELOPE_THEN_THUD,
            Haptics.shoulderClickRoute(
                36,
                envelopeSupported = true,
                Haptics.ShoulderEffectKind.CLICK_THUD,
            ),
        )
        assertEquals(
            Haptics.ShoulderClickRoute.ENVELOPE_ONLY,
            Haptics.shoulderClickRoute(
                36,
                envelopeSupported = true,
                Haptics.ShoulderEffectKind.CLICK_ONLY,
            ),
        )
        assertEquals(
            Haptics.ShoulderClickRoute.ENVELOPE_ONLY,
            Haptics.shoulderClickRoute(
                36,
                envelopeSupported = true,
                Haptics.ShoulderEffectKind.PLAIN_CLICK,
            ),
        )
        assertEquals(
            Haptics.ShoulderClickRoute.COMPOSITION,
            Haptics.shoulderClickRoute(
                36,
                envelopeSupported = false,
                Haptics.ShoulderEffectKind.CLICK_THUD,
            ),
        )
        assertEquals(
            Haptics.ShoulderClickRoute.COMPOSITION,
            Haptics.shoulderClickRoute(
                35,
                envelopeSupported = true,
                Haptics.ShoulderEffectKind.CLICK_THUD,
            ),
        )
        assertEquals(
            Haptics.ShoulderClickRoute.COMPOSITION,
            Haptics.shoulderClickRoute(
                24,
                envelopeSupported = true,
                Haptics.ShoulderEffectKind.CLICK_THUD,
            ),
        )
    }

    @Test
    fun shoulderThudScaleAndTimingAreUnchanged() {
        // The thud is 0.3x of the user's strength in both the
        // composed path and the posted path — one function,
        // so the fold-in cannot drift it — and it still fires
        // exactly 20ms after the click starts.
        assertEquals(0.3f, Haptics.shoulderThudScale(100), 0.001f)
        assertEquals(0.15f, Haptics.shoulderThudScale(50), 0.001f)
        assertEquals(0f, Haptics.shoulderThudScale(0), 0.001f)
        assertEquals(20, Haptics.SHOULDER_THUD_DELAY_MS)
    }
}
