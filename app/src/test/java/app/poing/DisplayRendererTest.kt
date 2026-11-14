package app.poing

import android.graphics.Bitmap
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
}
