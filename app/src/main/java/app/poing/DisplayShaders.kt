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
     * rebuilt 2026-11-13 from two measured models composed
     * the way mGBA composes them: SameBoy's CGB response
     * ramp (the 32-entry LUT, row 0), then the mGBA
     * colorspace matrix in linear light (x0.94). The old
     * Pokefan531 ramp + matrix was the palest of the three
     * measured models — that paleness is what Robert
     * rejected ("still terrible", washed-out). Dusk: the Game
     * Boy Advance's reflective TFT, 2001 — rebuilt 2026-11-13
     * the same way, with SameBoy's AGB response ramp (the
     * 32-entry LUT, row 1) then the same mGBA colorspace
     * matrix in linear light (x0.94); the matrix constants
     * are identical for both systems — mGBA's GBC/GBA
     * constants differ only in gamma, which the ramp replaces.
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
            // Prism, rebuilt 2026-11-13 (Robert: "still terrible" —
            // washed-out, and the grid a lattice on flat fields):
            // SameBoy's measured CGB response ramp (the 32-entry
            // LUT, row 0), then the mGBA colorspace matrix in
            // linear light (x0.94). The ramp deepens the shadows
            // and restores full white where the old measured ramp
            // lifted them (grey8 92 -> 66, white 243 -> 248); the
            // matrix is the core's own colorspace, the standard
            // in other mGBA-based emulators. No green-mix: the
            // matrix already carries the channel crosstalk, and
            // doing both would count it twice. The old
            // Pokefan531 ramp + matrix was the palest of the
            // three measured models — that paleness is what
            // Robert rejected. See the rebuild record.
            float ri = min(floor(col.r * 32.0), 31.0);
            float gi = min(floor(col.g * 32.0), 31.0);
            float bi = min(floor(col.b * 32.0), 31.0);
            float vr = (uGammaRow + 0.5) / 2.0;
            vec3 ramp = vec3(
              texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((gi + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((bi + 0.5) / 32.0, vr)).r);
            vec3 lin = pow(ramp, vec3(2.2));
            vec3 m;
            m.r = 0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b);
            m.g = 0.94 * (0.125 * lin.r + 0.665 * lin.g + 0.21 * lin.b);
            m.b = 0.94 * (0.195 * lin.r + 0.075 * lin.g + 0.73 * lin.b);
            col = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
            col = clamp(col, vec3(0.0), vec3(1.0));
          } else if (uMode == 3) {
            // Dusk, rebuilt 2026-11-13 (same rebuild as
            // Prism): SameBoy's measured AGB response ramp
            // (the 32-entry LUT, row 1), then the mGBA
            // colorspace matrix in linear light (x0.94) —
            // the same matrix as Prism: mGBA's GBC/GBA
            // constants differ only in gamma, which the
            // ramp replaces. The ramp deepens the shadows
            // and restores full white where the old measured
            // ramp lifted them (grey8 59 -> 46, white 242
            // -> 248); the old Pokefan531 GBA matrix and its
            // lum factor are gone. No green-mix: the matrix
            // already carries the channel crosstalk. The
            // AGB-001 is dark and unlit, and the ramp
            // already lifts the shadows, so there is no
            // extra gamma or saturation lift. See the
            // rebuild record.
            float ri = min(floor(col.r * 32.0), 31.0);
            float gi = min(floor(col.g * 32.0), 31.0);
            float bi = min(floor(col.b * 32.0), 31.0);
            float vr = (uGammaRow + 0.5) / 2.0;
            vec3 ramp = vec3(
              texture2D(uGammaLut, vec2((ri + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((gi + 0.5) / 32.0, vr)).r,
              texture2D(uGammaLut, vec2((bi + 0.5) / 32.0, vr)).r);
            vec3 lin = pow(ramp, vec3(2.2));
            vec3 m;
            m.r = 0.94 * (0.82 * lin.r + 0.24 * lin.g - 0.06 * lin.b);
            m.g = 0.94 * (0.125 * lin.r + 0.665 * lin.g + 0.21 * lin.b);
            m.b = 0.94 * (0.195 * lin.r + 0.075 * lin.g + 0.73 * lin.b);
            col = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
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
     * Rebuild (2026-11-13, Robert: "still terrible" — the grid
     * a lattice on flat fields): the old TFT line was a
     * knife-edge — smoothstep(0.0, 0.04), 0.08 of a cell
     * wide, ~0.5 display px at 6.34x, i.e. sub-pixel. It
     * landed unevenly as the texel grid drifted against the
     * display pixels (beating, ~19 px / 3 texels), so some
     * boundaries made a dark display pixel and others did
     * not: 5% of display pixels on a flat white field sat
     * ~5% below the field (darkest 1% 11-12% below) — a
     * regular lattice, though the phase-averaged dip was
     * only 2-5%. At integer scales (5x, 8x) the old grid
     * vanished entirely. The references all use smooth,
     * wide, flat-centred profiles (lcd3x's sine, zfast's
     * polynomial border, lcd-grid-v2's integrated aperture)
     * — never a knife-edge. So the line is now smoothstep
     * (0.0, 0.25): a soft fall over the outer quarter of the
     * cell, wide enough (1.1-1.6 display px at 4.5-6.5x) to
     * always cover at least one display pixel. And the
     * strength now fades with the pixel's own brightness
     * (uGridStrength * (1 - luma)): on a bright field there
     * is no grid at all, and it survives as a faint texture
     * on mid and dark tones. Measured at 6.34x: white 0.00%,
     * sky 0.40%, mid-grey 1.44%, dark 2.32% (was 5.58% /
     * 4.76% everywhere). See the rebuild record.
     *
     * Jade's dot got the same treatment in its own commit:
     * smoothstep(0.68, 0.44) instead of (0.56, 0.44) — the
     * same bright disc (r<=0.44) with a fall twice as wide,
     * so the dots read softer with less overall dulling
     * (mid-grey mean factor 0.9949 -> 0.9973 at 6.34x). The
     * FSTN ramp itself was re-verified, not changed: mGBA's
     * Pocket palette is the primary source, Wikipedia's
     * Pocket palette cross-checks it, and the dot-matrix
     * shader's palette endpoints sit in the same family.
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
     *
     * Worm Light realism (Robert, 2026-11-12) rides on this
     * pass too: the lamp's pool, added AFTER the optics
     * lines so the room dim never cancels the lamp. An
     * elliptical warm-white pool (0xFFFFD18C), brightest at
     * the top-centre, smooth falloff to nothing at the
     * edges, with a hotspot where the beam lands most
     * directly. ADDITIVE — it adds warm light to the picture
     * instead of laying a tint over it. The game view is a
     * GLSurfaceView, whose pixels live behind the window: an
     * overlay wash would blend against the window's black
     * and read as a tint, the look Robert already rejected.
     * Intensity 0 adds exactly 0.0, so the chains are
     * pixel-identical with the lamp off. The pool is in
     * frame UV (y = 0 at the top), so it follows the head
     * as it is dragged. This stretches the brief's "no chain
     * change" — the pool is a lamp uniform, not a chain
     * rework; the chains' colour and grid are untouched.
     * The same pool lines live in the plain program, so the
     * lamp works with the chain off too.
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
        uniform vec2 uWlCenter;
        uniform vec2 uWlRadii;
        uniform float uWlRot;
        uniform float uWlIntensity;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          vec2 cell = fract(vTex * uTexSize);
          float g;
          if (uGridMode == 1) {
            vec2 d = min(cell, 1.0 - cell);
            g = smoothstep(0.0, 0.25, min(d.x, d.y));
          } else {
            g = smoothstep(0.68, 0.44, distance(cell, vec2(0.5)));
          }
          // The grid fades with the pixel's own brightness: on a
          // bright field (white title, sky) there is no dark
          // lattice at all, and the grid survives as a faint
          // texture on mid and dark tones. A sharp dark line on
          // white is what the eye catches; a reflective TFT's
          // bright cell dominates its own gap.
          float luma = dot(col, vec3(0.299, 0.587, 0.114));
          float gridStrength = uGridStrength * (1.0 - luma);
          col *= 1.0 - gridStrength * (1.0 - g);
          col *= uBrightness;
          col = (col - 0.5) * uContrast + 0.5;
          col = pow(max(col, vec3(0.0)), vec3(uGamma));
          // Worm Light realism (Robert, 2026-11-12): the
          // lamp's pool, AFTER the optics lines so the room
          // dim never cancels the lamp. An elliptical
          // warm-white pool, brightest at the top-centre,
          // smooth falloff to nothing at the edges, with a
          // hotspot where the beam lands most directly.
          // ADDITIVE — it adds warm light to the picture
          // instead of laying a tint over it. Intensity 0
          // adds exactly 0.0, so the chains are
          // pixel-identical with the lamp off. The pool is
          // in frame UV (y = 0 at the top), so it follows
          // the head as it is dragged. The falloff is
          // mirrored by wormLightPoolFactor (WormLightOverlay)
          // so it is unit-testable on the JVM — keep the
          // constants in step.
          //
          // Retune (Robert, 2026-11-13): the peak is 0.30
          // (0.14 fall + 0.16 hotspot), down from 0.65.
          // At 0.65 the lightest two Jade shades clipped
          // to white at the pool core — the top of Super
          // Mario Land 2 bleached to cream in his video
          // (core measured R 0.97). The lightest Jade
          // shade clips above a peak of 0.353, so 0.30
          // keeps it at 0.95 with all four shades
          // distinct. The fall/hot balance is unchanged.
          //
          // The centre's y is flipped into vTex space
          // below: on this pass vTex is flipped (uFlipV 1 —
          // the source is the pass-1 framebuffer), so y = 0
          // is the BOTTOM of the picture here. Without the
          // flip the pool lands brightest at the bottom
          // (Robert's 2026-11-13 screenshot: the glow sat
          // low on the title screen, and the top quarter
          // was the coolest). The plain program samples the
          // Bitmap directly (uFlipV 0, y = 0 at the top)
          // and takes the centre as-is.
          vec2 wlD = vTex - vec2(uWlCenter.x, 1.0 - uWlCenter.y);
          float wlC = cos(uWlRot);
          float wlS = sin(uWlRot);
          vec2 wlR = vec2(wlD.x * wlC - wlD.y * wlS, wlD.x * wlS + wlD.y * wlC);
          float wlE = dot(wlR / uWlRadii, wlR / uWlRadii);
          float wlFall = smoothstep(1.0, 0.0, wlE);
          float wlHot = smoothstep(0.45, 0.0, wlE);
          col += uWlIntensity * (wlFall * 0.14 + wlHot * 0.16) * vec3(1.0, 0.82, 0.55);
          col = clamp(col, vec3(0.0), vec3(1.0));
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * The grid's base strength (see the grid pass above).
     * The renderer scales it down with the display scale,
     * so the grid stays subtle as the game grows. Rebuilt
     * 2026-11-13: 0.08, down from 0.10 — the wider line and
     * the brightness tie do the work now, not the peak.
     */
    const val GRID_BASE_STRENGTH = 0.08f

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
     * The measured response ramps (SameBoy's `Core/display.c`,
     * master ea667ad — the per-channel response of the GBC /
     * GBA panels, read first-hand for the 2026-11-13 rebuild).
     * 32 entries, one per 5-bit game value; the frame's 8-bit
     * values are the core's 5-bit expansion, so floor(c * 32)
     * recovers the exact index in the shader. SameBoy's ramps
     * are the deepest of the three measured models (GBC grey8
     * -> 66, grey24 -> 232, white 255); the Pokefan531 ramps
     * they replace were the palest (grey8 -> 92, white 243)
     * and that paleness is what Robert rejected. Gamma varies
     * with light angle, so neither reference is exact — the
     * choice is recorded in the rebuild record, not hidden.
     * Both rows are now SameBoy's ramps (row 0 CGB, row 1 AGB).
     */
    val GAMMA_LUT_GBC = listOf(
        0, 6, 12, 20, 28, 36, 45, 56, 66, 76, 88, 100, 113, 125, 137, 149,
        161, 172, 182, 192, 202, 210, 218, 225, 232, 238, 243, 247, 250, 252, 254, 255,
    )
    val GAMMA_LUT_GBA = listOf(
        0, 3, 8, 14, 20, 26, 33, 40, 47, 54, 62, 70, 78, 86, 94, 103,
        112, 120, 129, 138, 147, 157, 166, 176, 185, 195, 205, 215, 225, 235, 245, 255,
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

    /**
     * Fallback if a chain program fails to compile: a plain
     * texture sample, so the game stays visible. It carries
     * the Worm Light's pool too (the same add as the grid
     * pass), so the lamp works with the chain off (clean)
     * and on a failed chain — the lamp is not a chain.
     */
    const val PLAIN_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform vec2 uWlCenter;
        uniform vec2 uWlRadii;
        uniform float uWlRot;
        uniform float uWlIntensity;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          // Worm Light realism: the lamp's pool, the same
          // add as the grid pass (see GRID_FRAGMENT) — the
          // lamp works with the chain off too. Intensity 0
          // adds exactly 0.0, so clean stays clean.
          vec2 wlD = vTex - uWlCenter;
          float wlC = cos(uWlRot);
          float wlS = sin(uWlRot);
          vec2 wlR = vec2(wlD.x * wlC - wlD.y * wlS, wlD.x * wlS + wlD.y * wlC);
          float wlE = dot(wlR / uWlRadii, wlR / uWlRadii);
          float wlFall = smoothstep(1.0, 0.0, wlE);
          float wlHot = smoothstep(0.45, 0.0, wlE);
          col += uWlIntensity * (wlFall * 0.14 + wlHot * 0.16) * vec3(1.0, 0.82, 0.55);
          col = clamp(col, vec3(0.0), vec3(1.0));
          gl_FragColor = vec4(col, 1.0);
        }
    """
}
