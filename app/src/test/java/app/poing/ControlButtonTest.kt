package app.poing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
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

    // Alpha.27 — the D-pad envelope split: ControlButton
    // forwards the routed callback (previous mask, new
    // mask) to the D-pad, so a press then a roll to a
    // diagonal reports both transitions with their masks.
    @Test
    fun dpadRoutesPreviousAndNewMasks() {
        val changes = mutableListOf<Pair<Int, Int>>()
        composeRule.setContent {
            PoingTheme {
                ControlButton(
                    spec = ControlLayout.DEFAULT.control(ControlId.DPAD),
                    theme = ControlThemes.themeFor(GameSystem.GB),
                    held = HeldInput(),
                    paused = false,
                    onInputChanged = {},
                    onControlDown = {},
                    onDpadDirectionChange = { previous, new -> changes += previous to new },
                )
            }
        }
        val up = 1 shl InputMask.JOY_UP
        val right = 1 shl InputMask.JOY_RIGHT
        val size = composeRule.onNodeWithTag("dpad").fetchSemanticsNode().size
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
            moveTo(Offset(size.width - 8f, 8f))
        }
        assertEquals(listOf(0 to up, up to (up or right)), changes)
    }

    @Test
    fun dpadWithoutRoutedCallbackFallsBackToPlainTick() {
        // No routed callback supplied: every direction
        // change falls back to the plain roll tick — the
        // editor preview and any older caller keep the
        // alpha.17 behaviour.
        var ticks = 0
        composeRule.setContent {
            PoingTheme {
                ControlButton(
                    spec = ControlLayout.DEFAULT.control(ControlId.DPAD),
                    theme = ControlThemes.themeFor(GameSystem.GB),
                    held = HeldInput(),
                    paused = false,
                    onInputChanged = {},
                    onControlDown = {},
                    onDirectionTick = { ticks++ },
                )
            }
        }
        val size = composeRule.onNodeWithTag("dpad").fetchSemanticsNode().size
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
            moveTo(Offset(size.width - 8f, 8f))
        }
        assertEquals(2, ticks)
    }

    // Alpha.10 (Part D) — the FF / 0.5x / RW trio, moved out
    // of the fixed Row into the overlay: the same buttons, the
    // same tags, at the spec's size.
    private var ffDowns = 0
    private var ffUps = 0
    private var slowToggles = 0
    private var rwDowns = 0
    private var rwUps = 0

    private fun setTrioContent(id: ControlId, slowOn: Boolean = false) {
        ffDowns = 0
        ffUps = 0
        slowToggles = 0
        rwDowns = 0
        rwUps = 0
        composeRule.setContent {
            PoingTheme {
                ControlButton(
                    spec = ControlLayout.DEFAULT.control(id),
                    theme = ControlThemes.themeFor(GameSystem.GB),
                    held = HeldInput(),
                    paused = false,
                    onInputChanged = {},
                    onControlDown = {},
                    slowOn = slowOn,
                    onFfDown = { ffDowns++ },
                    onFfUp = { ffUps++ },
                    onSlowToggle = { slowToggles++ },
                    onRwDown = { rwDowns++ },
                    onRwUp = { rwUps++ },
                )
            }
        }
    }

    @Test
    fun ffHoldButtonRendersAndFiresDownAndUp() {
        setTrioContent(ControlId.FF)
        composeRule.onNodeWithTag("hold-action-FF").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-action-FF").performTouchInput {
            down(center)
            up()
        }
        assertEquals(1, ffDowns)
        assertEquals(1, ffUps)
    }

    @Test
    fun slowToggleButtonRendersAndToggles() {
        setTrioContent(ControlId.SLOW)
        composeRule.onNodeWithTag("toggle-action-0.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-action-0.5x").performClick()
        assertEquals(1, slowToggles)
    }

    @Test
    fun rwHoldButtonRendersAndFiresDownAndUp() {
        setTrioContent(ControlId.RW)
        composeRule.onNodeWithTag("hold-action-RW").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-action-RW").performTouchInput {
            down(center)
            up()
        }
        assertEquals(1, rwDowns)
        assertEquals(1, rwUps)
    }
}
