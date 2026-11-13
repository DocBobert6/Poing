package app.poing

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * the slot; the game surface inside it is the box minus the bezel
 * chrome only (2dp silver border + 4dp gap per side = 12dp) — the
 * 8dp transparent margin that used to sit outside the bezel was
 * invisible dead space over the slot's black, and it shrank the
 * surface by 16dp in each dimension for nothing.
 *
 * The session is never started (no native code under Robolectric),
 * so the GL view is null and the plain Compose Image draws the
 * frame — the same inner box sizes both paths.
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
                Box(Modifier.size(300.dp, 200.dp)) {
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
        val node = composeRule.onNodeWithContentDescription("Game: TEST")
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val d = composeRule.density.density
        // Slot 300x200dp: the 3:2 box is height-limited at 300x200dp,
        // and the game surface is the box minus the 12dp of bezel
        // chrome per side: 288x188dp.
        assertEquals(288f * d, bounds.width, 1f * d)
        assertEquals(188f * d, bounds.height, 1f * d)
    }
}
