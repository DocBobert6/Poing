package app.poing

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Alpha.10 — the held arm lights up: pressing up shows
    // only the up arm, and lifting clears it. The arms are
    // composed only while their direction is held, so their
    // presence is the highlight.
    @Test
    fun dpadHighlightLightsHeldArmAndClearsOnRelease() {
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {})
            }
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertExists()
        composeRule.onNodeWithTag("dpad-highlight-down").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad-highlight-left").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad-highlight-right").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertDoesNotExist()
    }

    // Alpha.10 — a diagonal lights both arms: pressing the
    // top-right corner shows up and right, and nothing else.
    @Test
    fun dpadHighlightLightsBothArmsOnDiagonal() {
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {})
            }
        }
        val size = composeRule.onNodeWithTag("dpad").fetchSemanticsNode().size
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(size.width - 8f, 8f))
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertExists()
        composeRule.onNodeWithTag("dpad-highlight-right").assertExists()
        composeRule.onNodeWithTag("dpad-highlight-down").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad-highlight-left").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad-highlight-right").assertDoesNotExist()
    }

    // Alpha.10 — pausing clears the highlight exactly as it
    // clears the input: the pointer-input loop restarts on
    // the pause key, and the highlight state resets with it.
    @Test
    fun dpadHighlightClearsOnPause() {
        val pausedState = mutableStateOf(false)
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), pausedState.value, {})
            }
        }
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        composeRule.onNodeWithTag("dpad-highlight-up").assertExists()
        pausedState.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dpad-highlight-up").assertDoesNotExist()
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
    }

    // Alpha.11 — the release hook: onUp fires once when the
    // finger lifts, after onDown, and never without a press.
    @Test
    fun holdButtonOnUpFiresOnLiftAfterOnDown() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            PoingTheme {
                HoldButton("A", InputMask.JOY_A, HeldInput(), false, {},
                    onDown = { events += "down" },
                    onUp = { events += "up" })
            }
        }
        composeRule.onNodeWithTag("hold-A").performTouchInput {
            down(center)
            up()
        }
        assertEquals(listOf("down", "up"), events)
    }

    // Alpha.11 — the release hook: the empty hook fires once
    // when the last finger lifts, and never while a direction
    // is still held.
    @Test
    fun dpadDirectionsEmptyFiresWhenAllLift() {
        var empties = 0
        var changes = 0
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {},
                    onDirectionChange = { _, _ -> changes++ },
                    onDirectionsEmpty = { empties++ })
            }
        }
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
        }
        assertEquals(1, changes)
        assertEquals(0, empties)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        assertEquals(1, empties)
    }

    @Test
    fun dpadDirectionsEmptyDoesNotFireOnDirectionChange() {
        // Rolling from up to up-right changes the direction
        // set but never empties it: no release tick.
        var empties = 0
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {},
                    onDirectionsEmpty = { empties++ })
            }
        }
        val size = composeRule.onNodeWithTag("dpad").fetchSemanticsNode().size
        composeRule.onNodeWithTag("dpad").performTouchInput {
            down(Offset(center.x, 8f))
            moveTo(Offset(size.width - 8f, 8f))
        }
        assertEquals(0, empties)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        assertEquals(1, empties)
    }

    // Alpha.27 — the D-pad envelope split: the direction
    // callback carries the previous and new masks — a press
    // from neutral reports (0, direction), a roll reports
    // the old set and the new set, and the release fires
    // the empty hook only, never the change hook.
    @Test
    fun dpadDirectionChangeCarriesPreviousAndNewMasks() {
        val changes = mutableListOf<Pair<Int, Int>>()
        var empties = 0
        composeRule.setContent {
            PoingTheme {
                DpadControl(HeldInput(), false, {},
                    onDirectionChange = { previous, new -> changes += previous to new },
                    onDirectionsEmpty = { empties++ })
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
        assertEquals(0, empties)
        composeRule.onNodeWithTag("dpad").performTouchInput {
            up()
        }
        assertEquals(1, empties)
        assertEquals(2, changes.size)
    }

    // Alpha.16 — proportional steering never drives a
    // direction tick: the tick path reads the direction mask
    // only, and the stick's state report (mask + deflection)
    // fires on every knob move. Pushing deeper in the same
    // direction reports new deflections with the same mask —
    // the tick must fire once for the actuation, never again
    // for the deeper push.
    @Test
    fun stickDeflectionChangesWithoutAMaskChangeFireNoTick() {
        var ticks = 0
        val reports = mutableListOf<Pair<Int, Float>>()
        composeRule.setContent {
            PoingTheme {
                StickControl(
                    HeldInput(), false, {},
                    onDirectionChange = { ticks++ },
                    onStickStateChanged = { mask, deflection ->
                        reports += mask to deflection
                    },
                )
            }
        }
        val size = composeRule.onNodeWithTag("stick").fetchSemanticsNode().size
        val centre = Offset(size.width / 2f, size.height / 2f)
        composeRule.onNodeWithTag("stick").performTouchInput {
            // Down at the centre, then up partway, then
            // further up in the same direction, then release.
            down(centre)
            moveTo(Offset(centre.x, centre.y - 30f))
            moveTo(Offset(centre.x, centre.y - 38f))
            up()
        }
        // One actuation (neutral -> UP); the deeper push and
        // the release fire no second tick.
        assertEquals(1, ticks)
        // But the state report did fire for the deeper push:
        // two distinct non-zero deflections for the same UP
        // mask, so the session would have seen the change.
        val upMask = 1 shl InputMask.JOY_UP
        val upDeflections = reports.filter { it.first == upMask }.map { it.second }
        assertTrue(upDeflections.size >= 2)
        assertTrue(upDeflections.max() > upDeflections.min())
    }
}
