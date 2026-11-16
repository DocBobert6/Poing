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

    /**
     * The edge-fallback bug (Robert, 2026-11-14, Golden
     * Sun GBA): the dev-stats line read "plain (edge fell
     * back)" with edge smoothing ON. Perf fix 9 made the
     * programs lazy, but the edge branch built only the
     * edge program — drawEdge's pass 0 needs the colour
     * program too, and nothing in the edge branch built
     * it, so in a fresh EGL context (no chain ever run)
     * every frame fell back to plain. This pins the
     * contract end to end: a fresh renderer with edge on
     * draws EDGE on its first frame. Robolectric's GLES20
     * stubs succeed (programs build, framebuffers report
     * complete, textures get ids), so the path runs here
     * exactly as the lazy-contract test documents.
     */
    @Test
    fun edgeBranchBuildsColourProgramInAFreshContext() {
        val renderer = DisplayRenderer()
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 800, 600)
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.EDGE,
            GameSystem.GBA,
        )
        renderer.setFrame(Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888))
        renderer.onDrawFrame(null)
        assertEquals(DrawPath.EDGE, renderer.lastDrawPath)
    }

    /**
     * Black screen on launch (Robert, 2026-11-15, still
     * happening on alpha.9): the cold-launch order is
     * setFrame BEFORE the GL thread exists — the first
     * frame's requestRender is dropped, and the dirty
     * check then blocks every later frame because
     * frameConsumed stays false. Surface creation must
     * notify the view when a frame is already pending, so
     * the render request lands now that the thread exists
     * and the next draw consumes the frame with no input.
     * This pins the new onSurfaceReady contract: on the
     * old code the property does not exist, so this test
     * does not compile — that is the failure. Robolectric's
     * GLES20 stubs succeed, so the surface/draw path runs
     * here (see the edge-fallback test above).
     */
    @Test
    fun frameSetBeforeSurfaceCreationDrawsOnceTheSurfaceExists() {
        val renderer = DisplayRenderer()
        var readyCalls = 0
        renderer.onSurfaceReady = { readyCalls++ }
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GB,
        )
        // The cold-launch order: the first frame arrives
        // before the GL thread exists.
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        assertFalse(renderer.frameConsumed)
        // Surface creation notifies the view that a frame
        // is pending; the view's requestRender (and the
        // session's replay) deliver it, and the next draw
        // consumes it — the deadlock is broken.
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, readyCalls)
        renderer.onSurfaceChanged(null, 800, 600)
        renderer.onDrawFrame(null)
        assertTrue(renderer.frameConsumed)
        assertEquals(DrawPath.PLAIN, renderer.lastDrawPath)
    }

    /**
     * Black screen on launch (Robert, 2026-11-16, still
     * happening on alpha.16): the alpha.10 fix notified
     * only while a frame was pending — and the launch
     * rotation destroys and recreates the surface AFTER
     * the pending frame was already consumed, so the
     * recreation found nothing pending and nothing drew.
     * On a static screen no new frame ever arrives, so
     * the screen stayed black until the picture changed
     * (a button press advances the game; a tap on empty
     * space does not). The notify is unconditional now:
     * every surface creation asks the session to
     * re-deliver its latest frame, because
     * onSurfaceCreated resets every texture and handle —
     * after it the renderer holds no valid picture at
     * all, pending frame or not. The replay is null-safe
     * before the first frame, so a creation with nothing
     * to re-deliver is a no-op. This pins the new
     * contract: a creation with no pending frame still
     * notifies exactly once.
     */
    @Test
    fun surfaceCreatedNotifiesReadyEvenWithNoPendingFrame() {
        val renderer = DisplayRenderer()
        var readyCalls = 0
        renderer.onSurfaceReady = { readyCalls++ }
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, readyCalls)
    }

    /**
     * Alpha.28 — the black-screen trace: surface creation
     * records with the dirty-check state the creation found
     * (the alpha.20 hole was a recreation finding nothing
     * pending), surface change records the new geometry,
     * and the first draw after a creation records the frame
     * that finally draws — once, not per frame. The hook is
     * recording-only: with no listener set, the surface
     * lifecycle is unchanged (the tests above already run
     * it with no listener).
     */
    @Test
    fun surfaceCreatedRecordsTraceWithDirtyCheckState() {
        val renderer = DisplayRenderer()
        val seen = mutableListOf<Pair<String, String>>()
        renderer.onTrace = { label, detail -> seen.add(label to detail) }
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, seen.size)
        assertEquals("surface created", seen[0].first)
        assertTrue(seen[0].second.contains("creation #1"))
        assertTrue(seen[0].second.contains("pending frame: no"))
        assertTrue(seen[0].second.contains("frameConsumed: true"))
    }

    @Test
    fun surfaceCreatedWithPendingFrameRecordsIt() {
        val renderer = DisplayRenderer()
        val seen = mutableListOf<Pair<String, String>>()
        renderer.onTrace = { label, detail -> seen.add(label to detail) }
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, seen.size)
        assertEquals("surface created", seen[0].first)
        assertTrue(seen[0].second.contains("pending frame: yes"))
        assertTrue(seen[0].second.contains("frameConsumed: false"))
    }

    @Test
    fun surfaceChangedRecordsNewGeometry() {
        val renderer = DisplayRenderer()
        val seen = mutableListOf<Pair<String, String>>()
        renderer.onTrace = { label, detail -> seen.add(label to detail) }
        renderer.onSurfaceChanged(null, 800, 600)
        assertEquals(listOf("surface changed" to "800x600"), seen)
    }

    @Test
    fun firstDrawAfterSurfaceCreationRecordsOnce() {
        val renderer = DisplayRenderer()
        val seen = mutableListOf<Pair<String, String>>()
        renderer.onTrace = { label, detail -> seen.add(label to detail) }
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GB,
        )
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 800, 600)
        renderer.onDrawFrame(null)
        assertEquals(
            listOf("surface created", "surface changed", "frame drawn"),
            seen.map { it.first },
        )
        assertTrue(seen[2].second.contains("first draw after surface #1"))
        // A second frame draws without recording again.
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onDrawFrame(null)
        assertEquals(3, seen.size)
    }

    @Test
    fun recreationRecordsFirstDrawAgain() {
        // The alpha.20 rotation case, traced: the recreation
        // records its own creation, and the replayed frame's
        // first draw after it records again.
        val renderer = DisplayRenderer()
        val seen = mutableListOf<String>()
        renderer.onTrace = { label, _ -> seen.add(label) }
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GB,
        )
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 800, 600)
        renderer.onDrawFrame(null)
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, 800, 600)
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onDrawFrame(null)
        assertEquals(
            listOf(
                "surface created", "surface changed", "frame drawn",
                "surface created", "surface changed", "frame drawn",
            ),
            seen,
        )
    }

    /**
     * The rotation case, end to end: the first creation
     * notifies and the first draw consumes the pending
     * frame; the window then rotates, the surface is
     * destroyed and recreated, and the recreation must
     * notify AGAIN even though nothing is pending — the
     * picture was lost with the old surface, and on a
     * static screen no new frame will arrive to draw it.
     * Fails on the old code: the pending-only check
     * leaves readyCalls at 1 after the recreation.
     */
    @Test
    fun surfaceRecreationAfterFrameConsumedNotifiesReadyAgain() {
        val renderer = DisplayRenderer()
        var readyCalls = 0
        renderer.onSurfaceReady = { readyCalls++ }
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GB,
        )
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, readyCalls)
        // Alpha.29 — the change notifies too now (the creation
        // is immediately followed by its own change), so the
        // count is 2 before the draw, not 1.
        renderer.onSurfaceChanged(null, 800, 600)
        assertEquals(2, readyCalls)
        renderer.onDrawFrame(null)
        assertTrue(renderer.frameConsumed)
        assertEquals(DrawPath.PLAIN, renderer.lastDrawPath)
        // The window rotates: surface destroyed, recreated.
        renderer.onSurfaceCreated(null, null)
        assertEquals(3, readyCalls)
    }

    /**
     * Black screen, second cause (Robert, 2026-11-16: still
     * black until a button press, DW 3 trace): the frame drew
     * after the launch surface was created, but the surface
     * then changed size (the top bar appearing, 1200x1080
     * to 838x754) — the EGL surface is recreated on a size
     * change and the presented buffer is lost with the old
     * one. The view renders on demand, so on a static screen
     * nothing re-renders into the new surface and it stays
     * black until the picture changes. A size change must
     * notify like a creation: the session re-delivers its
     * latest frame and it draws into the new surface with no
     * input. Fails on the old code: the change notifies
     * nothing, so readyCalls stays 2 after the second change.
     */
    @Test
    fun surfaceChangedAfterFrameConsumedNotifiesReadyAgain() {
        val renderer = DisplayRenderer()
        var readyCalls = 0
        renderer.onSurfaceReady = { readyCalls++ }
        renderer.setDisplay(
            DisplayChain.CLEAN,
            JadeMode.FSTN,
            EdgeMode.OFF,
            GameSystem.GB,
        )
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onSurfaceCreated(null, null)
        assertEquals(1, readyCalls)
        renderer.onSurfaceChanged(null, 1200, 1080)
        assertEquals(2, readyCalls)
        renderer.onDrawFrame(null)
        assertTrue(renderer.frameConsumed)
        assertEquals(DrawPath.PLAIN, renderer.lastDrawPath)
        // The top bar appears: the surface changes size and
        // the presented buffer is lost with the old surface.
        // The change notifies again, so the replayed frame
        // draws into the new surface with no input.
        renderer.onSurfaceChanged(null, 838, 754)
        assertEquals(3, readyCalls)
        // The session's replay re-delivers the latest frame;
        // it draws into the new surface.
        renderer.setFrame(Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888))
        renderer.onDrawFrame(null)
        assertTrue(renderer.frameConsumed)
        assertEquals(DrawPath.PLAIN, renderer.lastDrawPath)
    }
}
