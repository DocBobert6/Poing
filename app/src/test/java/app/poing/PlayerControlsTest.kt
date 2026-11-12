package app.poing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 — the touch controls drawn like the icon's hardware.
 * The gesture behaviour is unchanged from Phase 2/4 (and covered
 * there); these tests pin that the restyled controls still render:
 * the silver cross D-pad, the round A/B buttons, and the L/R
 * shoulder pills with their labels.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun holdButtonShowsItsLabel() {
        composeRule.setContent {
            PoingTheme {
                HoldButton("A", InputMask.JOY_A, HeldInput(), false, {})
            }
        }
        composeRule.onNodeWithText("A").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-A").assertIsDisplayed()
    }

    @Test
    fun shoulderButtonRendersAsPill() {
        composeRule.setContent {
            PoingTheme {
                HoldButton("L", InputMask.JOY_L, HeldInput(), false, {}, shoulder = true)
            }
        }
        composeRule.onNodeWithText("L").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-L").assertIsDisplayed()
    }

    @Test
    fun startButtonAt48dpRenders() {
        composeRule.setContent {
            PoingTheme {
                HoldButton("Start", InputMask.JOY_START, HeldInput(), false, {}, sizeDp = 48.dp)
            }
        }
        composeRule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun dpadRenders() {
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {})
            }
        }
        composeRule.onNodeWithTag("dpad").assertIsDisplayed()
    }

    @Test
    fun holdActionButtonShowsItsLabel() {
        composeRule.setContent {
            PoingTheme {
                HoldActionButton("FF", false, {}, {})
            }
        }
        composeRule.onNodeWithText("FF").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-action-FF").assertIsDisplayed()
    }

    @Test
    fun toggleActionButtonShowsItsLabel() {
        composeRule.setContent {
            PoingTheme {
                ToggleActionButton("0.5x", false, {})
            }
        }
        composeRule.onNodeWithText("0.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-action-0.5x").assertIsDisplayed()
    }
}
