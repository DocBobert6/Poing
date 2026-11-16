package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.10 — the left-hand slot holds EITHER the D-pad cross
 * OR the joystick, never both: the switch in ControlButton
 * renders the cross for DPAD and the stick for STICK, at the
 * same spec (position, size) the overlay gives the slot.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ControlButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(control: LeftControl) {
        composeRule.setContent {
            PoingTheme {
                ControlButton(
                    spec = ControlLayout.DEFAULT.control(ControlId.DPAD),
                    theme = ControlThemes.themeFor(GameSystem.GB),
                    held = HeldInput(),
                    paused = false,
                    onInputChanged = {},
                    onControlDown = {},
                    leftControl = control,
                )
            }
        }
    }

    @Test
    fun dpadChoiceRendersTheCross() {
        setContent(LeftControl.DPAD)
        composeRule.onNodeWithTag("dpad").assertIsDisplayed()
        composeRule.onAllNodesWithTag("stick").assertCountEquals(0)
    }

    @Test
    fun stickChoiceRendersTheJoystick() {
        setContent(LeftControl.STICK)
        composeRule.onNodeWithTag("stick").assertIsDisplayed()
        composeRule.onAllNodesWithTag("dpad").assertCountEquals(0)
    }
}
