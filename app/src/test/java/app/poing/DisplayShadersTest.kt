package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 — the chain shader source, on the JVM: the passes
 * exist, the chain is flat (no curvature, no CRT geometry
 * anywhere), and a compile failure has a plain fallback.
 * This pins the text the renderer compiles — the look itself
 * is a device check for Robert.
 */
class DisplayShadersTest {

    @Test
    fun vertexPassesTextureThroughWithAFlipUniform() {
        assertTrue(DisplayShaders.VERTEX.contains("aPos"))
        assertTrue(DisplayShaders.VERTEX.contains("aTex"))
        assertTrue(DisplayShaders.VERTEX.contains("uFlipV"))
        assertTrue(DisplayShaders.VERTEX.contains("vTex"))
    }

    @Test
    fun colourPassHasTheJadeModes() {
        val src = DisplayShaders.COLOUR_FRAGMENT
        assertTrue(src.contains("uMode"))
        assertTrue(src.contains("uTex"))
        // The FSTN ramp is a 4-stop piecewise map in luma,
        // the stops at the core's Grayscale palette lumas,
        // landing on mGBA's GB Pocket palette shades —
        // never the old linear blue ramp.
        assertTrue(src.contains("dot(col"))
        assertTrue(src.contains("vec3(0.647, 0.710, 0.647)"))
        assertTrue(src.contains("vec3(0.518, 0.612, 0.518)"))
        assertTrue(src.contains("vec3(0.290, 0.388, 0.322)"))
        assertTrue(src.contains("vec3(0.094, 0.161, 0.129)"))
        assertFalse(src.contains("mix(dark, light, luma)"))
        assertFalse(src.contains("vec3(0.85, 0.89, 0.93)"))
    }

    @Test
    fun gridPassIsDotMatrixOverNativeTexels() {
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("uTexSize"))
        assertTrue(src.contains("uGridStrength"))
        // One dot per native texel: the cell is the texel grid.
        assertTrue(src.contains("fract(vTex * uTexSize)"))
        assertTrue(src.contains("distance(cell, vec2(0.5))"))
    }

    @Test
    fun colourPassHasThePrismMode() {
        val src = DisplayShaders.COLOUR_FRAGMENT
        // Prism is mode 2: gamma-expand, colour-correct,
        // gamma-compress, then a lift for a reflective
        // screen in daylight.
        assertTrue(src.contains("uMode == 2"))
        assertTrue(src.contains("pow(col, vec3(1.7))"))
        assertTrue(src.contains("pow(max(m, vec3(0.0)), vec3(1.0 / 2.2))"))
        assertTrue(src.contains("col * 1.08"))
    }

    @Test
    fun colourPassHasTheDuskMode() {
        val src = DisplayShaders.COLOUR_FRAGMENT
        // Dusk is mode 3: the Advance's gamma, then a gamma
        // lift and a saturation lift for a dark, unlit screen.
        assertTrue(src.contains("uMode == 3"))
        assertTrue(src.contains("pow(col, vec3(3.2))"))
        assertTrue(src.contains("pow(col, vec3(0.85))"))
        assertTrue(src.contains("mix(vec3(luma), col, 1.15)"))
        // The saturation mix extrapolates below zero (a
        // colour whose blue is far below its luma comes out
        // negative on that channel), so the output is clamped
        // on both sides — the old min() only clamped the top.
        assertTrue(src.contains("clamp(col, vec3(0.0), vec3(1.0))"))
    }

    @Test
    fun gridPassHasTheTftMode() {
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("uGridMode"))
        // TFT lines: dark where one native texel meets the
        // next, not dots.
        assertTrue(src.contains("uGridMode == 1"))
        assertTrue(src.contains("min(cell, 1.0 - cell)"))
        // And the dot-matrix mode is still there for Jade.
        assertTrue(src.contains("distance(cell, vec2(0.5))"))
    }

    @Test
    fun gridProfilesAreSubtleAtFullHeightSize() {
        // The 2026-11-12 rework (Robert's screenshots): at the
        // new full-height size (4.5-5x) the old grid measured a
        // 22% luminance dip at every texel boundary. The dot is
        // wider now (bright to r<=0.44, dark only toward the
        // corners — 0.40 after the rework, 0.35 before it) and
        // the TFT lines narrower (0.04 of a cell each side,
        // was 0.08). The wider dot keeps the same max dip
        // (~6.3%) with a higher mean factor, so the dots read
        // cleaner with less overall dulling.
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("smoothstep(0.56, 0.44, distance(cell, vec2(0.5)))"))
        assertTrue(src.contains("smoothstep(0.0, 0.04, min(d.x, d.y))"))
        assertFalse(src.contains("smoothstep(0.52, 0.40,"))
        assertFalse(src.contains("smoothstep(0.5, 0.35,"))
        assertFalse(src.contains("smoothstep(0.0, 0.08,"))
    }

    @Test
    fun gridStrengthScalesDownWithDisplayScale() {
        // Base strength 0.10 (was a fixed 0.22), full at 2x
        // and below, tapering to half at 8x and above — so the
        // grid stays subtle as the game grows. At Robert's
        // sizes the effective strength is ~0.063 (5x) and
        // ~0.067 (4.5x), for a max dip near 6%, not 22%.
        assertEquals(0.10f, DisplayShaders.GRID_BASE_STRENGTH, 0.001f)
        assertEquals(0.10f, DisplayShaders.gridStrengthForScale(1.0f), 0.001f)
        assertEquals(0.10f, DisplayShaders.gridStrengthForScale(2.0f), 0.001f)
        assertEquals(0.0667f, DisplayShaders.gridStrengthForScale(4.5f), 0.001f)
        assertEquals(0.0632f, DisplayShaders.gridStrengthForScale(5.0f), 0.001f)
        assertEquals(0.05f, DisplayShaders.gridStrengthForScale(8.0f), 0.001f)
        assertEquals(0.05f, DisplayShaders.gridStrengthForScale(20.0f), 0.001f)
    }

    @Test
    fun gridPassHasTheScreenOpticsUniforms() {
        // The ambient light's brightness / contrast / gamma,
        // applied after the grid so the whole presented
        // picture sits under the room's light.
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("uBrightness"))
        assertTrue(src.contains("uContrast"))
        assertTrue(src.contains("uGamma"))
        assertTrue(src.contains("col *= uBrightness"))
        assertTrue(src.contains("(col - 0.5) * uContrast + 0.5"))
        assertTrue(src.contains("pow(max(col, vec3(0.0)), vec3(uGamma))"))
    }

    @Test
    fun plainFallbackIsABareSample() {
        val src = DisplayShaders.PLAIN_FRAGMENT
        assertTrue(src.contains("texture2D(uTex, vTex)"))
        assertFalse(src.contains("uMode"))
        assertFalse(src.contains("uGridStrength"))
        assertFalse(src.contains("uGridMode"))
    }

    @Test
    fun noCurvatureOrCrtGeometryAnywhere() {
        // Every handheld LCD here is flat. If a curve ever
        // creeps into a chain, this fails first.
        val all = DisplayShaders.VERTEX +
            DisplayShaders.COLOUR_FRAGMENT +
            DisplayShaders.GRID_FRAGMENT +
            DisplayShaders.PLAIN_FRAGMENT
        val lower = all.lowercase()
        for (word in listOf("curve", "barrel", "pincushion", "distort", "scanline", "grille", "vignette")) {
            assertFalse("shader source must not contain '$word'", lower.contains(word))
        }
    }
}
