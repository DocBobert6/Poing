package app.poing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import java.io.File
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

    private fun layoutStore() = ControlLayoutStore(File(temp.root, "control-layouts"))

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
    fun hapticsStrengthSliderShownWhileHapticsOn() {
        // Alpha.8 — the strength slider lives under the
        // Haptics row while haptics is on, 100 by default.
        // The list is a LazyColumn: scroll it to the slider
        // first, or the row is not composed in the small
        // test window (the item 5b lesson).
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("haptics-strength-slider"))
        composeRule.onNodeWithText("Haptic strength").assertIsDisplayed()
        composeRule.onNodeWithText("100%").assertIsDisplayed()
        assertEquals(100, AppSettingsStore(temp.root).hapticsStrength())
    }

    @Test
    fun hapticsStrengthSliderHiddenWhileHapticsOff() {
        // Same pattern as the fast-forward speed and the edge
        // grid rows: the slider only exists while its toggle
        // is on.
        val s = store()
        s.setHapticsEnabled(false)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onAllNodesWithText("Haptic strength").assertCountEquals(0)
    }

    @Test
    fun movingHapticsStrengthSliderPersists() {
        // Alpha.8 — setting the slider to 50 writes 50 to
        // the store (steps of 5: 50 is a valid stop).
        // SetProgress takes the TARGET VALUE in the slider's
        // range (0..100 here), not a fraction: 0.5f would
        // set the value to 0.5, which truncates to 0.
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("haptics-strength-slider"))
        composeRule.onNodeWithTag("haptics-strength-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        assertEquals(50, AppSettingsStore(temp.root).hapticsStrength())
        composeRule.onNodeWithText("50%").assertIsDisplayed()
    }

    @Test
    fun devStatsOffByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The Dev stats row is index 9 (header, Player,
        // fast-forward, input display, haptics, haptics
        // strength, RA title, RA, rewind, Dev stats) — the
        // last Player row before the Display title.
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(9)
        composeRule.onNodeWithText("Dev stats").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).devStatsEnabled())
        composeRule.onNodeWithTag("dev-stats-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).devStatsEnabled())
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
        // to the Jade row first: index 11 (header, Player,
        // fast-forward, input display, haptics, haptics
        // strength, RA title, RA, rewind, Dev stats,
        // Display title, Jade).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(11)
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
        // The palette row is index 12 (the Jade row's index 11,
        // plus the palette row itself — see the Jade test).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(12)
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
        // The Prism row is index 12 here (no options store,
        // so no palette row: header, Player, fast-forward,
        // input display, haptics, haptics strength, RA
        // title, RA, rewind, Dev stats, Display title,
        // Jade, Prism).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(12)
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
        // The Dusk row is index 13 here (no options store,
        // so no palette row: header, Player, fast-forward,
        // input display, haptics, haptics strength, RA
        // title, RA, rewind, Dev stats, Display title,
        // Jade, Prism, Dusk).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(13)
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
        // The Worm Light row is index 15 here (no options
        // store, so no palette row: header, Player,
        // fast-forward, input display, haptics, haptics
        // strength, RA title, RA, rewind, Dev stats,
        // Display title, Jade, Prism, Dusk, Edge
        // smoothing, Worm Light).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(15)
        composeRule.onNodeWithText("Worm Light").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).wormLightEnabled())
        composeRule.onNodeWithTag("worm-light-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).wormLightEnabled())
    }

    @Test
    fun wormLightColorSwatchesPickAndPersist() {
        // The hardware colour is a row of swatches under the
        // Worm Light switch (Robert, 2026-11-12): dark medium
        // gray by default, Atomic Purple / Clear / Green /
        // Black to pick from. The light itself is never a
        // choice — it stays warm white.
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The colour row is index 16 here (the Worm Light
        // row's index 15, plus the colour row itself).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(16)
        composeRule.onNodeWithText("Worm Light colour").assertIsDisplayed()
        assertEquals(WormLightColor.DEFAULT, AppSettingsStore(temp.root).wormLightColor())
        composeRule.onNodeWithTag("worm-light-color-ATOMIC_PURPLE").performClick()
        assertEquals(
            WormLightColor.ATOMIC_PURPLE,
            AppSettingsStore(temp.root).wormLightColor(),
        )
        composeRule.onNodeWithTag("worm-light-color-GREEN").performClick()
        assertEquals(WormLightColor.GREEN, AppSettingsStore(temp.root).wormLightColor())
    }

    @Test
    fun opticsDimmingOffByDefaultAndTogglingPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The dimming row is index 17 here (the Worm Light
        // row's index 15, the colour row's 16, plus the
        // dimming row itself).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(17)
        composeRule.onNodeWithText("Dim in dark rooms").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).opticsDimmingEnabled())
        composeRule.onNodeWithTag("optics-dimming-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).opticsDimmingEnabled())
    }

    @Test
    fun coverArtMobileDataOffByDefaultAndTogglingPersists() {
        // Cover-art fix, commit B (2026-11-14): the mobile
        // data opt-in — off by default, and the switch writes
        // it to the store.
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The row is index 19 here (the dimming row's 17,
        // the Library header's 18, plus the row itself).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(19)
        composeRule.onNodeWithText("Download cover art over mobile data").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).coverArtMobileDataAllowed())
        composeRule.onNodeWithTag("cover-art-mobile-data-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).coverArtMobileDataAllowed())
    }

    @Test
    fun edgeSmoothingOffByDefaultAndGridRowHidden() {
        // Phase 9 — off by default, and the combined-mode
        // row only exists while edge smoothing is on.
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        // The edge row is index 14 here (the Dusk row's
        // index 13, plus the edge row itself).
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(14)
        composeRule.onNodeWithText("Edge smoothing").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).edgeSmoothingEnabled())
        composeRule.onAllNodesWithText("Edge smoothing + grid").assertCountEquals(0)
    }

    @Test
    fun togglingEdgeSmoothingOnShowsGridRowAndPersists() {
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(14)
        composeRule.onNodeWithTag("edge-smoothing-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).edgeSmoothingEnabled())
        composeRule.onNodeWithText("Edge smoothing + grid").assertIsDisplayed()
        assertFalse(AppSettingsStore(temp.root).edgeGridEnabled())
        composeRule.onNodeWithTag("edge-grid-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).edgeGridEnabled())
        assertEquals(EdgeMode.EDGE_GRID, AppSettingsStore(temp.root).edgeMode())
    }

    @Test
    fun developerSectionHiddenUntilSevenFooterTaps() {
        // Task 6 — the Developer section is not straight up
        // out in the open: it appears only after the version
        // footer has been tapped 7 times. Six taps are not
        // enough; the seventh unlocks it, and the unlock
        // persists in the store.
        val s = store()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        composeRule.onAllNodesWithText("Developer — experimental").assertCountEquals(0)
        composeRule.onAllNodesWithText("Reconstruction (GBC)").assertCountEquals(0)
        composeRule.onAllNodesWithText("LCD grid on reconstruction").assertCountEquals(0)
        assertFalse(AppSettingsStore(temp.root).developerUnlocked())
        // The footer is the last item of the LazyColumn, so it
        // is not composed until the list is scrolled to it —
        // scroll the list to the footer node before each tap.
        repeat(6) {
            composeRule.onNodeWithTag("settings-list")
                .performScrollToNode(hasTestTag("version-footer"))
            composeRule.onNodeWithTag("version-footer").performClick()
        }
        composeRule.onAllNodesWithText("Developer — experimental").assertCountEquals(0)
        assertFalse(AppSettingsStore(temp.root).developerUnlocked())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("version-footer"))
        composeRule.onNodeWithTag("version-footer").performClick()
        assertTrue(AppSettingsStore(temp.root).developerUnlocked())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Developer — experimental"))
        composeRule.onNodeWithText("Developer — experimental").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Reconstruction (GBC)"))
        composeRule.onNodeWithText("Reconstruction (GBC)").assertIsDisplayed()
        // The warning line, verbatim.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(
                hasText(
                    "Experimental. May look wrong and may use more battery. Replaces edge smoothing while on.",
                ),
            )
        composeRule.onNodeWithText(
            "Experimental. May look wrong and may use more battery. Replaces edge smoothing while on.",
        ).assertIsDisplayed()
    }

    @Test
    fun togglingReconGbcPersists() {
        // Task 6 — the GBC reconstruction toggle: off by
        // default, and the switch writes it to the store.
        // The section is already unlocked here (the store
        // is pre-seeded), so the toggle is visible at once.
        val s = store()
        s.setDeveloperUnlocked(true)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
        // The toggle row sits below the fold in the
        // LazyColumn — scroll the list to it first.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gbc-switch"))
        composeRule.onNodeWithTag("recon-gbc-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).reconGbcEnabled())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gbc-switch"))
        composeRule.onNodeWithTag("recon-gbc-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
    }

    @Test
    fun togglingReconGbaPersists() {
        // Task 7 — the GBA reconstruction toggle, same as
        // the GBC one: off by default, and the switch
        // writes it to the store. The section is already
        // unlocked here (the store is pre-seeded).
        val s = store()
        s.setDeveloperUnlocked(true)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        assertFalse(AppSettingsStore(temp.root).reconGbaEnabled())
        // The toggle row sits below the fold in the
        // LazyColumn — scroll the list to it first.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gba-switch"))
        composeRule.onNodeWithTag("recon-gba-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).reconGbaEnabled())
        // The GBC toggle is independent: it stays off.
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gba-switch"))
        composeRule.onNodeWithTag("recon-gba-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).reconGbaEnabled())
    }

    @Test
    fun togglingReconGbPersists() {
        // Task 5 — the GB reconstruction toggle, same as
        // the GBC/GBA ones: off by default, and the switch
        // writes it to the store. The section is already
        // unlocked here (the store is pre-seeded).
        val s = store()
        s.setDeveloperUnlocked(true)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        assertFalse(AppSettingsStore(temp.root).reconGbEnabled())
        // The toggle row sits below the fold in the
        // LazyColumn — scroll the list to it first.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gb-switch"))
        composeRule.onNodeWithTag("recon-gb-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).reconGbEnabled())
        // The GBC toggle is independent: it stays off.
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-gb-switch"))
        composeRule.onNodeWithTag("recon-gb-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).reconGbEnabled())
    }

    @Test
    fun togglingReconGridPersists() {
        // The LCD grid over the reconstruction (Robert,
        // 2026-11-14): one toggle for all systems, off by
        // default, and the switch writes it to the store.
        // The section is already unlocked here (the store
        // is pre-seeded), so the toggle is visible at once.
        val s = store()
        s.setDeveloperUnlocked(true)
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = s, onCoreOptions = {}, onBack = { })
            }
        }
        assertFalse(AppSettingsStore(temp.root).reconGridEnabled())
        // The toggle row sits below the fold in the
        // LazyColumn — scroll the list to it first.
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-grid-switch"))
        composeRule.onNodeWithTag("recon-grid-switch").performClick()
        assertTrue(AppSettingsStore(temp.root).reconGridEnabled())
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("recon-grid-switch"))
        composeRule.onNodeWithTag("recon-grid-switch").performClick()
        assertFalse(AppSettingsStore(temp.root).reconGridEnabled())
    }

    @Test
    fun defaultLayoutRowsHiddenWithoutALayoutStore() {
        // Overlay defaults (Robert, 2026-11-14): the
        // Controls section needs the layout store — without
        // it (the old call sites) the rows are hidden, same
        // pattern as the Jade palette picker.
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onAllNodesWithText("GB default layout").assertCountEquals(0)
        composeRule.onAllNodesWithText("GBC default layout").assertCountEquals(0)
        composeRule.onAllNodesWithText("GBA default layout").assertCountEquals(0)
    }

    @Test
    fun defaultLayoutRowOpensTheEditorAndDoneSavesTheHandheldDefault() {
        // Overlay defaults (Robert, 2026-11-14): the GB row
        // opens the same full-screen editor the player uses,
        // and Done saves the draft as the GB default — the
        // other systems stay on the factory default, and a
        // fresh store instance (the restart round-trip) reads
        // it back.
        val layouts = layoutStore()
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), layoutStore = layouts, onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("default-layout-gb"))
        composeRule.onNodeWithTag("default-layout-gb").performClick()
        composeRule.onNodeWithTag("editor-dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithTag("editor-done").performClick()
        val reopened = ControlLayoutStore(File(temp.root, "control-layouts"))
        val gb = reopened.loadDefault(GameSystem.GB)
        assertTrue("dragging up should save a smaller cy than the 0.5 default", gb.control(ControlId.DPAD).cy < 0.5f)
        assertEquals(ControlLayout.DEFAULT, reopened.loadDefault(GameSystem.GBC))
        assertEquals(ControlLayout.DEFAULT, reopened.loadDefault(GameSystem.GBA))
        // The GB row now says it is customised, and only it:
        // scroll the row back into view first, so the label
        // is asserted on a composed row, not on an empty tree.
        // Substring: the row is clickable, so its texts merge
        // into one node ("GB default layout … Customised").
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("default-layout-gb"))
        composeRule.onAllNodesWithText("Customised", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Factory default", substring = true).assertCountEquals(2)
    }

    /**
     * In-app updater (Step 2 of the alpha, 2026-11-15): the
     * Updates section shows the current version and a manual
     * check; a fake checker answers so no network is needed.
     * The section sits above the Reset row, so the list is
     * scrolled to it first, like the footer and layout rows.
     */
    @Test
    fun updatesSectionShowsVersionAndCheckButton() {
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(store = store(), onCoreOptions = {}, onBack = {})
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("check-updates-button"))
        // The version line carries its own tag: the footer
        // below it also reads "Poing! 0.1.0", so a text
        // match finds two nodes once both are composed.
        composeRule.onNodeWithTag("update-version").assertExists()
        composeRule.onNodeWithTag("check-updates-button").assertIsDisplayed()
    }

    @Test
    fun checkForUpdatesShowsAvailableAndDownload() {
        val available = UpdateCheckResult.Available(
            UpdateRelease(
                "v0.1.0-alpha.2",
                AppVersion(0, 1, 0, listOf("alpha", "2")),
                "https://example.com/a2.apk",
            ),
        )
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(
                    store = store(),
                    onCoreOptions = {},
                    onBack = {},
                    checkUpdates = { available },
                )
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("check-updates-button"))
        composeRule.onNodeWithTag("check-updates-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("Update available: v0.1.0-alpha.2", substring = true)
                    .assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithTag("download-update-button").assertExists()
    }

    @Test
    fun checkForUpdatesShowsUpToDate() {
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(
                    store = store(),
                    onCoreOptions = {},
                    onBack = {},
                    checkUpdates = {
                        UpdateCheckResult.UpToDate(
                            UpdateRelease(
                                "v0.1.0",
                                AppVersion(0, 1, 0),
                                "https://example.com/a.apk",
                            ),
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("check-updates-button"))
        composeRule.onNodeWithTag("check-updates-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("You're up to date.", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun downloadShowsInstallButtonAndWritesTheApk() {
        val available = UpdateCheckResult.Available(
            UpdateRelease(
                "v0.1.0-alpha.2",
                AppVersion(0, 1, 0, listOf("alpha", "2")),
                "https://example.com/a2.apk",
            ),
        )
        composeRule.setContent {
            PoingTheme {
                AppSettingsScreen(
                    store = store(),
                    onCoreOptions = {},
                    onBack = {},
                    checkUpdates = { available },
                    downloadUpdate = { _, dir ->
                        dir.mkdirs()
                        File(dir, AppUpdate.APK_FILE_NAME).apply {
                            writeBytes("fake-apk".toByteArray())
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("check-updates-button"))
        composeRule.onNodeWithTag("check-updates-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag("download-update-button").assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithTag("download-update-button").performClick()
        // The download runs on IO; the Install button appears
        // once the file lands, whichever install branch the
        // device takes (direct install, or the unknown-apps
        // grant first).
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag("install-update-button").assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        assertTrue(File(context.filesDir, "updates/${AppUpdate.APK_FILE_NAME}").isFile)
    }
}
