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
}
