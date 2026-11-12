package app.poing

/** Jade's colour mode: the FSTN look, or the palette as the core rendered it. */
enum class JadeMode {
    FSTN,
    PASSTHROUGH,
}

/**
 * Phase 8 — the display chains: one named look per system,
 * Jade (GB) / Prism (GBC) / Dusk (GBA), plus a clean/off
 * option. The names are the approved one-word vibe names
 * (2026-10-24); the user sees only the name and on/off, per
 * system — the chain's inner workings are never surfaced.
 *
 * Each chain is the default look for its system. The mapping
 * is complete: Jade (GB), Prism (GBC), Dusk (GBA). This file
 * is the single place the mapping lives.
 *
 * Pure Kotlin — no Android APIs — so the mapping runs under
 * plain JVM unit tests.
 */
enum class DisplayChain {
    JADE,
    PRISM,
    DUSK,
    CLEAN,
    ;

    companion object {

        /** The chain for a system — one named look each. */
        fun forSystem(system: GameSystem): DisplayChain = when (system) {
            GameSystem.GB -> JADE
            GameSystem.GBC -> PRISM
            GameSystem.GBA -> DUSK
        }

        /**
         * Jade's colour mode for a GB palette (the core's
         * mgba_gb_colors value). The uniform greyscale palettes
         * get the Pocket FSTN look — true black-and-white, blue
         * tinted; the DMG green and every coloured GBC/SGB
         * palette pass through as the core rendered them, because
         * that palette IS the look the picker promises.
         */
        fun jadeModeForPalette(palette: String): JadeMode = when (palette) {
            "Grayscale", "GB Pocket", "GB Light" -> JadeMode.FSTN
            else -> JadeMode.PASSTHROUGH
        }

        /**
         * The core options a chain actually runs under. While a
         * chain is on, the chain owns the colour: the core's own
         * colour correction is switched off, so a game is never
         * corrected twice (once by the core, once by the chain).
         * Clean leaves the user's core setting exactly as set.
         */
        fun effectiveCoreOptions(
            options: Map<String, String>,
            chain: DisplayChain,
        ): Map<String, String> {
            if (chain == CLEAN) return options
            return options + ("mgba_color_correction" to "OFF")
        }
    }
}
