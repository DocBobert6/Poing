package app.poing

import android.os.Looper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 4 — the session's save guards, without any native code.
 * Under Robolectric there is no .so to load, so the session can
 * never start; these tests pin the safe behaviour of that path:
 * save/load/flush are no-ops (false) before a game is loaded, and
 * a failed start reports its error instead of crashing.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerSessionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun session(): PlayerSession {
        val system = File(temp.root, "system").apply { mkdirs() }
        val saves = File(temp.root, "saves").apply { mkdirs() }
        return PlayerSession(
            systemDir = system.absolutePath,
            saveDir = saves.absolutePath,
            gamePath = File(temp.root, "game.gb").absolutePath,
            saveBase = "game",
            onFrame = {},
            onStats = {},
            onError = {},
        )
    }

    @Test
    fun saveLoadFlushAreNoOpsBeforeStart() {
        val s = session()
        assertFalse(s.isRunning)
        assertFalse(s.isLoaded)
        assertFalse(s.isPaused)
        assertFalse(s.saveState(0))
        assertFalse(s.loadState(0))
        assertFalse(s.flushSave())
        assertFalse(s.stateExists(0))
    }

    @Test
    fun applyOptionsDoesNotCrashWithoutNativeCode() {
        val s = session()
        // No .so under Robolectric: ensureLoaded returns false and
        // the options are simply not pushed anywhere.
        s.applyOptions(mapOf("mgba_color_correction" to "Auto"))
        s.applyOptions()
    }

    @Test
    fun applyCheatsIsNoOpBeforeStart() {
        val s = session()
        // No game loaded: nothing is applied and the call reports
        // false instead of touching native code.
        assertFalse(s.applyCheats(listOf(Cheat("01234567", true))))
        assertFalse(s.applyCheats(emptyList()))
    }

    @Test
    fun playtimeSecondsIsZeroBeforeStart() {
        val s = session()
        // No playtime dir wired in the default helper: nothing
        // tracked, nothing banked.
        assertEquals(0L, s.playtimeSeconds())
    }

    @Test
    fun playtimeSecondsReadsTheStoredTotal() {
        PlaytimeStore(temp.root).add("game", 3600)
        val system = File(temp.root, "system").apply { mkdirs() }
        val saves = File(temp.root, "saves").apply { mkdirs() }
        val s = PlayerSession(
            systemDir = system.absolutePath,
            saveDir = saves.absolutePath,
            gamePath = File(temp.root, "game.gb").absolutePath,
            saveBase = "game",
            playtimeDir = temp.root.absolutePath,
            onFrame = {},
            onStats = {},
            onError = {},
        )
        assertEquals(3600L, s.playtimeSeconds())
        // Pause/stop before start bank nothing (never started).
        s.pause()
        s.stop()
        assertEquals(3600L, PlaytimeStore(temp.root).get("game"))
    }

    @Test
    fun failedStartReportsErrorInsteadOfCrashing() {
        var message: String? = null
        val system = File(temp.root, "system").apply { mkdirs() }
        val saves = File(temp.root, "saves").apply { mkdirs() }
        val s = PlayerSession(
            systemDir = system.absolutePath,
            saveDir = saves.absolutePath,
            gamePath = File(temp.root, "game.gb").absolutePath,
            saveBase = "game",
            onFrame = {},
            onStats = {},
            onError = { message = it },
        )
        s.start()
        // The emu thread reports the failure by posting to the main
        // looper, which Robolectric keeps paused: idle it in a loop
        // until the callback lands (or the deadline passes).
        val deadline = System.currentTimeMillis() + 5000
        while (message == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertNotNull("start must report its failure", message)
        assertTrue(message!!.contains("Native libraries"))
        s.stop()
    }

    @Test
    fun speedDefaultsToNormalAndSetSpeedSticks() {
        val s = session()
        assertEquals(PlaySpeed.NORMAL, s.currentSpeed)
        s.setSpeed(PlaySpeed.FF3)
        assertEquals(PlaySpeed.FF3, s.currentSpeed)
        s.setSpeed(PlaySpeed.SLOW)
        assertEquals(PlaySpeed.SLOW, s.currentSpeed)
    }

    @Test
    fun noAutoSaveBeforeStartAndStopWritesNone() {
        val s = session()
        assertFalse(s.autoStateExists())
        assertNull(s.autoStateModifiedAt())
        s.stop()
        assertFalse(s.autoStateExists())
    }

    @Test
    fun recordingGuardsAreNoOpsBeforeStart() {
        val s = session()
        assertFalse(s.isRecording)
        assertEquals(0L, s.recordingElapsedSeconds())
        assertEquals(0, s.recordingFrameCount())
        assertNull(s.latestFrameBitmap())
        // No game loaded: a recording cannot start, and stopping
        // one that never ran is a no-op.
        val fd = java.io.FileDescriptor()
        assertFalse(s.startRecording(fd))
        assertFalse(s.stopRecording())
        assertFalse(s.isRecording)
    }

    @Test
    fun rtcGuardsAreNoOpsBeforeStart() {
        val s = session()
        assertNull(s.readRtcBytes())
        assertFalse(s.spinRtc(ByteArray(CartridgeClock.BUFFER_SIZE)))
    }
}
