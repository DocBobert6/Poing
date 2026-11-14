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
     * Task 6 (2026-11-14) — DLSS-in-spirit reconstruction:
     * the mode in effect, and the system it reconstructs
     * for. Recon on means the chain and the edge mode are
     * off — the caller resolves that (see
     * EdgeSmoothing.resolve); this renderer only draws
     * what it is told. Task 6 is GBC only; GB / GBA get
     * their modes in Tasks 5 / 7.
     */
    @Volatile
    private var reconMode: ReconMode = ReconMode.OFF

    @Volatile
    private var reconSystem: GameSystem = GameSystem.GBC

    /**
     * The LCD grid over the reconstruction (Robert,
     * 2026-11-14): one toggle for all three systems.
     * Internal, not private, so the wiring test can
     * observe what setDisplay stored — the grid pass
     * itself needs a GL context and is not exercised
     * under Robolectric.
     */
    @Volatile
    internal var reconGrid: Boolean = false

    /**
     * Edge smoothing, item 5 (2026-11-14) — the dev-stats
     * readout: which program actually drew the last frame.
     * Written on the GL thread at each draw decision (the
     * plain blit, the edge pass, the chain passes); read
     * on the main thread when the once-a-second stats
     * arrive (GameGlView.drawPath). The value is the
     * truth about the picture, not the request: edge on
     * with a failed program or a failed framebuffer still
     * records PLAIN, so the diagnostic line can say the
     * edge fell back instead of letting "edge on" in the
     * settings stand for a picture that is silently
     * plain. Starts PLAIN — nothing has drawn yet.
     */
    @Volatile
    var lastDrawPath: DrawPath = DrawPath.PLAIN

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
    private var reconA1Program = 0
    private var reconA2Program = 0
    private var reconBProgram = 0
    private var frameTextureId = 0
    private var fboTextureId = 0
    private var gammaLutTextureId = 0
    private var fboId = 0
    /**
     * Performance fix 7 — the edge pass's own colour
     * framebuffer: the colour-fold runs once at the native
     * size into this FBO, and the edge pass reads it. A
     * separate pair from the chain / edge FBO on purpose,
     * same as the recon path: the edge pass needs both
     * framebuffers live in one frame (colour in, edge out),
     * so it can never share the one the edge output lands
     * in.
     */
    private var edgeColourFboId = 0
    private var edgeColourTextureId = 0
    private var edgeColourW = 0
    private var edgeColourH = 0
    /**
     * Task 6 — the reconstruction's own framebuffer and
     * its two textures (A1 -> texA, A2 -> texB, B reads
     * texB). Separate from the chain / edge FBO on
     * purpose: the recon path attaches its own textures,
     * so it can never leave the shared FBO pointing at
     * the wrong texture for the next chain or edge draw.
     */
    private var reconFboId = 0
    private var reconTexAId = 0
    private var reconTexBId = 0
    private var reconW = 0
    private var reconH = 0
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

    /**
     * Performance fix 5 — backpressure: true once the GL
     * thread has consumed the pending frame. setFrame clears
     * it; onDrawFrame sets it when it actually takes a
     * pendingBitmap. The emu thread reads it through
     * GameGlView to decide whether the next frame is worth
     * copying at all — when the GL thread is behind, frames
     * are dropped, not queued. Volatile: written on the main
     * thread (setFrame) and the GL thread (onDrawFrame),
     * read on the emu thread. Starts true so the first frame
     * is always produced.
     */
    @Volatile
    var frameConsumed: Boolean = true
        private set

    fun setFrame(bitmap: Bitmap) {
        pendingBitmap = bitmap
        frameConsumed = false
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
        recon: ReconMode = ReconMode.OFF,
        reconGrid: Boolean = false,
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
        this.reconMode = recon
        this.reconSystem = system
        this.reconGrid = reconGrid
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
        // Task 6 — the reconstruction programs: A1 (the
        // 3x3 detectors + corrected colour + pattern map),
        // A2 (the 5x5 field + class + collapse), B (the
        // line reconstruction at display size). A failed
        // program falls back to plain, so the game stays
        // visible.
        reconA1Program = buildProgram(DisplayShaders.VERTEX, DisplayShaders.RECON_A1_FRAGMENT)
        if (reconA1Program == 0) {
            Log.e(TAG, "Recon A1 program failed to compile; reconstruction falls back to plain")
        }
        reconA2Program = buildProgram(DisplayShaders.VERTEX, DisplayShaders.RECON_A2_FRAGMENT)
        if (reconA2Program == 0) {
            Log.e(TAG, "Recon A2 program failed to compile; reconstruction falls back to plain")
        }
        reconBProgram = buildProgram(DisplayShaders.VERTEX, DisplayShaders.RECON_B_FRAGMENT)
        if (reconBProgram == 0) {
            Log.e(TAG, "Recon B program failed to compile; reconstruction falls back to plain")
        }
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
        // Performance fix 7 — the edge colour framebuffer
        // (see the field note): created here like the rest,
        // storage allocated on first use at the frame's
        // native size (see ensureEdgeColourFbo).
        val edgeColourFbo = IntArray(1)
        GLES20.glGenFramebuffers(1, edgeColourFbo, 0)
        edgeColourFboId = edgeColourFbo[0]
        val edgeColourTex = IntArray(1)
        GLES20.glGenTextures(1, edgeColourTex, 0)
        edgeColourTextureId = edgeColourTex[0]
        setupTexture(edgeColourTextureId)
        // Task 6 — the reconstruction's own framebuffer
        // and its two ping-pong textures (A1 -> texA,
        // A2 -> texB). Created here (and re-created if the
        // EGL context is ever lost) so the data is never
        // stale; storage is allocated on first use, at the
        // frame's native size (see ensureReconTex).
        val reconFbo = IntArray(1)
        GLES20.glGenFramebuffers(1, reconFbo, 0)
        reconFboId = reconFbo[0]
        val reconTex = IntArray(2)
        GLES20.glGenTextures(2, reconTex, 0)
        reconTexAId = reconTex[0]
        setupTexture(reconTexAId)
        reconTexBId = reconTex[1]
        setupTexture(reconTexBId)
        quadVbo = buildQuad()
        uploadedW = 0
        uploadedH = 0
        fboW = 0
        fboH = 0
        edgeColourW = 0
        edgeColourH = 0
        reconW = 0
        reconH = 0
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
            // Performance fix 5 — the pending frame is now
            // consumed: the emu thread may produce the next one.
            frameConsumed = true
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
        // Task 6 — reconstruction wins over the edge mode
        // and the chain (the caller resolves that); a
        // failed recon program falls back to plain, so the
        // game stays visible.
        if (reconMode != ReconMode.OFF) {
            if (reconA1Program != 0 && reconA2Program != 0 && reconBProgram != 0) {
                drawRecon()
            } else {
                drawPlain()
            }
            return
        }
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
        // Item 5 — the dev-stats readout: the chain drew.
        lastDrawPath = DrawPath.CHAIN
    }

    /** The Worm Light's pool uniforms, shared by the grid and the plain programs. */
    private fun setWormLightUniforms(prog: Int) {
        GLES20.glUniform2f(uniform(prog, "uWlCenter"), wormLight.center.x, wormLight.center.y)
        GLES20.glUniform2f(uniform(prog, "uWlRadii"), wormLight.radii.x, wormLight.radii.y)
        GLES20.glUniform1f(uniform(prog, "uWlRot"), wormLight.rotationRad)
        GLES20.glUniform1f(uniform(prog, "uWlIntensity"), wormLight.intensity)
    }

    /**
     * Task 6 — GBC colour reconstruction, three passes.
     * Pass A1 runs the 3x3 detectors + colour-fold at the
     * native size into texA (RGB the corrected colour, A
     * the pattern map). Pass A2 runs the 5x5 field + class
     * + collapse at the native size into texB (RGB the
     * collapsed / corrected colour, A the class / 4.0).
     * Pass B runs the line reconstruction at the DISPLAY
     * size to the screen — the same reason the edge pass
     * runs at display size (the Phase 9 no-op lesson): at
     * the native size every fragment would sit at a texel
     * centre and the sub-texel line position would be
     * exactly 0. A failed framebuffer falls back to plain,
     * so the game stays visible. The Worm Light's pool
     * rides on Pass B (it draws to the screen); there are
     * no optics here, following the edge precedent.
     */
    private fun drawRecon() {
        if (!ensureReconTex(frameW, frameH)) {
            drawPlain()
            return
        }
        val displayScale = minOf(
            viewW.toFloat() / frameW,
            viewH.toFloat() / frameH,
        )
        val params = EdgeSmoothing.edgeParamsFor(reconSystem)
        // Pass A1 — detectors + colour-fold, native size,
        // into texA. The source is the frame Bitmap, and a
        // Bitmap texture reads true row v — but the pass
        // WRITES into an FBO, whose rows run bottom-first,
        // so the stored texture comes out flipped unless
        // uFlipV is 1 (the same rule as the edge colour
        // pass). Task 6 shipped 0 here and the recon came
        // out upside down on device; see RECON_A1_FLIP_V.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, reconFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, reconTexAId, 0,
        )
        GLES20.glViewport(0, 0, frameW, frameH)
        GLES20.glUseProgram(reconA1Program)
        GLES20.glUniform1f(uniform(reconA1Program, "uFlipV"), if (RECON_A1_FLIP_V) 1f else 0f)
        GLES20.glUniform1i(uniform(reconA1Program, "uTex"), 0)
        GLES20.glUniform2f(
            uniform(reconA1Program, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1f(uniform(reconA1Program, "uDitherTol"), params.ditherTol)
        // GBC is mode 2 (Prism), LUT row 0 — Task 6 is GBC
        // only; GB / GBA get their modes in Tasks 5 / 7.
        // The LUT is already uploaded on unit 1 for the
        // chains, as in the edge pass.
        GLES20.glUniform1i(uniform(reconA1Program, "uMode"), 2)
        if (gammaLutTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gammaLutTextureId)
            GLES20.glUniform1i(uniform(reconA1Program, "uGammaLut"), 1)
            GLES20.glUniform1f(uniform(reconA1Program, "uGammaRow"), 0f)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
        drawQuad(reconA1Program)
        // Pass A2 — field + class + collapse, native size,
        // into texB. Reads texA (unit 0) and the raw frame
        // (unit 1, for the text ring's raw luma). texA is
        // stored unflipped now (A1's uFlipV 1), so the two
        // inputs agree row for row — with A1 at 0 they
        // were vertically mirrored.
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, reconTexBId, 0,
        )
        GLES20.glViewport(0, 0, frameW, frameH)
        GLES20.glUseProgram(reconA2Program)
        GLES20.glUniform1f(uniform(reconA2Program, "uFlipV"), if (RECON_A2_FLIP_V) 1f else 0f)
        GLES20.glUniform1i(uniform(reconA2Program, "uTex"), 0)
        GLES20.glUniform1i(uniform(reconA2Program, "uRawTex"), 1)
        GLES20.glUniform2f(
            uniform(reconA2Program, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1f(uniform(reconA2Program, "uDitherTol"), params.ditherTol)
        GLES20.glUniform1f(uniform(reconA2Program, "uDitherGuardHi"), params.ditherGuardHi)
        GLES20.glUniform1f(uniform(reconA2Program, "uTextAlign"), params.textAlign)
        GLES20.glUniform1f(uniform(reconA2Program, "uEdgeLo"), params.edgeLo)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reconTexAId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
        drawQuad(reconA2Program)
        // Pass B — the line reconstruction, display size,
        // to the screen — or into the shared framebuffer
        // at display size when the LCD grid is on, so the
        // grid pass can layer over it (drawReconGrid
        // below). The shared FBO is free here: the chain
        // and edge paths never run while recon is on, and
        // the edge path already uses it at display size.
        // A failed grid program or framebuffer degrades
        // to the recon without the grid, never to plain —
        // the reconstruction works, only its overlay
        // failed. The source is texB (a framebuffer
        // texture), so V flips (uFlipV 1), as in the
        // chain's grid pass and the edge pass's blit.
        val gridOn = reconGrid && gridProgram != 0 && ensureFbo(viewW, viewH)
        GLES20.glBindFramebuffer(
            GLES20.GL_FRAMEBUFFER,
            if (gridOn) fboId else 0,
        )
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(reconBProgram)
        GLES20.glUniform1f(uniform(reconBProgram, "uFlipV"), if (RECON_B_FLIP_V) 1f else 0f)
        GLES20.glUniform1i(uniform(reconBProgram, "uTex"), 0)
        GLES20.glUniform2f(
            uniform(reconBProgram, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1f(uniform(reconBProgram, "uDisplayScale"), displayScale)
        GLES20.glUniform1f(
            uniform(reconBProgram, "uSnap15"),
            if (params.snap15) 1f else 0f,
        )
        // Worm Light realism: the pool rides on the recon
        // pass too, so the lamp works on the reconstruction
        // — with the FBO flip, like the plain program. No
        // optics here, following the edge precedent.
        GLES20.glUniform1f(uniform(reconBProgram, "uWlFlip"), 1f)
        setWormLightUniforms(reconBProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reconTexBId)
        drawQuad(reconBProgram)
        if (gridOn) drawReconGrid()
        // The dev-stats readout: the reconstruction drew.
        lastDrawPath = DrawPath.RECON_GBC
    }

    /**
     * The LCD grid on top of the reconstruction (Robert,
     * 2026-11-14): GRID_FRAGMENT after Pass B, screen
     * space, so it cannot inherit the flip — the source
     * is the Pass-B framebuffer, so V flips (uFlipV 1),
     * as in the chain's grid pass. The pitch comes from
     * uTexSize (the frame: 160x144 GB/GBC, 240x160 GBA),
     * so it adapts per system with no per-system work;
     * the mode follows the system (TFT lines for GBC /
     * GBA, dot-matrix for GB, as in the chain). The
     * strength is the specified 15%
     * (RECON_GRID_STRENGTH) — a starting value for
     * Robert to judge on device, not a slider. No
     * optics here (recon precedent) and no second Worm
     * Light pool — the pool already rode on Pass B, so
     * the intensity here is forced to 0 (the geometry
     * uniforms still go through setWormLightUniforms,
     * so the radii are valid and the 0 cannot meet a
     * NaN). The grid fragment flips the pool centre's y
     * inline, as it does for the chain — its source is
     * always a framebuffer.
     */
    private fun drawReconGrid() {
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
            if (reconSystem == GameSystem.GB) 0 else 1,
        )
        GLES20.glUniform1f(uniform(gridProgram, "uGridStrength"), DisplayShaders.RECON_GRID_STRENGTH)
        GLES20.glUniform1f(uniform(gridProgram, "uBrightness"), 1f)
        GLES20.glUniform1f(uniform(gridProgram, "uContrast"), 1f)
        GLES20.glUniform1f(uniform(gridProgram, "uGamma"), 1f)
        setWormLightUniforms(gridProgram)
        GLES20.glUniform1f(uniform(gridProgram, "uWlIntensity"), 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        drawQuad(gridProgram)
    }

    /**
     * Task 6 — the reconstruction's two textures at the
     * frame's native size, rebuilt only when the size
     * changes. False when the framebuffer is incomplete —
     * the caller falls back to plain instead of drawing
     * into it.
     */
    private fun ensureReconTex(w: Int, h: Int): Boolean {
        if (w == reconW && h == reconH) return true
        // GL_NEAREST on both: A2 reads A1's class channel (alpha) as a code, and B
        // reads B's class channel the same way — linear filtering would blend the
        // codes across texels and decode the wrong class. A fresh texture's default
        // min filter is NEAREST_MIPMAP_LINEAR, which without mipmaps makes the
        // texture incomplete (samples as black), so the parameters are mandatory.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reconTexAId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, reconTexBId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, reconFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, reconTexAId, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Recon framebuffer incomplete: $status; reconstruction falls back to plain")
            reconW = 0
            reconH = 0
            return false
        }
        reconW = w
        reconH = h
        return true
    }

    /**
     * Phase 9 — edge smoothing, three passes (performance
     * fix 7 added pass 0). Pass 0 runs the colour-fold once
     * at the native size into the edge colour framebuffer —
     * the 9 colourCorrect calls left the edge shader, so the
     * detectors, the dither averages, the gradients and the
     * blends still work in the corrected colours, at one
     * correction per texel instead of one per display
     * fragment. Pass 1 runs the edge shader at the DISPLAY
     * size into the offscreen framebuffer — the fix for the
     * 2026-11-14 no-op (Robert's V-Rally 3 screenshots): at
     * the frame's native size every fragment sat at a texel
     * centre (sub 0.5, 0.5), so the boundary weight wAlong
     * was exactly 0 everywhere and the shader output its
     * input unchanged on any game. At display size each
     * screen pixel lands at a real sub-texel position, so
     * the weight grows toward the texel boundary and the
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
        val params = EdgeSmoothing.edgeParamsFor(edgeSystem)
        // Pass 0 — colour, at the native size, into the edge
        // colour framebuffer (performance fix 7): the fold
        // runs once per texel here, not once per display
        // fragment in the edge shader. Exactly the chain's
        // pass 1, with the mode from the system (GB Jade
        // FSTN 0 / pass-through 1, GBC Prism 2, GBA Dusk 3)
        // and the LUT row from the system. The source is the
        // frame Bitmap — but the pass renders with uFlipV 1:
        // a framebuffer texture sampled with the same t reads
        // vertically flipped, and the edge pass samples this
        // texture AND the raw frame with the one varying, so
        // the colour framebuffer must store the image
        // UNFLIPPED (the same orientation as the raw Bitmap
        // texture) or the edge pass's two inputs would be
        // vertically mirrored. The recon pass's A2 renders
        // its colour pass with uFlipV 0 and lives with
        // exactly that mirror (its two inputs disagree by a
        // vertical flip — flagged to the parent, not fixed
        // here; the edge pass does not repeat it). A failed
        // colour program or framebuffer falls back to plain,
        // so the game stays visible.
        if (colourProgram == 0 || !ensureEdgeColourFbo(frameW, frameH)) {
            drawPlain()
            return
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, edgeColourFboId)
        GLES20.glViewport(0, 0, frameW, frameH)
        GLES20.glUseProgram(colourProgram)
        GLES20.glUniform1f(uniform(colourProgram, "uFlipV"), 1f)
        GLES20.glUniform1i(uniform(colourProgram, "uTex"), 0)
        GLES20.glUniform1i(
            uniform(colourProgram, "uMode"),
            when (edgeSystem) {
                GameSystem.GB -> if (jadeMode == JadeMode.FSTN) 0 else 1
                GameSystem.GBC -> 2
                GameSystem.GBA -> 3
            },
        )
        if (gammaLutTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, gammaLutTextureId)
            GLES20.glUniform1i(uniform(colourProgram, "uGammaLut"), 1)
            GLES20.glUniform1f(
                uniform(colourProgram, "uGammaRow"),
                when (edgeSystem) {
                    GameSystem.GBC -> 0f
                    GameSystem.GBA -> 1f
                    else -> 0f
                },
            )
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextureId)
        drawQuad(colourProgram)
        // Pass 1 — edge, at the display size, into the
        // offscreen framebuffer. The source is the pass-0
        // framebuffer (unit 0: the corrected colours, plain
        // taps) and the raw frame (unit 1: the 5x5 ring, the
        // stroke-gate taps and the centre reference, all raw
        // luma — direction is a property of the source art).
        // V stays unflipped (uFlipV 0): pass 0 stored the
        // corrected image unflipped, so both textures share
        // the Bitmap orientation and one varying serves both.
        if (!ensureFbo(viewW, viewH)) {
            drawPlain()
            return
        }
        val displayScale = minOf(
            viewW.toFloat() / frameW,
            viewH.toFloat() / frameH,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glViewport(0, 0, viewW, viewH)
        GLES20.glUseProgram(edgeProgram)
        GLES20.glUniform1f(uniform(edgeProgram, "uFlipV"), 0f)
        GLES20.glUniform1i(uniform(edgeProgram, "uTex"), 0)
        GLES20.glUniform1i(uniform(edgeProgram, "uRawTex"), 1)
        GLES20.glUniform2f(
            uniform(edgeProgram, "uTexSize"),
            frameW.toFloat(),
            frameH.toFloat(),
        )
        GLES20.glUniform1f(uniform(edgeProgram, "uDitherTol"), params.ditherTol)
        GLES20.glUniform1f(uniform(edgeProgram, "uDitherGuardHi"), params.ditherGuardHi)
        GLES20.glUniform1f(uniform(edgeProgram, "uEdgeLo"), params.edgeLo)
        GLES20.glUniform1f(uniform(edgeProgram, "uEdgeHi"), params.edgeHi)
        GLES20.glUniform1f(uniform(edgeProgram, "uBlendMax"), params.blendMax)
        GLES20.glUniform1f(
            uniform(edgeProgram, "uSnap15"),
            if (params.snap15) 1f else 0f,
        )
        GLES20.glUniform1f(uniform(edgeProgram, "uTextAlign"), params.textAlign)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, edgeColourTextureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
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
            // Item 5 — the dev-stats readout: the edge pass
            // ran, with the light grid over it.
            lastDrawPath = DrawPath.EDGE_GRID
        } else {
            drawPlainFromFbo()
            // Item 5 — the dev-stats readout: the edge pass
            // ran (the plain blit here is only pass 2 — the
            // smoothed frame still shows, just without the
            // grid — so this is EDGE, not a fallback).
            lastDrawPath = DrawPath.EDGE
        }
    }

    /** The chain off: the frame exactly as the core rendered it, straight to the screen. */
    private fun drawPlain() {
        // Item 5 — the dev-stats readout: the plain blit
        // drew. Every fallback in onDrawFrame and drawEdge
        // flows through here (failed edge program, failed
        // framebuffer, chain off or failed), so the record
        // is the truth about the picture, not the request.
        lastDrawPath = DrawPath.PLAIN
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
     * Performance fix 7 — the edge colour framebuffer at
     * the frame's native size, rebuilt only when the size
     * changes. The mirror of ensureFbo for the second pair:
     * the colour pass renders into it, the edge pass reads
     * it. False when the framebuffer is incomplete — the
     * caller falls back to plain instead of drawing into it.
     */
    private fun ensureEdgeColourFbo(w: Int, h: Int): Boolean {
        if (w == edgeColourW && h == edgeColourH) return true
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, edgeColourTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, edgeColourFboId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, edgeColourTextureId, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Edge colour framebuffer incomplete: $status; edge smoothing falls back to plain")
            edgeColourW = 0
            edgeColourH = 0
            return false
        }
        edgeColourW = w
        edgeColourH = h
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

        /**
         * The reconstruction's V-flip contract (the
         * 2026-11-14 upside-down bug). A pass's uFlipV
         * depends on what it READS, not on what it writes:
         * a Bitmap texture stores its top row first, so
         * sampling it at v returns true row v; a
         * framebuffer texture stores its bottom row first,
         * so sampling it at v returns true row (1 - v) of
         * what was written. A pass into an FBO therefore
         * stores its output flipped exactly when
         * (inputFlipped == uFlipV), and a pass to the
         * screen shows it right-side up exactly when
         * (uFlipV == inputFlipped). The chain proves the
         * rule (pass 1 reads the Bitmap with 0, pass 2
         * reads the FBO with 1), and the edge path follows
         * it too (colour pass 1, work pass 0, blit 1).
         * Task 6's recon broke it in exactly one place:
         * A1 read the Bitmap with 0, so texA stored the
         * frame upside down, A2 read texA and the raw
         * frame with one varying (the two inputs
         * vertically mirrored, so the text ring's rows
         * came from the wrong rows), and Pass B displayed
         * the mirrored field upside down on device —
         * V-Rally's HUD read upside down, left-to-right
         * (TIME / BEST / LAPS), a V flip, not a
         * 180-degree rotation: the horizontal axis was
         * never wrong (a vertical flip of the screenshot
         * restores the text exactly; a 180-degree
         * rotation gives mirrored garbage). The three
         * flips below are the single source of truth
         * drawRecon uses, so the unit test pins them.
         */
        internal const val RECON_A1_FLIP_V = true
        internal const val RECON_A2_FLIP_V = false
        internal const val RECON_B_FLIP_V = true

        /**
         * A pass into an FBO stores its output flipped
         * exactly when (inputFlipped == flipV): the
         * Bitmap (unflipped) written with 0 comes out
         * flipped, written with 1 comes out unflipped;
         * an FBO texture written with the same flip it
         * was read with keeps its orientation.
         */
        internal fun fboOutputFlipped(inputFlipped: Boolean, flipV: Boolean): Boolean =
            inputFlipped == flipV

        /**
         * A pass to the screen is right-side up exactly
         * when (flipV == inputFlipped): the Bitmap drawn
         * with 0, an FBO texture drawn with 1.
         */
        internal fun displayIsCorrect(inputFlipped: Boolean, flipV: Boolean): Boolean =
            flipV == inputFlipped

        /**
         * The reconstruction's orientation composition:
         * A2's two inputs (texA and the raw frame) must
         * agree row for row, and Pass B's display must be
         * right-side up. False for the triple Task 6
         * shipped (A1 0, A2 0, B 1) — the upside-down
         * bug — and true for the flips drawRecon uses now.
         */
        internal fun reconOrientationIsCorrect(
            a1FlipV: Boolean = RECON_A1_FLIP_V,
            a2FlipV: Boolean = RECON_A2_FLIP_V,
            bFlipV: Boolean = RECON_B_FLIP_V,
        ): Boolean {
            val rawFlipped = false
            val texAFlipped = fboOutputFlipped(rawFlipped, a1FlipV)
            val inputsAgree = texAFlipped == rawFlipped
            val texBFlipped = fboOutputFlipped(texAFlipped, a2FlipV)
            val displayCorrect = displayIsCorrect(texBFlipped, bFlipV)
            return inputsAgree && displayCorrect
        }
    }
}
