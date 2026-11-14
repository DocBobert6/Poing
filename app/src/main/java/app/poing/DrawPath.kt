package app.poing

/**
 * Edge smoothing, item 5 (2026-11-14) — the dev-stats
 * readout: which program actually drew the last frame.
 * The renderer records it on the GL thread (a @Volatile
 * var on DisplayRenderer); the UI reads it when the
 * once-a-second stats arrive, and the diagnostic line
 * shows it. This is the settler for the Phase 9 no-op
 * class of bug: edge on must never again read as "on"
 * in the settings while the picture is silently the
 * plain blit. The token is dev-stats only — it never
 * shows anywhere else.
 *
 * Pure Kotlin — no Android APIs — so the label mapping
 * runs under plain JVM unit tests. The renderer only
 * records the value.
 */
enum class DrawPath {
    /** The plain blit — the frame exactly as the core rendered it. */
    PLAIN,

    /** The chain's colour + grid passes. */
    CHAIN,

    /** The edge pass, with the plain blit for pass 2. */
    EDGE,

    /** The edge pass + the light grid (the combined mode). */
    EDGE_GRID;

    /**
     * The token for the diagnostic line. The fallback
     * case is the point: when edge was requested but the
     * frame came out plain, the token says so outright —
     * "plain (edge fell back)" — so a silent fallback
     * never reads as a quiet "plain".
     */
    fun displayLabel(edgeOn: Boolean): String = when (this) {
        EDGE -> "edge"
        EDGE_GRID -> "edge+grid"
        CHAIN -> "chain"
        PLAIN -> if (edgeOn) "plain (edge fell back)" else "plain"
    }
}
