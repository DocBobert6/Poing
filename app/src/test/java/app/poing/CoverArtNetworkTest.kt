package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Cover-art fix, commit B (2026-11-14): the network gate behind
 * the "Download cover art over mobile data" setting. Unmetered
 * Wi-Fi always qualifies; mobile data and metered Wi-Fi qualify
 * only when the user opts in; no network never qualifies.
 * Capabilities are built through the shadow — the Builder and
 * the mutators are @hide, not in the SDK stub (see
 * ConnectivityWatcherTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverArtNetworkTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun manager(): ConnectivityManager =
        context().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Puts these capabilities on the active network and returns the gate's answer. */
    private fun gateWith(
        transports: List<Int>,
        capabilities: List<Int>,
        allowMobileData: Boolean,
    ): Boolean {
        val shadow = Shadows.shadowOf(manager())
        val network = manager().activeNetwork ?: error("no active network under Robolectric")
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps: ShadowNetworkCapabilities = Shadows.shadowOf(caps)
        for (transport in transports) shadowCaps.addTransportType(transport)
        for (capability in capabilities) shadowCaps.addCapability(capability)
        shadow.setNetworkCapabilities(network, caps)
        return isCoverArtNetworkAvailable(context(), allowMobileData)
    }

    @Test
    fun unmeteredWifiAllowedRegardlessOfSetting() {
        val wifi = listOf(NetworkCapabilities.TRANSPORT_WIFI)
        val unmetered = listOf(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        assertTrue(gateWith(wifi, unmetered, allowMobileData = false))
        assertTrue(gateWith(wifi, unmetered, allowMobileData = true))
    }

    @Test
    fun cellularBlockedUnlessSettingOn() {
        val cellular = listOf(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertFalse(gateWith(cellular, emptyList(), allowMobileData = false))
        assertTrue(gateWith(cellular, emptyList(), allowMobileData = true))
    }

    @Test
    fun meteredWifiBlockedUnlessSettingOn() {
        val wifi = listOf(NetworkCapabilities.TRANSPORT_WIFI)
        assertFalse(gateWith(wifi, emptyList(), allowMobileData = false))
        assertTrue(gateWith(wifi, emptyList(), allowMobileData = true))
    }

    @Test
    fun noNetworkBlockedEvenWhenSettingOn() {
        Shadows.shadowOf(manager()).setDefaultNetworkActive(false)
        assertFalse(isCoverArtNetworkAvailable(context(), allowMobileData = false))
        assertFalse(isCoverArtNetworkAvailable(context(), allowMobileData = true))
    }
}
