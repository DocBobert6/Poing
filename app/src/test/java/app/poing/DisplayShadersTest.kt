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
        // Prism is mode 2: the measured 32-entry gamma LUT,
        // then the colour matrix in linear light (Pokefan531,
        // Nov 2025). No power-law gamma, no extra lift — the
        // LUT IS the lift, and the current reference pipeline
        // has no saturation matrix and no extra gamma lift.
        assertTrue(src.contains("uMode == 2"))
        assertTrue(src.contains("uGammaLut"))
        assertTrue(src.contains("uGammaRow"))
        assertTrue(src.contains("min(floor(col.r * 32.0), 31.0)"))
        assertTrue(src.contains("texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r"))
        assertTrue(src.contains("clamp(pow(g, vec3(2.2)) * 0.8989, vec3(0.0), vec3(1.0))"))
        // The current GBC matrix, effective rows — never the
        // old transposed rows (which did not preserve greys
        // and turned blue violet).
        assertTrue(src.contains("0.8944 * lin.r + 0.2179 * lin.g - 0.1125 * lin.b"))
        assertTrue(src.contains("0.1091 * lin.r + 0.6312 * lin.g + 0.2597 * lin.b"))
        assertTrue(src.contains("0.1447 * lin.r + 0.2837 * lin.g + 0.5716 * lin.b"))
        assertTrue(src.contains("pow(max(m, vec3(0.0)), vec3(1.0 / 2.2))"))
        assertFalse(src.contains("pow(col, vec3(1.7))"))
        assertFalse(src.contains("col * 1.08"))
        // The old transposed matrix is gone from Prism's own
        // block (Dusk still uses it until its own commit —
        // the Dusk test pins it there, so scope this check).
        val prism = src.substringAfter("uMode == 2").substringBefore("uMode == 3")
        assertFalse(prism.contains("0.82 * c.r + 0.125 * c.g + 0.195 * c.b"))
    }

    @Test
    fun gammaLutDataIsTheMeasuredRamps() {
        // The LUTs are Pokefan531's Nov 2025 measured ramps:
        // 32 entries each, one per 5-bit game value, 0 to 255,
        // strictly increasing.
        val gbc = DisplayShaders.GAMMA_LUT_GBC
        val gba = DisplayShaders.GAMMA_LUT_GBA
        assertEquals(32, gbc.size)
        assertEquals(32, gba.size)
        assertEquals(0, gbc.first())
        assertEquals(255, gbc.last())
        assertEquals(0, gba.first())
        assertEquals(255, gba.last())
        assertTrue(gbc.zipWithNext().all { (a, b) -> b > a })
        assertTrue(gba.zipWithNext().all { (a, b) -> b > a })
        // Spot values, pinned so a typo in the table fails
        // loudly: GBC index 9 is 110 (SameBoy says 76 — the
        // known reference disagreement, light angle), GBA
        // index 12 is 102.
        assertEquals(110, gbc[9])
        assertEquals(170, gbc[16])
        assertEquals(102, gba[12])
        assertEquals(160, gba[20])
        // The texture pixels: row 0 is the GBC ramp, row 1
        // the GBA ramp, 64 values in all.
        val pixels = DisplayShaders.gammaLutPixels()
        assertEquals(64, pixels.size)
        assertEquals(110, pixels[9])
        assertEquals(102, pixels[32 + 12])
        assertEquals(255, pixels[31])
        assertEquals(255, pixels[63])
    }

    @Test
    fun colourPassHasTheDuskMode() {
        val src = DisplayShaders.COLOUR_FRAGMENT
        // Dusk is mode 3: the measured 32-entry gamma LUT
        // (the GBA row), then the colour matrix in linear
        // light — the same hardware-scanned model as Prism,
        // with the Advance's own LUT and matrix. No
        // power-law gamma, no extra gamma lift, no
        // saturation mix: the LUT already lifts the shadows,
        // and the new pipeline is brighter in the shadows
        // than the old one without them.
        assertTrue(src.contains("uMode == 3"))
        assertTrue(src.contains("clamp(pow(g, vec3(2.2)) * 0.8915, vec3(0.0), vec3(1.0))"))
        // The current GBA matrix, effective rows — never the
        // old transposed rows.
        assertTrue(src.contains("0.8988 * lin.r + 0.2229 * lin.g - 0.1217 * lin.b"))
        assertTrue(src.contains("0.1163 * lin.r + 0.6217 * lin.g + 0.2620 * lin.b"))
        assertTrue(src.contains("0.1831 * lin.r + 0.2438 * lin.g + 0.5731 * lin.b"))
        assertTrue(src.contains("pow(max(m, vec3(0.0)), vec3(1.0 / 2.2))"))
        // The output is clamped on both sides.
        assertTrue(src.contains("clamp(col, vec3(0.0), vec3(1.0))"))
        assertFalse(src.contains("pow(col, vec3(3.2))"))
        assertFalse(src.contains("pow(col, vec3(0.85))"))
        assertFalse(src.contains("mix(vec3(luma), col, 1.15)"))
        assertFalse(src.contains("0.82 * c.r + 0.125 * c.g + 0.195 * c.b"))
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
