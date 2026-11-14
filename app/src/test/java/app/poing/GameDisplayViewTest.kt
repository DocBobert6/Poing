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
import androidx.compose.ui.test.onAllNodesWithText
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
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
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
     * the fill test above — and against the row, not the slot:
     * in the narrow Robolectric window the game is
     * width-limited, so the frame is vertically CENTRED in
     * its row (top-aligned only when the height binds, as on
     * the phone in landscape). The aspect invariant pins that
     * the frame keeps its ratio whatever binds.
     */
    @Test
    fun gameGivesUpATopStripWhileTheLampIsOn() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
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
        // The frame is the largest 3:2 box in the row below
        // the strip, centred in that row.
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxH, game.height, 1f * d)
        assertEquals(slot.top + stripPx + (rowH - boxH) / 2f, game.top, 1f * d)
        assertEquals(slot.left + (slot.width - boxW) / 2f, game.left, 1f * d)
        // The frame keeps its native aspect whatever binds.
        assertEquals(game.width, game.height * 1.5f, 1f * d)
        // The lamp itself is there, spanning the player area.
        composeRule.onNodeWithTag("worm-light").assertExists()
    }

    @Test
    fun gameTakesTheFullHeightWhenTheLampIsOff() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
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
        // No strip: the frame is the largest 3:2 box in the
        // slot, centred in it — exactly as the fill test
        // pins, with the centring made explicit.
        val boxW = minOf(slot.width, slot.height * 1.5f)
        val boxH = boxW / 1.5f
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxH, game.height, 1f * d)
        assertEquals(slot.top + (slot.height - boxH) / 2f, game.top, 1f * d)
        assertEquals(slot.left + (slot.width - boxW) / 2f, game.left, 1f * d)
        assertEquals(game.width, game.height * 1.5f, 1f * d)
        composeRule.onAllNodesWithTag("worm-light").assertCountEquals(0)
    }

    /**
     * Black screen on launch (Robert, 2026-11-14): the view
     * used to compose only once the once-a-second stats had
     * arrived, so the game launched black with audio for ~1s
     * behind a small "Loading…" line. The view now composes
     * on the first frame alone — no stats anywhere in this
     * test, and the game image is displayed, not the loading
     * line. (With the stats parameter gone, stats arrival
     * cannot change the composition at all — there is nothing
     * left for it to gate.)
     */
    @Test
    fun gameShowsWithFrameBeforeStatsArrive() {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
                        gameName = "TEST",
                        paused = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Game: TEST").assertIsDisplayed()
        composeRule.onAllNodesWithText("Loading", substring = true).assertCountEquals(0)
    }

    /**
     * The aspect now comes from the frame Bitmap itself (it
     * used to come from the stats): a 160x144 frame fills its
     * slot at 10:9, whatever the stats would have said.
     */
    @Test
    fun gameAspectComesFromTheFrameBitmap() {
        val bitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp).testTag("slot")) {
                    GameDisplayView(
                        glView = null,
                        bitmap = bitmap,
                        gameName = "TEST",
                        paused = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val d = composeRule.density.density
        val slot = composeRule.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        val game = composeRule.onNodeWithContentDescription("Game: TEST").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithContentDescription("Game: TEST").assertIsDisplayed()
        val boxW = minOf(slot.width, slot.height * 10f / 9f)
        val boxH = boxW * 9f / 10f
        assertEquals(boxW, game.width, 1f * d)
        assertEquals(boxH, game.height, 1f * d)
        assertEquals(game.width, game.height * 10f / 9f, 1f * d)
    }

    /**
     * Top bar / back swipe (Robert, 2026-11-12): the tap
     * hook is gone — a tap on the game area does nothing to
     * the bar, which is only reachable by a back swipe (see
     * GameScreenLayoutTest.backSwipeShowsTheBar). This view
     * takes no tap callback at all, so there is nothing to
     * pin here; the no-peek behaviour is pinned there, at
     * the player level.
     */
}
