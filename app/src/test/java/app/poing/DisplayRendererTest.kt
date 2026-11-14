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
}
