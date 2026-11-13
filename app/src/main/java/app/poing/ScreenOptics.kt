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

    /**
     * The factor below which a room counts as dark for the
     * dimming toggle: factor 0.5 is log10(lux + 1) = 2, i.e.
     * ~99 lux — a room a person would call dark. The old
     * 0.3 (~15 lux, nearly pitch black) meant a normal dark
     * room never dimmed at all (Robert, 2026-11-12).
     */
    private const val DARK_FACTOR = 0.5f

    /**
     * The dim's depth at pitch black: brightness falls to
     * 1 - DIM_DEPTH of its bright-room base (0.60 at 0 lux),
     * so the dim is clearly visible instead of the old flat
     * 0.85 that read as no difference.
     */
    private const val DIM_DEPTH = 0.40f

    /** The gamma rise at pitch black, on top of the bright-room gamma. */
    private const val DIM_GAMMA_RISE = 0.15f

    /**
     * The chain's brightness / contrast / gamma for a lux
     * reading. A sensor that reads nothing usable (no sensor,
     * or a non-finite value) leaves the chain neutral.
     *
     * With the dimming toggle on, the dim scales with the
     * darkness instead of switching on at one lux value:
     * depth is 0 at the ~99 lux threshold and 1 at pitch
     * black, eased (squared) so a dimly-lit room dims a
     * little and a near-black room dims clearly. The
     * bright-room contrast boost fades out with the depth,
     * to neutral at pitch black — never boosted while
     * dimming, never flattened below 1. At and above the
     * threshold the mapping is exactly the bright-room one,
     * so there is no step where the dim starts.
     */
    fun paramsFor(lux: Float, dimmingEnabled: Boolean): OpticsParams {
        if (!lux.isFinite()) return OpticsParams.NEUTRAL
        val factor = (log10(lux.coerceAtLeast(0f) + 1f) / 4f).coerceIn(0f, 1f)
        var brightness = 1f + 0.12f * factor
        var contrast = 1f + 0.20f * factor
        var gamma = 1f - 0.08f * factor
        if (dimmingEnabled && factor < DARK_FACTOR) {
            val depth = 1f - (factor / DARK_FACTOR).let { it * it }
            brightness *= 1f - DIM_DEPTH * depth
            contrast = 1f + 0.20f * factor * (1f - depth)
            gamma += DIM_GAMMA_RISE * depth
        }
        return OpticsParams(brightness, contrast, gamma)
    }
}
