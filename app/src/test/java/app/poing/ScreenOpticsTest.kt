package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 — Screen Optics, on the JVM: the ambient light
 * mapping. A bright room boosts, a dark room never dims
 * unless the player asked for it, and a sensor that reads
 * nothing usable leaves the chain neutral.
 */
class ScreenOpticsTest {

    @Test
    fun darkRoomIsNeutralWithoutDimming() {
        // 0 lux: no boost, no dim — the chain as designed.
        assertEquals(OpticsParams.NEUTRAL, ScreenOptics.paramsFor(0f, false))
    }

    @Test
    fun brightRoomBoostsBrightnessAndContrast() {
        val params = ScreenOptics.paramsFor(10_000f, false)
        assertEquals(1.12f, params.brightness, 0.001f)
        assertEquals(1.20f, params.contrast, 0.001f)
        assertEquals(0.92f, params.gamma, 0.001f)
    }

    @Test
    fun midRoomSitsBetweenDarkAndBright() {
        val dark = ScreenOptics.paramsFor(0f, false)
        val mid = ScreenOptics.paramsFor(100f, false)
        val bright = ScreenOptics.paramsFor(10_000f, false)
        assertTrue(mid.brightness > dark.brightness)
        assertTrue(mid.brightness < bright.brightness)
        assertTrue(mid.contrast > dark.contrast)
        assertTrue(mid.contrast < bright.contrast)
        assertTrue(mid.gamma < dark.gamma)
        assertTrue(mid.gamma > bright.gamma)
    }

    @Test
    fun darkRoomDimsOnlyWhenTheToggleIsOn() {
        val dimmed = ScreenOptics.paramsFor(10f, true)
        assertEquals(0.85f, dimmed.brightness, 0.001f)
        assertEquals(1.12f, dimmed.gamma, 0.001f)
        // The same room without the toggle: no dimming.
        val lit = ScreenOptics.paramsFor(10f, false)
        assertTrue(lit.brightness > 1f)
        assertTrue(lit.gamma < 1f)
    }

    @Test
    fun brightRoomIgnoresTheDimmingToggle() {
        // Dimming only ever applies in a dark room.
        assertEquals(
            ScreenOptics.paramsFor(10_000f, false),
            ScreenOptics.paramsFor(10_000f, true),
        )
    }

    @Test
    fun unusableReadingsLeaveTheChainNeutral() {
        assertEquals(OpticsParams.NEUTRAL, ScreenOptics.paramsFor(Float.NaN, false))
        assertEquals(OpticsParams.NEUTRAL, ScreenOptics.paramsFor(Float.POSITIVE_INFINITY, false))
        assertEquals(OpticsParams.NEUTRAL, ScreenOptics.paramsFor(-5f, false))
        assertEquals(OpticsParams.NEUTRAL, ScreenOptics.paramsFor(Float.NaN, true))
    }
}
