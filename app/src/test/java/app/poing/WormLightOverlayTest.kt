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
    // density is not 1 either. Every touch point below is
    // anchored to the scope's own `center` (the node's centre
    // in the scope's own coordinates, whatever they are) plus
    // a delta from the node's measured size — never to an
    // absolute point, which would land off the head if the
    // node is offset in the root. The default head sits at
    // (0.5, 0.06) of the node, i.e. centre + (0, -0.44h).
    // fetchSemanticsNode is a member of
    // SemanticsNodeInteraction, not a top-level import.
    private fun wormLightSize(): Pair<Float, Float> {
        val bounds = composeRule.onNodeWithTag("worm-light")
            .fetchSemanticsNode().boundsInRoot
        return bounds.width to bounds.height
    }

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
        // (This test is the regression test for the tap
        // itself: detectDragGestures never reports a tap at
        // all — all its callbacks sit inside the
        // touch-slop-passed branch — so the overlay writes
        // its own gesture loop and the toggle lives in the
        // tap branch, where awaitTouchSlopOrCancellation
        // returns null. Five rounds of test-only fixes all
        // failed on the same probe before that was read in
        // the detector's source.) Two probes separate the
        // failure modes: sawAnyHead proves the swipe produced
        // a drag at all (its down must land inside the node
        // — so the swipe starts ON the head, not beside it,
        // where a small measured node could put the start
        // outside); sawAimHead proves the tap toggled aim
        // mode. The rotation tolerance is 0.15, not exact:
        // the swipe's final move lands within a few px of
        // straight down, and near straight down a few px
        // sideways swings the angle — the exact angle is
        // pinned by the pure geometry test instead
        // (aimAngleSetsTheLensAndThePool).
        var reported: WormLightPool? = null
        var sawAnyHead = false
        var sawAimHead = false
        // Every onDragHeadChange call, null or not: the
        // tap branch calls it with null, so a count
        // of 1 right after the tap proves the tap's gesture
        // completed (and the toggle ran); 0 proves the tap
        // never completed in the detector at all.
        var dragHeadCalls = 0
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onPoolChange = { reported = it },
                        onDragHeadChange = {
                            dragHeadCalls++
                            if (it != null) sawAnyHead = true
                            if (it?.aimMode == true) sawAimHead = true
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val size = wormLightSize()
        // Tap the head to enter aim mode. The point is
        // anchored to the scope's own centre: the default
        // head sits at (0.5, 0.06) of the node, i.e. centre
        // + (0, -0.44h) — so the tap lands on the head
        // whatever the node's root offset or measured size
        // (centre is node-local per the API docs). A plain
        // click(): the overlay's own gesture loop reports
        // the tap, where detectDragGestures never did.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(center + Offset(0f, -0.44f * size.second))
        }
        composeRule.waitForIdle()
        assertTrue(
            "the tap should have completed (the tap branch reports null)",
            dragHeadCalls >= 1,
        )
        // Drag straight down from the head: in aim mode this
        // rotates the pool toward straight down (~1.57 rad)
        // while the head stays put. Starting on the head
        // keeps the down inside the node however small it
        // measures; in aim mode the press may start anywhere.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            val head = center + Offset(0f, -0.44f * size.second)
            swipe(head, head + Offset(0f, 90f))
        }
        composeRule.waitForIdle()
        assertTrue("the swipe should have produced a drag", sawAnyHead)
        assertTrue("the tap should have toggled aim mode", sawAimHead)
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(1.57f, pool.rotationRad, 0.15f)
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
        val size = wormLightSize()
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            val head = center + Offset(0f, -0.44f * size.second)
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
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(center + Offset(0f, 0.45f * size.second))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            val head = center + Offset(0f, -0.44f * size.second)
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
