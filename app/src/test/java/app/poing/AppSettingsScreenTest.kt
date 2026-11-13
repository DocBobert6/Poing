package app.poing

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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

    @Test
    fun inputDisplayOffByDefault() {
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Input display").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).inputDisplayVisible())
    }

    @Test
    fun togglingInputDisplayPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithTag("input-display-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).inputDisplayVisible())
    }

    @Test
    fun hapticsOnByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onNodeWithText("Haptics").assertIsDisplayed()
        assertTrue(AppSettingsStore(temp.root).hapticsEnabled())
        // The haptics row sits near the bottom of the settings
        // list (below the FF and input-display rows): scroll it
        // into view before clicking, or the click can miss a
        // switch that is only just inside the viewport.
        composeRule.onNodeWithTag("haptics-switch").performScrollTo().performClick()
        assertFalse(AppSettingsStore(temp.root).hapticsEnabled())
    }

    @Test
    fun jadeOnByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The Display section is the last one in the list: its
        // rows are never composed in the small test window until
        // the list scrolls there (the item 5b lesson — a bare
        // onNodeWithTag finds nothing below the fold). Scroll
        // to the Jade row first: index 9 (header, Player,
        // fast-forward, input display, haptics, RA title, RA,
        // rewind, Display title, Jade).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(9)
        composeRule.onNodeWithText("Jade").assertIsDisplayed()
        assertTrue(AppSettingsStore(temp.root).jadeEnabled())
        composeRule.onNodeWithTag("jade-chain-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).jadeEnabled())
        // Off hides the palette picker (it lives inside Jade).
        composeRule.onAllNodesWithText("Game Boy palette").assertCountEquals(0)
    }

    @Test
    fun pickingAPalettePersistsItInCoreOptions() {
        val s = store()
        val options = CoreOptionsStore(temp.root)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, optionsStore = options, onCoreOptions = {}, onBack = { })
            }
        }
        // The palette row is index 10 (the Jade row's index 9,
        // plus the palette row itself — see the Jade test).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(10)
        composeRule.onNodeWithTag("jade-palette-row").performClick()
        composeRule.onNodeWithText("DMG Green").performScrollTo().performClick()
        assertEquals("DMG Green", options.get("mgba_gb_colors"))
    }

    @Test
    fun prismOnByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The Prism row is index 10 here (no options store,
        // so no palette row: header, Player, fast-forward,
        // input display, haptics, RA title, RA, rewind,
        // Display title, Jade, Prism).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(10)
        composeRule.onNodeWithText("Prism").assertIsDisplayed()
        assertTrue(AppSettingsStore(temp.root).prismEnabled())
        composeRule.onNodeWithTag("prism-chain-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).prismEnabled())
    }

    @Test
    fun duskOnByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The Dusk row is index 11 here (no options store,
        // so no palette row: header, Player, fast-forward,
        // input display, haptics, RA title, RA, rewind,
        // Display title, Jade, Prism, Dusk).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(11)
        composeRule.onNodeWithText("Dusk").assertIsDisplayed()
        assertTrue(AppSettingsStore(temp.root).duskEnabled())
        composeRule.onNodeWithTag("dusk-chain-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).duskEnabled())
    }

    @Test
    fun wormLightOffByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The Worm Light row is index 12 here (no options
        // store, so no palette row: header, Player,
        // fast-forward, input display, haptics, RA title,
        // RA, rewind, Display title, Jade, Prism, Dusk,
        // Worm Light).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(12)
        composeRule.onNodeWithText("Worm Light").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).wormLightEnabled())
        composeRule.onNodeWithTag("worm-light-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).wormLightEnabled())
    }

    @Test
    fun opticsDimmingOffByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The dimming row is index 13 here (the Worm Light
        // row's index 12, plus the dimming row itself).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(13)
        composeRule.onNodeWithText("Dim in dark rooms").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).opticsDimmingEnabled())
        composeRule.onNodeWithTag("optics-dimming-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).opticsDimmingEnabled())
    }
}
