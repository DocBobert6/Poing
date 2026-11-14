package app.poing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Worm Light rework (Robert, 2026-11-12): the overlay renders
 * when on and is not composed at all when off — so it cannot
 * swallow a touch while off. The lamp/coil geometry itself is
 * pinned by WormLightGeometryTest on the plain JVM.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WormLightOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(enabled: Boolean) {
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(enabled = enabled)
                }
            }
        }
    }

    // Touch tests must not assume the 300x200dp box measures
    // 300x200dp: the Robolectric window is smaller than the
    // requested box (see GameDisplayViewTest), and the
    // density is not 1 either. Every touch point and every
    // expectation below is derived from the node's own
    // measured size (px) at runtime — the default head sits
    // at (0.5, 0.06) of it, whatever it measures.
    // fetchSemanticsNode is a member of
    // SemanticsNodeInteraction, not a top-level import.
    private fun wormLightSize(): Pair<Float, Float> {
        val bounds = composeRule.onNodeWithTag("worm-light")
            .fetchSemanticsNode().boundsInRoot
        return bounds.width to bounds.height
    }

    private fun defaultHead(size: Pair<Float, Float>) =
        Offset(size.first * 0.5f, size.second * 0.06f)

    @Test
    fun overlayPresentWhenEnabled() {
        setContent(enabled = true)
        // assertExists is a member of SemanticsNodeInteraction,
        // not a top-level import (the same trap as the
        // screen-size fix and the control-moves fix).
        composeRule.onNodeWithTag("worm-light").assertExists()
    }

    @Test
    fun overlayAbsentWhenDisabled() {
        setContent(enabled = false)
        composeRule.onAllNodesWithTag("worm-light").assertCountEquals(0)
    }

    @Test
    fun overlayReportsItsPoolToTheGlView() {
        // Worm Light realism: the overlay reports the lamp's
        // pool up to the GL view whenever the head moves, so
        // the shader lights the picture (and dragging while
        // paused still moves the pool). In a 300x200dp box
        // the default head is centred, so the pool is the
        // top-centre of the game at full intensity.
        var reported: WormLightPool? = null
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onPoolChange = { reported = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(1f, pool.intensity, 0.001f)
        assertEquals(0.5f, pool.center.x, 0.01f)
        assertEquals(0.20f, pool.center.y, 0.01f)
        assertEquals(0.55f, pool.radii.x, 0.01f)
        assertEquals(0.42f, pool.radii.y, 0.01f)
        assertEquals(0f, pool.rotationRad, 0.001f)
    }

    @Test
    fun tapOnTheHeadTogglesAimMode() {
        // Tap-to-aim (Robert, 2026-11-13): a tap on the head
        // toggles aim mode, where a drag rotates the head and
        // the pool instead of moving the head. The tap lands
        // on the default head's measured position; the drag
        // ends straight below it, so the pool rotates toward
        // straight down (~1.57 rad) while its centre stays at
        // the top-centre — the head never moves. The angle is
        // a direction, so it holds at any density or window.
        var reported: WormLightPool? = null
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onPoolChange = { reported = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val head = defaultHead(wormLightSize())
        // Tap the head to enter aim mode.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(head)
        }
        composeRule.waitForIdle()
        // Drag down from beside the head: in aim mode the
        // press need not start on the head.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(head + Offset(40f, 0f), head + Offset(0f, 60f))
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(1.57f, pool.rotationRad, 0.05f)
        assertEquals(0.5f, pool.center.x, 0.01f)
        assertEquals(0.20f, pool.center.y, 0.01f)
    }

    @Test
    fun dragHeadIsReportedWhileDragging() {
        // Head visible while dragging (Robert, 2026-11-13):
        // the overlay reports its head while a drag is in
        // progress, so the player can draw it above the
        // controls, and clears it when the drag ends. The
        // swipe is 60px — well past touch slop at any
        // density — from the head's measured position.
        var lastDragHead: WormLightDragHead? = null
        var sawNonNull = false
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onDragHeadChange = {
                            lastDragHead = it
                            if (it != null) sawNonNull = true
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val head = defaultHead(wormLightSize())
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(head, head + Offset(60f, 0f))
        }
        composeRule.waitForIdle()
        assertTrue("the head should be reported during a drag", sawNonNull)
        assertTrue("the head should be cleared when the drag ends", lastDragHead == null)
    }

    @Test
    fun tapElsewhereDoesNotToggleAimMode() {
        // A tap away from the head is not a head tap: aim
        // mode stays off, and a drag after it MOVES the head
        // instead of rotating it. The tap lands at the
        // bottom-centre of the measured node — far below the
        // head in the strip, outside its grab rect at any
        // density. The drag moves the head right by three
        // tenths of the node's width (well past touch slop);
        // the pool centre must follow it right (past 0.6)
        // while the rotation stays a position tilt (under
        // 1.0 rad), far from an aim rotation (1.57). Both
        // bounds hold whatever the slop absorbs and however
        // narrow the game is in the measured box, so no
        // exact position is pinned.
        var reported: WormLightPool? = null
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onPoolChange = { reported = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val size = wormLightSize()
        val head = defaultHead(size)
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(Offset(size.first * 0.5f, size.second * 0.95f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(head, head + Offset(size.first * 0.3f, 0f))
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertTrue("the pool centre should follow the head right", pool.center.x > 0.6f)
        assertTrue(
            "the rotation should be only the position tilt, not an aim rotation",
            abs(pool.rotationRad) < 1.0f,
        )
    }
}
