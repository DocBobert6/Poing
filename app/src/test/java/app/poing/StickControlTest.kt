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

    private fun setContent() {
        held = HeldInput()
        clicks = 0
        composeRule.setContent {
            PoingTheme {
                StickControl(held, false, {}, onDirectionChange = { clicks++ })
            }
        }
    }

    @Test
    fun stickRenders() {
        setContent()
        composeRule.onNodeWithTag("stick").assertIsDisplayed()
    }

    @Test
    fun dragRightReportsRight() {
        setContent()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 0f))
        }
        assertEquals(right, held.mask)
    }

    @Test
    fun dragDiagonalReportsBothDirections() {
        setContent()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(50f, -50f))
        }
        assertEquals(up or right, held.mask)
    }

    @Test
    fun dragInsideTheDeadZoneReportsNothing() {
        setContent()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(5f, 0f))
        }
        assertEquals(0, held.mask)
    }

    @Test
    fun releaseClearsTheDirection() {
        setContent()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 0f))
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
        // Down at the centre: neutral, no click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(60f, 0f))
        }
        assertEquals(1, clicks)
        // Direction -> diagonal: a second click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            moveTo(center + Offset(50f, -50f))
        }
        assertEquals(2, clicks)
        // Diagonal -> cardinal: a third click.
        composeRule.onNodeWithTag("stick").performTouchInput {
            moveTo(center + Offset(60f, 0f))
        }
        assertEquals(3, clicks)
        // Release back to neutral stays silent.
        composeRule.onNodeWithTag("stick").performTouchInput {
            up()
        }
        assertEquals(3, clicks)
        assertEquals(0, held.mask)
    }

    @Test
    fun leftAndDownReportTheirDirections() {
        setContent()
        composeRule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(-60f, 60f))
        }
        assertEquals(down or left, held.mask)
    }
}
