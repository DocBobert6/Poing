package app.poing

/**
 * Phase 6 (item 1) — fast-forward / slow motion.
 *
 * The emulation loop paces itself to the core's frame rate: one
 * retro_run() per [frameIntervalNs]. A speed multiplier divides
 * that interval — 2x runs two frames in the time of one, 0.5x
 * runs one frame in the time of two. The audio is muted whenever
 * the speed is not normal (the core still produces samples at
 * the multiplied rate, and playing them at 48 kHz would sound
 * fast or slow — the session drains and discards them instead).
 *
 * Pure Kotlin, no Android APIs, so the pacing math is unit-tested
 * on the JVM (PlaySpeedTest).
 */
enum class PlaySpeed(val multiplier: Double, val label: String) {
    SLOW(0.5, "0.5x"),
    NORMAL(1.0, "1x"),
    FF2(2.0, "2x"),
    FF3(3.0, "3x"),
    FF4(4.0, "4x");

    companion object {
        /** The fast-forward speeds the Settings screen offers. */
        val FAST_FORWARD = listOf(FF2, FF3, FF4)

        /** The [PlaySpeed] for a stored fast-forward multiplier (2, 3 or 4); NORMAL for anything else. */
        fun fromMultiplier(multiplier: Int): PlaySpeed = when (multiplier) {
            2 -> FF2
            3 -> FF3
            4 -> FF4
            else -> NORMAL
        }

        /**
         * The nanoseconds between frames at [fps] core frames per
         * second, run at [speed]. A non-positive fps or speed is
         * not a pace at all — it returns 0, and the caller must
         * not sleep on it.
         */
        fun frameIntervalNs(fps: Double, speed: PlaySpeed): Long {
            if (fps <= 0.0 || speed.multiplier <= 0.0) return 0L
            return (1_000_000_000.0 / fps / speed.multiplier).toLong()
        }
    }
}
