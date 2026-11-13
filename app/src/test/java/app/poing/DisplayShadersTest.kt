package app.poing

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
        // The FSTN ramp: luma into a dark-to-light mix.
        assertTrue(src.contains("dot(col"))
        assertTrue(src.contains("mix(dark, light, luma)"))
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
