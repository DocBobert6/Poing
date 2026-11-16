package app.poing

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
    private var committedChoice: LeftControl? = null
    private var committedDeadZone: Int? = null

    private fun setContent(
        isGba: Boolean = false,
        resetLayout: ControlLayout = ControlLayout.DEFAULT,
        // Alpha.10 — the per-game left-control choice: the
        // toggle row shows only when showLeftControlChoice is
        // true (the per-game editor); the Settings default
        // editor never passes it.
        showLeftControlChoice: Boolean = false,
        leftControlDefault: LeftControl = LeftControl.DPAD,
        leftControlOverride: LeftControl? = null,
        // Alpha.14 — the per-game joystick dead zone: the
        // stepper row shows only when showDeadZoneChoice is
        // true and the stick is the control in the slot.
        showDeadZoneChoice: Boolean = false,
        deadZoneDefaultPct: Int = StickInput.DEAD_ZONE_DEFAULT_PCT,
        deadZoneOverridePct: Int? = null,
    ) {
        committed = null
        committedChoice = null
        committedDeadZone = null
        composeRule.setContent {
            PoingTheme {
                LayoutEditor(
                    initial = ControlLayout.DEFAULT,
                    isGba = isGba,
                    system = if (isGba) GameSystem.GBA else GameSystem.GB,
                    held = HeldInput(),
                    onInputChanged = {},
                    onControlDown = {},
                    leftControlDefault = leftControlDefault,
                    leftControlOverride = leftControlOverride,
                    showLeftControlChoice = showLeftControlChoice,
                    deadZoneDefaultPct = deadZoneDefaultPct,
                    deadZoneOverridePct = deadZoneOverridePct,
                    showDeadZoneChoice = showDeadZoneChoice,
                    onDone = { layout, choice, deadZonePct ->
                        committed = layout
                        committedChoice = choice
                        committedDeadZone = deadZonePct
                    },
                    onBack = {},
                    resetLayout = resetLayout,
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
        composeRule.onNodeWithTag("editor-ff").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-slow").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-rw").assertIsDisplayed()
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

    // Alpha.10 (Part D) — the FF / 0.5x / RW trio moves
    // together in the editor like any other cluster: a drag
    // on FF's right strip (clear of Slow, which sits to its
    // left — the same overlap dodge as the A-drag) moves the
    // trio, and Done commits all three at their new spots.
    @Test
    fun draggingFfMovesTheTrioTogetherAndCommits() {
        setContent()
        val slowBefore = centreX("editor-slow")
        composeRule.onNodeWithTag("editor-ff").performTouchInput {
            val start = center + Offset(width * 0.35f, 0f)
            swipe(start = start, end = start + Offset(-120f, -60f), durationMillis = 300)
        }
        val slowAfter = centreX("editor-slow")
        assertTrue("Slow should move left with FF when grouped, was $slowBefore now $slowAfter", slowAfter < slowBefore - 10f)
        composeRule.onNodeWithTag("editor-done").performClick()
        val done = committed
        assertNotNull(done)
        assertTrue(done!!.control(ControlId.FF).cx < ControlLayout.DEFAULT.control(ControlId.FF).cx)
        assertTrue(done.control(ControlId.FF).cy < ControlLayout.DEFAULT.control(ControlId.FF).cy)
        assertTrue(done.control(ControlId.SLOW).cx < ControlLayout.DEFAULT.control(ControlId.SLOW).cx)
        assertTrue(done.control(ControlId.RW).cx < ControlLayout.DEFAULT.control(ControlId.RW).cx)
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

    @Test
    fun resetRestoresTheGivenResetLayoutNotTheFactoryDefault() {
        // Overlay defaults (Robert, 2026-11-14): the player
        // passes the handheld default as the reset layout, so
        // Reset restores that — A at cx 0.2, opacity 60% —
        // not the factory default (A at 0.948, opacity 100%).
        val custom = ControlLayout.DEFAULT
            .moved(ControlId.A, 0.2f, 0.3f, grouped = false)
            .withOpacity(60)
        setContent(resetLayout = custom)
        composeRule.onNodeWithTag("editor-a").performTouchInput {
            val start = aDragStart(center, width)
            swipe(start = start, end = start + Offset(-200f, 0f), durationMillis = 300)
        }
        composeRule.onNodeWithTag("editor-opacity-minus").performClick()
        composeRule.onNodeWithText("Opacity 95%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-reset").performClick()
        composeRule.onNodeWithText("Opacity 60%").assertIsDisplayed()
        // A back at the custom spot: cx 0.2 of the root width
        // (both in pixels, so the density cancels).
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertEquals(
            "Reset should put A back at the reset layout's spot",
            0.2f * rootWidth,
            centreX("editor-a"),
            3f,
        )
        // And Done commits the reset layout itself.
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(custom, committed)
    }

    // Alpha.10 — the per-game left-control choice: the toggle
    // row shows only in the per-game editor, choosing the
    // joystick previews the stick live in the slot, Done
    // commits the choice with the layout, and Reset never
    // touches the choice draft.
    @Test
    fun leftControlRowHiddenWithoutTheChoice() {
        setContent()
        composeRule.onAllNodesWithTag("editor-left-control-global").assertCountEquals(0)
        composeRule.onAllNodesWithTag("editor-left-control-dpad").assertCountEquals(0)
        composeRule.onAllNodesWithTag("editor-left-control-stick").assertCountEquals(0)
    }

    @Test
    fun choosingJoystickCommitsStickAndPreviewsTheStick() {
        setContent(showLeftControlChoice = true)
        composeRule.onNodeWithTag("editor-left-control-stick").performClick()
        // The preview switches live: the stick renders in the
        // slot and the cross leaves it.
        composeRule.onNodeWithTag("stick").assertIsDisplayed()
        composeRule.onAllNodesWithTag("dpad").assertCountEquals(0)
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(LeftControl.STICK, committedChoice)
    }

    @Test
    fun choosingGlobalCommitsNullAndPreviewsTheDefault() {
        setContent(showLeftControlChoice = true, leftControlOverride = LeftControl.STICK)
        // It starts on the stored override: the stick shows.
        composeRule.onNodeWithTag("stick").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-left-control-global").performClick()
        // Back to the global default (D-pad): the cross returns.
        composeRule.onNodeWithTag("dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(null, committedChoice)
    }

    @Test
    fun resetLeavesTheLeftControlChoiceAlone() {
        setContent(showLeftControlChoice = true)
        composeRule.onNodeWithTag("editor-left-control-stick").performClick()
        composeRule.onNodeWithTag("editor-reset").performClick()
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(LeftControl.STICK, committedChoice)
    }

    // Alpha.14 — the per-game joystick dead zone: the stepper
    // row shows only in the per-game editor while the stick is
    // the control in the slot, stepping creates an override
    // from the global value, Global clears it, Done commits it
    // with the layout, and Reset never touches the draft.
    @Test
    fun deadZoneRowHiddenWithoutTheChoice() {
        // The per-game editor never passes the choice: the
        // stick is in the slot, but there is no dead-zone row.
        setContent(leftControlOverride = LeftControl.STICK)
        composeRule.onAllNodesWithTag("editor-dead-zone-value").assertCountEquals(0)
    }

    @Test
    fun deadZoneRowHiddenWithTheDpad() {
        // The choice is passed, but the D-pad is the control
        // in the slot: the dead zone is the stick's, so there
        // is no row to show.
        setContent(showDeadZoneChoice = true)
        composeRule.onAllNodesWithTag("editor-dead-zone-value").assertCountEquals(0)
    }

    @Test
    fun deadZoneRowShowsTheGlobalValueForTheStick() {
        setContent(showDeadZoneChoice = true, leftControlOverride = LeftControl.STICK)
        composeRule.onNodeWithText("Dead zone 22% (Global)").assertIsDisplayed()
    }

    @Test
    fun deadZoneStepperCreatesAnOverrideAndDoneCommitsIt() {
        setContent(showDeadZoneChoice = true, leftControlOverride = LeftControl.STICK)
        composeRule.onNodeWithTag("editor-dead-zone-plus").performClick()
        composeRule.onNodeWithText("Dead zone 27%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-dead-zone-minus").performClick()
        composeRule.onNodeWithText("Dead zone 22%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-dead-zone-plus").performClick()
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(27, committedDeadZone)
    }

    @Test
    fun deadZoneGlobalClearsTheOverride() {
        setContent(
            showDeadZoneChoice = true,
            leftControlOverride = LeftControl.STICK,
            deadZoneOverridePct = 30,
        )
        composeRule.onNodeWithText("Dead zone 30%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-dead-zone-global").performClick()
        composeRule.onNodeWithText("Dead zone 22% (Global)").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(null, committedDeadZone)
    }

    @Test
    fun deadZoneRowHidesWhenTheDpadIsChosenButTheOverrideSurvives() {
        setContent(
            showLeftControlChoice = true,
            showDeadZoneChoice = true,
            leftControlOverride = LeftControl.STICK,
            deadZoneOverridePct = 30,
        )
        composeRule.onNodeWithText("Dead zone 30%").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-left-control-dpad").performClick()
        composeRule.onAllNodesWithTag("editor-dead-zone-value").assertCountEquals(0)
        // The hidden override is still committed: switching
        // away from the stick does not wipe it.
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(30, committedDeadZone)
    }

    @Test
    fun resetLeavesTheDeadZoneOverrideAlone() {
        setContent(showDeadZoneChoice = true, leftControlOverride = LeftControl.STICK)
        composeRule.onNodeWithTag("editor-dead-zone-plus").performClick()
        composeRule.onNodeWithTag("editor-reset").performClick()
        composeRule.onNodeWithTag("editor-done").performClick()
        assertEquals(27, committedDeadZone)
    }
}
