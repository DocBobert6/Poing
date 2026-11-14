package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Cover-art fix, commit A (2026-11-14): the library re-triggers
 * the cover fetch when connectivity changes, via a
 * NetworkCallback. The watcher is the trigger mechanism — the
 * composable wires it to the fetch effect — so it is tested on
 * its own: start registers one callback, a network event fires
 * the handler, stop unregisters it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectivityWatcherTest {

    private fun manager(): ConnectivityManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Test
    fun firesOnNetworkEventsAndUnregistersOnStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var changes = 0
        val watcher = ConnectivityWatcher(context) { changes++ }
        watcher.start()
        val manager = manager()
        val shadow = Shadows.shadowOf(manager)
        assertEquals(1, shadow.networkCallbacks.size)
        val network = manager.activeNetwork ?: error("no active network under Robolectric")
        shadow.networkCallbacks.single().onAvailable(network)
        assertEquals(1, changes)
        shadow.networkCallbacks.single().onLost(network)
        assertEquals(2, changes)
        // The watcher ignores the capabilities argument; the
        // shadow builds the instance (NetworkCapabilities'
        // Builder and mutators are @hide — not in the SDK
        // stub — so tests build capabilities through the
        // shadow, which is exactly what it is for).
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps: ShadowNetworkCapabilities = Shadows.shadowOf(caps)
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadow.networkCallbacks.single().onCapabilitiesChanged(network, caps)
        assertEquals(3, changes)
        watcher.stop()
        assertEquals(0, shadow.networkCallbacks.size)
    }
}
