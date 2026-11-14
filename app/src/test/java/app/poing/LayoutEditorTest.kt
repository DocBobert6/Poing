package app.poing

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Overlay rework (Robert, 2026-11-13) — the full-screen layout
 * editor, composed directly: every control renders, clusters
 * move together by default and separately with the Together
 * switch off, the size stepper changes the selected control
 * 1dp at a time, the opacity stepper steps by 5, Reset
 * restores the defaults, and Done commits the draft.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LayoutEditorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var committed: ControlLayout? = null

    private fun setContent(isGba: Boolean = false) {
        committed = null
        composeRule.setContent {
            PoingTheme {
                LayoutEditor(
                    initial = ControlLayout.DEFAULT,
                    isGba = isGba,
                    system = if (isGba) GameSystem.GBA else GameSystem.GB,
                    held = HeldInput(),
                    onInputChanged = {},
                    onControlDown = {},
                    onDone = { committed = it },
                    onBack = {},
                )
            }
        }
    }

    private fun centreX(tag: String): Float =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center.x

    @Test
    fun editorRendersEveryControlAndPill() {
        setContent(isGba = true)
        composeRule.onNodeWithTag("editor-dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-a").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-b").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-l").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-r").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-start").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-select").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-done").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-back").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-reset").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-snap").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-together").assertIsDisplayed()
        composeRule.onNodeWithText("Opacity 100%").assertIsDisplayed()
    }

    @Test
    fun noLrControlsForGbGames() {
        setContent(isGba = false)
        composeRule.onNodeWithTag("editor-l").assertDoesNotExist()
        composeRule.onNodeWithTag("editor-r").assertDoesNotExist()
    }

    // A and B overlap in the narrow test window (their centres
    // sit 0.082 of the screen apart and both are 64dp wide), and
    // B is drawn on top of A, so a drag that starts on A's
    // centre grabs B. A-drags start on A's right strip instead:
    // inside A, clear of B in any window.
    private fun aDragStart(center: Offset, width: Int) =
        center + Offset(width * 0.35f, 0f)

    @Test
    fun draggingAMovesBTogetherByDefault() {
        setContent()
        val bBefore = centreX("editor-b")
        composeRule.onNodeWithTag("editor-a").performTouchInput {
            val start = aDragStart(center, width)
            swipe(start = start, end = start + Offset(200f, 0f), durationMillis = 300)
        }
        val bAfter = centreX("editor-b")
        assertTrue("B should move right with A when grouped, was $bBefore now $bAfter", bAfter > bBefore + 10f)
        // And the draft carries it: Done commits A and B both moved.
        composeRule.onNodeWithTag("editor-done").performClick()
        val done = committed
        assertNotNull(done)
        assertTrue(done!!.control(ControlId.A).cx > ControlLayout.DEFAULT.control(ControlId.A).cx)
        assertTrue(done.control(ControlId.B).cx > ControlLayout.DEFAULT.control(ControlId.B).cx)
    }

    @Test
    fun togetherOffMovesAAlone() {
        setContent()
        composeRule.onNodeWithTag("editor-together").performClick()
        val bBefore = centreX("editor-b")
        composeRule.onNodeWithTag("editor-a").performTouchInput {
            val start = aDragStart(center, width)
            swipe(start = start, end = start + Offset(200f, 0f), durationMillis = 300)
        }
        val bAfter = centreX("editor-b")
        assertEquals("B should not move when Together is off", bBefore, bAfter, 2f)
        composeRule.onNodeWithTag("editor-done").performClick()
        val done = committed
        assertNotNull(done)
        assertTrue(done!!.control(ControlId.A).cx > ControlLayout.DEFAULT.control(ControlId.A).cx)
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.B),
            done.control(ControlId.B),
        )
    }

    @Test
    fun sizeStepperChangesTheSelectedSizeOneDpAtATime() {
        setContent()
        // A drag selects the control (Mariner's model), so the
        // size stepper appears.
        composeRule.onNodeWithTag("editor-a").performTouchInput {
            val start = aDragStart(center, width)
            swipe(start = start, end = start + Offset(200f, 0f), durationMillis = 300)
        }
        // The tag sits on the pill's wrapper Box and the text in
        // its child Text, so the assertion reads the child: a
        // bare text lookup would find two "64dp" nodes (the
        // pill, and the size shown inside the selected control).
        assertSizeValue("64dp")
        composeRule.onNodeWithTag("editor-size-plus").performClick()
        assertSizeValue("65dp")
        composeRule.onNodeWithTag("editor-size-minus").performClick()
        assertSizeValue("64dp")
    }

    private fun assertSizeValue(expected: String) {
        composeRule.onNodeWithTag("editor-size-value")
            .onChildren()
            .onFirst()
            .assertTextEquals(expected)
    }

    @Test
    fun opacityStepperStepsByFive() {
        setContent()
        composeRule.onNodeWithTag("editor-opacity-minus").performClick()
        composeRule.onNodeWithText("Opacity 95%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-opacity-plus").performClick()
        composeRule.onNodeWithText("Opacity 100%").assertIsDisplayed()
    }

    @Test
    fun resetRestoresTheDefaults() {
        setContent()
        val aBefore = centreX("editor-a")
        // Left, not right: A sits at 0.948 already, so a
        // rightward drag clamps at 0.97 after a few pixels and
        // the move is too small to assert on; left has room.
        composeRule.onNodeWithTag("editor-a").performTouchInput {
            val start = aDragStart(center, width)
            swipe(start = start, end = start + Offset(-200f, 0f), durationMillis = 300)
        }
        assertTrue(centreX("editor-a") < aBefore - 10f)
        composeRule.onNodeWithTag("editor-reset").performClick()
        assertEquals("Reset should put A back at its default spot", aBefore, centreX("editor-a"), 2f)
    }
}
