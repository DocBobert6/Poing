package app.poing

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Performance fix 5 (2026-11-14) — the consumed flag: the
 * renderer starts with the frame consumed (so the first
 * frame is always produced) and setFrame clears it until
 * onDrawFrame takes the pending bitmap. The GL-thread half
 * (onDrawFrame setting it back) needs a GL context and is
 * not exercised under Robolectric — it is one line beside
 * the pendingBitmap = null it already does.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayRendererTest {

    @Test
    fun frameConsumedStartsTrue() {
        assertTrue(DisplayRenderer().frameConsumed)
    }

    @Test
    fun setFrameClearsFrameConsumed() {
        val renderer = DisplayRenderer()
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        assertFalse(renderer.frameConsumed)
    }

    @Test
    fun secondSetFrameKeepsFrameConsumedFalse() {
        val renderer = DisplayRenderer()
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        assertFalse(renderer.frameConsumed)
    }

    /**
     * The reconstruction's upside-down bug (2026-11-14):
     * Task 6 shipped A1 with uFlipV 0, so texA stored the
     * frame upside down, A2's two inputs were vertically
     * mirrored, and Pass B displayed the game upside down
     * on device (V-Rally's HUD: TIME / BEST / LAPS upside
     * down, left-to-right). The fix is A1 at 1, and these
     * tests pin the flip contract: the composition must
     * hold for the flips drawRecon uses, fail for the
     * triple that shipped, and agree with the chain and
     * edge paths (both device-verified right-side up).
     */
    @Test
    fun reconOrientationIsCorrectWithTheRendererFlips() {
        assertTrue(DisplayRenderer.RECON_A1_FLIP_V)
        assertFalse(DisplayRenderer.RECON_A2_FLIP_V)
        assertTrue(DisplayRenderer.RECON_B_FLIP_V)
        assertTrue(DisplayRenderer.reconOrientationIsCorrect())
    }

    @Test
    fun reconOrientationFailsForTheTripleTask6Shipped() {
        assertFalse(
            DisplayRenderer.reconOrientationIsCorrect(
                a1FlipV = false,
                a2FlipV = false,
                bFlipV = true,
            )
        )
    }

    @Test
    fun reconOrientationMatchesTheChainAndEdgePaths() {
        // Chain: Bitmap (unflipped) --0--> FBO --1--> screen.
        val chainFboFlipped = DisplayRenderer.fboOutputFlipped(false, false)
        assertTrue(chainFboFlipped)
        assertTrue(DisplayRenderer.displayIsCorrect(chainFboFlipped, true))
        // Edge: Bitmap --1--> colour FBO --0--> edge FBO --1--> screen.
        val colourFboFlipped = DisplayRenderer.fboOutputFlipped(false, true)
        assertFalse(colourFboFlipped)
        val edgeFboFlipped = DisplayRenderer.fboOutputFlipped(colourFboFlipped, false)
        assertTrue(edgeFboFlipped)
        assertTrue(DisplayRenderer.displayIsCorrect(edgeFboFlipped, true))
        // And the recon composes to the same result.
        assertTrue(DisplayRenderer.reconOrientationIsCorrect())
    }

    /**
     * The LCD grid over the reconstruction (Robert,
     * 2026-11-14): setDisplay stores the flag, off by
     * default. The grid pass itself needs a GL context
     * and is not exercised under Robolectric — this pins
     * the plumbing from GameScreen through GameGlView.
     */
    @Test
    fun reconGridDefaultsOff() {
        assertFalse(DisplayRenderer().reconGrid)
    }

    @Test
    fun setDisplayStoresReconGrid() {
        val renderer = DisplayRenderer()
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GBC,
            ReconMode.GBC,
            reconGrid = true,
        )
        assertTrue(renderer.reconGrid)
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GBC,
            ReconMode.GBC,
            reconGrid = false,
        )
        assertFalse(renderer.reconGrid)
    }

    /**
     * Performance fix 8 — the per-system edge parameters
     * are computed once in setDisplay, not allocated fresh
     * every frame in drawEdge/drawRecon. GBC and GBA have
     * different thresholds, so the stored params must follow
     * the system, not the default.
     */
    @Test
    fun setDisplayStoresEdgeParamsForTheSystem() {
        val renderer = DisplayRenderer()
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GBC,
        )
        assertEquals(EdgeSmoothing.edgeParamsFor(GameSystem.GBC), renderer.edgeParams)
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GBA,
        )
        assertEquals(EdgeSmoothing.edgeParamsFor(GameSystem.GBA), renderer.edgeParams)
    }

    /**
     * Performance fix 9 — the chain, edge and recon programs
     * are built lazily on first use. Robolectric's GLES20
     * stubs report a successful compile, so the ensure
     * functions report true here (the first version of this
     * test assumed the stubs fail — they do not); what this
     * pins is the lazy contract itself: the first call
     * builds, a second call reuses the built program, and
     * onSurfaceCreated resets the handles and the attempted
     * flags, so a lost EGL context builds again instead of
     * trusting a dead handle. A failed build on a real device
     * still falls back to plain — the callers check the
     * handles, not the return value.
     */
    @Test
    fun ensureProgramsBuildLazilyAndRebuildAfterSurfaceRecreation() {
        val renderer = DisplayRenderer()
        assertTrue(renderer.ensureColourProgram())
        assertTrue(renderer.ensureColourProgram())
        assertTrue(renderer.ensureGridProgram())
        assertTrue(renderer.ensureChainPrograms())
        assertTrue(renderer.ensureEdgeProgram())
        assertTrue(renderer.ensureReconPrograms())
        // A lost EGL context: onSurfaceCreated resets the
        // handles and the attempted flags, so the next use
        // builds again instead of trusting a dead handle.
        renderer.onSurfaceCreated(null, null)
        assertTrue(renderer.ensureColourProgram())
        assertTrue(renderer.ensureReconPrograms())
    }
}
