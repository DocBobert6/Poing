package app.poing

/**
 * Worm Light realism (Robert, 2026-11-12): the colour of the
 * lamp's HARDWARE — the coiled cord, the head plastic and the
 * plug — is the player's choice. The light the lamp casts is
 * always warm white and is never customizable: a real Worm
 * Light's bulb is one warm white, whatever colour its plastic
 * is.
 *
 * Default is dark medium gray (Robert's word, 2026-11-12).
 * The swatches are the colours the real accessories shipped
 * in: the Atomic Purple of the GBC itself, clear, and the
 * bright green of the common third-party lights — plus black.
 * Clear is translucent plastic, so it renders at reduced
 * alpha with a stronger rim; the rest are solid.
 *
 * Pure Kotlin — no Android APIs — so the model runs under
 * plain JVM unit tests.
 */
enum class WormLightColor(
    /** The name shown beside the swatch. */
    val label: String,
    /** The hardware colour, as an ARGB int. */
    val argb: Int,
    /** True for translucent plastic (clear): render faded. */
    val translucent: Boolean = false,
) {
    DARK_GRAY("Dark grey", 0xFF555555.toInt()),
    ATOMIC_PURPLE("Atomic Purple", 0xFF7E57C2.toInt()),
    CLEAR("Clear", 0xFFD9D9D9.toInt(), translucent = true),
    GREEN("Green", 0xFF43A047.toInt()),
    BLACK("Black", 0xFF222222.toInt());

    companion object {
        /** Dark medium gray — Robert's default. */
        val DEFAULT = DARK_GRAY

        /** The swatch row, in display order. */
        val SWATCHES = listOf(DARK_GRAY, ATOMIC_PURPLE, CLEAR, GREEN, BLACK)

        /** A stored name back to a colour; anything unknown (a
         * hand-edited file, an older build) falls back to the
         * default rather than failing. */
        fun fromName(name: String?): WormLightColor =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
