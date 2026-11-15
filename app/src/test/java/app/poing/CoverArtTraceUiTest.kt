package app.poing

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cover-art fetch trace UI (2026-11-15, alpha.3): the Trace
 * button in the library's status bar opens the on-device
 * record of what the fetch did. Robolectric: the pass runs on
 * composition (NoOp fetcher, no network in Robolectric's
 * default, so the gate is closed and the pass ends at once),
 * and the dialog shows its started/ended lines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverArtTraceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun seedGame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
    }

    @Test
    fun traceButtonOpensDialogWithThePassLines() {
        seedGame()
        composeRule.setContent {
            LibraryScreen(
                notice = null,
                onPlay = {},
                onLicences = {},
                onSettings = {},
                coverFetcher = NoOpCoverArtFetcher,
            )
        }
        composeRule.waitForText("TETRIS")
        composeRule.onNodeWithTag("cover-art-trace").performClick()
        composeRule.onNodeWithTag("cover-art-trace-dialog").assertExists()
        // The pass runs on composition and ends at once (gate
        // closed, NoOp fetcher): its started/ended lines are
        // in the dialog — live, from the store's trace state.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("pass ended", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithText("pass started", substring = true).assertExists()
        composeRule.onNodeWithText("gate closed", substring = true).assertExists()
    }
}
