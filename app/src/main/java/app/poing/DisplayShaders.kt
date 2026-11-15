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
 * 2 = Prism, 3 = Dusk. The edge pass carries the same
 * modes (uMode / uGammaRow / uGammaLut) — the colour-fold,
 * item 4 of the 2026-11-14 edge rework — so edge mode
 * does not lose the chains' researched colour.
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
          // Performance fix 8 — the gamma pow is a no-op at
          // the neutral 1.0 (the common case: optics off, or
          // a steady room), so skip it there; a uniform
          // branch costs nothing against a pow per fragment.
          if (uGamma != 1.0) {
            col = pow(max(col, vec3(0.0)), vec3(uGamma));
          }
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
          // Performance fix 8 — the pool math (cos/sin,
          // two smoothsteps, the ellipse) runs per fragment
          // even at intensity 0, where it adds exactly 0.0;
          // skip it there. The clamp stays outside: the
          // optics lines above can still push col out of
          // range with the lamp off.
          if (uWlIntensity > 0.0) {
            vec2 wlD = vTex - vec2(uWlCenter.x, 1.0 - uWlCenter.y);
            float wlC = cos(uWlRot);
            float wlS = sin(uWlRot);
            vec2 wlR = vec2(wlD.x * wlC - wlD.y * wlS, wlD.x * wlS + wlD.y * wlC);
            float wlE = dot(wlR / uWlRadii, wlR / uWlRadii);
            float wlFall = smoothstep(1.0, 0.0, wlE);
            float wlHot = smoothstep(0.45, 0.0, wlE);
            col += uWlIntensity * (wlFall * 0.14 + wlHot * 0.16) * vec3(1.0, 0.82, 0.55);
          }
          col = clamp(col, vec3(0.0), vec3(1.0));
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * Edge smoothing, pass 1 — REWORKED 2026-11-14:
     * HowlingDog's dither-aware edge shader, rebuilt
     * per-system for these handhelds. It detects hard
     * sprite edges and fills intermediate anti-aliased
     * pixels along them, so a diagonal reads as a clean
     * line instead of a staircase — and it runs at the
     * DISPLAY size into the offscreen framebuffer (the
     * renderer binds the FBO at the view size for this
     * pass). That placement is the whole point: at the
     * frame's native size every fragment sat at a texel
     * centre (sub 0.5, 0.5), the boundary weight was
     * exactly 0 everywhere, and the shader output its input
     * unchanged — a no-op on any game (Robert's V-Rally 3
     * screenshots, 2026-11-14). At display size each screen
     * pixel lands at a real sub-texel position, so the
     * weight grows toward the texel boundary; the centre
     * of a texel still stays its own colour, so flat areas
     * stay crisp. Pass 2 is the plain blit (edge only)
     * or the light grid (the combined mode).
     *
     * The rework, over Phase 9: patterned dither BLENDS
     * to the average of its shades (the real-LCD look),
     * gated by the 1px and text bypasses — it is not
     * preserved as noise. GB has NO palette snap (on 4
     * shades any grey is harmonious; the old binary snap
     * turned a smooth ramp into a hard one-shade band).
     * GBC / GBA quantize blended outputs to the 15-bit
     * grid (uSnap15). There is NO motion factor — the
     * rework deleted motion-adaptive entirely (frame
     * history breaks the pure-function temporal-stability
     * goal). There is NO blend-cap taper. The text
     * bypass's axis-aligned threshold is per-system
     * (uTextAlign: GB 0.15, GBC 0.08, GBA 0.05), and the
     * bypass itself is gated by stroke length (Task 4
     * item 1): it survives only where the luma step along
     * the stroke terminates within 2 texels, so round
     * sprites blend while glyph strokes stay crisp. The
     * gate feeds the edge blend only — the dither blend
     * keeps the ungated bypass, because it replaces the
     * texel rather than mixing it. There
     * is NO subpixel branch — the rework removed it in
     * item 3 (the offset was 0.3333 of a texel, and the
     * target phone is pOLED, not an RGB-stripe panel).
     *
     * The result is very clean, sharp pixel art — NOT
     * hand-drawn vector art. It does not turn pixel art into
     * a painting, and the code does not claim it does.
     *
     * The combined mode's grid lives in pass 2, not here:
     * this pass never draws a grid, and it never draws a
     * line-based overlay of any kind. No curvature anywhere
     * — every handheld LCD here is flat.
     */
    const val EDGE_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform sampler2D uRawTex;
        uniform vec2 uTexSize;
        uniform float uDitherTol;
        uniform float uDitherGuardHi;
        uniform float uEdgeLo;
        uniform float uEdgeHi;
        uniform float uBlendMax;
        uniform float uSnap15;
        uniform float uTextAlign;
        float edgeLuma(vec3 c) {
          return dot(c, vec3(0.299, 0.587, 0.114));
        }
        // Performance fix 7 (2026-11-14) — the colour-fold
        // moved OUT of this pass into its own native-size
        // pass (the colour program, into the edge colour
        // framebuffer — see DisplayRenderer.drawEdge), so the
        // nine 3x3 samples below read the already-corrected
        // colours with plain taps: 9 per-tap corrections
        // removed (each up to 3 LUT reads + 2 pow() for
        // Prism / Dusk) over ~1M display fragments, every
        // frame. The detectors, the dither averages, the
        // the blends still work in the corrected colours —
        // the correction is identical, it just runs once per
        // texel instead of once per display fragment. The
        // only round trip is the 8-bit colour framebuffer
        // (~0.002 a channel — far inside every detector
        // tolerance; the recon pass's A1 notes the same).
        // The 5x5 ring, the stroke-gate taps and the centre
        // reference still read the RAW frame (uRawTex):
        // direction is a property of the source art, not the
        // colour model, and the ring's thresholds were tuned
        // on raw luma. The colour pass renders with uFlipV
        // 1, so its framebuffer stores the corrected image
        // UNFLIPPED — the same orientation as the raw Bitmap
        // texture — and this pass samples both with the one
        // varying (the recon pass's A2 renders its colour
        // pass with uFlipV 0, so its two inputs are
        // vertically mirrored; this pass does not repeat
        // that — see the drawEdge note).
        void main() {
          vec2 texel = vec2(1.0 / uTexSize.x, 1.0 / uTexSize.y);
          vec3 c0 = texture2D(uTex, vTex).rgb;
          vec3 cN = texture2D(uTex, vTex + vec2(0.0, texel.y)).rgb;
          vec3 cS = texture2D(uTex, vTex - vec2(0.0, texel.y)).rgb;
          vec3 cE = texture2D(uTex, vTex + vec2(texel.x, 0.0)).rgb;
          vec3 cW = texture2D(uTex, vTex - vec2(texel.x, 0.0)).rgb;
          vec3 cNE = texture2D(uTex, vTex + vec2(texel.x, texel.y)).rgb;
          vec3 cNW = texture2D(uTex, vTex + vec2(-texel.x, texel.y)).rgb;
          vec3 cSE = texture2D(uTex, vTex + vec2(texel.x, -texel.y)).rgb;
          vec3 cSW = texture2D(uTex, vTex + vec2(-texel.x, -texel.y)).rgb;
          float tol = uDitherTol;
          // Patterned dither: regular alternation is shading,
          // not an edge — so the centre blends to the average
          // of its shades (the real-LCD look), it is not
          // preserved as noise and not blurred into mud.
          // Checkerboard: the centre matches its diagonal
          // neighbour (the match must be real — the loose
          // dDiag*3.0 rule false-fired on Bayer 2x2 and
          // blended it to the wrong level) and differs from
          // BOTH orthogonal neighbours — requiring both is
          // what keeps a diagonal edge from reading as
          // dither (a diagonal differs from only one
          // orthogonal neighbour). Stripes: the centre
          // matches along the stripe and differs across it.
          // Bayer 2x2: the ordered 2x2 pattern repeats every
          // two texels, so the east and west neighbours match
          // each other, the north and south match each other,
          // and all four diagonals match each other, while
          // the three groups differ from the centre and from
          // each other. Bayer 4x4: a 3x3 window cannot see a
          // 4-texel period, so the local proxy is the
          // pattern's non-monotonic steps — the luma on both
          // sides of the centre sits on the SAME side of it
          // AND differs from it, in both axes — AND the
          // opposite sides differ from each other (a real
          // 4x4 has different levels on E vs W and N vs S).
          // Without that last guard the proxy erases
          // isolated 1px dots, whose four neighbours are all
          // the background level. Both conditions matter: a
          // diagonal edge has one neighbour equal to the
          // centre on each axis, so requiring both sides to
          // differ is what keeps a diagonal from reading as
          // a pattern. A real edge or gradient is monotonic
          // across the edge, so it never trips this either.
          float dDiag = distance(c0, cSE);
          float dE = distance(c0, cE);
          float dS = distance(c0, cS);
          float checker = step(dDiag, tol) * step(tol, dE) * step(tol, dS);
          float stripeV = step(distance(c0, cN) + distance(c0, cS), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cW));
          float stripeH = step(distance(c0, cE) + distance(c0, cW), tol) * step(tol, distance(c0, cN)) * step(tol, distance(c0, cS));
          float bayer2 = step(distance(cE, cW), tol) * step(distance(cN, cS), tol) * step(distance(cNE, cSE), tol) * step(distance(cNE, cNW), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cN)) * step(tol, distance(cE, cN));
          float l0 = edgeLuma(c0);
          float lN = edgeLuma(cN);
          float lS = edgeLuma(cS);
          float lE = edgeLuma(cE);
          float lW = edgeLuma(cW);
          float lNE = edgeLuma(cNE);
          float lNW = edgeLuma(cNW);
          float lSE = edgeLuma(cSE);
          float lSW = edgeLuma(cSW);
          float bayer4 = step(0.0, (lE - l0) * (lW - l0)) * step(0.0, (lN - l0) * (lS - l0)) * step(0.03, abs(lE - l0)) * step(0.03, abs(lW - l0)) * step(0.03, abs(lN - l0)) * step(0.03, abs(lS - l0)) * step(0.05, abs(lE - lW)) * step(0.05, abs(lN - lS));
          float pattern = max(max(checker, max(stripeV, stripeH)), max(bayer2, bayer4));
          // The dither average: the intended level of the
          // pattern, not the centre texel. Checker /
          // stripeV: the two shades are the centre and its
          // east neighbour (both orthogonals carry the other
          // shade). StripeH: centre and north. Bayer 2x2:
          // the four texels of the 2x2 block. Bayer 4x4
          // (proxy): the 3x3 mean is the local estimate of
          // the intended level. Priority is bayer4, then
          // bayer2, then stripeH, then stripeV / checker —
          // the same order as the numeric replica; the
          // detectors are mutually exclusive on real
          // patterns, so the priority only settles
          // JPEG-noise overlaps.
          vec3 ditherAvg;
          if (bayer4 > 0.5) {
            ditherAvg = (c0 + cN + cS + cE + cW + cNE + cNW + cSE + cSW) / 9.0;
          } else if (bayer2 > 0.5) {
            ditherAvg = (c0 + cE + cN + cNE) * 0.25;
          } else if (stripeH > 0.5) {
            ditherAvg = (c0 + cN) * 0.5;
          } else if (checker > 0.5 || stripeV > 0.5) {
            ditherAvg = (c0 + cE) * 0.5;
          } else {
            ditherAvg = c0;
          }
          // 1-pixel detail: the eight neighbours all agree
          // with each other and the centre stands apart — an
          // eye, an outline dot, a single bright pixel.
          // Output it untouched.
          vec3 nMean = (cN + cS + cE + cW + cNE + cNW + cSE + cSW) / 8.0;
          float spread = max(max(distance(cN, nMean), distance(cS, nMean)), max(distance(cE, nMean), distance(cW, nMean)));
          spread = max(spread, max(max(distance(cNE, nMean), distance(cNW, nMean)), max(distance(cSE, nMean), distance(cSW, nMean))));
          float isolated = step(spread, 0.05) * step(0.15, distance(c0, nMean));
          // Edge: a Sobel-like luma gradient over the 3x3.
          float gx = (lE + lNE + lSE) - (lW + lNW + lSW);
          float gy = (lN + lNE + lNW) - (lS + lSE + lSW);
          float edgeStrength = abs(gx) + abs(gy);
          // Text: a high-contrast edge that runs straight
          // along one axis — a glyph stroke, a text-box
          // border, HUD numbers. The gradient ratio is
          // evaluated over the 5x5 OUTER RING, on RAW luma
          // (direction is a property of the source art, not
          // the colour model; the ring costs 12 plain taps
          // on uRawTex, no colour correction, no mixing with
          // the corrected inner 3x3). The old 3x3 ratio fired on
          // staircase steps — each step texel's gradient is
          // one-directional, so a staircased diagonal read
          // as "text" on a third to a half of its texels and
          // the blend never ran (Robert's device photos,
          // 2026-11-14). Over the ring a staircase's steps
          // average into a diagonal gradient (0% fire on
          // clean 1:2 / 1:3 / 1:4 staircases); a glyph stroke
          // stays one-directional. The 3x3 gradient still
          // drives the edge strength and the blend direction.
          // The axis-aligned threshold is per-system
          // (uTextAlign: GB 0.15, GBC 0.08, GBA 0.05), set
          // by the sim from the text / diagonal margins.
          float lMax = max(l0, max(max(lN, lS), max(max(lE, lW), max(max(lNE, lNW), max(lSE, lSW)))));
          float lMin = min(l0, min(min(lN, lS), min(min(lE, lW), min(min(lNE, lNW), min(lSE, lSW)))));
          float rE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, 0.0)).rgb);
          float rNE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, texel.y)).rgb);
          float rSE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, -texel.y)).rgb);
          float rW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, 0.0)).rgb);
          float rNW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, texel.y)).rgb);
          float rSW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, -texel.y)).rgb);
          float rN2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, 2.0 * texel.y)).rgb);
          float rN2E = edgeLuma(texture2D(uRawTex, vTex + vec2(texel.x, 2.0 * texel.y)).rgb);
          float rN2W = edgeLuma(texture2D(uRawTex, vTex + vec2(-texel.x, 2.0 * texel.y)).rgb);
          float rS2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, -2.0 * texel.y)).rgb);
          float rS2E = edgeLuma(texture2D(uRawTex, vTex + vec2(texel.x, -2.0 * texel.y)).rgb);
          float rS2W = edgeLuma(texture2D(uRawTex, vTex + vec2(-texel.x, -2.0 * texel.y)).rgb);
          float gx5 = (rE2 + rNE2 + rSE2) - (rW2 + rNW2 + rSW2);
          float gy5 = (rN2 + rN2E + rN2W) - (rS2 + rS2E + rS2W);
          float axisAligned = step(min(abs(gx5), abs(gy5)), uTextAlign * max(abs(gx5), abs(gy5)) + 0.001);
          float text = step(0.5, lMax - lMin) * axisAligned;
          // Stroke-length gate (Task 4 item 1, 2026-11-14):
          // the text bypass survives only when the luma step
          // along the stroke terminates within 2 texels in at
          // least one direction. The stroke runs perpendicular
          // to the 5x5-ring gradient (vertical when |gx5| >
          // |gy5|, horizontal otherwise), so the two extra raw
          // taps sit at +-2 texels along it and the raw centre
          // luma is the reference; the scale is half the raw
          // step across the 12 ring taps (the ring spans both
          // sides of the edge). A glyph stroke is <=4 texels,
          // so from any of its texels one end is within 2; a
          // round edge continues past 2 in both directions — which
          // is why the ungated bypass ate round sprites (a
          // synthetic circle's boundary fired it on 21-35% of
          // texels). The gate applies to the edge blend (w)
          // ONLY: patternEff below keeps the ungated text
          // bypass, because it REPLACES the texel with its
          // dither average — a glyph the gate un-bypasses on
          // a long straight run would be averaged away (the
          // sim's TEST DRIVE title letters: 303 texels changed
          // by up to 0.49 when the gate fed patternEff). The
          // distance is 2, not the specified 4: the sim proved
          // +-4 leaves tight arcs (R=6: 0.18, R=10: 0.14),
          // +-3 leaves R=6 at 0.12, and +-2 follows every
          // tested arc (R>=6: 0.00) with glyph contrast at
          // baseline — a 2-tap gate at distance d can only
          // follow arcs with drift d^2/(2R) < 0.5, i.e.
          // R > d^2. Sim: sim4c.py, 93/93 (sim4c-run2.log).
          float rMax = max(max(max(rE2, rNE2), max(rSE2, rW2)), max(max(rNW2, rSW2), max(max(rN2, rN2E), max(rN2W, max(rS2, max(rS2E, rS2W))))));
          float rMin = min(min(min(rE2, rNE2), min(rSE2, rW2)), min(min(rNW2, rSW2), min(min(rN2, rN2E), min(rN2W, min(rS2, min(rS2E, rS2W))))));
          float gateScale = 0.5 * (rMax - rMin);
          float l0r = edgeLuma(texture2D(uRawTex, vTex).rgb);
          float lF2;
          float lB2;
          if (abs(gx5) > abs(gy5)) {
            lF2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, 2.0 * texel.y)).rgb);
            lB2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, -2.0 * texel.y)).rgb);
          } else {
            lF2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, 0.0)).rgb);
            lB2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, 0.0)).rgb);
          }
          float gate = max(step(gateScale, abs(lF2 - l0r)), step(gateScale, abs(lB2 - l0r)));
          float textGated = text * gate;
          // Corner: both diagonal pairs match. A diagonal
          // edge has a strong gradient in BOTH directions,
          // but its two diagonal pairs do not both match —
          // one pair straddles the edge. A true corner has
          // all four diagonal neighbours on the same side, so
          // both pairs match. Keep it sharp.
          float corner = step(distance(cNW, cSE), 0.1) * step(distance(cNE, cSW), 0.1) * step(0.05, edgeStrength);
          // Directional blend: along the edge, not across it.
          // The weight grows toward the texel boundary, so
          // the centre of a texel stays its own colour and
          // the boundary pixel is the anti-aliased mix.
          // No subpixel branch: the rework removed it in
          // item 3 (the offset was 0.3333 of a texel, about
          // 1.9 screen px at 5.7x, and the target phone is
          // pOLED, not an RGB-stripe panel). A vertical edge
          // blends the same way as any other edge.
          vec2 sub = fract(vTex * uTexSize);
          float wAlong;
          vec3 along;
          vec3 lo;
          vec3 hi;
          if (abs(gx) > abs(gy)) {
            along = mix(cS, cN, sub.y);
            wAlong = abs(sub.y - 0.5) * 2.0;
            lo = min(min(c0, cS), cN);
            hi = max(max(c0, cS), cN);
          } else {
            along = mix(cW, cE, sub.x);
            wAlong = abs(sub.x - 0.5) * 2.0;
            lo = min(min(c0, cW), cE);
            hi = max(max(c0, cW), cE);
          }
          float edge = smoothstep(uEdgeLo, uEdgeHi, edgeStrength);
          // No motion factor: the rework deleted motion-
          // adaptive entirely (frame history breaks the
          // pure-function temporal-stability goal, and
          // scrolling needs none of it — smoothing identical
          // on every frame is no wobble by construction).
          float w = edge * wAlong * (1.0 - isolated) * (1.0 - textGated) * (1.0 - corner);
          w = clamp(w, 0.0, uBlendMax);
          vec3 col = mix(c0, along, w);
          col = clamp(col, lo, hi);
          // Dither blend: a patterned texel becomes its
          // intended level. Gated by the 1px and text
          // bypasses — a real dither texel is never
          // isolated and never text, so the gate does not
          // block real dither, but it keeps the blend from
          // averaging a glyph or a 1px detail away. The
          // text bypass here is the UNGATED one, not
          // textGated: patternEff replaces the texel (see
          // the stroke-length gate above), so it must keep
          // the old protection — only the edge blend takes
          // the gated bypass. And the shade-distance guard
          // (Task 4 item 4): the flattening only runs when
          // the shades it would average are close enough
          // to be one intended grey. shadeDist is the
          // distance between those shades (ditherAvg's
          // inputs, same priority): bayer4 -> the farthest
          // of the 3x3 from the centre; bayer2 -> the
          // farthest pair of the 2x2 block; stripeH ->
          // centre to north; checker / stripeV -> centre
          // to east. The bound is 0.7 for all three systems
          // (uDitherGuardHi), set by the sim: GB adjacent
          // pairs sit at 0.22-0.32 and 0-2 skips at
          // 0.54-0.62 (real logo / frame shading — the
          // "adjacent only" rule would leave the SML2 title
          // logo checkerboarded), extremes at 0.84-0.93;
          // the 0.35 first guess would stop the same logo
          // shading from flattening, so it was rejected.
          // Above the bound the pattern stays sharp: map
          // trees, the TEST DRIVE letter bevel, foliage
          // texture. A low-distance transparency mesh (the
          // Danny Phantom fence, median 0.274) is NOT
          // protected by any distance bound — that needs
          // transparency detection, a later mechanism, not
          // a distance cut.
          float shadeDist;
          if (bayer4 > 0.5) {
            shadeDist = max(distance(c0, cN), distance(c0, cS));
            shadeDist = max(shadeDist, max(distance(c0, cE), distance(c0, cW)));
            shadeDist = max(shadeDist, max(distance(c0, cNE), distance(c0, cNW)));
            shadeDist = max(shadeDist, max(distance(c0, cSE), distance(c0, cSW)));
          } else if (bayer2 > 0.5) {
            shadeDist = max(max(distance(c0, cE), distance(c0, cN)), distance(c0, cNE));
            shadeDist = max(shadeDist, max(distance(cE, cN), distance(cE, cNE)));
            shadeDist = max(shadeDist, distance(cN, cNE));
          } else if (stripeH > 0.5) {
            shadeDist = distance(c0, cN);
          } else {
            shadeDist = distance(c0, cE);
          }
          float patternEff = pattern * (1.0 - isolated) * (1.0 - text) * step(shadeDist, uDitherGuardHi);
          col = mix(col, ditherAvg, patternEff);
          // 15-bit snap-back, GBC / GBA only (uSnap15): the
          // blend's intermediates are never left as they
          // fall — they quantize to the 15-bit grid, which
          // IS the GBC / GBA palette space. GB has no snap
          // at all (uSnap15 0): on 4 shades any grey is
          // harmonious, and the old binary snap turned a
          // smooth ramp into a hard one-shade band. A texel
          // that was neither blended nor patterned (w == 0,
          // patternEff == 0) keeps its own colour exactly:
          // the snap only ever applies to a blend.
          if (uSnap15 > 0.5) {
            vec3 snapped = floor(col * 31.0 + 0.5) / 31.0;
            col = mix(c0, snapped, max(step(0.001, distance(col, c0)), patternEff));
          }
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * Task 6 (2026-11-14) — DLSS-in-spirit reconstruction,
     * GBC colour reconstruction. Two passes at source
     * resolution (Pass A) and one at display size (Pass B),
     * proven in the numeric replica sim8.py
     * (~/workspace/poing/verify/edge-rework/sim8.py, 42/42
     * — synthetic GBC frames + the real TD6 GBC frames; no
     * Gold / Silver device frames exist, so the sim proof
     * is synthetic + TD6, stated plainly, not presented as
     * device frames).
     *
     * Pass A is split in two programs for one reason: a
     * single-pass Pass A needs the pattern at all 25 texels
     * of the 5x5 window, and each pattern value needs its
     * own 3x3 — a 7x7 corrected-colour window, 49
     * colourCorrect calls (each 3 LUT reads for GBC) plus
     * 25 pattern evaluations in one fragment shader. That
     * risks driver register pressure on the phone, and the
     * failure mode is the whole feature silently falling
     * back to plain. Two cheap sub-passes compute the
     * IDENTICAL math (the sim's recon_passA_np, split at
     * its own seam): A1 classifies the 3x3 and writes the
     * corrected colour + the pattern map; A2 reads A1 and
     * does the 5x5 field, the class and the collapse. The
     * only difference from the sim is the 8-bit round trip
     * through A1's RGBA FBO (corrected colours quantize to
     * ~0.002 a channel — far inside every detector
     * tolerance; the class channel is exact: 0 / 64 / 128
     * / 192 / 255).
     *
     * The class channel: 0 flat, 1 edge, 2 dither, 3 text,
     * 4 1px — written as cls / 4.0, read back as
     * floor(alpha * 4.0 + 0.5). NEAREST filtering
     * preserves the classes through the RGBA FBO.
     *
     * Two sim-driven refinements, recorded here so the
     * design text is not misread: (1) Pass A uses the
     * UNGATED text bypass for the class and the collapse
     * gate — the stroke-length gate belongs to the edge
     * blend, which Pass B replaces with line
     * reconstruction; a glyph texel the gate un-bypasses
     * would be line-reconstructed as an edge and soften
     * (sim R6: synthetic text contrast kept 1.000 with
     * ungated text). (2) The 6-direction vote for the edge
     * angle was designed, built in the sim, and DISPROVEN
     * there (errors 13.3 / 90.0 / 39.9 degrees on 45 / 1:2
     * / 1:3 — on a clean staircase every direction's
     * along-variance ties, so the vote has no preferred
     * direction); Pass B estimates the angle from the 5x5
     * Sobel gradient instead (0.0 / 2.4 / 2.1 degrees on
     * the same diagonals), and the interpolation itself is
     * directional along the gradient-derived normal. The
     * six directions are not used for the angle.
     *
     * No frame history, no ML, no Vulkan, off by default,
     * never the default. Reconstruction replaces edge
     * smoothing AND the chains while on (see
     * EdgeSmoothing.resolve) — never stacked.
     */
    const val RECON_A1_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform sampler2D uGammaLut;
        uniform int uMode;
        uniform float uGammaRow;
        uniform vec2 uTexSize;
        uniform float uDitherTol;
        float edgeLuma(vec3 c) {
          return dot(c, vec3(0.299, 0.587, 0.114));
        }
        // The colour-fold, copied verbatim from
        // COLOUR_FRAGMENT (same text, same modes): the
        // corrected colours are what the detectors run on,
        // so a dither's intended colour is the average of
        // the corrected shades. GBC is mode 2 (Prism: the
        // SameBoy ramp, LUT row 0, then the mGBA matrix
        // x0.94); GBA is mode 3 (Dusk: the same fold, LUT
        // row 1 — Task 7). GB gets its mode in Task 5.
        vec3 colourCorrect(vec3 col) {
          if (uMode == 0) {
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            vec3 s0 = vec3(0.094, 0.161, 0.129);
            vec3 s1 = vec3(0.290, 0.388, 0.322);
            vec3 s2 = vec3(0.518, 0.612, 0.518);
            vec3 s3 = vec3(0.647, 0.710, 0.647);
            vec3 c01 = mix(s0, s1, clamp(luma / 0.322, 0.0, 1.0));
            vec3 c12 = mix(s1, s2, clamp((luma - 0.322) / 0.356, 0.0, 1.0));
            vec3 c23 = mix(s2, s3, clamp((luma - 0.678) / 0.322, 0.0, 1.0));
            return mix(mix(c01, c12, step(0.322, luma)), c23, step(0.678, luma));
          } else if (uMode == 2 || uMode == 3) {
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
            vec3 outCol = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
            return clamp(outCol, vec3(0.0), vec3(1.0));
          }
          return col;
        }
        void main() {
          vec2 texel = vec2(1.0 / uTexSize.x, 1.0 / uTexSize.y);
          vec3 c0 = colourCorrect(texture2D(uTex, vTex).rgb);
          vec3 cN = colourCorrect(texture2D(uTex, vTex + vec2(0.0, texel.y)).rgb);
          vec3 cS = colourCorrect(texture2D(uTex, vTex - vec2(0.0, texel.y)).rgb);
          vec3 cE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, 0.0)).rgb);
          vec3 cW = colourCorrect(texture2D(uTex, vTex - vec2(texel.x, 0.0)).rgb);
          vec3 cNE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, texel.y)).rgb);
          vec3 cNW = colourCorrect(texture2D(uTex, vTex + vec2(-texel.x, texel.y)).rgb);
          vec3 cSE = colourCorrect(texture2D(uTex, vTex + vec2(texel.x, -texel.y)).rgb);
          vec3 cSW = colourCorrect(texture2D(uTex, vTex + vec2(-texel.x, -texel.y)).rgb);
          float tol = uDitherTol;
          // The five pattern detectors, verbatim from
          // EDGE_FRAGMENT: checker (the diagonal must
          // actually match), stripes, Bayer 2x2, Bayer 4x4
          // proxy (opposite sides differ). A2 reads this
          // map back as the field, so this pass outputs
          // the pattern, not a blend.
          float dDiag = distance(c0, cSE);
          float dE = distance(c0, cE);
          float dS = distance(c0, cS);
          float checker = step(dDiag, tol) * step(tol, dE) * step(tol, dS);
          float stripeV = step(distance(c0, cN) + distance(c0, cS), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cW));
          float stripeH = step(distance(c0, cE) + distance(c0, cW), tol) * step(tol, distance(c0, cN)) * step(tol, distance(c0, cS));
          float bayer2 = step(distance(cE, cW), tol) * step(distance(cN, cS), tol) * step(distance(cNE, cSE), tol) * step(distance(cNE, cNW), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cN)) * step(tol, distance(cE, cN));
          float l0 = edgeLuma(c0);
          float lN = edgeLuma(cN);
          float lS = edgeLuma(cS);
          float lE = edgeLuma(cE);
          float lW = edgeLuma(cW);
          float bayer4 = step(0.0, (lE - l0) * (lW - l0)) * step(0.0, (lN - l0) * (lS - l0)) * step(0.03, abs(lE - l0)) * step(0.03, abs(lW - l0)) * step(0.03, abs(lN - l0)) * step(0.03, abs(lS - l0)) * step(0.05, abs(lE - lW)) * step(0.05, abs(lN - lS));
          float pattern = max(max(checker, max(stripeV, stripeH)), max(bayer2, bayer4));
          gl_FragColor = vec4(c0, pattern);
        }
    """

    /**
     * Pass A, second half — the 5x5 field, the class and
     * the collapse, at source resolution. Reads A1's
     * output (uTex: RGB the corrected colour, A the
     * pattern) and the raw frame (uRawTex: the text ring
     * needs RAW luma — direction is a property of the
     * source art, not the colour model, same as the edge
     * pass). The sim's recon_passA_np, split at its own
     * seam: the field is the pattern map averaged over the
     * 5x5 window, the class priority is edge, then dither
     * (any pattern texel, or a texel inside a dither field
     * — field >= 0.3 — even when the guard blocks the
     * collapse, so a high-contrast dither texel is never
     * line-reconstructed as an edge), then text (UNGATED),
     * then 1px. RGB is the field mean where the field test
     * passes, else the 3x3 dither average where a pattern
     * texel passes the guard, else the centre's corrected
     * colour. The collapse is gated by the UNGATED text
     * bypass and the 1px test, exactly like the edge
     * pass's patternEff — a glyph texel that happens to
     * look like a pattern texel keeps its own colour.
     *
     * The sim zeroes the 2-texel border of the collapse
     * masks (its np.roll windows WRAP at the frame edge);
     * this shader does not — the texture clamps
     * (CLAMP_TO_EDGE), so a border texel's window repeats
     * the edge texel, which is the correct boundary
     * condition, not a wrap.
     */
    const val RECON_A2_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform sampler2D uRawTex;
        uniform vec2 uTexSize;
        uniform float uDitherTol;
        uniform float uDitherGuardHi;
        uniform float uTextAlign;
        uniform float uEdgeLo;
        float edgeLuma(vec3 c) {
          return dot(c, vec3(0.299, 0.587, 0.114));
        }
        void main() {
          vec2 texel = vec2(1.0 / uTexSize.x, 1.0 / uTexSize.y);
          vec4 v0 = texture2D(uTex, vTex);
          vec4 vN = texture2D(uTex, vTex + vec2(0.0, texel.y));
          vec4 vS = texture2D(uTex, vTex - vec2(0.0, texel.y));
          vec4 vE = texture2D(uTex, vTex + vec2(texel.x, 0.0));
          vec4 vW = texture2D(uTex, vTex - vec2(texel.x, 0.0));
          vec4 vNE = texture2D(uTex, vTex + vec2(texel.x, texel.y));
          vec4 vNW = texture2D(uTex, vTex + vec2(-texel.x, texel.y));
          vec4 vSE = texture2D(uTex, vTex + vec2(texel.x, -texel.y));
          vec4 vSW = texture2D(uTex, vTex + vec2(-texel.x, -texel.y));
          vec3 c0 = v0.rgb;
          vec3 cN = vN.rgb;
          vec3 cS = vS.rgb;
          vec3 cE = vE.rgb;
          vec3 cW = vW.rgb;
          vec3 cNE = vNE.rgb;
          vec3 cNW = vNW.rgb;
          vec3 cSE = vSE.rgb;
          vec3 cSW = vSW.rgb;
          float pattern = v0.a;
          // The 5x5 field: the pattern map averaged over
          // the window, and the 5x5 mean of the corrected
          // colours — the field's intended colour, tracking
          // the local dither ratio, so a gradient dither
          // collapses to its local level, not a global
          // 50/50.
          float field = 0.0;
          vec3 mean5 = vec3(0.0);
          for (int j = -2; j <= 2; j++) {
            for (int i = -2; i <= 2; i++) {
              vec4 s = texture2D(uTex, vTex + vec2(float(i), float(j)) * texel);
              field += s.a;
              mean5 += s.rgb;
            }
          }
          field /= 25.0;
          mean5 /= 25.0;
          float tol = uDitherTol;
          // The centre's detectors, recomputed from A1's
          // already-corrected colours (same formulas as
          // the edge pass — the priority and the shade
          // distance both need the individual detectors,
          // which the pattern flag alone does not carry).
          float dDiag = distance(c0, cSE);
          float dE = distance(c0, cE);
          float dS = distance(c0, cS);
          float checker = step(dDiag, tol) * step(tol, dE) * step(tol, dS);
          float stripeV = step(distance(c0, cN) + distance(c0, cS), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cW));
          float stripeH = step(distance(c0, cE) + distance(c0, cW), tol) * step(tol, distance(c0, cN)) * step(tol, distance(c0, cS));
          float bayer2 = step(distance(cE, cW), tol) * step(distance(cN, cS), tol) * step(distance(cNE, cSE), tol) * step(distance(cNE, cNW), tol) * step(tol, distance(c0, cE)) * step(tol, distance(c0, cN)) * step(tol, distance(cE, cN));
          float l0 = edgeLuma(c0);
          float lN = edgeLuma(cN);
          float lS = edgeLuma(cS);
          float lE = edgeLuma(cE);
          float lW = edgeLuma(cW);
          float lNE = edgeLuma(cNE);
          float lNW = edgeLuma(cNW);
          float lSE = edgeLuma(cSE);
          float lSW = edgeLuma(cSW);
          float bayer4 = step(0.0, (lE - l0) * (lW - l0)) * step(0.0, (lN - l0) * (lS - l0)) * step(0.03, abs(lE - l0)) * step(0.03, abs(lW - l0)) * step(0.03, abs(lN - l0)) * step(0.03, abs(lS - l0)) * step(0.05, abs(lE - lW)) * step(0.05, abs(lN - lS));
          // The dither average: the intended level of the
          // pattern. Priority bayer4, then bayer2, then
          // stripeH, then stripeV / checker — the same
          // order as the edge pass and the sim.
          vec3 ditherAvg;
          if (bayer4 > 0.5) {
            ditherAvg = (c0 + cN + cS + cE + cW + cNE + cNW + cSE + cSW) / 9.0;
          } else if (bayer2 > 0.5) {
            ditherAvg = (c0 + cE + cN + cNE) * 0.25;
          } else if (stripeH > 0.5) {
            ditherAvg = (c0 + cN) * 0.5;
          } else if (checker > 0.5 || stripeV > 0.5) {
            ditherAvg = (c0 + cE) * 0.5;
          } else {
            ditherAvg = c0;
          }
          // The shade-distance guard (Task 4 item 4): the
          // flattening only runs when the shades it would
          // average are within 0.7 of each other
          // (uDitherGuardHi) — same priority as ditherAvg.
          float shadeDist;
          if (bayer4 > 0.5) {
            shadeDist = max(distance(c0, cN), distance(c0, cS));
            shadeDist = max(shadeDist, max(distance(c0, cE), distance(c0, cW)));
            shadeDist = max(shadeDist, max(distance(c0, cNE), distance(c0, cNW)));
            shadeDist = max(shadeDist, max(distance(c0, cSE), distance(c0, cSW)));
          } else if (bayer2 > 0.5) {
            shadeDist = max(max(distance(c0, cE), distance(c0, cN)), distance(c0, cNE));
            shadeDist = max(shadeDist, max(distance(cE, cN), distance(cE, cNE)));
            shadeDist = max(shadeDist, distance(cN, cNE));
          } else if (stripeH > 0.5) {
            shadeDist = distance(c0, cN);
          } else {
            shadeDist = distance(c0, cE);
          }
          // 1-pixel detail: the eight neighbours all agree
          // with each other and the centre stands apart.
          vec3 nMean = (cN + cS + cE + cW + cNE + cNW + cSE + cSW) / 8.0;
          float spread = max(max(distance(cN, nMean), distance(cS, nMean)), max(distance(cE, nMean), distance(cW, nMean)));
          spread = max(spread, max(max(distance(cNE, nMean), distance(cNW, nMean)), max(distance(cSE, nMean), distance(cSW, nMean))));
          float isolated = step(spread, 0.05) * step(0.15, distance(c0, nMean));
          // Edge: a Sobel-like luma gradient over the 3x3.
          float gx = (lE + lNE + lSE) - (lW + lNW + lSW);
          float gy = (lN + lNE + lNW) - (lS + lSE + lSW);
          float edgeStrength = abs(gx) + abs(gy);
          // Text: a high-contrast edge that runs straight
          // along one axis. The gradient ratio is evaluated
          // over the 5x5 OUTER RING, on RAW luma (the
          // ring costs 12 plain taps on uRawTex, no colour
          // correction) — the same rule as the edge pass:
          // over the ring a staircase's steps average into
          // a diagonal gradient, while a glyph stroke stays
          // one-directional. UNGATED here: the class and
          // the collapse gate both keep the old protection
          // (see the Pass A header note).
          float lMax = max(l0, max(max(lN, lS), max(max(lE, lW), max(max(lNE, lNW), max(lSE, lSW)))));
          float lMin = min(l0, min(min(lN, lS), min(min(lE, lW), min(min(lNE, lNW), min(lSE, lSW)))));
          float rE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, 0.0)).rgb);
          float rNE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, texel.y)).rgb);
          float rSE2 = edgeLuma(texture2D(uRawTex, vTex + vec2(2.0 * texel.x, -texel.y)).rgb);
          float rW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, 0.0)).rgb);
          float rNW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, texel.y)).rgb);
          float rSW2 = edgeLuma(texture2D(uRawTex, vTex + vec2(-2.0 * texel.x, -texel.y)).rgb);
          float rN2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, 2.0 * texel.y)).rgb);
          float rN2E = edgeLuma(texture2D(uRawTex, vTex + vec2(texel.x, 2.0 * texel.y)).rgb);
          float rN2W = edgeLuma(texture2D(uRawTex, vTex + vec2(-texel.x, 2.0 * texel.y)).rgb);
          float rS2 = edgeLuma(texture2D(uRawTex, vTex + vec2(0.0, -2.0 * texel.y)).rgb);
          float rS2E = edgeLuma(texture2D(uRawTex, vTex + vec2(texel.x, -2.0 * texel.y)).rgb);
          float rS2W = edgeLuma(texture2D(uRawTex, vTex + vec2(-texel.x, -2.0 * texel.y)).rgb);
          float gx5 = (rE2 + rNE2 + rSE2) - (rW2 + rNW2 + rSW2);
          float gy5 = (rN2 + rN2E + rN2W) - (rS2 + rS2E + rS2W);
          float axisAligned = step(min(abs(gx5), abs(gy5)), uTextAlign * max(abs(gx5), abs(gy5)) + 0.001);
          float text = step(0.5, lMax - lMin) * axisAligned;
          // The collapse gates: the field test and the
          // pattern test both need the guard, and both are
          // blocked by text or an isolated dot.
          float guard = step(shadeDist, uDitherGuardHi);
          float noCollapse = max(text, isolated);
          float fieldOk = step(0.6, field) * guard * (1.0 - noCollapse);
          float patOk = step(0.5, pattern) * guard * (1.0 - noCollapse);
          // Class priority (later overrides win): edge,
          // then dither, then text, then 1px.
          float cls = 0.0;
          if (edgeStrength > uEdgeLo) cls = 1.0;
          if (pattern > 0.5 || field >= 0.3) cls = 2.0;
          if (text > 0.5) cls = 3.0;
          if (isolated > 0.5) cls = 4.0;
          vec3 rgb = c0;
          if (fieldOk > 0.5) {
            rgb = mean5;
          } else if (patOk > 0.5) {
            rgb = ditherAvg;
          }
          gl_FragColor = vec4(rgb, cls / 4.0);
        }
    """

    /**
     * Pass B — the line reconstruction, at display size.
     * Reads Pass A's output (uTex: RGB the collapsed /
     * corrected colour, A the class / 4.0). The sim's
     * recon_passB_np (v3): the 5x5 Sobel gradient gives the
     * edge angle (the 3x3 Sobel is too local — on a 1:2
     * staircase it reads 18.4 degrees for a 26.6 degree
     * line; the 5x5 reads 0.0 / 2.4 / 2.1 on 45 / 1:2 /
     * 1:3); the side colours come from the profile ends
     * (t = +-2 along the normal); the offset comes from a
     * 2D interval fit over the 5x5 window (the dark texels'
     * largest normal coordinate and the light texels'
     * smallest bound the line; the midpoint is the offset
     * — measured identical, to 0.001 texels, for every
     * texel along a 45-degree line). The output is a
     * 1-display-px transition centred on the line. Only
     * edge-class texels are reconstructed, with a real
     * interval, a strong enough edge and no corner;
     * text / 1px texels output their nearest texel
     * exactly, flat / dither texels output nearest (the
     * field is already collapsed in Pass A). The 15-bit
     * snap (GBC) follows the edge pass's rule: snap only
     * when the colour actually moved — including a dither
     * texel, whose colour moved in Pass A.
     *
     * The Worm Light's pool rides on this pass (it draws
     * to the screen, like the plain program); there are no
     * optics here, following the edge precedent (optics
     * are only set on the grid program).
     */
    const val RECON_B_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform vec2 uTexSize;
        uniform float uDisplayScale;
        uniform float uSnap15;
        uniform vec2 uWlCenter;
        uniform vec2 uWlRadii;
        uniform float uWlRot;
        uniform float uWlIntensity;
        uniform float uWlFlip;
        float edgeLuma(vec3 c) {
          return dot(c, vec3(0.299, 0.587, 0.114));
        }
        float sobW(int i) {
          if (i == 0 || i == 4) return 1.0;
          if (i == 1 || i == 3) return 4.0;
          return 6.0;
        }
        void main() {
          vec2 srcPos = vTex * uTexSize;
          vec2 base = floor(srcPos);
          vec2 sub = fract(srcPos);
          vec4 c0v = texture2D(uTex, vTex);
          vec3 c0 = c0v.rgb;
          float cls0 = floor(c0v.a * 4.0 + 0.5);
          // 5x5 Sobel on the Pass A luma. Separable kernel
          // w = [1, 4, 6, 4, 1], k = [-2, -1, 0, 1, 2];
          // gx = sum w(dy) * k(dx) * luma, gy = sum k(dy) *
          // w(dx) * luma. The diagonal RGBs are stashed for
          // the corner test.
          float gx = 0.0;
          float gy = 0.0;
          vec3 cNW = vec3(0.0);
          vec3 cNE = vec3(0.0);
          vec3 cSE = vec3(0.0);
          vec3 cSW = vec3(0.0);
          for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 5; i++) {
              int dx = i - 2;
              int dy = j - 2;
              vec2 tapUV = (base + vec2(float(dx) + 0.5, float(dy) + 0.5)) / uTexSize;
              vec3 s = texture2D(uTex, tapUV).rgb;
              float l = edgeLuma(s);
              gx += sobW(j) * float(i - 2) * l;
              gy += float(j - 2) * sobW(i) * l;
              if (dx == -1 && dy == -1) cNW = s;
              if (dx == 1 && dy == -1) cNE = s;
              if (dx == 1 && dy == 1) cSE = s;
              if (dx == -1 && dy == 1) cSW = s;
            }
          }
          float gn = sqrt(gx * gx + gy * gy) + 0.000000001;
          float nx = gx / gn;
          float ny = gy / gn;
          // Corner: both diagonal pairs match. A corner
          // keeps its sharp point, never a blend.
          float corner = step(distance(cNW, cSE), 0.1) * step(distance(cNE, cSW), 0.1) * step(0.05, abs(gx) + abs(gy));
          // Side colours: the profile ends (t = +-2 along
          // the normal), rounded to the nearest texel.
          vec2 offA = vec2(floor(-2.0 * nx + 0.5), floor(-2.0 * ny + 0.5));
          vec2 offB = vec2(floor(2.0 * nx + 0.5), floor(2.0 * ny + 0.5));
          vec3 cA = texture2D(uTex, (base + offA + vec2(0.5)) / uTexSize).rgb;
          vec3 cB = texture2D(uTex, (base + offB + vec2(0.5)) / uTexSize).rgb;
          float lA = edgeLuma(cA);
          float lB = edgeLuma(cB);
          // 2D interval fit for the offset: over the 5x5
          // window, the dark texels' largest normal
          // coordinate and the light texels' smallest bound
          // the line. A texel counts as dark (light) when
          // its colour is within 0.15 of the dark (light)
          // side colour and closer to it than to the other
          // side; dither / text / 1px texels never count
          // (their colours are not the edge's sides). The
          // sentinels (+-100) are far outside the
          // coordinate range (at most +-2.83), so a bound
          // still at its sentinel means no texel counted.
          float dLow = -100.0;
          float dHigh = 100.0;
          for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 5; i++) {
              int dx = i - 2;
              int dy = j - 2;
              vec2 tapUV = (base + vec2(float(dx) + 0.5, float(dy) + 0.5)) / uTexSize;
              vec4 s = texture2D(uTex, tapUV);
              float cls = floor(s.a * 4.0 + 0.5);
              float sv = nx * float(dx) + ny * float(dy);
              float dA = distance(s.rgb, cA);
              float dB = distance(s.rgb, cB);
              float okCls = 1.0;
              if (cls == 2.0 || cls == 3.0 || cls == 4.0) okCls = 0.0;
              if (dA < 0.15 && dA < dB && okCls > 0.5) dLow = max(dLow, sv);
              if (dB < 0.15 && dB < dA && okCls > 0.5) dHigh = min(dHigh, sv);
            }
          }
          float okInterval = 1.0;
          if (dLow <= -50.0 || dHigh >= 50.0 || dLow >= dHigh) okInterval = 0.0;
          float d = 0.0;
          if (okInterval > 0.5) d = 0.5 * (dLow + dHigh);
          // Signed distance of the fragment from the line,
          // in texels then display px; the output is a
          // 1-display-px transition centred on the line.
          float sTex = (sub.x - 0.5) * nx + (sub.y - 0.5) * ny - d;
          float sPx = sTex * uDisplayScale;
          float mixw = smoothstep(-0.5, 0.5, sPx);
          vec3 outRecon = cA + (cB - cA) * mixw;
          // Guards: reconstruct only edge-class texels,
          // with a real interval, a strong enough edge, and
          // no corner. (The gradient guard is a weak sanity
          // guard only — the 5x5 Sobel's scale is ~16x the
          // 3x3's, so its threshold is 0.5, not 0.1.)
          float doRecon = 0.0;
          if (cls0 == 1.0 && okInterval > 0.5 && abs(lB - lA) >= 0.1 && (abs(gx) + abs(gy)) >= 0.5 && corner < 0.5) doRecon = 1.0;
          vec3 outCol = mix(c0, outRecon, doRecon);
          // 15-bit snap (GBC), same rule as the edge pass:
          // snap only when the colour actually moved —
          // including a dither texel, whose colour moved in
          // Pass A.
          if (uSnap15 > 0.5) {
            vec3 snapped = floor(outCol * 31.0 + 0.5) / 31.0;
            float moved = max(step(0.001, distance(outCol, c0)), step(abs(cls0 - 2.0), 0.5));
            outCol = mix(c0, snapped, moved);
          }
          // Worm Light realism: the lamp's pool, the same
          // add as the plain program — the lamp works on
          // the reconstruction too. uWlFlip is 1 when the
          // source is the pass-1 framebuffer: V is flipped
          // there, so the centre's y flips too. Intensity 0
          // adds exactly 0.0.
          vec2 wlD = vTex - vec2(uWlCenter.x, mix(uWlCenter.y, 1.0 - uWlCenter.y, uWlFlip));
          float wlC = cos(uWlRot);
          float wlS = sin(uWlRot);
          vec2 wlR = vec2(wlD.x * wlC - wlD.y * wlS, wlD.x * wlS + wlD.y * wlC);
          float wlE = dot(wlR / uWlRadii, wlR / uWlRadii);
          float wlFall = smoothstep(1.0, 0.0, wlE);
          float wlHot = smoothstep(0.45, 0.0, wlE);
          outCol += uWlIntensity * (wlFall * 0.14 + wlHot * 0.16) * vec3(1.0, 0.82, 0.55);
          outCol = clamp(outCol, vec3(0.0), vec3(1.0));
          gl_FragColor = vec4(outCol, 1.0);
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
     * The LCD grid over the reconstruction (Robert,
     * 2026-11-14): 15% strength — the specified starting
     * value for him to judge on device, not a slider.
     * Unlike the chain's grid (gridStrengthForScale,
     * tapered with the display scale), this is fixed:
     * the grid is a layer over the reconstruction, and
     * its strength is the value Robert named.
     */
    const val RECON_GRID_STRENGTH = 0.15f

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
        uniform float uWlFlip;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          // Worm Light realism: the lamp's pool, the same
          // add as the grid pass (see GRID_FRAGMENT) — the
          // lamp works with the chain off too. Intensity 0
          // adds exactly 0.0, so clean stays clean.
          // uWlFlip is 1 when the source is the pass-1
          // framebuffer (the edge pass's blit, Phase 9): V
          // is flipped there, so the centre's y flips too —
          // the same flip the grid pass does inline. 0 is
          // the direct Bitmap source, y = 0 at the top.
          // Performance fix 8 — same branch as the grid
          // pass: the pool math is skipped at intensity 0,
          // where it adds exactly 0.0; the clamp stays
          // outside.
          if (uWlIntensity > 0.0) {
            vec2 wlD = vTex - vec2(uWlCenter.x, mix(uWlCenter.y, 1.0 - uWlCenter.y, uWlFlip));
            float wlC = cos(uWlRot);
            float wlS = sin(uWlRot);
            vec2 wlR = vec2(wlD.x * wlC - wlD.y * wlS, wlD.x * wlS + wlD.y * wlC);
            float wlE = dot(wlR / uWlRadii, wlR / uWlRadii);
            float wlFall = smoothstep(1.0, 0.0, wlE);
            float wlHot = smoothstep(0.45, 0.0, wlE);
            col += uWlIntensity * (wlFall * 0.14 + wlHot * 0.16) * vec3(1.0, 0.82, 0.55);
          }
          col = clamp(col, vec3(0.0), vec3(1.0));
          gl_FragColor = vec4(col, 1.0);
        }
    """
}
