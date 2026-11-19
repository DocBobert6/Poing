package app.poing

import android.os.Build
import android.os.Handler
import android.os.Looper
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
 * Alpha.15 — the retune (Robert, 2026-11-16, device verdict
 * on alpha.13): the envelope is GOOD on Start/Select and
 * WORSE than the alpha.12 click on A/B, so A/B are back on
 * the alpha.12 click (no envelope) and Start/Select keep
 * the envelope. The D-pad and the stick try the envelope
 * on a direction change — a rocker rolling onto a new dome,
 * not a button press, so the envelope is tuned softer than
 * Start/Select's, landing slightly stronger than the old
 * 0.5x tick. Where envelopes are unsupported the roll
 * falls back to the tick, whose scale rose 0.5 to 0.6 —
 * clearly present, still obviously below A/B's full click.
 * Whether the envelope suits the rocker is Robert's device
 * verdict, not something tests can settle. Fold-in
 * (Robert, 2026-11-16 00:36): L/R take the envelope too —
 * only the CLICK part of the shoulder; the thud stays
 * exactly as it is (PRIMITIVE_THUD 0.3x at 20ms), fired
 * 20ms after the envelope click starts, once the ~15ms
 * click has finished, so the second vibrate() never cuts
 * it off.
 *
 * Alpha.17 — the haptics pair (Robert, 2026-11-16, device
 * verdicts on alpha.15): the envelope is OFF the rocker —
 * a direction change is back on the plain PRIMITIVE_TICK
 * at 0.6, "normal haptics for that", and the direction
 * envelope's strength and sharpness functions are gone
 * entirely, not kept as a fallback. The release tick rose
 * 0.4 to 0.5 (slightly too faint) — still clearly below
 * the 0.6 roll tick and the full press click. A/B, the
 * Start/Select envelope, the L/R envelope + thud, the
 * steering, the dead zone, and all the wiring are
 * untouched.
 *
 * Alpha.27 — the D-pad envelope split (Robert, 2026-11-16
 * 13:29, design confirmed 13:31): the envelope is back on
 * the rocker, but only where the dome is struck fresh or
 * rolled onto straight — a press from neutral (any
 * direction) and a roll to a cardinal take the alpha.15
 * direction envelope (0.75x resonant sharpness, strength
 * 0.6x); a roll to a diagonal takes the plain tick at 0.6.
 * The split is a pure routing ([directionEffectKind] in
 * the companion) over the previous and new direction
 * masks, so the tests pin it; the stick never takes this
 * path — it keeps the plain tick — and the release tick
 * (0.5, full release only) is untouched.
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

    /** Which route the shoulder click takes. */
    enum class ShoulderClickRoute {
        /** Envelope click now, thud scheduled 20ms later. */
        ENVELOPE_THEN_THUD,

        /** Envelope click alone (no thud primitive). */
        ENVELOPE_ONLY,

        /** The current composition: click + thud, click alone, or plain click. */
        COMPOSITION,
    }

    /** Which effect a D-pad direction change is, for the alpha.27 split. */
    enum class DirectionEffectKind {
        /** The direction envelope (alpha.15 strength and sharpness). */
        ENVELOPE,

        /** The plain tick primitive at 0.6. */
        PLAIN_TICK,
    }

    /**
     * A/B: the full click — the alpha.12 press, no envelope.
     * Robert's device verdict on alpha.13 was that the
     * envelope on A/B is worse than this click, so the face
     * buttons stay on the scaled click primitive whatever
     * the device supports.
     */
    fun click() {
        val v = vibrator ?: return
        play(v) { buildEffect(strength, Build.VERSION.SDK_INT, primitivesSupported(v)) }
    }

    /** Start/Select: the same click, lighter — a smaller pill. */
    fun lightClick() {
        val v = vibrator ?: return
        val effectiveStrength = (strength * START_SELECT_SCALE).roundToInt()
        play(v) { pressClickEffect(v, effectiveStrength) }
    }

    /**
     * A joystick direction change while held: the stick
     * rolling onto a new direction. The plain
     * PRIMITIVE_TICK at 0.6 — the stick never takes the
     * envelope path (alpha.27 split: the envelope is the
     * D-pad's only). A tick, not a click: the dome is
     * already half down, so the event is lighter than the
     * press. This is also the D-pad's fallback where no
     * routing is supplied (the editor preview).
     */
    fun directionTick() {
        val v = vibrator ?: return
        play(v) { buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
    }

    /**
     * A D-pad direction change while held, routed by the
     * alpha.27 split (Robert, 2026-11-16 13:29): a press
     * from neutral (any direction) and a roll to a cardinal
     * take the direction envelope — the alpha.15 envelope
     * at the direction strength and the softer 0.75x
     * resonant sharpness; a roll to a diagonal takes the
     * plain tick at 0.6. Where the device has no envelopes,
     * or an envelope it claims to support will not build,
     * the envelope branch falls back to the same tick, so
     * the choice is only about character, never about how
     * hard the roll lands. The release never comes through
     * here — it has its own hook ([releaseTick]), fired
     * only on full release. [newMask] is never 0 on this
     * path: the D-pad only calls it on a change to a
     * non-empty set.
     */
    fun directionTick(previousMask: Int, newMask: Int) {
        val v = vibrator ?: return
        when (directionEffectKind(previousMask, newMask)) {
            DirectionEffectKind.ENVELOPE -> play(v) { directionTickEffect(v) }
            DirectionEffectKind.PLAIN_TICK ->
                play(v) { buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
        }
    }

    /**
     * The D-pad's envelope event: the envelope where the
     * device supports envelope effects (API 36+), at the
     * direction strength and a softer sharpness than the
     * press — a rocker onto a dome, not a button press.
     * Where the device has no envelopes, or an envelope
     * it claims to support will not build, the fallback
     * is the tick at 0.6x — the same amplitude the
     * envelope lands at, so the choice is only about
     * character, never about how hard the roll lands.
     * Built lazily, inside [play]'s guard, like every
     * effect. Restored from alpha.15 (alpha.17 removed
     * it); the split in [directionEffectKind] decides
     * when it is taken.
     */
    private fun directionTickEffect(v: Vibrator): VibrationEffect {
        if (useEnvelopeClick(Build.VERSION.SDK_INT, envelopeSupported(v))) {
            return try {
                val (attackMs, decayMs) =
                    envelopeClickDurations(minControlPointDurationMs(v))
                buildEnvelopeClickEffect(
                    directionEnvelopeStrength(strength),
                    directionEnvelopeSharpness(envelopeSharpness(v)),
                    attackMs, decayMs,
                )
            } catch (_: Exception) {
                buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v))
            }
        }
        return buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v))
    }

    /** A release: the dome springing back. Lighter than the press, always. */
    fun releaseTick() {
        val v = vibrator ?: return
        play(v) { buildTickEffect(RELEASE_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
    }

    /**
     * Alpha.33 — the unlock's rarity haptic, by tier (see
     * [RaUnlockRarityKind]): a common unlock plays nothing
     * extra — whatever the pop itself does on device is
     * untouched, and this function never claims to know
     * what that is (Robert's device report is that the pop
     * has its own unique very light haptic; the code shows
     * no haptic on the unlock path, and the question is
     * closed unresolved). The rare tiers play a pattern
     * clearly different from a single light pop, on top of
     * whatever the pop does: a rare unlock is one tick, an
     * ultra-rare unlock is two ticks, the second 80ms after
     * the first — so a rare unlock feels different from a
     * common one and an ultra-rare one different again, on
     * any device, regardless of what the base pop haptic
     * turns out to be. Capped by the user's haptics level
     * like every effect (the [play] guard): off means off.
     */
    fun unlockRarity(kind: RaUnlockRarityKind) {
        val v = vibrator ?: return
        when (kind) {
            RaUnlockRarityKind.PLAIN -> return
            RaUnlockRarityKind.RARE -> {
                play(v) { buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }
            }
            RaUnlockRarityKind.ULTRA_RARE -> {
                if (!play(v) { buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v)) }) return
                postUnlockRaritySecondTick(v)
            }
        }
    }

    /**
     * The ultra-rare unlock's second tick, fired 80ms after
     * the first. The first tick is ~8ms, so it has finished
     * by then and this second vibrate() never cuts it off.
     * The guard is re-checked at fire time: if haptics was
     * turned off in those 80ms, the second tick stays
     * silent — the same pattern as the shoulder thud.
     */
    private fun postUnlockRaritySecondTick(v: Vibrator) {
        val tick = try {
            buildTickEffect(DIRECTION_TICK_SCALE, strength, Build.VERSION.SDK_INT, tickSupported(v))
        } catch (_: Exception) {
            return
        }
        try {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (!enabled || strength <= 0) return@postDelayed
                    try {
                        v.vibrate(tick)
                    } catch (_: Exception) {
                        // Silent, like every haptics failure.
                    }
                },
                UNLOCK_RARITY_SECOND_TICK_DELAY_MS.toLong(),
            )
        } catch (_: Exception) {
            // No looper to post on: the first tick already played.
        }
    }

    /**
     * GBA L/R: the shoulder's longer travel — a click plus a
     * low thud 20ms later, one thunk. Alpha.15 fold-in: the
     * CLICK is the envelope where the device supports it
     * (the same envelope press Start/Select use); the thud
     * follows 20ms later, once the ~15ms click has
     * finished. Without the thud primitive it is just the
     * (envelope) click; without primitives at all it is the
     * plain click effect. The thud's timing, scale and
     * fallbacks are exactly as before.
     */
    fun shoulderClick() {
        val v = vibrator ?: return
        val kind = shoulderEffectKind(
            Build.VERSION.SDK_INT,
            primitivesSupported(v),
            thudSupported(v),
        )
        when (shoulderClickRoute(Build.VERSION.SDK_INT, envelopeSupported(v), kind)) {
            ShoulderClickRoute.ENVELOPE_THEN_THUD -> {
                if (!play(v) { pressClickEffect(v, strength) }) return
                postShoulderThud(v)
            }
            ShoulderClickRoute.ENVELOPE_ONLY -> {
                play(v) { pressClickEffect(v, strength) }
            }
            ShoulderClickRoute.COMPOSITION -> {
                play(v) { buildShoulderEffect(strength, Build.VERSION.SDK_INT, primitivesSupported(v), thudSupported(v)) }
            }
        }
    }

    /**
     * The shoulder's thud, fired 20ms after the envelope
     * click starts. The click is ~15ms, so it has finished
     * by then and this second vibrate() never cuts it off.
     * The thud is a composition with a single thud primitive
     * at the same 0.3x scale — the 20ms gap is the post
     * delay, not a composition offset, so the thud's timing
     * and scale are exactly as before. The guard is
     * re-checked at fire time: if haptics was turned off in
     * those 20ms, the thud stays silent.
     */
    private fun postShoulderThud(v: Vibrator) {
        val thud = try {
            VibrationEffect.startComposition()
                .addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    shoulderThudScale(strength),
                )
                .compose()
        } catch (_: Exception) {
            return
        }
        try {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (!enabled || strength <= 0) return@postDelayed
                    try {
                        v.vibrate(thud)
                    } catch (_: Exception) {
                        // Silent, like every haptics failure.
                    }
                },
                SHOULDER_THUD_DELAY_MS.toLong(),
            )
        } catch (_: Exception) {
            // No looper to post on: the click already played.
        }
    }

    /**
     * The press click for Start/Select (alpha.15: A/B no
     * longer come through here — they are back on the
     * alpha.12 click): the envelope click where the device
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
     * Returns true when an effect was actually played, so
     * the shoulder knows whether to schedule its thud.
     */
    private fun play(v: Vibrator, effect: () -> VibrationEffect): Boolean {
        if (!enabled || strength <= 0) return false
        if (!v.hasVibrator()) return false
        return try {
            v.vibrate(effect())
            true
        } catch (_: Exception) {
            // A vibrator that rejects the effect: silent, never a crash.
            false
        }
    }

    companion object {
        const val CLICK_MS = 12L
        const val TICK_MS = 8L
        const val DEFAULT_STRENGTH = 100
        const val MIN_STRENGTH = 0
        const val MAX_STRENGTH = 100

        /** Release tick scale: 0.5 — the top of the 0.3–0.5 band, raised 0.4 to 0.5 in alpha.17 (Robert: slightly too faint), still clearly below the 0.6 roll tick and the full press. */
        const val RELEASE_TICK_SCALE = 0.5f

        /**
         * Direction-change tick scale: raised 0.5 to 0.6 in
         * alpha.15 (Robert: the D-pad haptics was too
         * light). A roll is an actuation, so it ticks harder
         * than a release — and it now sits at the same scale
         * as Start/Select, which is fine: the tick is a
         * softer primitive than the click, so the roll still
         * lands clearly below a face button's full click.
         * Alpha.17: the envelope is gone from the rocker —
         * this plain tick is the whole direction effect.
         */
        const val DIRECTION_TICK_SCALE = 0.6f

        /**
         * The direction envelope's sharpness scale: the
         * resonant sharpness pulled down a quarter (0.75x),
         * so the roll reads softer than Start/Select's
         * envelope, which takes the resonant sharpness
         * whole, while staying in the resonant
         * neighbourhood. Restored from alpha.15 (alpha.17
         * removed it with the rocker envelope); alpha.27
         * brings the envelope back on the split routing.
         */
        const val DIRECTION_ENVELOPE_SHARPNESS_SCALE = 0.75f

        /** Start/Select click scale: a lighter click, still clearly a click. */
        const val START_SELECT_SCALE = 0.6f

        /** L/R thud scale: low, the shoulder's deeper travel under the click. */
        const val SHOULDER_THUD_SCALE = 0.3f

        /** L/R thud delay: 15–25ms after the click starts, one thunk. */
        const val SHOULDER_THUD_DELAY_MS = 20

        /**
         * Alpha.33 — the ultra-rare unlock's second tick:
         * 80ms after the first, so the two ticks read as a
         * double-tap, clearly different from a rare unlock's
         * single tick and from any single light pop haptic.
         */
        const val UNLOCK_RARITY_SECOND_TICK_DELAY_MS = 80

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
         * The D-pad split routing (alpha.27, Robert
         * 2026-11-16 13:29): a press from neutral — the
         * previous mask empty — takes the envelope whatever
         * the new direction is; a roll to a cardinal takes
         * the envelope; a roll to a diagonal takes the plain
         * tick. A pure function, so the matrix pins in the
         * tests; the envelope effect itself can only be
         * proven on a real API 36 device, never under
         * Robolectric's SDK-35 harness.
         */
        fun directionEffectKind(previousMask: Int, newMask: Int): DirectionEffectKind =
            when {
                previousMask == 0 -> DirectionEffectKind.ENVELOPE
                isCardinalDirection(newMask) -> DirectionEffectKind.ENVELOPE
                else -> DirectionEffectKind.PLAIN_TICK
            }

        /**
         * True when [mask] holds exactly one direction —
         * a straight up / down / left / right. Two bits is
         * a diagonal; no bits is neutral (never routed
         * here — a release has its own hook). Only the
         * four direction bits count, so an unrelated bit
         * never changes the answer.
         */
        fun isCardinalDirection(mask: Int): Boolean {
            val dirs = mask and (
                (1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_DOWN) or
                    (1 shl InputMask.JOY_LEFT) or (1 shl InputMask.JOY_RIGHT)
            )
            return dirs != 0 && (dirs and (dirs - 1)) == 0
        }

        /**
         * The direction envelope's strength: the user's
         * strength times the 0.6x direction scale — the
         * envelope lands at exactly the fallback tick's
         * amplitude, so the envelope-or-tick choice changes
         * only the character of the roll, never how hard
         * it lands. Restored from alpha.15.
         */
        fun directionEnvelopeStrength(strength: Int): Int =
            (strength * DIRECTION_TICK_SCALE).roundToInt()

        /**
         * The direction envelope's sharpness: the resonant
         * sharpness pulled down a quarter (0.75x) — softer
         * than Start/Select's envelope, which takes the
         * resonant sharpness whole. Restored from alpha.15.
         */
        fun directionEnvelopeSharpness(resonantSharpness: Float): Float =
            (resonantSharpness * DIRECTION_ENVELOPE_SHARPNESS_SCALE).coerceIn(0f, 1f)

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
                    val thudScale = shoulderThudScale(strength)
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
         * The shoulder's route: the envelope where the device
         * is API 36+ and reports envelope effects supported,
         * with the thud scheduled after it only where the
         * thud primitive exists; otherwise the current
         * composition. A pure function, so the matrix pins in
         * the tests — the envelope effect itself can only be
         * proven on a real API 36 device, never under
         * Robolectric's SDK-35 harness.
         */
        fun shoulderClickRoute(
            sdkInt: Int,
            envelopeSupported: Boolean,
            kind: ShoulderEffectKind,
        ): ShoulderClickRoute =
            when {
                useEnvelopeClick(sdkInt, envelopeSupported) && kind == ShoulderEffectKind.CLICK_THUD ->
                    ShoulderClickRoute.ENVELOPE_THEN_THUD
                useEnvelopeClick(sdkInt, envelopeSupported) ->
                    ShoulderClickRoute.ENVELOPE_ONLY
                else -> ShoulderClickRoute.COMPOSITION
            }

        /**
         * The shoulder thud's amplitude: the user's strength
         * times the 0.3x thud scale. One source of truth for
         * both the composed thud and the posted thud, so the
         * fold-in cannot drift the thud's scale.
         */
        fun shoulderThudScale(strength: Int): Float =
            (primitiveScale(strength) * SHOULDER_THUD_SCALE).coerceIn(0f, 1f)

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
