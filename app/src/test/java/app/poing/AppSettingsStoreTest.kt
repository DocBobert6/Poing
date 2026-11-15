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
}
