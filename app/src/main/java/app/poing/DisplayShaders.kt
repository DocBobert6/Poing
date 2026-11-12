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
 * pass-through (the palette as the core rendered it). Prism
 * and Dusk add their own modes in their commits.
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
     * Any mode this build does not know yet falls through to
     * pass-through, never to a wrong look.
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
          }
          gl_FragColor = vec4(col, 1.0);
        }
    """

    /**
     * Pass 2 — grid. Jade's dot-matrix: each native pixel is
     * a dot, darker toward its edges, so the Pocket's screen
     * reads as dots, not squares. The strength is the only
     * dial, and it stays subtle — the dots must read, never
     * shout. The TFT grid (Prism / Dusk) lands with Prism.
     */
    const val GRID_FRAGMENT = """
        precision mediump float;
        varying vec2 vTex;
        uniform sampler2D uTex;
        uniform vec2 uTexSize;
        uniform float uGridStrength;
        void main() {
          vec3 col = texture2D(uTex, vTex).rgb;
          vec2 cell = fract(vTex * uTexSize);
          float g = smoothstep(0.5, 0.35, distance(cell, vec2(0.5)));
          col *= 1.0 - uGridStrength * (1.0 - g);
          gl_FragColor = vec4(col, 1.0);
        }
    """

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
