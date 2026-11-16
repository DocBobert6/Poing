package app.poing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.28 — the black-screen trace viewer. The newest-first
 * ordering itself is pinned in DisplayTraceTest (the pure
 * recorder); this pins the rendering: recorded events show
 * with their labels, and an empty trace explains how to
 * turn it on instead of showing a blank list.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DisplayTraceViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsRecordedEvents() {
        var now = 1_000L
        val trace = DisplayTrace(clock = { now })
        trace.enabled = true
        trace.record("session start", "TETRIS (GB)")
        now += 500
        trace.record("surface created", "creation #1; pending frame: no; frameConsumed: true")
        composeRule.setContent {
            PoingTheme {
                DisplayTraceView(trace = trace, onClose = {})
            }
        }
        composeRule.onNodeWithTag("display-trace-view").assertIsDisplayed()
        composeRule.onNodeWithText("session start", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("surface created", substring = true).assertIsDisplayed()
    }

    @Test
    fun emptyTraceExplainsHowToTurnItOn() {
        // Disabled by default: nothing recorded.
        val trace = DisplayTrace()
        composeRule.setContent {
            PoingTheme {
                DisplayTraceView(trace = trace, onClose = {})
            }
        }
        composeRule.onNodeWithTag("display-trace-empty").assertIsDisplayed()
        // Exact match, not substring: the empty text itself
        // contains "Display trace", so a substring query
        // matches two nodes and fails.
        composeRule.onNodeWithText("Display trace").assertIsDisplayed()
    }
}
