package app.poing

import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Phase 6 (item 7) — haptics, light version only: a short click
 * for the D-pad and the A/B buttons. Nothing else vibrates — no
 * coin-drop thud, that is arcade-only.
 *
 * [enabled] is the Settings toggle (on by default). A null
 * vibrator — a device without one — is a silent no-op, never a
 * crash. One 12ms click at the default amplitude.
 */
class Haptics(
    private val vibrator: Vibrator?,
    private val enabled: Boolean = true,
) {
    fun click() {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(CLICK_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        const val CLICK_MS = 12L
    }
}
