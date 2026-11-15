package app.poing

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

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

    fun click() {
        if (!enabled || strength <= 0) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(buildEffect(strength, Build.VERSION.SDK_INT, primitivesSupported(v)))
        } catch (_: Exception) {
            // A vibrator that rejects the effect: silent, never a crash.
        }
    }

    companion object {
        const val CLICK_MS = 12L
        const val DEFAULT_STRENGTH = 100
        const val MIN_STRENGTH = 0
        const val MAX_STRENGTH = 100

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
         * True when this vibrator can play the scaled click
         * primitive. A vibrator that cannot answer falls back
         * to the predefined click — never a crash.
         */
        fun primitivesSupported(vibrator: Vibrator): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            return try {
                vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            } catch (_: Exception) {
                false
            }
        }
    }
}
