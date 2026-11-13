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
        // 10 lux, toggle on: the dim scales with the darkness
        // (Robert, 2026-11-12 — the old flat 0.85/1.12 read as
        // no difference, and the old ~15 lux threshold meant a
        // normal dark room never dimmed at all).
        val dimmed = ScreenOptics.paramsFor(10f, true)
        assertEquals(0.731f, dimmed.brightness, 0.001f)
        assertEquals(1.014f, dimmed.contrast, 0.001f)
        assertEquals(1.089f, dimmed.gamma, 0.001f)
        // The same room without the toggle: no dimming.
        val lit = ScreenOptics.paramsFor(10f, false)
        assertTrue(lit.brightness > 1f)
        assertTrue(lit.gamma < 1f)
    }

    @Test
    fun dimEngagesBelowAboutOneHundredLux() {
        // The threshold is factor 0.5, i.e. ~99 lux: a dimly-lit
        // room dims, a lit room does not.
        assertTrue(ScreenOptics.paramsFor(50f, true).brightness < 1f)
        assertEquals(
            ScreenOptics.paramsFor(100f, false),
            ScreenOptics.paramsFor(100f, true),
        )
        assertEquals(
            ScreenOptics.paramsFor(1000f, false),
            ScreenOptics.paramsFor(1000f, true),
        )
    }

    @Test
    fun dimScalesWithDarkness() {
        // Brightness rises and gamma falls monotonically as the
        // room gets lighter — a near-black room dims clearly, a
        // dimly-lit room dims a little, a lit room not at all.
        // (brightness, contrast, gamma) at 0 / 1 / 10 / 20 / 50 lux.
        val at = listOf(0f, 1f, 10f, 20f, 50f).map { ScreenOptics.paramsFor(it, true) }
        for (i in 0 until at.size - 1) {
            assertTrue(at[i].brightness < at[i + 1].brightness)
            assertTrue(at[i].gamma > at[i + 1].gamma)
        }
        assertEquals(0.600f, at[0].brightness, 0.001f)
        assertEquals(1.000f, at[0].contrast, 0.001f)
        assertEquals(1.150f, at[0].gamma, 0.001f)
        assertEquals(0.615f, at[1].brightness, 0.001f)
        assertEquals(0.806f, at[3].brightness, 0.001f)
        assertEquals(0.937f, at[4].brightness, 0.001f)
    }

    @Test
    fun contrastBoostFadesOutInTheDark() {
        // The bright-room contrast boost never survives into a
        // dimming room: at pitch black it is gone (neutral 1.0),
        // and at 10 lux it is smaller than the undimmed value.
        assertEquals(1.000f, ScreenOptics.paramsFor(0f, true).contrast, 0.001f)
        assertTrue(
            ScreenOptics.paramsFor(10f, true).contrast <
                ScreenOptics.paramsFor(10f, false).contrast,
        )
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
