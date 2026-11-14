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
        // Prism is mode 2, rebuilt 2026-11-13: SameBoy's
        // measured CGB response ramp (the 32-entry LUT, row 0),
        // then the mGBA colorspace matrix in linear light
        // (x0.94). The old Pokefan531 ramp + matrix was the
        // palest of the three measured models — grey8 92,
        // white 243 — and that paleness is what Robert
        // rejected. No green-mix: the matrix already carries
        // the channel crosstalk.
        assertTrue(src.contains("uMode == 2"))
        assertTrue(src.contains("uGammaLut"))
        assertTrue(src.contains("uGammaRow"))
        assertTrue(src.contains("min(floor(col.r * 32.0), 31.0)"))
        assertTrue(src.contains("texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r"))
        assertTrue(src.contains("pow(ramp, vec3(2.2))"))
        // The mGBA matrix, executed rows, x0.94 — never the
        // old Pokefan531 matrix (which did not lift the
        // shadows enough and washed the mid-tones).
        assertTrue(src.contains("0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.125 * lin.r + 0.665 * lin.g + 0.21 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.195 * lin.r + 0.075 * lin.g + 0.73 * lin.b)"))
        assertTrue(src.contains("pow(max(m, vec3(0.0)), vec3(1.0 / 2.2))"))
        assertFalse(src.contains("pow(col, vec3(1.7))"))
        assertFalse(src.contains("col * 1.08"))
        // The old Pokefan531 matrix is gone from Prism's own
        // block (Dusk still uses it until its own commit —
        // the Dusk test pins it there, so scope this check).
        val prism = src.substringAfter("uMode == 2").substringBefore("uMode == 3")
        assertFalse(prism.contains("0.8944 * lin.r"))
        assertFalse(prism.contains("0.8989"))
        assertFalse(prism.contains("0.82 * c.r + 0.125 * c.g + 0.195 * c.b"))
    }

    @Test
    fun gammaLutDataIsTheMeasuredRamps() {
        // The LUTs are SameBoy's measured response ramps
        // (`Core/display.c`): 32 entries each, one per 5-bit
        // game value, 0 to 255, strictly increasing. The GBC
        // row changed in the Prism commit and the GBA row in
        // the Dusk commit of the 2026-11-13 rebuild
        // (Pokefan531's ramps were the palest model and
        // Robert rejected their look).
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
        // loudly: GBC index 9 is 76 (Pokefan531 said 110 —
        // the known reference disagreement, light angle),
        // GBC index 16 is 161; GBA index 12 is 78
        // (Pokefan531 said 102), GBA index 19 is 138,
        // GBA index 20 is 147.
        assertEquals(76, gbc[9])
        assertEquals(161, gbc[16])
        assertEquals(78, gba[12])
        assertEquals(138, gba[19])
        assertEquals(147, gba[20])
        // The texture pixels: row 0 is the GBC ramp, row 1
        // the GBA ramp, 64 values in all.
        val pixels = DisplayShaders.gammaLutPixels()
        assertEquals(64, pixels.size)
        assertEquals(76, pixels[9])
        assertEquals(78, pixels[32 + 12])
        assertEquals(255, pixels[31])
        assertEquals(255, pixels[63])
    }

    @Test
    fun colourPassHasTheDuskMode() {
        val src = DisplayShaders.COLOUR_FRAGMENT
        // Dusk is mode 3, rebuilt 2026-11-13: SameBoy's
        // measured AGB response ramp (the 32-entry LUT, row 1),
        // then the mGBA colorspace matrix in linear light
        // (x0.94) — the same matrix as Prism. The old
        // Pokefan531 GBA matrix + lum 0.8915 is gone. No
        // power-law gamma, no extra gamma lift, no
        // saturation mix: the ramp already lifts the shadows.
        assertTrue(src.contains("uMode == 3"))
        assertTrue(src.contains("pow(ramp, vec3(2.2))"))
        // The mGBA matrix, executed rows, x0.94 — the same
        // matrix Prism uses.
        assertTrue(src.contains("0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.125 * lin.r + 0.665 * lin.g + 0.21 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.195 * lin.r + 0.075 * lin.g + 0.73 * lin.b)"))
        assertTrue(src.contains("pow(max(m, vec3(0.0)), vec3(1.0 / 2.2))"))
        // The output is clamped on both sides.
        assertTrue(src.contains("clamp(col, vec3(0.0), vec3(1.0))"))
        // The old Pokefan531 GBA matrix + lum is gone.
        assertFalse(src.contains("0.8915"))
        assertFalse(src.contains("0.8988 * lin.r"))
        assertFalse(src.contains("0.1163 * lin.r"))
        assertFalse(src.contains("0.1831 * lin.r"))
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
        // The 2026-11-13 rebuild (Robert's screenshots): the
        // old TFT line was a knife-edge — smoothstep(0.0,
        // 0.04), 0.08 of a cell, ~0.5 display px at 6.34x —
        // and it beat against the display pixels into a
        // lattice (5% of pixels ~5% dark on a flat white
        // field). The line is now a soft fall over the outer
        // quarter of the cell, and the strength fades with
        // the pixel's own brightness, so there is no grid at
        // all on a bright field. Jade's dot got the same
        // treatment: the same bright disc (r<=0.44) with a
        // fall twice as wide (0.68, 0.44), so the dots read
        // softer with less overall dulling.
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("smoothstep(0.68, 0.44, distance(cell, vec2(0.5)))"))
        assertTrue(src.contains("smoothstep(0.0, 0.25, min(d.x, d.y))"))
        assertTrue(src.contains("uGridStrength * (1.0 - luma)"))
        assertTrue(src.contains("dot(col, vec3(0.299, 0.587, 0.114))"))
        assertFalse(src.contains("smoothstep(0.56, 0.44,"))
        assertFalse(src.contains("smoothstep(0.0, 0.04,"))
        assertFalse(src.contains("smoothstep(0.0, 0.08,"))
    }

    @Test
    fun gridStrengthScalesDownWithDisplayScale() {
        // Base strength 0.08 (was 0.10, was a fixed 0.22),
        // full at 2x and below, tapering to half at 8x and
        // above — so the grid stays subtle as the game grows.
        // At Robert's sizes the effective peak is ~0.045
        // (6.34x) and ~0.053 (4.5x), and the brightness tie
        // takes it to zero on a bright field.
        assertEquals(0.08f, DisplayShaders.GRID_BASE_STRENGTH, 0.001f)
        assertEquals(0.08f, DisplayShaders.gridStrengthForScale(1.0f), 0.001f)
        assertEquals(0.08f, DisplayShaders.gridStrengthForScale(2.0f), 0.001f)
        assertEquals(0.0533f, DisplayShaders.gridStrengthForScale(4.5f), 0.001f)
        assertEquals(0.0506f, DisplayShaders.gridStrengthForScale(5.0f), 0.001f)
        assertEquals(0.04f, DisplayShaders.gridStrengthForScale(8.0f), 0.001f)
        assertEquals(0.04f, DisplayShaders.gridStrengthForScale(20.0f), 0.001f)
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
    fun gridPassHasTheWormLightPool() {
        // Worm Light realism: the lamp's pool, added AFTER
        // the optics lines so the room dim never cancels
        // the lamp. An elliptical warm-white pool, additive
        // — intensity 0 adds exactly 0.0, so the chains are
        // pixel-identical with the lamp off.
        val src = DisplayShaders.GRID_FRAGMENT
        assertTrue(src.contains("uWlCenter"))
        assertTrue(src.contains("uWlRadii"))
        assertTrue(src.contains("uWlRot"))
        assertTrue(src.contains("uWlIntensity"))
        assertTrue(src.contains("smoothstep(1.0, 0.0, wlE)"))
        assertTrue(src.contains("smoothstep(0.45, 0.0, wlE)"))
        assertTrue(src.contains("uWlIntensity * (wlFall * 0.14 + wlHot * 0.16)"))
        assertTrue(src.contains("vec3(1.0, 0.82, 0.55)"))
        assertTrue(src.contains("col = clamp(col, vec3(0.0), vec3(1.0))"))
        // The pool comes after the optics: the dim is
        // applied first, the lamp added on top of it.
        val optics = src.indexOf("pow(max(col, vec3(0.0)), vec3(uGamma))")
        val pool = src.indexOf("uWlIntensity * (wlFall")
        assertTrue("the pool should be added after the optics", optics >= 0 && pool > optics)
    }

    @Test
    fun plainPassHasTheWormLightPool() {
        // The lamp works with the chain off (clean) too:
        // the plain program carries the same pool add.
        val src = DisplayShaders.PLAIN_FRAGMENT
        assertTrue(src.contains("uWlCenter"))
        assertTrue(src.contains("uWlRadii"))
        assertTrue(src.contains("uWlRot"))
        assertTrue(src.contains("uWlIntensity"))
        assertTrue(src.contains("uWlIntensity * (wlFall * 0.14 + wlHot * 0.16)"))
        assertTrue(src.contains("vec3(1.0, 0.82, 0.55)"))
        assertTrue(src.contains("texture2D(uTex, vTex)"))
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
