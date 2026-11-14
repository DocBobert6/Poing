package app.poing

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Phase 8 — the display-chain renderer: a
 * GLSurfaceView.Renderer that draws the latest frame Bitmap
 * as a GL texture through the per-system chain
 * (DisplayShaders). No native code, no core changes: the
 * frame still arrives as a Bitmap from PlayerSession, and
 * this renderer only presents it.
 *
 * Two passes. Pass 1 colours the frame at its native size
 * into an offscreen framebuffer (160x144 or 240x160); pass 2
 * draws that result to the screen with the grid. The grid is
 * a separate pass so it stays aligned to the native texel
 * grid. Both textures use GL_NEAREST — integer scale, hard
 * texels; the grid pass needs hard texels, and nothing here
 * blurs the picture.
 *
 * Flat only: a fullscreen quad, no mesh, no curve, no
 * corner rounding — a Game Boy screen is a rectangle
 * (Robert, 2026-11-12).
 *
 * The view renders on demand (RENDERMODE_WHEN_DIRTY): a new
 * frame or a chain change requests a redraw, and the last
 * frame stays on screen while paused. The EGL context is
 * preserved across pauses so a rotation does not lose the
 * texture (it is re-uploaded on the next frame in any case).
 *
 * If a chain program fails to compile, the plain fallback
 * program draws instead, so the game stays visible and the
 * failure is only a log line.
 */
class DisplayRenderer : GLSurfaceView.Renderer {

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var chain: DisplayChain = DisplayChain.CLEAN

    @Volatile
    private var jadeMode: JadeMode = JadeMode.FSTN

    /**
     * Edge smoothing (reworked 2026-11-14): the mode in
     * effect, and the system it is smoothing for (the
     * per-system parameters come from
     * EdgeSmoothing.edgeParamsFor). Edge on means the
     * chain is off — the caller resolves that (see
     * EdgeSmoothing.resolve); this renderer only draws
     * what it is told. There is no motion state and no
     * palette scan here any more: the rework deleted
     * motion-adaptive entirely (frame history breaks the
     * pure-function goal) and GB has no palette snap, so
     * nothing is sampled from the frame on arrival.
     */
    @Volatile
    private var edgeMode: EdgeMode = EdgeMode.OFF

    @Volatile
    private var edgeSystem: GameSystem = GameSystem.GB

    /**
     * Phase 8 — Screen Optics: the ambient light's
     * brightness / contrast / gamma for the active chain.
     * Neutral until the first lux reading arrives.
     */
    @Volatile
    private var optics: OpticsParams = OpticsParams.NEUTRAL

    /**
     * Worm Light realism (Robert, 2026-11-12): the lamp's
     * pool, in frame UV. Off (intensity 0) until the lamp
     * reports its first pool — the shader adds exactly 0.0
     * at intensity 0, so the chains are pixel-identical
     * with the lamp off.
     */
    @Volatile
    private var wormLight: WormLightPool = WormLightPool.OFF

    private var colourProgram = 0
    private var gridProgram = 0
    private var edgeProgram = 0
    private var plainProgram = 0
    private var frameTextureId = 0
    private var fboTextureId = 0
    private var gammaLutTextureId = 0
    private var fboId = 0
    private var quadVbo = 0
    private var viewW = 0
    private var viewH = 0
    private var frameW = 0
    private var frameH = 0
    private var uploadedW = 0
    private var uploadedH = 0
    private var fboW = 0
    private var fboH = 0

    /** Uniform locations, cached at link time. */
    private val uniformLocs = HashMap<String, Int>()

    fun setFrame(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    /**
     * Apply a chain. A chain this build does not know yet
     * runs clean rather than drawing a wrong look — the
     * mapping in DisplayChain.forSystem never returns it,
     * and this is the backstop.
     *
     * This is also where the edge mode arrives: the chain,
     * the edge mode, and the system being smoothed.
     */
    fun setDisplay(
        chain: DisplayChain,
        jadeMode: JadeMode,
        edge: EdgeMode,
        system: GameSystem,
    ) {
        this.chain = when (chain) {
            DisplayChain.JADE -> DisplayChain.JADE
            DisplayChain.PRISM -> DisplayChain.PRISM
            DisplayChain.DUSK -> DisplayChain.DUSK
            DisplayChain.CLEAN -> DisplayChain.CLEAN
        }
        this.jadeMode = jadeMode
        this.edgeMode = edge
        this.edgeSystem = system
    }

    /**
     * Apply the ambient light's brightness / contrast /
     * gamma. True when the picture would visibly change, so
     * the caller can skip a redraw for sensor chatter: the
     * light sensor fires ~50 times a second, and most
     * readings move the picture by nothing worth drawing.
     */
    fun setOptics(params: OpticsParams): Boolean {
        val old = optics
        optics = params
        return kotlin.math.abs(params.brightness - old.brightness) >= OPTICS_EPSILON ||
            kotlin.math.abs(params.contrast - old.contrast) >= OPTICS_EPSILON ||
            kotlin.math.abs(params.gamma - old.gamma) >= OPTICS_EPSILON
    }

    /**
     * Apply the Worm Light's pool. True when the picture
     * would visibly change, so the caller can skip a redraw
     * for a no-op report: the pool moves continuously while
     * the head is dragged, and every move redraws the last
     * frame — including while paused, so the pool follows
     * the finger even when the game is not drawing frames.
     * The epsilon is tighter than the optics one (the pool
     * is a position, not sensor chatter).
     */
    fun setWormLight(pool: WormLightPool): Boolean {
        val old = wormLight
        wormLight = pool
        return kotlin.math.abs(pool.intensity - old.intensity) >= WL_EPSILON ||
            kotlin.math.abs(pool.center.x - old.center.x) >= WL_EPSILON ||
            kotlin.math.abs(pool.center.y - old.center.y) >= WL_EPSILON ||
            kotlin.math.abs(pool.radii.x - old.radii.x) >= WL_EPSILON ||
            kotlin.math.abs(pool.radii.y - old.radii.y) >= WL_EPSILON ||
            kotlin.math.abs(pool.rotationRad - old.rotationRad) >= WL_EPSILON
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        colourProgram = buildProgram(DisplayShaders.VERTEX, DisplayShaders.COLOUR_FRAGMENT)
        if (colourProgram == 0) {
            Log.e(TAG, "Colour program failed to compile; the chain falls back to plain")
        }
        gridProgram = buildProgram(DisplayShaders.VERTEX, DisplayShaders.GRID_FRAGMENT)
        if (gridProgram == 0) {
            Log.e(TAG, "Grid program failed to compile; the chain falls back to plain")
        }
        edgeProgram = buildProgram(DisplayShaders.VERTEX, DisplayShaders.EDGE_FRAGMENT)
        if (edgeProgram == 0) {
            Log.e(TAG, "Edge program failed to compile; edge smoothing falls back to plain")
        }
        plainProgram = buildProgram(DisplayShaders.VERTEX, DisplayShaders.PLAIN_FRAGMENT)
        val frameTex = IntArray(1)
        GLES20.glGenTextures(1, frameTex, 0)
        frameTextureId = frameTex[0]
        setupTexture(frameTextureId)
        val fboTex = IntArray(1)
        GLES20.glGenTextures(1, fboTex, 0)
        fboTextureId = fboTex[0]
        setupTexture(fboTextureId)
        // The gamma LUT for Prism and Dusk (DisplayShaders):
        // a 32x2 RGBA texture, row 0 = GBC ramp, row 1 = GBA
        // ramp, GL_NEAREST so the shader reads the exact
        // entry. Created here (and re-created if the EGL
        // context is ever lost) so the data is never stale.
        val lutTex = IntArray(1)
        GLES20.glGenTextures(1, lutTex, 0)
        gammaLutTextureId = lutTex[0]
        setupTexture(gammaLutTextureId)
        uploadGammaLut(gammaLutTextureId)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        fboId = fbo[0]
        quadVbo = buildQuad()
        uploadedW = 0
        uploadedH = 0
        fboW = 0
        fboH = 0
        frameW = 0
        frameH = 0
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        viewW = w
        viewH = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val bmp = pendingBitmap
        if (bmp != null) {
            pendingBitmap = null
            if (frameTextureId != 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
                if (bmp.width == uploadedW && bmp.height == uploadedH) {
                    GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp)
                } else {
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    uploadedW = bmp.width
                    uploadedH = bmp.height
                }
                if (bmp.width != frameW || bmp.height != frameH) {
                    frameW = bmp.width
                    frameH = bmp.height
                }
            }
        }
        if (frameTextureId == 0 || frameW <= 0 || viewW <= 0 || viewH <= 0) return
        // Phase 9 — edge smoothing wins over the chain (the
        // caller resolves that); a failed edge program falls
        // back to plain, so the game stays visible.
        if (edgeMode != EdgeMode.OFF) {
            if (edgeProgram != 0) {
                drawEdge()
            } else {
                drawPlain()
            }
            return
        }
        if (chain == DisplayChain.CLEAN || colourProgram == 0 || gridProgram == 0) {
            drawPlain()
            return
        }
        if (!ensureFbo(frameW, frameH)) {
            drawPlain()
            return
        }
        // Pass 1 — colour, at the native size, into the
        // framebuffer. The source is a Bitmap texture, so V
        // stays unflipped (uFlipV 0).
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, frameW, frameH)
        GLES20.glUseProgram(colourProgram)
        GLES20.glUniform1f(uniform(colourProgram, "uFlipV"), 0f)
        GLES20.glUniform1i(uniform(colourProgram, "uTex"), 0)
        // Colour mode: 0 = Jade FSTN, 1 = Jade pass-through,
        // 2 = Prism, 3 = Dusk. The grid mode follows the
        // chain: the dot-matrix is Jade's, the TFT lines are
        // Prism's and Dusk's.
        GLES20.glUniform1i(
            uniform(colourProgram, "uMode"),
            when (chain) {
                DisplayChain.JADE -> if (jadeMode == JadeMode.FSTN) 0 else 1
                DisplayChain.PRISM -> 2
                DisplayChain.DUSK -> 3
                else -> 1
            },
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
        // The gamma LUT, on texture unit 1: row 0 is the GBC
        // ramp (Prism), row 1 the GBA ramp (Dusk); Jade never
        // samples it. The uniform lookups are cached and a
        // missing uniform is a silent no-op, so this is safe
        // even if a program fails to link.
        if (gammaLutTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gammaLutTextureId)
            GLES20.glUniform1i(uniform(colourProgram, "uGammaLut"), 1)
            GLES20.glUniform1f(
                uniform(colourProgram, "uGammaRow"),
                when (chain) {
                    DisplayChain.PRISM -> 0f
                    DisplayChain.DUSK -> 1f
                    else -> 0f
                },
            )
        }
        drawQuad(colourProgram)
        // Pass 2 — grid, to the screen. The source is the
        // pass-1 framebuffer, so V flips (uFlipV 1).
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(gridProgram)
        GLES20.glUniform1f(uniform(gridProgram, "uFlipV"), 1f)
        GLES20.glUniform1i(uniform(gridProgram, "uTex"), 0)
        GLES20.glUniform2f(
            uniform(gridProgram, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1i(
            uniform(gridProgram, "uGridMode"),
            if (chain == DisplayChain.PRISM || chain == DisplayChain.DUSK) 1 else 0,
        )
        // The grid strength for this display scale: the game
        // fills the view height, so the scale is the view over
        // the frame on the tighter axis. At the new full-height
        // size (4.5-5x) the old fixed 0.22 measured a 22%
        // dip at every texel boundary — a mesh over the
        // picture (Robert's screenshots, 2026-11-12); the
        // scale-aware strength keeps the max dip near 6%.
        val displayScale = minOf(
            viewW.toFloat() / frameW,
            viewH.toFloat() / frameH,
        )
        val gridStrength = DisplayShaders.gridStrengthForScale(displayScale)
        GLES20.glUniform1f(uniform(gridProgram, "uGridStrength"), gridStrength)
        GLES20.glUniform1f(uniform(gridProgram, "uBrightness"), optics.brightness)
        GLES20.glUniform1f(uniform(gridProgram, "uContrast"), optics.contrast)
        GLES20.glUniform1f(uniform(gridProgram, "uGamma"), optics.gamma)
        // Worm Light realism: the lamp's pool, AFTER the
        // optics — the room dim never cancels the lamp.
        setWormLightUniforms(gridProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        drawQuad(gridProgram)
    }

    /** The Worm Light's pool uniforms, shared by the grid and the plain programs. */
    private fun setWormLightUniforms(prog: Int) {
        GLES20.glUniform2f(uniform(prog, "uWlCenter"), wormLight.center.x, wormLight.center.y)
        GLES20.glUniform2f(uniform(prog, "uWlRadii"), wormLight.radii.x, wormLight.radii.y)
        GLES20.glUniform1f(uniform(prog, "uWlRot"), wormLight.rotationRad)
        GLES20.glUniform1f(uniform(prog, "uWlIntensity"), wormLight.intensity)
    }

    /**
     * Phase 9 — edge smoothing, two passes. Pass 1 runs the
     * edge shader at the DISPLAY size into the offscreen
     * framebuffer — the fix for the 2026-11-14 no-op
     * (Robert's V-Rally 3 screenshots): at the frame's
     * native size every fragment sat at a texel centre
     * (sub 0.5, 0.5), so the boundary weight wAlong was
     * exactly 0 everywhere and the shader output its input
     * unchanged on any game. At display size each screen
     * pixel lands at a real sub-texel position, so the
     * weight grows toward the texel boundary and the
     * smoothing actually happens — while the centre of a
     * texel still stays its own colour (wAlong 0 at sub
     * 0.5), so flat areas stay crisp. Pass 2 is the plain
     * blit (edge only) or the light grid over the smoothed
     * frame (the combined mode) — edge + light grid only,
     * never the colour pass. The combined mode's grid is
     * the same grid the chains draw (dot-matrix for GB,
     * TFT lines for GBC / GBA), at the same scale-aware
     * strength; the room light rides with it, as it does
     * on the chains. The pass-2 grid still reads the
     * native texel grid (uTexSize is the frame size), so
     * the grid stays aligned to the texels even though
     * pass 1 runs at display size.
     */
    private fun drawEdge() {
        if (!ensureFbo(viewW, viewH)) {
            drawPlain()
            return
        }
        val displayScale = minOf(
            viewW.toFloat() / frameW,
            viewH.toFloat() / frameH,
        )
        val params = EdgeSmoothing.edgeParamsFor(edgeSystem)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(edgeProgram)
        GLES20.glUniform1f(uniform(edgeProgram, "uFlipV"), 0f)
        GLES20.glUniform1i(uniform(edgeProgram, "uTex"), 0)
        GLES20.glUniform2f(
            uniform(edgeProgram, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1f(uniform(edgeProgram, "uDitherTol"), params.ditherTol)
        GLES20.glUniform1f(uniform(edgeProgram, "uEdgeLo"), params.edgeLo)
        GLES20.glUniform1f(uniform(edgeProgram, "uEdgeHi"), params.edgeHi)
        GLES20.glUniform1f(uniform(edgeProgram, "uBlendMax"), params.blendMax)
        GLES20.glUniform1f(
            uniform(edgeProgram, "uSubpixel"),
            if (params.subpixel) 1f else 0f,
        )
        GLES20.glUniform1f(
            uniform(edgeProgram, "uSnap15"),
            if (params.snap15) 1f else 0f,
        )
        GLES20.glUniform1f(uniform(edgeProgram, "uTextAlign"), params.textAlign)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
        drawQuad(edgeProgram)
        if (edgeMode == EdgeMode.EDGE_GRID && gridProgram != 0) {
            // Pass 2 — the light grid over the smoothed
            // frame. The source is the pass-1 framebuffer,
            // so V flips (uFlipV 1), as in the chain's grid
            // pass; the grid mode follows the system (dot
            // for GB, TFT lines for GBC / GBA). A grid
            // program that failed to compile falls back to
            // the plain blit — the smoothed frame still
            // shows, just without the grid.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewW, viewH)
            GLES20.glUseProgram(gridProgram)
            GLES20.glUniform1f(uniform(gridProgram, "uFlipV"), 1f)
            GLES20.glUniform1i(uniform(gridProgram, "uTex"), 0)
            GLES20.glUniform2f(
                uniform(gridProgram, "uTexSize"),
                frameW.toFloat(),
                frameH.toFloat(),
            )
            GLES20.glUniform1i(
                uniform(gridProgram, "uGridMode"),
                if (edgeSystem == GameSystem.GB) 0 else 1,
            )
            GLES20.glUniform1f(
                uniform(gridProgram, "uGridStrength"),
                DisplayShaders.gridStrengthForScale(displayScale),
            )
            GLES20.glUniform1f(uniform(gridProgram, "uBrightness"), optics.brightness)
            GLES20.glUniform1f(uniform(gridProgram, "uContrast"), optics.contrast)
            GLES20.glUniform1f(uniform(gridProgram, "uGamma"), optics.gamma)
            setWormLightUniforms(gridProgram)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
            drawQuad(gridProgram)
        } else {
            drawPlainFromFbo()
        }
    }

    /** The chain off: the frame exactly as the core rendered it, straight to the screen. */
    private fun drawPlain() {
        drawPlainSource(frameTextureId, 0f, 0f)
    }

    /**
     * Phase 9 — the edge pass's blit: the pass-1
     * framebuffer to the screen. The source is the FBO, so
     * V flips (uFlipV 1) and the Worm Light's pool centre
     * flips with it (uWlFlip 1) — the same flip the grid
     * pass does inline.
     */
    private fun drawPlainFromFbo() {
        drawPlainSource(fboTextureId, 1f, 1f)
    }

    private fun drawPlainSource(textureId: Int, flipV: Float, wlFlip: Float) {
        if (plainProgram == 0) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(plainProgram)
        GLES20.glUniform1f(uniform(plainProgram, "uFlipV"), flipV)
        GLES20.glUniform1i(uniform(plainProgram, "uTex"), 0)
        GLES20.glUniform1f(uniform(plainProgram, "uWlFlip"), wlFlip)
        // Worm Light realism: the pool rides on the plain
        // program too, so the lamp works with the chain off.
        setWormLightUniforms(plainProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        drawQuad(plainProgram)
    }

    /**
     * The offscreen framebuffer at the frame's native size,
     * rebuilt only when the size changes. False when the
     * framebuffer is incomplete — the caller falls back to
     * plain instead of drawing into it.
     */
    private fun ensureFbo(w: Int, h: Int): Boolean {
        if (w == fboW && h == fboH) return true
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, fboTextureId, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer incomplete: $status; the chain falls back to plain")
            fboW = 0
            fboH = 0
            return false
        }
        fboW = w
        fboH = h
        return true
    }

    /**
     * Upload the gamma LUT (DisplayShaders.gammaLutPixels)
     * as a 32x2 RGBA texture: row 0 is the GBC ramp, row 1
     * the GBA ramp, each entry a grey value. The bytes are
     * written R, G, B, A in order, so the upload is exact
     * regardless of the device's byte order.
     */
    private fun uploadGammaLut(id: Int) {
        val pixels = DisplayShaders.gammaLutPixels()
        val buf = ByteBuffer
            .allocateDirect(pixels.size * 4)
            .order(ByteOrder.nativeOrder())
        for (v in pixels) {
            buf.put(v.toByte())
            buf.put(v.toByte())
            buf.put(v.toByte())
            buf.put(0xFF.toByte())
        }
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 32, 2, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
        )
    }

    private fun setupTexture(id: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
    }

    /**
     * The fullscreen quad, triangle strip: positions in clip
     * space, texcoords in Bitmap convention (t = 0 is the top
     * row — see DisplayShaders.VERTEX). Built once; the same
     * quad serves every program.
     */
    private fun buildQuad(): Int {
        val verts = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )
        val buf: FloatBuffer = ByteBuffer
            .allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(verts); position(0) }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        val vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            verts.size * 4,
            buf,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return vbo
    }

    private fun drawQuad(prog: Int) {
        if (quadVbo == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        val aTex = GLES20.glGetAttribLocation(prog, "aTex")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, 8)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun uniform(prog: Int, name: String): Int {
        val key = "$prog:$name"
        return uniformLocs.getOrPut(key) { GLES20.glGetUniformLocation(prog, name) }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc) ?: return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            ?: run {
                GLES20.glDeleteShader(vs)
                return 0
            }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (ok[0] == 0) {
            Log.e(TAG, "program link failed: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, src: String): Int? {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    companion object {
        private const val TAG = "PoingDisplay"

        /** Screen Optics: a smaller move than this is sensor chatter, not a new picture. */
        private const val OPTICS_EPSILON = 0.01f

        /** Worm Light: a smaller pool move than this is a no-op report, not a new picture. */
        private const val WL_EPSILON = 0.005f
    }
}
