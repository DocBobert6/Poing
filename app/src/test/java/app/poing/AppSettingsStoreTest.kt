package app.poing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Phase 6 — the app-level settings store (pure java.io). */
class AppSettingsStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = AppSettingsStore(temp.root)

    @Test
    fun defaultsKeepTheSpeedControlOff() {
        val s = store()
        assertFalse(s.ffControlVisible())
        assertEquals(AppSettingsStore.DEFAULT_FF_SPEED, s.ffSpeed())
    }

    @Test
    fun ffControlRoundTrips() {
        val s = store()
        s.setFfControlVisible(true)
        assertTrue(s.ffControlVisible())
        assertTrue(AppSettingsStore(temp.root).ffControlVisible())
        s.setFfControlVisible(false)
        assertFalse(s.ffControlVisible())
    }

    @Test
    fun ffSpeedRoundTripsOnlyOfferedSpeeds() {
        val s = store()
        assertTrue(s.setFfSpeed(4))
        assertEquals(4, s.ffSpeed())
        assertTrue(s.setFfSpeed(3))
        assertEquals(3, s.ffSpeed())
        // 1, 5 and anything else are rejected and change nothing.
        assertFalse(s.setFfSpeed(1))
        assertFalse(s.setFfSpeed(5))
        assertFalse(s.setFfSpeed(0))
        assertEquals(3, s.ffSpeed())
    }

    @Test
    fun inputDisplayRoundTrips() {
        val s = store()
        assertFalse(s.inputDisplayVisible())
        s.setInputDisplayVisible(true)
        assertTrue(s.inputDisplayVisible())
        assertTrue(AppSettingsStore(temp.root).inputDisplayVisible())
        s.setInputDisplayVisible(false)
        assertFalse(s.inputDisplayVisible())
    }

    @Test
    fun hapticsStrengthDefaultsFullAndRoundTripsOnlyOfferedValues() {
        // Alpha.8 — the strength slider, 0–100, 100 by default.
        val s = store()
        assertEquals(100, s.hapticsStrength())
        assertTrue(s.setHapticsStrength(50))
        assertEquals(50, s.hapticsStrength())
        assertEquals(50, AppSettingsStore(temp.root).hapticsStrength())
        assertTrue(s.setHapticsStrength(0))
        assertEquals(0, s.hapticsStrength())
        // -1, 101 and anything else are rejected and change nothing.
        assertFalse(s.setHapticsStrength(-1))
        assertFalse(s.setHapticsStrength(101))
        assertEquals(0, s.hapticsStrength())
    }

    @Test
    fun hapticsStrengthClampsStaleValues() {
        // A hand-edited value outside 0–100 reads clamped,
        // never out of range.
        val s = store()
        java.io.File(temp.root, "app-settings.properties")
            .writeText("haptics_strength=250\n")
        assertEquals(100, AppSettingsStore(temp.root).hapticsStrength())
        java.io.File(temp.root, "app-settings.properties")
            .writeText("haptics_strength=-40\n")
        assertEquals(0, AppSettingsStore(temp.root).hapticsStrength())
    }

    @Test
    fun hapticsDefaultsOnAndRoundTrips() {
        val s = store()
        assertTrue(s.hapticsEnabled())
        s.setHapticsEnabled(false)
        assertFalse(s.hapticsEnabled())
        assertFalse(AppSettingsStore(temp.root).hapticsEnabled())
        s.setHapticsEnabled(true)
        assertTrue(s.hapticsEnabled())
    }

    @Test
    fun corruptFileReadsAsDefaults() {
        val s = store()
        File(temp.root, "app-settings.properties").writeText("this is not properties: [[[\n")
        assertFalse(s.ffControlVisible())
        assertEquals(AppSettingsStore.DEFAULT_FF_SPEED, s.ffSpeed())
    }

    @Test
    fun resetRestoresDefaults() {
        val s = store()
        s.setFfControlVisible(true)
        s.setFfSpeed(4)
        s.reset()
        assertFalse(s.ffControlVisible())
        assertEquals(AppSettingsStore.DEFAULT_FF_SPEED, s.ffSpeed())
    }

    @Test
    fun rewindControlOffByDefaultAndRoundTrips() {
        val s = store()
        assertFalse(s.rewindControlVisible())
        s.setRewindControlVisible(true)
        assertTrue(s.rewindControlVisible())
        assertTrue(AppSettingsStore(temp.root).rewindControlVisible())
        s.setRewindControlVisible(false)
        assertFalse(s.rewindControlVisible())
    }

    @Test
    fun jadeDefaultsOnAndRoundTrips() {
        // Each chain is the default look for its system.
        val s = store()
        assertTrue(s.jadeEnabled())
        s.setJadeEnabled(false)
        assertFalse(s.jadeEnabled())
        assertFalse(AppSettingsStore(temp.root).jadeEnabled())
        s.setJadeEnabled(true)
        assertTrue(s.jadeEnabled())
    }

    @Test
    fun chainEnabledFollowsTheJadeToggle() {
        val s = store()
        assertTrue(s.chainEnabled(DisplayChain.JADE))
        s.setJadeEnabled(false)
        assertFalse(s.chainEnabled(DisplayChain.JADE))
        // Clean is never gated by a toggle.
        assertTrue(s.chainEnabled(DisplayChain.CLEAN))
    }

    @Test
    fun prismDefaultsOnAndRoundTrips() {
        // Each chain is the default look for its system.
        val s = store()
        assertTrue(s.prismEnabled())
        s.setPrismEnabled(false)
        assertFalse(s.prismEnabled())
        assertFalse(AppSettingsStore(temp.root).prismEnabled())
        s.setPrismEnabled(true)
        assertTrue(s.prismEnabled())
    }

    @Test
    fun chainEnabledFollowsThePrismToggle() {
        val s = store()
        assertTrue(s.chainEnabled(DisplayChain.PRISM))
        s.setPrismEnabled(false)
        assertFalse(s.chainEnabled(DisplayChain.PRISM))
    }

    @Test
    fun duskDefaultsOnAndRoundTrips() {
        // Each chain is the default look for its system.
        val s = store()
        assertTrue(s.duskEnabled())
        s.setDuskEnabled(false)
        assertFalse(s.duskEnabled())
        assertFalse(AppSettingsStore(temp.root).duskEnabled())
        s.setDuskEnabled(true)
        assertTrue(s.duskEnabled())
    }

    @Test
    fun chainEnabledFollowsTheDuskToggle() {
        val s = store()
        assertTrue(s.chainEnabled(DisplayChain.DUSK))
        s.setDuskEnabled(false)
        assertFalse(s.chainEnabled(DisplayChain.DUSK))
    }

    @Test
    fun wormLightDefaultsOffAndRoundTrips() {
        // An easter egg, not a headline feature.
        val s = store()
        assertFalse(s.wormLightEnabled())
        s.setWormLightEnabled(true)
        assertTrue(s.wormLightEnabled())
        assertTrue(AppSettingsStore(temp.root).wormLightEnabled())
        s.setWormLightEnabled(false)
        assertFalse(s.wormLightEnabled())
    }

    @Test
    fun opticsDimmingDefaultsOffAndRoundTrips() {
        // Dimming for authenticity fights the player seeing
        // the game, so a dark room never dims the picture
        // unless the player asked for it.
        val s = store()
        assertFalse(s.opticsDimmingEnabled())
        s.setOpticsDimmingEnabled(true)
        assertTrue(s.opticsDimmingEnabled())
        assertTrue(AppSettingsStore(temp.root).opticsDimmingEnabled())
        s.setOpticsDimmingEnabled(false)
        assertFalse(s.opticsDimmingEnabled())
    }

    // Alpha.8 — per-system settings: each system reads
    // and writes its own key, and a system never set
    // falls back to the current global value (migration
    // by read, not by copy — nothing changes on install).
    @Test
    fun perSystemEdgeSmoothingIsIndependent() {
        val s = store()
        s.setEdgeSmoothingEnabled(GameSystem.GB, true)
        s.setEdgeSmoothingEnabled(GameSystem.GBC, false)
        s.setEdgeSmoothingEnabled(GameSystem.GBA, true)
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GB))
        assertFalse(s.edgeSmoothingEnabled(GameSystem.GBC))
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GBA))
        // A fresh read of the same file agrees.
        val again = AppSettingsStore(temp.root)
        assertTrue(again.edgeSmoothingEnabled(GameSystem.GB))
        assertFalse(again.edgeSmoothingEnabled(GameSystem.GBC))
        assertTrue(again.edgeSmoothingEnabled(GameSystem.GBA))
    }

    @Test
    fun perSystemEdgeSmoothingMigratesFromTheGlobalValue() {
        val s = store()
        // The global is on: every system reads on until
        // it is set on its own.
        s.setEdgeSmoothingEnabled(true)
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GB))
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GBC))
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GBA))
        // Turning one system off leaves the others — and
        // the global — alone.
        s.setEdgeSmoothingEnabled(GameSystem.GBC, false)
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GB))
        assertFalse(s.edgeSmoothingEnabled(GameSystem.GBC))
        assertTrue(s.edgeSmoothingEnabled(GameSystem.GBA))
        assertTrue(s.edgeSmoothingEnabled())
        // The global off migrates as off for a system
        // never set.
        s.setEdgeSmoothingEnabled(false)
        val fresh = AppSettingsStore(temp.root)
        assertFalse(fresh.edgeSmoothingEnabled(GameSystem.GBA))
    }

    @Test
    fun perSystemEdgeModeFollowsItsOwnToggles() {
        val s = store()
        assertEquals(EdgeMode.OFF, s.edgeMode(GameSystem.GB))
        s.setEdgeSmoothingEnabled(GameSystem.GB, true)
        assertEquals(EdgeMode.EDGE, s.edgeMode(GameSystem.GB))
        assertEquals(EdgeMode.OFF, s.edgeMode(GameSystem.GBC))
        s.setEdgeGridEnabled(GameSystem.GB, true)
        assertEquals(EdgeMode.EDGE_GRID, s.edgeMode(GameSystem.GB))
        // The grid alone, without smoothing, is nothing.
        s.setEdgeSmoothingEnabled(GameSystem.GB, false)
        assertEquals(EdgeMode.OFF, s.edgeMode(GameSystem.GB))
        // The global mode is untouched by per-system writes.
        assertEquals(EdgeMode.OFF, s.edgeMode())
    }

    @Test
    fun perSystemEdgeGridMigratesFromTheGlobalValue() {
        val s = store()
        s.setEdgeGridEnabled(true)
        assertTrue(s.edgeGridEnabled(GameSystem.GB))
        s.setEdgeGridEnabled(GameSystem.GB, false)
        assertFalse(s.edgeGridEnabled(GameSystem.GB))
        assertTrue(s.edgeGridEnabled(GameSystem.GBC))
        assertTrue(s.edgeGridEnabled())
    }

    @Test
    fun perSystemWormLightIsIndependentAndMigrates() {
        val s = store()
        s.setWormLightEnabled(true)
        assertTrue(s.wormLightEnabled(GameSystem.GBA))
        s.setWormLightEnabled(GameSystem.GBA, false)
        assertFalse(s.wormLightEnabled(GameSystem.GBA))
        assertTrue(s.wormLightEnabled(GameSystem.GB))
        assertTrue(s.wormLightEnabled())
    }

    @Test
    fun perSystemOpticsDimmingIsIndependentAndMigrates() {
        val s = store()
        s.setOpticsDimmingEnabled(GameSystem.GB, true)
        s.setOpticsDimmingEnabled(GameSystem.GBC, false)
        assertTrue(s.opticsDimmingEnabled(GameSystem.GB))
        assertFalse(s.opticsDimmingEnabled(GameSystem.GBC))
        // GBA was never set: it falls back to the global,
        // which is still off.
        assertFalse(s.opticsDimmingEnabled(GameSystem.GBA))
        assertFalse(s.opticsDimmingEnabled())
    }

    @Test
    fun perSystemChainFollowsTheHandheldChain() {
        val s = store()
        assertTrue(s.chainEnabled(GameSystem.GB))
        s.setChainEnabled(GameSystem.GB, false)
        assertFalse(s.chainEnabled(GameSystem.GB))
        assertFalse(s.jadeEnabled())
        assertTrue(s.chainEnabled(GameSystem.GBC))
        assertTrue(s.prismEnabled())
        s.setChainEnabled(GameSystem.GBA, false)
        assertFalse(s.duskEnabled())
    }

    @Test
    fun coverArtMobileDataDefaultsOffAndRoundTrips() {
        // Cover art downloads on unmetered Wi-Fi only unless
        // the player opts into mobile data — off by default,
        // so nothing ever uses the data allowance unasked.
        val s = store()
        assertFalse(s.coverArtMobileDataAllowed())
        s.setCoverArtMobileDataAllowed(true)
        assertTrue(s.coverArtMobileDataAllowed())
        assertTrue(AppSettingsStore(temp.root).coverArtMobileDataAllowed())
        s.setCoverArtMobileDataAllowed(false)
        assertFalse(s.coverArtMobileDataAllowed())
    }

    @Test
    fun devStatsDefaultsOffAndRoundTrips() {
        // The top bar hides during gameplay unless the player
        // asked for the dev stats — off by default, so the bar
        // is gone while playing and only the power light stays.
        val s = store()
        assertFalse(s.devStatsEnabled())
        s.setDevStatsEnabled(true)
        assertTrue(s.devStatsEnabled())
        assertTrue(AppSettingsStore(temp.root).devStatsEnabled())
        s.setDevStatsEnabled(false)
        assertFalse(s.devStatsEnabled())
    }

    @Test
    fun wormLightColorDefaultsToDarkMediumGrayAndRoundTrips() {
        // The lamp's hardware is dark medium gray by default
        // (Robert, 2026-11-12); the light itself is always
        // warm white and is never a setting.
        val s = store()
        assertEquals(WormLightColor.DEFAULT, s.wormLightColor())
        assertEquals(WormLightColor.DARK_GRAY, s.wormLightColor())
        s.setWormLightColor(WormLightColor.ATOMIC_PURPLE)
        assertEquals(WormLightColor.ATOMIC_PURPLE, s.wormLightColor())
        assertEquals(
            WormLightColor.ATOMIC_PURPLE,
            AppSettingsStore(temp.root).wormLightColor(),
        )
        s.setWormLightColor(WormLightColor.CLEAR)
        assertEquals(WormLightColor.CLEAR, s.wormLightColor())
    }

    @Test
    fun wormLightCoverageDefaultsFullAndRoundTripsOnlyOfferedValues() {
        // Alpha.10 — the coverage slider, 75–175%, 100 by
        // default (the current look, unchanged on install).
        val s = store()
        assertEquals(100, s.wormLightCoveragePct())
        assertEquals(1f, s.wormLightCoverage(), 0f)
        assertTrue(s.setWormLightCoveragePct(150))
        assertEquals(150, s.wormLightCoveragePct())
        assertEquals(1.5f, s.wormLightCoverage(), 0f)
        assertEquals(150, AppSettingsStore(temp.root).wormLightCoveragePct())
        assertTrue(s.setWormLightCoveragePct(75))
        assertEquals(0.75f, s.wormLightCoverage(), 0f)
        // 74, 176 and anything else are rejected and change nothing.
        assertFalse(s.setWormLightCoveragePct(74))
        assertFalse(s.setWormLightCoveragePct(176))
        assertEquals(75, s.wormLightCoveragePct())
    }

    @Test
    fun wormLightCoverageClampsStaleValues() {
        // A hand-edited value outside 75–175 reads clamped,
        // never out of range.
        val s = store()
        java.io.File(temp.root, "app-settings.properties")
            .writeText("display_worm_light_coverage=250\n")
        assertEquals(175, AppSettingsStore(temp.root).wormLightCoveragePct())
        java.io.File(temp.root, "app-settings.properties")
            .writeText("display_worm_light_coverage=-40\n")
        assertEquals(75, AppSettingsStore(temp.root).wormLightCoveragePct())
    }

    @Test
    fun stickDeadZoneDefaultsTwentyTwoAndRoundTripsOnlyOfferedValues() {
        // Alpha.14 — the joystick dead-zone slider, 0–50%,
        // 22 by default (the fixed 0.22 that shipped in
        // alpha.10, unchanged on install).
        val s = store()
        assertEquals(22, s.stickDeadZonePct())
        assertEquals(0.22f, s.stickDeadZone(), 0f)
        assertTrue(s.setStickDeadZonePct(30))
        assertEquals(30, s.stickDeadZonePct())
        assertEquals(0.3f, s.stickDeadZone(), 0f)
        assertEquals(30, AppSettingsStore(temp.root).stickDeadZonePct())
        assertTrue(s.setStickDeadZonePct(0))
        assertEquals(0f, s.stickDeadZone(), 0f)
        assertTrue(s.setStickDeadZonePct(50))
        assertEquals(0.5f, s.stickDeadZone(), 0f)
        // -1, 51 and anything else are rejected and change nothing.
        assertFalse(s.setStickDeadZonePct(-1))
        assertFalse(s.setStickDeadZonePct(51))
        assertEquals(50, s.stickDeadZonePct())
    }

    @Test
    fun stickDeadZoneClampsStaleValues() {
        // A hand-edited value outside 0–50 reads clamped,
        // never out of range.
        val s = store()
        java.io.File(temp.root, "app-settings.properties")
            .writeText("controls.stickDeadZone=99\n")
        assertEquals(50, AppSettingsStore(temp.root).stickDeadZonePct())
        java.io.File(temp.root, "app-settings.properties")
            .writeText("controls.stickDeadZone=-10\n")
        assertEquals(0, AppSettingsStore(temp.root).stickDeadZonePct())
    }

    @Test
    fun proportionalSteeringDefaultsOffAndRoundTrips() {
        // Alpha.16 — off by default: steering changes what
        // holding a direction means everywhere, so racing
        // games opt in.
        val s = store()
        assertFalse(s.proportionalSteering())
        s.setProportionalSteering(true)
        assertTrue(s.proportionalSteering())
        assertTrue(AppSettingsStore(temp.root).proportionalSteering())
        s.setProportionalSteering(false)
        assertFalse(s.proportionalSteering())
    }

    @Test
    fun edgeSmoothingDefaultsOffAndRoundTrips() {
        // Phase 9 — off by default: edge smoothing is an
        // alternative to the chains, never the default look.
        val s = store()
        assertFalse(s.edgeSmoothingEnabled())
        assertEquals(EdgeMode.OFF, s.edgeMode())
        s.setEdgeSmoothingEnabled(true)
        assertTrue(s.edgeSmoothingEnabled())
        assertEquals(EdgeMode.EDGE, s.edgeMode())
        assertTrue(AppSettingsStore(temp.root).edgeSmoothingEnabled())
        s.setEdgeSmoothingEnabled(false)
        assertFalse(s.edgeSmoothingEnabled())
        assertEquals(EdgeMode.OFF, s.edgeMode())
    }

    @Test
    fun edgeGridIsTheCombinedModeOnlyWhileEdgeIsOn() {
        // The combined mode is edge + light grid, one tap —
        // and nothing on its own: the grid switch without
        // edge smoothing is OFF, not a grid by itself.
        val s = store()
        assertFalse(s.edgeGridEnabled())
        s.setEdgeGridEnabled(true)
        assertEquals(EdgeMode.OFF, s.edgeMode())
        s.setEdgeSmoothingEnabled(true)
        assertEquals(EdgeMode.EDGE_GRID, s.edgeMode())
        assertTrue(AppSettingsStore(temp.root).edgeGridEnabled())
        s.setEdgeSmoothingEnabled(false)
        assertEquals(EdgeMode.OFF, s.edgeMode())
    }

    @Test
    fun wormLightColorUnknownStoredNameFallsBackToDefault() {
        // A hand-edited file can never leave the lamp without
        // a colour: anything the app does not know reads as
        // the default.
        val s = store()
        s.setWormLightColor(WormLightColor.GREEN)
        assertEquals(WormLightColor.GREEN, s.wormLightColor())
        // Rewrite the stored value by hand, the way a corrupt
        // or foreign file would.
        val file = File(temp.root, "app-settings.properties")
        val text = file.readText().replace("GREEN", "NotAColour")
        file.writeText(text)
        assertEquals(WormLightColor.DEFAULT, AppSettingsStore(temp.root).wormLightColor())
    }

    @Test
    fun libraryViewDefaultsToListAndRoundTrips() {
        // List by default — nothing changes for him unless he
        // switches — and the choice survives a restart.
        val s = store()
        assertEquals(LibraryView.LIST, s.libraryView())
        s.setLibraryView(LibraryView.GRID)
        assertEquals(LibraryView.GRID, s.libraryView())
        assertEquals(LibraryView.GRID, AppSettingsStore(temp.root).libraryView())
        s.setLibraryView(LibraryView.LIST)
        assertEquals(LibraryView.LIST, s.libraryView())
    }

    @Test
    fun unknownLibraryViewReadsAsList() {
        // A stale or hand-edited value never breaks the library.
        File(temp.root, "app-settings.properties").writeText("library_view=banana\n")
        assertEquals(LibraryView.LIST, store().libraryView())
    }

    @Test
    fun developerUnlockDefaultsLockedAndRoundTrips() {
        // Task 6 — the Developer section stays locked until
        // the version footer is tapped 7 times, and the
        // unlock survives a restart (a fresh store reads it
        // back). Reset wipes it with the rest of the file.
        val s = store()
        assertFalse(s.developerUnlocked())
        s.setDeveloperUnlocked(true)
        assertTrue(s.developerUnlocked())
        assertTrue(AppSettingsStore(temp.root).developerUnlocked())
        s.reset()
        assertFalse(AppSettingsStore(temp.root).developerUnlocked())
    }

    @Test
    fun reconGbcDefaultsOffAndRoundTrips() {
        // Task 6 — the GBC reconstruction is off by default
        // (an experimental quality path, never the default),
        // and the choice survives a restart.
        val s = store()
        assertFalse(s.reconGbcEnabled())
        s.setReconGbcEnabled(true)
        assertTrue(s.reconGbcEnabled())
        assertTrue(AppSettingsStore(temp.root).reconGbcEnabled())
        s.setReconGbcEnabled(false)
        assertFalse(s.reconGbcEnabled())
    }

    @Test
    fun reconGbaDefaultsOffAndRoundTrips() {
        // Task 7 — the GBA reconstruction, same rule as the
        // GBC one: off by default, and the choice survives
        // a restart. The two toggles are independent keys.
        val s = store()
        assertFalse(s.reconGbaEnabled())
        s.setReconGbaEnabled(true)
        assertTrue(s.reconGbaEnabled())
        assertTrue(AppSettingsStore(temp.root).reconGbaEnabled())
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
        s.setReconGbaEnabled(false)
        assertFalse(s.reconGbaEnabled())
    }

    @Test
    fun reconGbDefaultsOffAndRoundTrips() {
        // Task 5 — the GB reconstruction, same rule as the
        // GBC/GBA ones: off by default, and the choice survives
        // a restart. The three toggles are independent keys.
        val s = store()
        assertFalse(s.reconGbEnabled())
        s.setReconGbEnabled(true)
        assertTrue(s.reconGbEnabled())
        assertTrue(AppSettingsStore(temp.root).reconGbEnabled())
        assertFalse(AppSettingsStore(temp.root).reconGbcEnabled())
        s.setReconGbEnabled(false)
        assertFalse(s.reconGbEnabled())
    }

    @Test
    fun leftControlDefaultsToDpadAndRoundTrips() {
        // Alpha.10 — the control in the left-hand slot: the
        // D-pad cross by default, the joystick when chosen,
        // and the choice survives a restart.
        val s = store()
        assertEquals(LeftControl.DPAD, s.leftControl())
        s.setLeftControl(LeftControl.STICK)
        assertEquals(LeftControl.STICK, s.leftControl())
        assertEquals(LeftControl.STICK, AppSettingsStore(temp.root).leftControl())
        s.setLeftControl(LeftControl.DPAD)
        assertEquals(LeftControl.DPAD, s.leftControl())
    }

    @Test
    fun unknownLeftControlReadsAsDpad() {
        // A stale or hand-edited value never leaves the slot
        // empty: anything the app does not know reads as the
        // D-pad.
        File(temp.root, "app-settings.properties").writeText("controls.leftControl=banana\n")
        assertEquals(LeftControl.DPAD, store().leftControl())
    }
}
