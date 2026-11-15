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
 * transitions, so one network flap does not re-run the whole
 * fetch pass several times. Cover-art rebuild (2026-11-15): it
 * fires on ANY gate transition — open→closed included, and a
 * transition between two closed states (no network → metered)
 * too — because the repository needs the gate-closed signal to
 * park its pipelines, and the status line's wording depends on
 * which closed state the gate is in. It is tested on its own:
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
        var gate = CoverArtGate.NO_NETWORK
        var changes = 0
        val watcher = ConnectivityWatcher(context, gateState = { gate }) { changes++ }
        watcher.start()
        val shadow = Shadows.shadowOf(manager())
        assertEquals(1, shadow.networkCallbacks.size)
        // The gate is closed: a network event is not a retry.
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(0, changes)
        // The gate opens: one debounced check fires once, even
        // though two events arrived in the burst.
        gate = CoverArtGate.OPEN
        fireOnAvailable()
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(1, changes)
        // Still open: further events are not transitions.
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(1, changes)
        // The gate closes again: it fires — the rebuild's
        // repository needs the gate-closed signal to park its
        // pipelines (the old watcher deliberately did not fire
        // here, "open→closed is not a retry").
        gate = CoverArtGate.NO_NETWORK
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(2, changes)
        // A transition between two closed states fires too:
        // the status line's wording depends on which closed
        // state the gate is in.
        gate = CoverArtGate.METERED_NO_OPT_IN
        fireOnAvailable()
        advancePastDebounce()
        assertEquals(3, changes)
        watcher.stop()
        assertEquals(0, shadow.networkCallbacks.size)
    }

    @Test
    fun stopCancelsAPendingDebouncedCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var gate = CoverArtGate.NO_NETWORK
        var changes = 0
        val watcher = ConnectivityWatcher(context, gateState = { gate }) { changes++ }
        watcher.start()
        gate = CoverArtGate.OPEN
        fireOnAvailable()
        watcher.stop()
        advancePastDebounce()
        assertEquals(0, changes)
        assertEquals(0, Shadows.shadowOf(manager()).networkCallbacks.size)
    }
}
