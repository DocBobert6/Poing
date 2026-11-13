package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-system control styles (Robert, 2026-11-12: each console
 * gets its own buttons and style, like HowlingDog's
 * OverlayTheme): the three themes are matched to the real
 * hardware — DMG-01's black D-pad and maroon A/B, the Color's
 * dark grey buttons, the indigo Advance's pale grey buttons.
 * Pure Kotlin, so these run on the plain JVM.
 */
class ControlThemesTest {

    private fun red(argb: Int): Int = (argb shr 16) and 0xFF
    private fun green(argb: Int): Int = (argb shr 8) and 0xFF
    private fun blue(argb: Int): Int = argb and 0xFF

    /** Relative luminance, 0..1 (sRGB, the WCAG formula). */
    private fun luminance(argb: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(red(argb)) + 0.7152 * lin(green(argb)) + 0.0722 * lin(blue(argb))
    }

    @Test
    fun `each system gets its own theme`() {
        val gb = ControlThemes.themeFor(GameSystem.GB)
        val gbc = ControlThemes.themeFor(GameSystem.GBC)
        val gba = ControlThemes.themeFor(GameSystem.GBA)
        assertNotEquals(gb.buttonA.background, gbc.buttonA.background)
        assertNotEquals(gb.buttonA.background, gba.buttonA.background)
        assertNotEquals(gbc.buttonA.background, gba.buttonA.background)
        assertNotEquals(gb.dpadWell, gbc.dpadWell)
        assertNotEquals(gb.dpadWell, gba.dpadWell)
    }

    @Test
    fun `DMG has a black D-pad on a light shell well and maroon A and B`() {
        val theme = ControlThemes.themeFor(GameSystem.GB)
        // Black cross: every channel near zero.
        assertTrue(red(theme.dpadCross) <= 0x20)
        assertTrue(green(theme.dpadCross) <= 0x20)
        assertTrue(blue(theme.dpadCross) <= 0x20)
        // Light well: the shell's grey, lighter than either other well.
        assertTrue(red(theme.dpadWell) >= 0xA0)
        assertTrue(luminance(theme.dpadWell) > luminance(ControlThemes.themeFor(GameSystem.GBC).dpadWell))
        assertTrue(luminance(theme.dpadWell) > luminance(ControlThemes.themeFor(GameSystem.GBA).dpadWell))
        // Maroon A/B: red dominant over blue and green, white label.
        assertTrue(red(theme.buttonA.background) > blue(theme.buttonA.background))
        assertTrue(red(theme.buttonA.background) > green(theme.buttonA.background))
        assertEquals(theme.buttonA.background, theme.buttonB.background)
        assertEquals(0xFFFFFFFF.toInt(), theme.buttonA.label)
        // Grey Start/Select pills, black label.
        assertEquals(0xFF000000.toInt(), theme.startSelect.label)
    }

    @Test
    fun `Color has dark grey buttons and a purple well`() {
        val theme = ControlThemes.themeFor(GameSystem.GBC)
        // Dark grey A/B: all channels dark and near-equal (grey, not coloured).
        assertTrue(red(theme.buttonA.background) <= 0x50)
        assertTrue(green(theme.buttonA.background) <= 0x50)
        assertTrue(blue(theme.buttonA.background) <= 0x50)
        assertTrue(Math.abs(red(theme.buttonA.background) - blue(theme.buttonA.background)) <= 0x10)
        assertEquals(theme.buttonA.background, theme.buttonB.background)
        assertEquals(0xFFFFFFFF.toInt(), theme.buttonA.label)
        // Atomic Purple well: blue dominant, the shell's purple.
        assertTrue(blue(theme.dpadWell) > red(theme.dpadWell))
        assertTrue(red(theme.dpadWell) > green(theme.dpadWell))
        // Pressed is the app's Atomic Purple.
        assertEquals(0xFF7E57C2.toInt(), theme.pressed)
    }

    @Test
    fun `Advance has pale grey buttons on a deep indigo well`() {
        val theme = ControlThemes.themeFor(GameSystem.GBA)
        // Pale grey A/B: every channel light, and lighter than the Color's buttons.
        assertTrue(red(theme.buttonA.background) >= 0xC0)
        assertTrue(green(theme.buttonA.background) >= 0xC0)
        assertTrue(blue(theme.buttonA.background) >= 0xC0)
        assertTrue(
            luminance(theme.buttonA.background) >
                luminance(ControlThemes.themeFor(GameSystem.GBC).buttonA.background),
        )
        assertEquals(theme.buttonA.background, theme.buttonB.background)
        assertEquals(0xFF000000.toInt(), theme.buttonA.label)
        // The D-pad is pale grey too — not the dark grey the other two use.
        assertTrue(red(theme.dpadCross) >= 0xC0)
        // Deep indigo well: blue dominant and dark.
        assertTrue(blue(theme.dpadWell) > red(theme.dpadWell))
        assertTrue(luminance(theme.dpadWell) < 0.1)
    }

    @Test
    fun `every pressed colour is dark enough for a white label`() {
        for (system in GameSystem.entries) {
            val theme = ControlThemes.themeFor(system)
            assertTrue(
                "pressed for $system is too light for white",
                luminance(theme.pressed) < 0.4,
            )
        }
    }
}
