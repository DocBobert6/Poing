package app.poing

import kotlin.math.log10

/**
 * Phase 8 — Screen Optics: the ambient light the active
 * chain renders under. Pure Kotlin — no Android APIs — so
 * the mapping runs under plain JVM unit tests.
 *
 * The phone's ambient light sensor (lux) drives the chain's
 * brightness / contrast / gamma in real time: a bright room
 * boosts reflective contrast like a real reflective LCD in
 * the sun. The mapping is logarithmic — lux runs from a
 * dark room (~10) to sunlight (~10,000+) — so the factor
 * is log10(lux + 1) / 4, clamped to 0..1.
 *
 * The dimming side is a toggle, and it defaults OFF: dimming
 * for authenticity fights the player seeing the game, so a
 * dark room never dims the picture unless the player asked
 * for it. The bright-room boost is always on — boosting
 * never hides the game.
 */
data class OpticsParams(
    val brightness: Float,
    val contrast: Float,
    val gamma: Float,
) {
    companion object {
        /** No adjustment: the chain exactly as designed. */
        val NEUTRAL = OpticsParams(1f, 1f, 1f)
    }
}

object ScreenOptics {

    /** The lux below which a room counts as dark for the dimming toggle. */
    private const val DARK_FACTOR = 0.3f

    /** The dim the toggle applies in a dark room. */
    private const val DIM_BRIGHTNESS = 0.85f
    private const val DIM_GAMMA = 1.12f

    /**
     * The chain's brightness / contrast / gamma for a lux
     * reading. A sensor that reads nothing usable (no sensor,
     * or a non-finite value) leaves the chain neutral.
     */
    fun paramsFor(lux: Float, dimmingEnabled: Boolean): OpticsParams {
        if (!lux.isFinite()) return OpticsParams.NEUTRAL
        val factor = (log10(lux.coerceAtLeast(0f) + 1f) / 4f).coerceIn(0f, 1f)
        var brightness = 1f + 0.12f * factor
        val contrast = 1f + 0.20f * factor
        var gamma = 1f - 0.08f * factor
        if (dimmingEnabled && factor < DARK_FACTOR) {
            brightness = DIM_BRIGHTNESS
            gamma = DIM_GAMMA
        }
        return OpticsParams(brightness, contrast, gamma)
    }
}
