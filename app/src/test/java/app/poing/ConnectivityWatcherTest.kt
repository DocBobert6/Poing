package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Cover-art fix, commit A (2026-11-14): the library re-triggers
 * the cover fetch when connectivity changes, via a
 * NetworkCallback. Performance fix 3 (2026-11-14): the watcher
 * debounces bursts and fires only when the cover-art gate
 * transitions closed→open, so one network flap does not re-run
 * the whole fetch pass several times. It is tested on its own:
 * start registers one callback, a gate-opening event fires the
 * handler once after the debounce, stop unregisters it and
 * cancels a pending check.
 *
 * The debounce runs on the main looper, so the test advances it
 * past DEBOUNCE_MS explicitly — Robolectric's looper is paused,
 * and idling it without a duration would not run a delayed task.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectivityWatcherTest {

    private fun manager(): ConnectivityManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun fireOnAvailable() {
        val manager = manager()
        val network = manager.activeNetwork ?: error("no active network under Robolectric")
        Shadows.shadowOf(manager).networkCallbacks.single().onAvailable(network)
    }

    private fun advancePastDebounce() {
        Shadows.shadowOf(Looper.getMainLooper())
            .idle(ConnectivityWatcher.DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun firesOnceWhenTheGateOpensAndUnregistersOnStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var gateOpen = false
        var changes = 0
        val watcher = ConnectivityWatcher(context, gateOpen = { gateOpen }) { changes++ }
        watcher.start()
        val shadow = Shadows.shadowOf(manager())
        assertEquals(1, shadow.networkCallbacks.size)
        // The gate is closed: a network event is not a retry.
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(0, changes)
        // The gate opens: one debounced check fires once, even
        // though two events arrived in the burst.
        gateOpen = true
        fireOnAvailable()
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(1, changes)
        // Still open: further events are not transitions.
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(1, changes)
        // The gate closes again: no fire (open→closed is not a retry).
        gateOpen = false
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(1, changes)
        watcher.stop()
        assertEquals(0, shadow.networkCallbacks.size)
    }

    @Test
    fun stopCancelsAPendingDebouncedCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var gateOpen = false
        var changes = 0
        val watcher = ConnectivityWatcher(context, gateOpen = { gateOpen }) { changes++ }
        watcher.start()
        gateOpen = true
        fireOnAvailable()
        watcher.stop()
        advancePastDebounce()
        assertEquals(0, changes)
        assertEquals(0, Shadows.shadowOf(manager()).networkCallbacks.size)
    }
}
