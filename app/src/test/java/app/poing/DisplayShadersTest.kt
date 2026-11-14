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
    fun edgeFragmentHasTheRework() {
        // Edge smoothing, REWORKED 2026-11-14: HowlingDog's
        // dither-aware edge shader, rebuilt per-system for
        // these handhelds. The rework's shape is all in the
        // text: (1) multi-pattern dither — checkerboard,
        // stripes, Bayer 2x2 and the 4x4 local proxy — that
        // BLENDS to the average of its shades (the
        // ditherAvg, applied by patternEff), gated by the
        // 1px and text bypasses; (2) 1-pixel detail
        // preservation; (3) the text / UI bypass with a
        // per-system threshold (uTextAlign); (4) the 15-bit
        // snap-back for GBC / GBA only (uSnap15) — GB has
        // NO snap; (5) NO motion-adaptive (uMotion is gone:
        // frame history breaks the pure-function goal);
        // (6) grid-aligned — the pass runs at display size
        // (the fix for the 2026-11-14 no-op: at native size
        // every fragment sat at a texel centre and the
        // boundary weight was exactly 0), so fragments land
        // between texel centres (fract of vTex * uTexSize);
        // (7) NO subpixel branch (uSubpixel is gone: the
        // offset was 0.3333 of a texel, and the target phone
        // is pOLED); (8) per-system parameters as uniforms
        // (uDitherTol, uEdgeLo, uEdgeHi, uBlendMax,
        // uTextAlign, uSnap15).
        val src = DisplayShaders.EDGE_FRAGMENT
        assertTrue(src.contains("uTexSize"))
        assertTrue(src.contains("uDitherTol"))
        assertTrue(src.contains("uEdgeLo"))
        assertTrue(src.contains("uEdgeHi"))
        assertTrue(src.contains("uBlendMax"))
        assertTrue(src.contains("uSnap15"))
        assertTrue(src.contains("uTextAlign"))
        // Deleted in the rework: the motion factor, the GB
        // palette snap, the scanned palette uniforms, and
        // the subpixel branch.
        assertFalse(src.contains("uMotion"))
        assertFalse(src.contains("uSnapGb"))
        assertFalse(src.contains("uSubpixel"))
        assertFalse(src.contains("uPal0"))
        assertFalse(src.contains("uPal1"))
        assertFalse(src.contains("uPal2"))
        assertFalse(src.contains("uPal3"))
        // Dither: checkerboard (the diagonal must actually
        // match — the loose dDiag*3.0 rule false-fired on
        // Bayer 2x2), stripes (match along, differ across),
        // Bayer 2x2 (opposite neighbours match), Bayer 4x4
        // (non-monotonic steps in both axes, opposite sides
        // differ — the guard that keeps it from erasing
        // isolated 1px dots).
        assertTrue(src.contains("checker"))
        assertTrue(src.contains("stripeV"))
        assertTrue(src.contains("stripeH"))
        assertTrue(src.contains("bayer2"))
        assertTrue(src.contains("bayer4"))
        assertTrue(src.contains("float checker = step(dDiag, tol)"))
        assertTrue(src.contains("step(0.05, abs(lE - lW))"))
        assertTrue(src.contains("step(0.05, abs(lN - lS))"))
        // The dither blend: the average of the pattern's
        // shades, applied in pattern regions gated by the
        // 1px and text bypasses.
        assertTrue(src.contains("ditherAvg"))
        assertTrue(src.contains("float patternEff = pattern * (1.0 - isolated) * (1.0 - text)"))
        assertTrue(src.contains("col = mix(col, ditherAvg, patternEff)"))
        // 1-pixel detail and text bypass (per-system threshold).
        assertTrue(src.contains("isolated"))
        assertTrue(src.contains("float text = step(0.5, lMax - lMin) * axisAligned"))
        assertTrue(src.contains("uTextAlign * max(abs(gx), abs(gy))"))
        // Directional blend along the edge, weight growing
        // toward the texel boundary — and no motion factor.
        assertTrue(src.contains("fract(vTex * uTexSize)"))
        assertTrue(src.contains("float w = edge * wAlong * (1.0 - pattern) * (1.0 - isolated) * (1.0 - text) * (1.0 - corner)"))
        assertFalse(src.contains("uMotion"))
        // 15-bit snap-back, GBC / GBA only, gated on
        // uSnap15 — and only a blend or a pattern is ever
        // snapped (w == 0 and patternEff == 0 keeps its own
        // colour).
        assertTrue(src.contains("floor(col * 31.0 + 0.5) / 31.0"))
        assertTrue(src.contains("if (uSnap15 > 0.5)"))
        assertTrue(src.contains("col = mix(c0, snapped, max(step(0.001, w), patternEff))"))
        // The corner test survives from HowlingDog.
        assertTrue(src.contains("float corner = step(distance(cNW, cSE), 0.1)"))
    }

    @Test
    fun edgeFragmentFoldsTheColourModelIn() {
        // Edge smoothing, item 4 (2026-11-14): the chains'
        // researched colour is folded INTO the edge pass,
        // before the edge work runs — edge mode no longer
        // shows the core's generic correction instead of
        // Jade / Prism / Dusk. The fold is COLOUR_FRAGMENT's
        // mode logic as a function (colourCorrect): mode 0
        // Jade's FSTN ramp, mode 1 pass-through, modes 2 / 3
        // the SameBoy ramp (LUT row via uGammaRow) then the
        // mGBA matrix x0.94 — and it is applied to all nine
        // samples, so the dither averages, the gradients and
        // the blends all work in the corrected colours. The
        // renderer binds the same LUT texture on unit 1 and
        // sets uMode from the system (GB FSTN 0 / pass 1,
        // GBC 2, GBA 3) with uGammaRow 0 for GBC, 1 for GBA.
        val src = DisplayShaders.EDGE_FRAGMENT
        assertTrue(src.contains("uniform sampler2D uGammaLut"))
        assertTrue(src.contains("uniform int uMode"))
        assertTrue(src.contains("uniform float uGammaRow"))
        assertTrue(src.contains("vec3 colourCorrect(vec3 col)"))
        assertTrue(src.contains("if (uMode == 0)"))
        assertTrue(src.contains("if (uMode == 2 || uMode == 3)"))
        assertTrue(src.contains("vec3 s0 = vec3(0.094, 0.161, 0.129)"))
        assertTrue(src.contains("vec3 s3 = vec3(0.647, 0.710, 0.647)"))
        assertTrue(src.contains("texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r"))
        assertTrue(src.contains("0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.125 * lin.r + 0.665 * lin.g + 0.21 * lin.b)"))
        assertTrue(src.contains("0.94 * (0.195 * lin.r + 0.075 * lin.g + 0.73 * lin.b)"))
        // All nine samples are corrected before the edge work.
        assertTrue(src.contains("vec3 c0 = colourCorrect(texture2D(uTex, vTex).rgb)"))
        assertTrue(src.contains("vec3 cN = colourCorrect(texture2D(uTex, vTex + vec2(0.0, texel.y)).rgb)"))
        assertTrue(src.contains("vec3 cS = colourCorrect(texture2D(uTex, vTex - vec2(0.0, texel.y)).rgb)"))
        assertTrue(src.contains("vec3 cE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, 0.0)).rgb)"))
        assertTrue(src.contains("vec3 cW = colourCorrect(texture2D(uTex, vTex - vec2(texel.x, 0.0)).rgb)"))
        assertTrue(src.contains("vec3 cNE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, texel.y)).rgb)"))
        assertTrue(src.contains("vec3 cNW = colourCorrect(texture2D(uTex, vTex + vec2(-texel.x, texel.y)).rgb)"))
        assertTrue(src.contains("vec3 cSE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, -texel.y)).rgb)"))
        assertTrue(src.contains("vec3 cSW = colourCorrect(texture2D(uTex, vTex + vec2(-texel.x, -texel.y)).rgb)"))
        // And the fold's mode logic is the same text as the
        // colour pass's — the two must not drift apart.
        val colour = DisplayShaders.COLOUR_FRAGMENT
        assertTrue(colour.contains("vec3 s0 = vec3(0.094, 0.161, 0.129)"))
        assertTrue(colour.contains("0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b)"))
    }

    @Test
    fun plainPassHasTheWlFlipUniform() {
        // Phase 9 — the edge pass's blit runs the plain
        // program on the pass-1 framebuffer, where V is
        // flipped: the pool centre's y flips with it (the
        // same flip the grid pass does inline). The direct
        // Bitmap path is unchanged (uWlFlip 0).
        val src = DisplayShaders.PLAIN_FRAGMENT
        assertTrue(src.contains("uWlFlip"))
        assertTrue(src.contains("mix(uWlCenter.y, 1.0 - uWlCenter.y, uWlFlip)"))
    }

    @Test
    fun noCurvatureOrCrtGeometryAnywhere() {
        // Every handheld LCD here is flat. If a curve ever
        // creeps into a chain, this fails first.
        val all = DisplayShaders.VERTEX +
            DisplayShaders.COLOUR_FRAGMENT +
            DisplayShaders.GRID_FRAGMENT +
            DisplayShaders.EDGE_FRAGMENT +
            DisplayShaders.PLAIN_FRAGMENT
        val lower = all.lowercase()
        for (word in listOf("curve", "barrel", "pincushion", "distort", "scanline", "grille", "vignette")) {
            assertFalse("shader source must not contain '$word'", lower.contains(word))
        }
    }
}
