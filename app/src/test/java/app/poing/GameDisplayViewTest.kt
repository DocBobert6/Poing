package app.poing

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen-size fix (2026-11-12): the game view must fill its slot
 * at the native aspect. The aspect box is the largest 3:2 box in
 * the slot; the game surface inside it is the box minus the bezel's
 * 4dp inner padding per side (the 2dp silver border is drawn inside
 * the box edge and takes no layout space) — the 8dp transparent
 * margin that used to sit outside the bezel was invisible dead
 * space over the slot's black, and it shrank the surface by 16dp
 * in each dimension for nothing.
 *
 * The assertions are relative to the measured slot, not to assumed
 * numbers: the slot is tagged and measured first, then the box is
 * the largest 3:2 box in that slot and the surface is the box minus
 * 8dp. The session is never started (no native code under
 * Robolectric), so the GL view is null and the plain Compose Image
 * draws the frame — the same inner box sizes both paths.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameDisplayViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gameViewFillsItsSlotAtNativeAspect() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        val stats = PlayerStats(
            width = 240,
            height = 160,
            coreFps = 59.7,
            measuredFps = 59.0,
            framesDrawn = 165,
            sampleRate = 48000,
        )
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
                        stats = stats,
                        gameName = "TEST",
                        paused = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val d = composeRule.density.density
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val slot = composeRule.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        val game = composeRule.onNodeWithContentDescription("Game: TEST").fetchSemanticsNode().boundsInRoot
        println("DISPLAY slot=${slot.width}x${slot.height} game=${game.width}x${game.height} root=${root.width}x${root.height} density=$d")
        composeRule.onNodeWithContentDescription("Game: TEST").assertIsDisplayed()
        // The box is the largest 3:2 box in the measured slot; the
        // surface is the box minus the 4dp inner padding per side
        // (8dp). Asserted against the measured slot, not assumed
        // numbers: the test window is smaller than the 300x200dp
        // box this test requests (the slot assert below used to
        // assume it), so the slot's own size is the reference.
        val boxW = minOf(slot.width, slot.height * 1.5f)
        val boxH = boxW / 1.5f
        assertEquals(boxW - 8f * d, game.width, 1f * d)
        assertEquals(boxH - 8f * d, game.height, 1f * d)
    }
}
