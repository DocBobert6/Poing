package app.poing

/**
 * Phase 8 — the GLSL source for the display chains
 * (GLES20 / GLSL ES 1.00). Kept as plain strings in pure
 * Kotlin (no Android imports) so the text itself is
 * unit-testable on the JVM — the renderer (DisplayRenderer)
 * only compiles it.
 *
 * Two passes, in the order a handheld LCD shapes a picture:
 * pass 1 colours the frame (the system's colour look), pass 2
 * draws the screen's grid over it at display size. The grid
 * is a separate pass so it stays aligned to the native texel
 * grid instead of smearing across it. All handheld LCDs are
 * flat — there is no curvature, no CRT geometry anywhere in
 * this file, and the corner rounding lives in the bezel, not
 * in a shader.
 *
 * With the chain off, the plain program draws the frame
 * exactly as the core rendered it: no colour pass, no grid.
 * A program that fails to compile falls back to the plain
 * one, so the game stays visible.
 *
 * uMode in the colour pass: 0 = Jade FSTN, 1 = Jade
 * pass-through (the palette as the core rendered it),
 * 2 = Prism, 3 = Dusk.
 */
object DisplayShaders {

    /**
     * Fullscreen quad. The stored texcoords are in Bitmap
     * convention (t = 0 is the top row): a Bitmap uploaded
     * with texImage2D stores its top row first, so the top
     * of the screen samples t = 0. A framebuffer texture is
     * the other way round — its first row is the bottom of
     * the rendered image — so pass 2 (which samples the
     * pass-1 framebuffer) flips V with uFlipV. One vertex
     * shader serves all three programs.
     */
    const val VERTEX = """
        attribute vec2 aPos;
        attribute vec2 aTex;
        uniform float uFlipV;
        varying vec2 vTex;
        void main() {
          gl_Position = vec4(aPos, 0.0, 1.0);
          vTex = vec2(aTex.x, mix(aTex.y, 1.0 - aTex.y, uFlipV));
        }
    """

    /**
     * Pass 1 — colour. Jade FSTN: the Game Boy Pocket's
     * screen, 1996 — true black-and-white, sharp, and
     * lower-contrast than an emissive display (a reflective
     * LCD's white is dimmer and its black is raised). The
     * four shades are mGBA's own "GB Pocket" palette
     * (src/gb/overrides.c: UNIFORM_PAL(0x52D4, 0x4270,
     * 0x2989, 0x10A3)), decoded to (165,181,165),
     * (132,156,132), (74,99,82), (24,41,33) — green-grey,
     * green dominant, never blue. Wikipedia's console
     * palette list cross-checks it: (197,202,164),
     * (140,146,107), (74,81,56), (24,24,24) — olive, also
     * green/olive-dominant, brightest a little higher.
     * The old ramp ((0.05,0.07,0.10) to (0.85,0.89,0.93))
     * was wrong on both counts: too bright at the top
     * (0.89 luma vs 0.68 researched) and blue-dominant,
     * which no reference supports. The ramp is a 4-stop
     * piecewise-linear map in luma, the stops at the core's
     * "Grayscale" palette lumas (1.000, 0.678, 0.322,
     * 0.000 — the four shades a GB game renders), so a GB
     * game lands on exactly the four Pocket shades; the
     * "GB Pocket" palette lands on nearly the same shades,
     * and "GB Light" (a Pocket screen with a backlight)
     * renders as Pocket shades too. Jade pass-through: the
     * frame untouched, for the DMG green and the coloured
     * GBC/SGB palettes.
     * Prism: the Game Boy Color's reflective TFT, 1998 —
     * Pokefan531's current (Nov 2025) hardware-scanned
     * model: the measured 32-entry gamma LUT, then the
     * colour matrix in linear light (see the LUT and matrix
     * notes below). Dusk: the Game Boy Advance's reflective
     * TFT, 2001 — the same correction with the Advance's own
     * gamma, plus a gamma lift and a saturation lift, because
     * the AGB-001 is dark and unlit. Any mode this build does
     * not know yet falls through to pass-through, never to a
     * wrong look.
     *
     * The matrix finding (2026-11-12, shader perfection):
     * the old matrix was the TRANSPOSE of the reference.
     * GLSL mat4 is column-major, so the hunterk
     * color-mangler's comment-rows are the transpose of the
     * math it actually computes — and mGBA's libretro.c
     * implements the computed math (rCorrect = 0.82r +
     * 0.24g - 0.06b, ...). The old shader applied the
     * comment-rows, which do not preserve greys (row sums
     * 1.14 / 0.98 / 0.88 — every grey came out warm, and
     * pure blue came out violet with red 0.50 instead of
     * 0.00). The correct rows sum to 1.00, so greys stay
     * neutral; the new Pokefan531 matrices (2025) do too.
     * The old shader's "same matrix as the core" claim was
     * wrong — same constants, transposed application.
     */
    const val COLOUR_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform sampler2D uGammaLut;
        uniform int uMode;
        uniform float uGammaRow;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          if (uMode == 0) {
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            // The Pocket's four shades (mGBA's GB Pocket
            // palette): luma 0.000 -> (24,41,33), 0.322 ->
            // (74,99,82), 0.678 -> (132,156,132), 1.000 ->
            // (165,181,165). The stops are the core's
            // Grayscale palette lumas, so a GB game lands on
            // exactly the four shades.
            vec3 s0 = vec3(0.094, 0.161, 0.129);
            vec3 s1 = vec3(0.290, 0.388, 0.322);
            vec3 s2 = vec3(0.518, 0.612, 0.518);
            vec3 s3 = vec3(0.647, 0.710, 0.647);
            vec3 c01 = mix(s0, s1, clamp(luma / 0.322, 0.0, 1.0));
            vec3 c12 = mix(s1, s2, clamp((luma - 0.322) / 0.356, 0.0, 1.0));
            vec3 c23 = mix(s2, s3, clamp((luma - 0.678) / 0.322, 0.0, 1.0));
            col = mix(mix(c01, c12, step(0.322, luma)), c23, step(0.678, luma));
          } else if (uMode == 2) {
            // The CGB-001's reflective TFT, 1998 — Pokefan531's
            // current (Nov 2025) hardware-scanned model: the
            // measured 32-entry gamma LUT, then the colour
            // matrix in linear light. The LUT is a 32x2 texture
            // (row 0 = GBC, row 1 = GBA); the frame's 8-bit
            // values are the core's 5-bit expansion, so
            // floor(c * 32) recovers the exact 5-bit index.
            // No extra lift: the LUT IS the lift, and the
            // current reference pipeline has no saturation
            // matrix and no extra gamma lift. The matrix is
            // the current Pokefan531 sRGB matrix, effective
            // rows (the GLSL mat4 as written is column-major,
            // so its comment-rows are the transpose — the old
            // shader applied the comment-rows, which do not
            // preserve greys and turn blue violet; see the
            // matrix finding in the colour-pass comment).
            float ri = min(floor(col.r * 32.0), 31.0);
            float gi = min(floor(col.g * 32.0), 31.0);
            float bi = min(floor(col.b * 32.0), 31.0);
            float vr = (uGammaRow + 0.5) / 2.0;
            vec3 g = vec3(
              texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((gi + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((bi + 0.5) / 32.0, vr)).r);
            vec3 lin = clamp(pow(g, vec3(2.2)) * 0.8989, vec3(0.0), vec3(1.0));
            vec3 m;
            m.r = 0.8944 * lin.r + 0.2179 * lin.g - 0.1125 * lin.b;
            m.g = 0.1091 * lin.r + 0.6312 * lin.g + 0.2597 * lin.b;
            m.b = 0.1447 * lin.r + 0.2837 * lin.g + 0.5716 * lin.b;
            col = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
            col = clamp(col, vec3(0.0), vec3(1.0));
          } else if (uMode == 3) {
            vec3 c = pow(col, vec3(3.2));
            vec3 m;
            m.r = (0.82 * c.r + 0.125 * c.g + 0.195 * c.b) * 0.94;
            m.g = (0.24 * c.r + 0.665 * c.g + 0.075 * c.b) * 0.94;
            m.b = (-0.06 * c.r + 0.21 * c.g + 0.73 * c.b) * 0.94;
            col = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
            col = pow(col, vec3(0.85));
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(luma), col, 1.15);
            // The saturation mix extrapolates: a colour whose
            // blue is far below its luma (Nintendo orange,
            // dark browns) comes out negative on that channel,
            // and the old min() only clamped the top. The GPU
            // clamps on write to the 8-bit framebuffer, so it
            // was invisible — but the shader now states both
            // sides itself.
            col = clamp(col, vec3(0.0), vec3(1.0));
          }
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * Pass 2 — grid. Jade's dot-matrix: each native pixel is
     * a dot, darker toward its edges, so the Pocket's screen
     * reads as dots, not squares. The TFT grid (Prism / Dusk):
     * the reflective TFT's cell borders — fine dark lines
     * where one native texel meets the next, never dots. The
     * lines are one per native texel, so at the Advance's
     * 240x160 they stay fine by construction, never chunky.
     *
     * The grid is subtle by design, and it stays subtle at
     * any size. At the old size (~1.5-2x) a per-texel grid
     * blurred into a slight tint and was effectively
     * invisible; at the new full-height size (4.5-5x) every
     * texel resolves, and the old grid — strength 0.22, a
     * bright dot of only r<=0.35 of the cell, lines 0.08 of
     * a cell each side — measured a 22% luminance dip at
     * every texel boundary, a mesh over the whole picture
     * (Robert's screenshots, 2026-11-12). The rework
     * (2026-11-12): base strength 0.10, scaled down with
     * the display scale (see [gridStrengthForScale]), a
     * wider bright dot (r<=0.40, dark only toward the
     * corners) and narrower TFT lines (0.04 of a cell).
     * Measured on a flat field at the real sizes (799x716
     * for 160x144, 1075x716 for 240x160): max dip 22% ->
     * ~6.5%, mean factor 0.91 / 0.97 -> 0.98 / 0.995.
     * The perfection pass (2026-11-12) widens the dot once
     * more, to r<=0.44: the real FSTN pixel's bright area
     * is most of the cell, and the wider dot keeps the same
     * max dip (~6.3%) while the mean factor rises (0.983 ->
     * 0.990 at 5x) — the dots read cleaner with less
     * overall dulling. The strength is the only dial, and
     * it stays subtle — the grid must read, never shout.
     * uGridMode picks which: 0 = dot-matrix, 1 = TFT lines.
     *
     * No subpixels: the real GBC panel is RGB and the GBA
     * panel BGR, and the accurate grid shaders render
     * vertical subpixel stripes — but only at integer
     * multiples of 3x (3x/6x/9x). At 4.5-5x the stripes
     * would moire and read as colour fringing, so the
     * chains keep the fine cell-border lines instead.
     *
     * Screen Optics (§10.1) rides on this pass: the ambient
     * light's brightness / contrast / gamma, applied after
     * the grid so the whole presented picture — grid
     * included — sits under the room's light. All three
     * default to 1 (no adjustment); the sensor only ever
     * moves them while a chain is on.
     */
    const val GRID_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform vec2 uTexSize;
        uniform int uGridMode;
        uniform float uGridStrength;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uGamma;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          vec2 cell = fract(vTex * uTexSize);
          float g;
          if (uGridMode == 1) {
            vec2 d = min(cell, 1.0 - cell);
            g = smoothstep(0.0, 0.04, min(d.x, d.y));
          } else {
            g = smoothstep(0.56, 0.44, distance(cell, vec2(0.5)));
          }
          col *= 1.0 - uGridStrength * (1.0 - g);
          col *= uBrightness;
          col = (col - 0.5) * uContrast + 0.5;
          col = pow(max(col, vec3(0.0)), vec3(uGamma));
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * The grid's base strength (see the grid pass above).
     * The renderer scales it down with the display scale,
     * so the grid stays subtle as the game grows.
     */
    const val GRID_BASE_STRENGTH = 0.10f

    /**
     * The grid strength for a display scale (display pixels
     * per native texel). Full strength at 2x and below —
     * there the grid barely resolves — tapering as the
     * texels grow, down to half strength at 8x and above:
     * a grid that shouts at 5x is a mesh over the picture.
     * Pure math, so it runs under plain JVM unit tests.
     */
    fun gridStrengthForScale(scale: Float): Float {
        val factor = kotlin.math.sqrt(2.0f / scale).coerceIn(0.5f, 1.0f)
        return GRID_BASE_STRENGTH * factor
    }

    /**
     * The measured gamma ramps (Pokefan531,
     * Handheld-Colorspace-Shaders, Nov 2025 — the GBC/GBA
     * screens scanned directly with a ColorMunki Photo, no
     * case/glass; the defaults are the average light angle).
     * 32 entries, one per 5-bit game value; the frame's
     * 8-bit values are the core's 5-bit expansion, so
     * floor(c * 32) recovers the exact index in the shader.
     * SameBoy's curves disagree by up to 0.13 (GBC index 9:
     * 110 here vs 76 there) — gamma varies with light angle,
     * so neither reference is exact; this one is the latest
     * direct scan, and it is the source of the matrix lineage
     * already in the code. See the perfection record.
     */
    val GAMMA_LUT_GBC = listOf(
        0, 8, 15, 26, 37, 49, 67, 85, 97, 110, 121, 134, 140, 148, 154, 160,
        170, 180, 188, 196, 202, 207, 212, 217, 222, 228, 232, 237, 246, 252, 254, 255,
    )
    val GAMMA_LUT_GBA = listOf(
        0, 3, 6, 10, 15, 25, 36, 50, 62, 71, 82, 92, 102, 109, 116, 124,
        131, 138, 145, 152, 160, 168, 176, 184, 193, 202, 211, 218, 229, 240, 249, 255,
    )

    /**
     * The LUT as 64 grey values for the 32x2 texture the
     * renderer uploads: row 0 is the GBC ramp, row 1 the
     * GBA ramp. Pure data, so it runs under plain JVM unit
     * tests; the renderer turns it into RGBA bytes.
     */
    fun gammaLutPixels(): IntArray {
        val out = IntArray(64)
        for (i in 0 until 32) {
            out[i] = GAMMA_LUT_GBC[i]
            out[32 + i] = GAMMA_LUT_GBA[i]
        }
        return out
    }

    /** Fallback if a chain program fails to compile: a plain texture sample, so the game stays visible. */
    const val PLAIN_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        void main() {
          gl_FragColor = texture2D(uTex, vTex);
        }
    """
}
