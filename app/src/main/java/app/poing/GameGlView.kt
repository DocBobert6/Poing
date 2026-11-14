package app.poing

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.AttributeSet

/**
 * Phase 8 — the game view: a GLSurfaceView that presents
 * the frame Bitmap through the display-chain renderer
 * (DisplayRenderer). It replaces the plain Compose Image,
 * which could not run a shader chain. Renders on demand: a
 * new frame or a chain change requests a redraw, and the
 * last frame stays on screen while paused. The EGL context
 * is preserved across pauses so a rotation does not lose
 * the texture (it is re-uploaded on the next frame in any
 * case).
 *
 * The touch controls are Compose content drawn over this
 * view (standard mobile-emulator layout): the surface sits
 * behind the window's view hierarchy, so the overlay draws
 * on top.
 */
class GameGlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private val renderer = DisplayRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        setPreserveEGLContextOnPause(true)
    }

    /** Present a new frame Bitmap (called from PlayerSession's onFrame, on the main thread). */
    fun setFrame(bitmap: Bitmap) {
        renderer.setFrame(bitmap)
        requestRender()
    }

    /**
     * Apply a chain and the edge mode (called at game start,
     * and when the chain or the GB palette changes — the
     * palette is still passed to the chain's Jade mode, but
     * the renderer no longer scans it: the rework deleted
     * the GB palette scan with the palette snap).
     */
    fun setDisplay(
        chain: DisplayChain,
        jadeMode: JadeMode,
        edge: EdgeMode,
        system: GameSystem,
    ) {
        renderer.setDisplay(chain, jadeMode, edge, system)
        requestRender()
    }

    /** Apply the ambient light (called when the lux reading moves the picture — see DisplayRenderer). */
    fun setOptics(params: OpticsParams) {
        if (renderer.setOptics(params)) requestRender()
    }

    /** Apply the Worm Light's pool (called when the head moves — see DisplayRenderer). */
    fun setWormLight(pool: WormLightPool) {
        if (renderer.setWormLight(pool)) requestRender()
    }
}
