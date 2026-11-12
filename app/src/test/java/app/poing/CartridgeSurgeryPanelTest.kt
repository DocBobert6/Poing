package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 12) — the Cartridge Surgery panel, without a
 * running game: the no-clock explanation, the clock display and
 * thumbwheel callbacks, the GBA-only solar cell, and the hardware
 * toggles. The live spin itself (the session + the core) is a
 * device test for Robert.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CartridgeSurgeryPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        isGba: Boolean = false,
        rtcTime: CartridgeClock.RtcTime? = null,
        onSpinRtc: (Long) -> Unit = {},
        solarLevel: String = "0",
        onSolarLevel: (String) -> Unit = {},
        rumbleEnabled: Boolean = true,
        onRumbleEnabled: (Boolean) -> Unit = {},
        sensorsEnabled: Boolean = true,
        onSensorsEnabled: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            PoingTheme {
                CartridgeSurgeryPanel(
                    gameName = "Test Game",
                    isGba = isGba,
                    rtcTime = rtcTime,
                    onSpinRtc = onSpinRtc,
                    solarLevel = solarLevel,
                    onSolarLevel = onSolarLevel,
                    rumbleEnabled = rumbleEnabled,
                    onRumbleEnabled = onRumbleEnabled,
                    sensorsEnabled = sensorsEnabled,
                    onSensorsEnabled = onSensorsEnabled,
                    onDone = {},
                )
            }
        }
    }

    @Test
    fun noClockShowsTheExplanationAndNoSolarCell() {
        show()
        composeRule.onNodeWithText("Cartridge").assertIsDisplayed()
        composeRule.onNodeWithText("This cartridge has no clock.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Solar cell").assertCountEquals(0)
        // The Done button is the last item — scroll it into view
        // before asserting it (the panel is taller than the test
        // window).
        composeRule.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clockTimeShowsAndTheThumbwheelSpins() {
        var spun = 0L
        show(
            rtcTime = CartridgeClock.RtcTime(days = 12, hour = 14, min = 32, sec = 7, halted = false),
            onSpinRtc = { spun = it },
        )
        composeRule.onNodeWithText("Day 12  14:32:07").assertIsDisplayed()
        composeRule.onNodeWithText("+1 hour").performClick()
        assertEquals(3_600L, spun)
        composeRule.onNodeWithText("−1 day").performClick()
        assertEquals(-86_400L, spun)
    }

    @Test
    fun gbaShowsTheSolarCell() {
        show(isGba = true)
        composeRule.onNodeWithText("Solar cell").assertIsDisplayed()
        composeRule.onNodeWithText("Level 0 of 10").assertIsDisplayed()
    }

    @Test
    fun togglesCallBackWithTheNewValue() {
        var rumble: Boolean? = null
        var sensors: Boolean? = null
        show(
            onRumbleEnabled = { rumble = it },
            onSensorsEnabled = { sensors = it },
        )
        // The switches sit in the third and fourth chips — below
        // the fold in the test window, so a bare click lands
        // off-screen and toggles nothing. Scroll each into view
        // first (the whole cartridge box is one LazyColumn item,
        // so both switches are composed; they are just not visible
        // yet).
        composeRule.onNodeWithTag("cartridge-rumble-switch").performScrollTo().performClick()
        assertEquals(false, rumble)
        composeRule.onNodeWithTag("cartridge-sensors-switch").performScrollTo().performClick()
        assertEquals(false, sensors)
    }
}
