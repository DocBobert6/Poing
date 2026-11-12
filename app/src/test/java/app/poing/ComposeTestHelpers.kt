package app.poing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText

/**
 * Phase 5 — the boot splash delays the library by ~1s, so tests
 * that need the library wait for a library-only node instead of
 * asserting it immediately. "Add game" exists on the library and
 * never on the splash (the splash shows only "Poing!"), so it is
 * the wait condition; the compose clock advances through the
 * splash delay while waiting.
 */
fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis = timeoutMillis) {
        try {
            onNodeWithText(text).assertExists()
            true
        } catch (_: AssertionError) {
            false
        }
    }
}
