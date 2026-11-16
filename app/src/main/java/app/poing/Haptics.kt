package app.poing

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.roundToInt

/**
 * Alpha.8 — haptics, the realistic-click version: a short click
 * for EVERY overlay button (D-pad, A/B, L/R, Start/Select), not
 * just D-pad and A/B. The click itself is the system's own
 * hardware-tuned click primitive where the device has one —
 * [VibrationEffect.EFFECT_CLICK] on API 29+, scaled through
 * the composition primitives on API 30+ — so the button feels
 * like a real click, not a buzz. Below that, or on a vibrator
 * that cannot play primitives, it falls back to the old 12ms
 * one-shot, quieter when the strength is turned down.
 *
 * Alpha.11 — per-button character (Robert, 2026-11-15): a real
 * rubber-dome button has TWO tactile events (dome collapse on
 * press, spring-back on release) and the D-pad is a rocker, so
 * each button now feels like its own hardware instead of the
 * same click everywhere. A/B keep the full click; Start/Select
 * click lighter; a D-pad direction change while held is a
 * lighter tick (the confirmed-kept roll tick — holding one
 * direction steady fires nothing, only a change ticks); GBA
 * L/R are click + low thud, the longer travel; every release
 * is a lighter tick. Each primitive is probed at runtime and
 * falls back to the plain click when unsupported — never to a
 * longer one-shot or a square waveform (50ms+ ringing reads
 * as a notification buzz), and a release tick is never at
 * press strength.
 *
 * Alpha.13 — envelope click (Robert, 2026-11-15): an
 * alternative press click, built with
 * [VibrationEffect.BasicEnvelopeBuilder] (API 36): instant
 * attack, ~15ms total, sharpness placed at the actuator's
 * resonant frequency from the FOAM frequency profile
 * ([Vibrator.getFrequencyProfile]). It is a refinement of
 * the press click only — the release tick, the roll tick,
 * the shoulder click+thud, the strength slider and all the
 * wiring are untouched. The device is probed at runtime
 * ([Vibrator.areEnvelopeEffectsSupported]); where envelope
 * effects are unsupported — which is unconfirmed on the
 * Pixel 10a until probed on device — the press falls back
 * to the current click, and if an envelope the device
 * claims to support will not build, the plain click is the
 * fallback there too. A button always clicks; nothing here
 * claims the envelope feels better — that is a device A/B
 * verdict, not something tests can settle. Never
 * WaveformEnvelopeBuilder: it has no fallback, so on an
 * unsupported device nothing would play.
 *
 * [enabled] is the Settings toggle (on by default).
 * [strength] is the Settings slider, 0–100 (100 by default);
 * 0 means off, the same as the toggle off. A null vibrator —
 * a device without one — is a silent no-op, never a crash,
 * and a vibrator that rejects an effect never crashes the
 * player either: haptics is cosmetic, gameplay is not.
 */
class Haptics(
    private val vibrator: Vibrator?,
    private val enabled: Boolean = true,
    private val strength: Int = DEFAULT_STRENGTH,
) {
    /** Which effect a click is, for this strength and device. */
    enum class EffectKind {
        /** The scaled click primitive (API 30+, vibrator plays primitives). */
        PRIMITIVE_CLICK,

        /** The system's prebaked click (API 29+, full strength only — it has no amplitude). */
        PREDEFINED_CLICK,

        /** The 12ms one-shot, amplitude scaled (older devices, or a reduced strength on API 29). */
        ONE_SHOT,
    }

    /** Which effect a tick is, for this device. */
    enum class TickEffectKind {
        /** The scaled tick primitive (API 30+, vibrator plays primitives). */
        PRIMITIVE_TICK,

        /** The 8ms one-shot, amplitude scaled (no tick primitive). */
        ONE_SHOT,
    }

    /** Which effect an L/R press is, for this device. */
    enum class ShoulderEffectKind {
        /** Click + low thud (API 30+, both primitives play). */
        CLICK_THUD,

        /** Click alone (thud primitive unsupported). */
        CLICK_ONLY,

        /** The plain click effect (no primitives at all). */
        PLAIN_CLICK,
    }

    /** A/B (and the old single-click path): the full click. */
    fun click() {
        val v = vibrator ?: return
        play(v) { pressClickEffect(v, strength) }
    }

    /** Start/Select: the same click, lighter — a smaller pill. */
    fun lightClick() {
        val v = vibrator ?: return
        val effectiveStrength = (strength * START_SELECT_SCALE).roundToInt()
        play(v) { pressClickEffect(v, effectiveStrength) }
    }

    /**
     * A D-pad / joystick direction change while held: the
     * rocker rolling onto a new dome. A tick, not a click —
     * the dome is already half down, so the event is lighter
     * than the press.
     */
    fun directionTick() {
        val v = vibrator ?: return
        play(v) { buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
    }

    /** A release: the dome springing back. Lighter than the press, always. */
    fun releaseTick() {
        val v = vibrator ?: return
        play(v) { buildTickEffect(RELEASE_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
    }

    /**
     * GBA L/R: the shoulder's longer travel — a click plus a
     * low thud 20ms later, one thunk. Without the thud
     * primitive it is just the click.
     */
    fun shoulderClick() {
        val v = vibrator ?: return
        play(v) { buildShoulderEffect(strength, Build.VERSION.SDK_INT, primitivesSupported(v), thudSupported(v)) }
    }

    /**
     * The press click: the envelope click where the device
     * supports envelope effects (API 36+), else the current
     * click. An envelope the device claims to support but
     * will not build still falls back to the plain click —
     * a button always clicks, never goes silent. Built
     * lazily, inside [play]'s guard, like every effect.
     */
    private fun pressClickEffect(v: Vibrator, clickStrength: Int): VibrationEffect {
        if (useEnvelopeClick(Build.VERSION.SDK_INT, envelopeSupported(v))) {
            return try {
                val (attackMs, decayMs) =
                    envelopeClickDurations(minControlPointDurationMs(v))
                buildEnvelopeClickEffect(clickStrength, envelopeSharpness(v), attackMs, decayMs)
            } catch (_: Exception) {
                buildEffect(clickStrength, Build.VERSION.SDK_INT, primitivesSupported(v))
            }
        }
        return buildEffect(clickStrength, Build.VERSION.SDK_INT, primitivesSupported(v))
    }

    /**
     * True when this vibrator can play envelope effects
     * (API 36+). The SDK guard runs first: the probe method
     * does not exist below API 36, and a vibrator that
     * cannot answer falls back to the current click.
     */
    private fun envelopeSupported(vibrator: Vibrator): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return try {
            vibrator.areEnvelopeEffectsSupported()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The envelope sharpness for this vibrator: the resonant
     * frequency's place in the profile's supported range, so
     * the click lives in the actuator's resonant band. No
     * profile (or a profile that cannot answer) takes the
     * full sharpness — the documented snappiest value.
     * Only ever called on the envelope path (API 36+).
     */
    private fun envelopeSharpness(vibrator: Vibrator): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return DEFAULT_ENVELOPE_SHARPNESS
        }
        return try {
            val profile = vibrator.frequencyProfile ?: return DEFAULT_ENVELOPE_SHARPNESS
            resonantSharpness(
                profile.minFrequencyHz,
                profile.maxFrequencyHz,
                vibrator.resonantFrequency,
            )
        } catch (_: Exception) {
            DEFAULT_ENVELOPE_SHARPNESS
        }
    }

    /**
     * The fastest step this vibrator's envelope effects can
     * take, from its own effect info — the attack duration.
     * 1ms where the info is unavailable. Only ever called
     * on the envelope path (API 36+).
     */
    private fun minControlPointDurationMs(vibrator: Vibrator): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return 1L
        return try {
            vibrator.envelopeEffectInfo?.minControlPointDurationMillis?.coerceAtLeast(1L) ?: 1L
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * The one guard: enabled, strength, a real vibrator,
     * never a crash. The effect is built lazily, after the
     * guard — createOneShot rejects amplitude 0, so building
     * it first would throw at strength 0 before the guard
     * ever ran (the strength-0 tests caught exactly that).
     */
    private fun play(v: Vibrator, effect: () -> VibrationEffect) {
        if (!enabled || strength <= 0) return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(effect())
        } catch (_: Exception) {
            // A vibrator that rejects the effect: silent, never a crash.
        }
    }

    companion object {
        const val CLICK_MS = 12L
        const val TICK_MS = 8L
        const val DEFAULT_STRENGTH = 100
        const val MIN_STRENGTH = 0
        const val MAX_STRENGTH = 100

        /** Release tick scale: inside the 0.3–0.5 band, lighter than the roll tick. */
        const val RELEASE_TICK_SCALE = 0.4f

        /** Direction-change tick scale: a roll is an actuation, so it ticks harder than a release. */
        const val DIRECTION_TICK_SCALE = 0.5f

        /** Start/Select click scale: a lighter click, still clearly a click. */
        const val START_SELECT_SCALE = 0.6f

        /** L/R thud scale: low, the shoulder's deeper travel under the click. */
        const val SHOULDER_THUD_SCALE = 0.3f

        /** L/R thud delay: 15–25ms after the click starts, one thunk. */
        const val SHOULDER_THUD_DELAY_MS = 20

        /**
         * The envelope click's total length, attack + decay:
         * ~15ms, inside the 10–20ms a crisp click wants.
         * The attack is the device's fastest envelope step;
         * the decay takes the rest.
         */
        const val ENVELOPE_CLICK_TOTAL_MS = 15L

        /** Envelope sharpness with no frequency profile: the full sharpness, the snappiest value. */
        const val DEFAULT_ENVELOPE_SHARPNESS = 1.0f

        fun effectKind(strength: Int, sdkInt: Int, primitivesSupported: Boolean): EffectKind =
            when {
                sdkInt >= Build.VERSION_CODES.R && primitivesSupported ->
                    EffectKind.PRIMITIVE_CLICK
                sdkInt >= Build.VERSION_CODES.Q && strength >= MAX_STRENGTH ->
                    EffectKind.PREDEFINED_CLICK
                else -> EffectKind.ONE_SHOT
            }

        /** The 0–100 strength as a 0.0–1.0 primitive scale. */
        fun primitiveScale(strength: Int): Float =
            strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) / 100f

        /** The 0–100 strength as a 1–255 one-shot amplitude (0 means off). */
        fun scaledAmplitude(strength: Int): Int {
            if (strength <= 0) return 0
            return (strength.coerceIn(1, MAX_STRENGTH) * 255 / 100).coerceIn(1, 255)
        }

        fun buildEffect(strength: Int, sdkInt: Int, primitivesSupported: Boolean): VibrationEffect =
            when (effectKind(strength, sdkInt, primitivesSupported)) {
                EffectKind.PRIMITIVE_CLICK -> VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        primitiveScale(strength),
                    )
                    .compose()
                EffectKind.PREDEFINED_CLICK ->
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                EffectKind.ONE_SHOT ->
                    VibrationEffect.createOneShot(CLICK_MS, scaledAmplitude(strength))
            }

        /**
         * The envelope click or the current click: the
         * envelope only where the device is API 36+ and
         * reports envelope effects supported. A pure
         * function, so the choice pins in the tests; the
         * envelope effect itself can only be proven on a
         * real API 36 device, never under Robolectric's
         * SDK-35 harness (BasicEnvelopeBuilder does not
         * exist there).
         */
        fun useEnvelopeClick(sdkInt: Int, envelopeSupported: Boolean): Boolean =
            sdkInt >= Build.VERSION_CODES.BAKLAVA && envelopeSupported

        /**
         * The sharpness that puts the click at the
         * actuator's resonant frequency: the resonant
         * frequency's place in the profile's supported
         * range. The platform maps sharpness across that
         * range, so the fraction lands the click in the
         * resonant band rather than at an arbitrary
         * extreme. An unknown resonant frequency, or a
         * profile with no range, takes the full sharpness.
         */
        fun resonantSharpness(minHz: Float, maxHz: Float, resonantHz: Float): Float {
            if (resonantHz <= 0f || maxHz <= minHz) return DEFAULT_ENVELOPE_SHARPNESS
            return ((resonantHz - minHz) / (maxHz - minHz)).coerceIn(0f, 1f)
        }

        /**
         * The envelope click's two steps from the device's
         * fastest envelope step: the attack is that step,
         * the decay takes the rest of the ~15ms total —
         * never shorter than the attack, since a step
         * shorter than the device's minimum would be
         * rejected.
         */
        fun envelopeClickDurations(minStepMs: Long): Pair<Long, Long> {
            val attackMs = minStepMs.coerceAtLeast(1L)
            val decayMs = (ENVELOPE_CLICK_TOTAL_MS - attackMs).coerceAtLeast(attackMs)
            return attackMs to decayMs
        }

        /**
         * The envelope press click: instant attack to the
         * strength-scaled intensity at the resonant
         * sharpness, then a fast decay to silence — the
         * whole effect ~15ms. The builder starts at 0
         * intensity, so the attack is the rise from
         * nothing; the initial sharpness is the resonant
         * one, so the click never sweeps frequency. The
         * effect ends at zero intensity, as the builder
         * requires. Strength scales the intensity exactly
         * like the primitive click, so the slider behaves
         * the same.
         */
        fun buildEnvelopeClickEffect(
            strength: Int,
            sharpness: Float,
            attackMs: Long,
            decayMs: Long,
        ): VibrationEffect =
            VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(sharpness)
                .addControlPoint(primitiveScale(strength), sharpness, attackMs)
                .addControlPoint(0f, sharpness, decayMs)
                .build()

        /**
         * A tick at [scale] of the user's strength: the tick
         * primitive where the vibrator plays it, else a short
         * one-shot at the tick's reduced amplitude. Never the
         * predefined click — it has no amplitude, so it would
         * land at press strength, which a tick must never do.
         * The choice is a pure function ([tickEffectKind]),
         * so the matrix pins in the tests.
         */
        fun buildTickEffect(scale: Float, strength: Int, sdkInt: Int, tickSupported: Boolean): VibrationEffect =
            when (tickEffectKind(sdkInt, tickSupported)) {
                TickEffectKind.PRIMITIVE_TICK -> VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        (primitiveScale(strength) * scale).coerceIn(0f, 1f),
                    )
                    .compose()
                TickEffectKind.ONE_SHOT -> VibrationEffect.createOneShot(
                    TICK_MS,
                    (scaledAmplitude(strength) * scale).roundToInt().coerceIn(1, 255),
                )
            }

        fun tickEffectKind(sdkInt: Int, tickSupported: Boolean): TickEffectKind =
            if (sdkInt >= Build.VERSION_CODES.R && tickSupported) {
                TickEffectKind.PRIMITIVE_TICK
            } else {
                TickEffectKind.ONE_SHOT
            }

        /**
         * L/R: a click, plus a low thud 20ms later where the
         * vibrator plays it. Without the thud primitive it is
         * just the click; without primitives at all it is the
         * plain click effect. The choice is a pure function
         * ([shoulderEffectKind]), so the matrix pins in the
         * tests.
         */
        fun buildShoulderEffect(
            strength: Int,
            sdkInt: Int,
            primitivesSupported: Boolean,
            thudSupported: Boolean,
        ): VibrationEffect =
            when (shoulderEffectKind(sdkInt, primitivesSupported, thudSupported)) {
                ShoulderEffectKind.CLICK_THUD -> {
                    val composition = VibrationEffect.startComposition()
                        .addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_CLICK,
                            primitiveScale(strength),
                        )
                    val thudScale = (primitiveScale(strength) * SHOULDER_THUD_SCALE).coerceIn(0f, 1f)
                    // The 4-arg form (delay type) is API 34+;
                    // below that the 3-arg form carries the
                    // same delay. The delay-type constant lives
                    // on Composition, not on VibrationEffect.
                    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        composition.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_THUD,
                            thudScale,
                            SHOULDER_THUD_DELAY_MS,
                            VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET,
                        )
                    } else {
                        composition.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_THUD,
                            thudScale,
                            SHOULDER_THUD_DELAY_MS,
                        )
                    }
                    composition.compose()
                }
                ShoulderEffectKind.CLICK_ONLY -> VibrationEffect.startComposition()
                    .addPrimitive(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        primitiveScale(strength),
                    )
                    .compose()
                ShoulderEffectKind.PLAIN_CLICK -> buildEffect(strength, sdkInt, primitivesSupported)
            }

        fun shoulderEffectKind(
            sdkInt: Int,
            primitivesSupported: Boolean,
            thudSupported: Boolean,
        ): ShoulderEffectKind =
            when {
                sdkInt >= Build.VERSION_CODES.R && primitivesSupported && thudSupported ->
                    ShoulderEffectKind.CLICK_THUD
                sdkInt >= Build.VERSION_CODES.R && primitivesSupported ->
                    ShoulderEffectKind.CLICK_ONLY
                else -> ShoulderEffectKind.PLAIN_CLICK
            }

        /**
         * True when this vibrator can play the scaled click
         * primitive. A vibrator that cannot answer falls back
         * to the predefined click — never a crash.
         */
        fun primitivesSupported(vibrator: Vibrator): Boolean =
            allPrimitivesSupported(vibrator, VibrationEffect.Composition.PRIMITIVE_CLICK)

        /** True when this vibrator can play the tick primitive. */
        fun tickSupported(vibrator: Vibrator): Boolean =
            allPrimitivesSupported(vibrator, VibrationEffect.Composition.PRIMITIVE_TICK)

        /** True when this vibrator can play the thud primitive. */
        fun thudSupported(vibrator: Vibrator): Boolean =
            allPrimitivesSupported(vibrator, VibrationEffect.Composition.PRIMITIVE_THUD)

        private fun allPrimitivesSupported(vibrator: Vibrator, vararg primitiveIds: Int): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            return try {
                vibrator.areAllPrimitivesSupported(*primitiveIds)
            } catch (_: Exception) {
                false
            }
        }
    }
}
