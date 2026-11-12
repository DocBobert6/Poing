package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 1) — the player settings screen: the
 * fast-forward control toggle (off by default) and the speed
 * choice (shown only while the control is on). The store is a
 * real one on a temp dir, so the test pins that a toggle
 * actually persists.
 *
 * No single-node assertDoesNotExist on this classpath (see
 * LibraryScreenTest) — absence is asserted with
 * onAllNodesWithText(...).assertCountEquals(0).
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = AppSettingsStore(temp.root)

    @Test
    fun ffControlOffByDefaultAndSpeedHidden() {
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Fast-forward control").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).ffControlVisible())
        composeRule.onAllNodesWithText("Fast-forward speed").assertCountEquals(0)
    }

    @Test
    fun togglingFfControlOnShowsSpeedAndPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithTag("ff-control-switch").performClick()
        composeRule.onNodeWithText("Fast-forward speed").assertIsDisplayed()
        assertTrue(AppSettingsStore(temp.root).ffControlVisible())
    }

    @Test
    fun choosingSpeedPersistsIt() {
        val s = store()
        s.setFfControlVisible(true)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("4x").performClick()
        assertEquals(4, AppSettingsStore(temp.root).ffSpeed())
    }

    @Test
    fun coreOptionsButtonIsOffered() {
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Core options").assertIsDisplayed()
    }
}
