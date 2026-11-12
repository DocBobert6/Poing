package app.poing

import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Phase 6 (item 10b) — cartridge rumble: the phone's vibrator
 * driven by the core's libretro rumble interface. The core calls
 * the native rumble callback whenever the rumble level changes
 * (both motors get the same level); the bridge forwards the
 * level to [Bridge.onRumble], and this class runs the motor
 * while the level is above zero.
 *
 * A null vibrator — a device without one — is a silent no-op,
 * never a crash (same rule as [Haptics]). A duplicate level is
 * a no-op too, so the waveform is not restarted on every
 * repeated call from the core. The waveform is 100ms on / 50ms
 * off, repeating, at the mapped amplitude — a continuous rumble
 * while the game holds the level, not a single buzz.
 */
class CartridgeRumble(
    private val vibrator: Vibrator?,
) {
    companion object {
        /** The core's rumble strength range (libretro: 0..0xffff). */
        const val MAX_STRENGTH = 0xFFFF

        /** One rumble pulse: 100ms on, 50ms off, repeating. */
        const val PULSE_ON_MS = 100L
        const val PULSE_OFF_MS = 50L
    }

    /** The level currently running (0 = off). */
    var currentStrength = 0
        private set

    /**
     * Set the rumble level (0..0xFFFF, clamped). Above zero the
     * motor runs at the mapped amplitude (1..255); zero cancels
     * it. A level equal to the running one changes nothing.
     */
    fun setStrength(strength: Int) {
        val clamped = strength.coerceIn(0, MAX_STRENGTH)
        if (clamped == currentStrength) return
        currentStrength = clamped
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (clamped == 0) {
            v.cancel()
            return
        }
        val amplitude = (clamped * 255 / MAX_STRENGTH).coerceAtLeast(1)
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, PULSE_ON_MS, PULSE_OFF_MS),
                intArrayOf(0, amplitude, 0),
                1, // repeat from index 1: on, off, on, off…
            ),
        )
    }

    /** Stop the motor (a game quit, or the player leaving). */
    fun stop() {
        currentStrength = 0
        vibrator?.cancel()
    }
}
