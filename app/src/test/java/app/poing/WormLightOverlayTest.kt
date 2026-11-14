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
        // the pool instead of moving the head. In a 300x200dp
        // box the default head sits at (150, 12).
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
        // Tap the head to enter aim mode.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(Offset(150f, 12f))
        }
        composeRule.waitForIdle()
        // Drag down from beside the head: in aim mode the
        // press need not start on the head, and the pool
        // rotates toward straight down (~1.57 rad) while its
        // centre stays at the top-centre — the head never
        // moves.
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(Offset(190f, 12f), Offset(150f, 52f))
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(1.57f, pool.rotationRad, 0.02f)
        assertEquals(0.5f, pool.center.x, 0.01f)
        assertEquals(0.20f, pool.center.y, 0.01f)
    }

    @Test
    fun dragHeadIsReportedWhileDragging() {
        // Head visible while dragging (Robert, 2026-11-13):
        // the overlay reports its head while a drag is in
        // progress, so the player can draw it above the
        // controls, and clears it when the drag ends.
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
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(Offset(150f, 12f), Offset(200f, 12f))
        }
        composeRule.waitForIdle()
        assertTrue("the head should be reported during a drag", sawNonNull)
        assertTrue("the head should be cleared when the drag ends", lastDragHead == null)
    }

    @Test
    fun tapElsewhereDoesNotToggleAimMode() {
        // A tap away from the head is not a head tap: aim
        // mode stays off, and a drag after it MOVES the head
        // instead of rotating it — the pool centre follows
        // the head (0.5 to 0.73), and the rotation is only
        // the head's small position tilt (-0.14 rad, the
        // head right of centre), not an aim rotation.
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
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            click(Offset(50f, 100f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("worm-light").performTouchInput {
            swipe(Offset(150f, 12f), Offset(200f, 12f))
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(0.73f, pool.center.x, 0.01f)
        assertEquals(-0.14f, pool.rotationRad, 0.02f)
    }
}
