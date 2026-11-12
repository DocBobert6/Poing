package app.poing

import android.os.Looper
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
