package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6 (item 1) — the fast-forward / slow-motion pacing math. */
class PlaySpeedTest {

    @Test
    fun normalSpeedIsOneFrameInterval() {
        // GB/GBA run at 59.7 fps: one frame every ~16.75 ms.
        val interval = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.NORMAL)
        assertTrue(interval in 16_700_000L..16_800_000L)
    }

    @Test
    fun fastForwardDividesTheInterval() {
        val normal = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.NORMAL)
        val ff2 = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.FF2)
        val ff4 = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.FF4)
        // Longs have no (expected, actual, delta) overload in
        // JUnit 4 — compare as Doubles with a 1ns tolerance.
        assertEquals(normal / 2.0, ff2.toDouble(), 1.0)
        assertEquals(normal / 4.0, ff4.toDouble(), 1.0)
    }

    @Test
    fun slowMotionDoublesTheInterval() {
        val normal = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.NORMAL)
        val slow = PlaySpeed.frameIntervalNs(59.7, PlaySpeed.SLOW)
        assertEquals(normal * 2.0, slow.toDouble(), 1.0)
    }

    @Test
    fun nonPositiveFpsOrSpeedIsNotAPace() {
        assertEquals(0L, PlaySpeed.frameIntervalNs(0.0, PlaySpeed.NORMAL))
        assertEquals(0L, PlaySpeed.frameIntervalNs(-59.7, PlaySpeed.NORMAL))
    }

    @Test
    fun fromMultiplierMapsOnlyOfferedSpeeds() {
        assertEquals(PlaySpeed.FF2, PlaySpeed.fromMultiplier(2))
        assertEquals(PlaySpeed.FF3, PlaySpeed.fromMultiplier(3))
        assertEquals(PlaySpeed.FF4, PlaySpeed.fromMultiplier(4))
        assertEquals(PlaySpeed.NORMAL, PlaySpeed.fromMultiplier(1))
        assertEquals(PlaySpeed.NORMAL, PlaySpeed.fromMultiplier(99))
    }

    @Test
    fun fastForwardOffersTwoThreeFour() {
        assertEquals(
            listOf(PlaySpeed.FF2, PlaySpeed.FF3, PlaySpeed.FF4),
            PlaySpeed.FAST_FORWARD,
        )
    }
}
