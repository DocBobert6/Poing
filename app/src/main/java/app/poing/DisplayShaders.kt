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
     * screen, 1996 — true black-and-white, blue-tinted
     * greyscale, sharp. The frame's luma drives a ramp from
     * a near-black blue to a paper-white blue; the four
     * shades of the core's greyscale palette land on it
     * exactly. Jade pass-through: the frame untouched, for
     * the DMG green and the coloured GBC/SGB palettes.
     * Prism: the Game Boy Color's reflective TFT, 1998 — the
     * core's colour correction done in the chain (the core's
     * own correction is switched off while the chain is on,
     * so a game is never corrected twice), plus a lift for a
     * reflective screen in daylight. Dusk: the Game Boy
     * Advance's reflective TFT, 2001 — the same correction
     * with the Advance's own gamma, plus a gamma lift and a
     * saturation lift, because the AGB-001 is dark and
     * unlit. Any mode this build does not know yet falls
     * through to pass-through, never to a wrong look.
     *
     * The corrections are the core's own numbers, verified
     * against the pinned tree (src/platform/libretro/
     * libretro.c: CC_TARGET_GAMMA 2.2, GBC_CC_GAMMA_ADJ -0.5
     * so Prism expands at 1.7, GBA_CC_GAMMA_ADJ +1.0 so Dusk
     * expands at 3.2, LUM 0.94, the same matrix) — the chains
     * cannot drift from the core. The shot-1 investigation
     * (2026-11-12) found no chain-selection bug: the tan in
     * Robert's GB screenshot is the GBC game's own background
     * colour through Prism (FSTN cannot make tan — its ramp
     * is always B>=G>=R — and the theme in the same shot
     * proves the game is GBC by header), and the dullness
     * was the grid, not the colour. The Dusk lift stays at
     * pow(0.85): lift variants (0.90 / 0.92 / 1.0) buy only
     * +0.02-0.04 chroma on brights and cost real shadow
     * readability (mid grey luma 0.417 -> 0.357 at pow 1.0);
     * the blue crush is the core's own matrix — the
     * authentic AGB look, not a bug. Screen Optics was
     * exonerated too (a mild bright-room boost, never a
     * wash). See ~/workspace/poing/verify/shader-rework/
     * MEASUREMENTS.md for the numbers.
     */
    const val COLOUR_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform int uMode;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          if (uMode == 0) {
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            vec3 dark = vec3(0.05, 0.07, 0.10);
            vec3 light = vec3(0.85, 0.89, 0.93);
            col = mix(dark, light, luma);
          } else if (uMode == 2) {
            vec3 c = pow(col, vec3(1.7));
            vec3 m;
            m.r = (0.82 * c.r + 0.125 * c.g + 0.195 * c.b) * 0.94;
            m.g = (0.24 * c.r + 0.665 * c.g + 0.075 * c.b) * 0.94;
            m.b = (-0.06 * c.r + 0.21 * c.g + 0.73 * c.b) * 0.94;
            col = pow(max(m, vec3(0.0)), vec3(1.0 / 2.2));
            col = min(col * 1.08, vec3(1.0));
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
     * The strength is the only dial, and it stays subtle —
     * the grid must read, never shout. uGridMode picks which:
     * 0 = dot-matrix, 1 = TFT lines.
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
            g = smoothstep(0.52, 0.40, distance(cell, vec2(0.5)));
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
