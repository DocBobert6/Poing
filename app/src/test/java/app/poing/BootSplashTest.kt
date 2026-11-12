package app.poing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 — the dot-matrix boot (idea 9). The splash itself is
 * tested in isolation (no timing — the ~1s auto-dismiss lives in
 * PoingHome, and the startup tests wait past it); this pins that
 * the splash renders "Poing!" in the display face on a dark
 * screen, and that the delay is about a second.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootSplashTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bootSplashShowsWordmark() {
        composeRule.setContent {
            PoingTheme {
                BootSplash()
            }
        }
        composeRule.onNodeWithTag("boot-splash").assertIsDisplayed()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
    }

    @Test
    fun bootSplashDismissesAfterAboutASecond() {
        assertEquals(1000L, BOOT_SPLASH_MS)
    }
}
