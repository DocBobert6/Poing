package app.poing

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen-size fix (2026-11-12) + no-bezel follow-up (Robert,
 * 2026-11-12) + layout rework (Robert, 2026-11-12): the game
 * view must fill its slot at the native aspect, edge-to-edge,
 * with square corners (a Game Boy screen is a rectangle — the
 * light 4dp rounding is gone too). The aspect box is the
 * largest 3:2 box in the slot, and the game surface IS the box
 * — the silver border, dark surround and 4dp inner padding are
 * gone, so nothing sits between the frame and the box edge
 * (the 2dp border used to be drawn inside the edge and the 4dp
 * gap shrank the surface by 8dp; the 8dp transparent margin
 * outside the box was dropped by the screen-size fix for the
 * same reason).
 *
 * The assertions are relative to the measured slot, not to assumed
 * numbers: the slot is tagged and measured first, then the box is
 * the largest 3:2 box in that slot and the surface is the box.
 * The session is never started (no native code under
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
        // The box is the largest 3:2 box in the measured slot, and
        // the surface is the box — no bezel, no inner padding.
        // Asserted against the measured slot, not assumed numbers:
        // the test window is smaller than the 300x200dp box this
        // test requests, so the slot's own size is the reference.
        val boxW = minOf(slot.width, slot.height * 1.5f)
        val boxH = boxW / 1.5f
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxH, game.height, 1f * d)
    }

    /**
     * Worm Light realism (Robert, 2026-11-12): while the lamp
     * is on, the game gives up a strip at the top — the lamp's
     * space, where the head hangs above the picture. The frame
     * stays the largest 3:2 box in the row BELOW the strip
     * (never stretched), and the lamp overlay spans the whole
     * player area. Asserted against the measured slot, like
     * the fill test above.
     */
    @Test
    fun gameGivesUpATopStripWhileTheLampIsOn() {
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
                        wormLight = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val d = composeRule.density.density
        val slot = composeRule.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        val game = composeRule.onNodeWithContentDescription("Game: TEST").fetchSemanticsNode().boundsInRoot
        val stripPx = WORM_LIGHT_STRIP_DP * d
        val rowH = slot.height - stripPx
        val boxW = minOf(slot.width, rowH * 1.5f)
        val boxH = boxW / 1.5f
        // The frame starts below the strip and is the largest
        // 3:2 box in the row below it.
        assertEquals(slot.top + stripPx, game.top, 1f * d)
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxH, game.height, 1f * d)
        // The lamp itself is there, spanning the player area.
        composeRule.onNodeWithTag("worm-light").assertExists()
    }

    @Test
    fun gameTakesTheFullHeightWhenTheLampIsOff() {
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
                        wormLight = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val d = composeRule.density.density
        val slot = composeRule.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        val game = composeRule.onNodeWithContentDescription("Game: TEST").fetchSemanticsNode().boundsInRoot
        // No strip: the frame starts at the slot's own top and
        // fills it, exactly as the fill test pins.
        assertEquals(slot.top, game.top, 1f * d)
        val boxW = minOf(slot.width, slot.height * 1.5f)
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxW / 1.5f, game.height, 1f * d)
        composeRule.onAllNodesWithTag("worm-light").assertCountEquals(0)
    }

    /**
     * Player chrome (Robert, 2026-11-12): a tap on the game
     * area calls onGameAreaTap — the hook GameScreen wires to
     * the bar peek. The detector sits on the view's outer Box,
     * so the whole player area is the tap target, including
     * the black bars beside the picture.
     */
    @Test
    fun tapOnGameAreaCallsOnGameAreaTap() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        val stats = PlayerStats(
            width = 240,
            height = 160,
            coreFps = 59.7,
            measuredFps = 59.0,
            framesDrawn = 165,
            sampleRate = 48000,
        )
        var taps = 0
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
                        stats = stats,
                        gameName = "TEST",
                        paused = false,
                        onGameAreaTap = { taps++ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("game-area").performClick()
        composeRule.waitForIdle()
        assertTrue("a tap on the game area should call onGameAreaTap", taps == 1)
    }
}
