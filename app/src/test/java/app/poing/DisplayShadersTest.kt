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
    fun plainFallbackIsABareSample() {
        val src = DisplayShaders.PLAIN_FRAGMENT
        assertTrue(src.contains("texture2D(uTex, vTex)"))
        assertFalse(src.contains("uMode"))
        assertFalse(src.contains("uGridStrength"))
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
