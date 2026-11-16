package app.poing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.10 — the joystick option, composed directly: it
 * renders in the left-hand slot, a drag reports the same
 * direction mask the D-pad reports, release clears it, a
 * drag inside the dead zone reports nothing, and the
 * actuation click fires only on crossing a threshold.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StickControlTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val up = 1 shl InputMask.JOY_UP
    private val down = 1 shl InputMask.JOY_DOWN
    private val left = 1 shl InputMask.JOY_LEFT
    private val right = 1 shl InputMask.JOY_RIGHT

    private lateinit var held: HeldInput
    private var clicks = 0
    private var empties = 0

    private fun setContent() {
        held = HeldInput()
        clicks = 0
        empties = 0
        composeRule.setContent {
            PoingTheme {
                StickControl(held, false, {},
                    onDirectionChange = { clicks++ },
                    onDirectionsEmpty = { empties++ })
            }
        }
    }

    // The node's centre in root coordinates — the ground
    // truth for dispatching touches. The TouchInjectionScope
    // `center` is the node's centre in its own space, which
    // is the same point only when the node sits at the root
    // origin; the semantics bounds are explicit either way.
    private fun stickCenter(): Offset =
        composeRule.onNodeWithTag("stick").fetchSemanticsNode().boundsInRoot.center

    @Test
    fun stickRenders() {
        setContent()
        composeRule.onNodeWithTag("stick").assertIsDisplayed()
    }

    @Test
    fun dragRightReportsRight() {
        setContent()
        val c = stickCenter()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(60f, 0f))
        }
        assertEquals(right, held.mask)
    }

    @Test
    fun dragDiagonalReportsBothDirections() {
        setContent()
        val c = stickCenter()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(50f, -50f))
        }
        assertEquals(up or right, held.mask)
    }

    @Test
    fun dragInsideTheDeadZoneReportsNothing() {
        setContent()
        val c = stickCenter()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(5f, 0f))
        }
        assertEquals(0, held.mask)
    }

    @Test
    fun releaseClearsTheDirection() {
        setContent()
        val c = stickCenter()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(60f, 0f))
        }
        assertEquals(right, held.mask)
        composeRule.onNodeWithTag("stick").performTouchInput {
            up()
        }
        assertEquals(0, held.mask)
    }

    @Test
    fun actuationClickFiresOnCrossingOnly() {
        setContent()
        val c = stickCenter()
        // Down at the centre: neutral, no click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(60f, 0f))
        }
        assertEquals(1, clicks)
        // Direction -> diagonal: a second click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            moveTo(c + Offset(50f, -50f))
        }
        assertEquals(2, clicks)
        // Diagonal -> cardinal: a third click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            moveTo(c + Offset(60f, 0f))
        }
        assertEquals(3, clicks)
        // Release back to neutral stays silent.
        composeRule.onNodeWithTag("stick").performTouchInput {
            up()
        }
        assertEquals(3, clicks)
        assertEquals(0, held.mask)
    }

    // Alpha.11 — the release hook: returning to neutral fires
    // the empty hook once, and the actuation click stays
    // silent on the release.
    @Test
    fun stickDirectionsEmptyFiresOnReleaseToNeutral() {
        setContent()
        val c = stickCenter()
        // Down at the centre: neutral, nothing fires.
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(60f, 0f))
        }
        assertEquals(1, clicks)
        assertEquals(0, empties)
        // Release back to neutral: no fourth click, one empty.
        composeRule.onNodeWithTag("stick").performTouchInput {
            up()
        }
        assertEquals(1, clicks)
        assertEquals(1, empties)
        assertEquals(0, held.mask)
    }

    @Test
    fun leftAndDownReportTheirDirections() {
        setContent()
        val c = stickCenter()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(c)
            moveTo(c + Offset(-60f, 60f))
        }
        assertEquals(down or left, held.mask)
    }
}
