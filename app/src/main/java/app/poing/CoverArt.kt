package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Cover art, rebuilt 2026-11-15 (alpha.6): what is left of the
 * old CoverArt.kt after the rebuild deleted the pass pipeline
 * (CoverArtFetcher, LibretroThumbnailsFetcher, CoverArtStore,
 * fetchMissingCovers and the stored CoverArtStatus — step 3).
 * This file now holds only the pieces the rebuilt subsystem
 * still needs: the network result type ([HttpFetchResult]), the
 * gate ([CoverArtGate], [coverArtGateState],
 * [isCoverArtNetworkAvailable]), the time format
 * ([formatTraceTime]) and the [ConnectivityWatcher]. The fetch
 * itself lives in [CoverRepository]: per-system pipelines, disk
 * as a verified authority, no pass, no cap. The source is still the
 * libretro-thumbnails collection ([Thumbnails]): one file per
 * game, cached on the device, never bundled in the repo or the
 * APK. By default the fetch runs on unmetered Wi-Fi only; the
 * "Download cover art over mobile data" setting (off by default)
 * lets it run on mobile data too.
 */
/** "HH:mm:ss" for a rate-limit reset time. Pure, so it is unit-testable. */
fun formatTraceTime(atMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(atMs))

/**
 * The outcome of one capped GET: the bytes on success, else the
 * HTTP status (when the server answered) and a short error. The
 * old shape returned null for every failure, so a 403 rate
 * limit, a timeout and a DNS failure were indistinguishable —
 * and invisible. The reason now reaches the library's status
 * line.
 */
data class HttpFetchResult(
    val bytes: ByteArray?,
    val statusCode: Int?,
    val error: String?,
) {
    companion object {
        fun success(bytes: ByteArray) = HttpFetchResult(bytes, 200, null)
        fun httpFailure(statusCode: Int) = HttpFetchResult(null, statusCode, "HTTP $statusCode")
        fun transportFailure(e: Exception) =
            HttpFetchResult(
                null,
                null,
                when (e) {
                    // A read/connect stall and a total-budget
                    // timeout are the same thing on the status
                    // line: "timed out", not the class name.
                    is java.net.SocketTimeoutException -> "timed out"
                    // A socket closed by a cancel (the repository
                    // abandons the call on timeout or gate close):
                    // "cancelled", not "SocketException".
                    is java.net.SocketException ->
                        if ((e.message ?: "").contains("closed", ignoreCase = true)) "cancelled"
                        else e.javaClass.simpleName
                    else -> e.javaClass.simpleName
                },
            )
    }
}

/**
 * Why the cover-art gate is open or closed. The Boolean gate
 * ([isCoverArtNetworkAvailable]) answers "may it fetch"; this
 * answers "why not", for the library's status line.
 */
enum class CoverArtGate { OPEN, NO_NETWORK, METERED_NO_OPT_IN }

/**
 * Why the cover-art gate is open or closed. Unmetered Wi-Fi
 * always qualifies. Any other connected network qualifies only
 * when [allowMobileData] is on — the user opted into data use
 * (the "Download cover art over mobile data" setting, off by
 * default). No active network never qualifies.
 * (2026-11-14: this was `isWifiConnected`, Wi-Fi-only; the
 * setting widened the gate, so the name follows the behaviour.)
 */
fun coverArtGateState(context: Context, allowMobileData: Boolean): CoverArtGate {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return CoverArtGate.NO_NETWORK
    val network = manager.activeNetwork ?: return CoverArtGate.NO_NETWORK
    val caps = manager.getNetworkCapabilities(network) ?: return CoverArtGate.NO_NETWORK
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    ) return CoverArtGate.OPEN
    return if (allowMobileData) CoverArtGate.OPEN else CoverArtGate.METERED_NO_OPT_IN
}

/**
 * True when cover art may be fetched — the gate is open (see
 * [coverArtGateState]). The Boolean answers "may it fetch";
 * the gate state answers "why not", for the status line.
 */
fun isCoverArtNetworkAvailable(context: Context, allowMobileData: Boolean): Boolean =
    coverArtGateState(context, allowMobileData) == CoverArtGate.OPEN

/**
 * Calls [onChanged] when the cover-art gate CHANGES — open→closed
 * included. Three fixes over the old watcher, which fired on every
 * network event: (1) debounce — a burst of onAvailable/onLost/
 * onCapabilitiesChanged within [DEBOUNCE_MS] collapses to one gate
 * check, so one network flap does not re-run the whole fetch
 * several times; (2) gate transition — only a change fires, not
 * every capability change (a flap that leaves the gate where it
 * was is not a retry); (3) the request asks only for networks
 * with internet capability. It deliberately does NOT filter to
 * Wi-Fi: the mobile-data setting lets the gate open on cellular
 * too, and a Wi-Fi-only request would never fire for that user.
 *
 * The gate is the full [CoverArtGate], not a Boolean: a
 * NO_NETWORK→METERED_NO_OPT_IN transition is a change the
 * status line must show, even though both are closed. And
 * open→closed fires (the 2026-11-14 watcher deliberately did
 * not — "not a retry"): the rebuilt repository needs the
 * gate-closed signal to park its pipelines, and a closed gate
 * that never reaches it leaves a Loading game behind.
 * Start/stop are the caller's lifecycle (a DisposableEffect);
 * stop cancels a pending debounced check and tolerates an
 * already-unregistered callback.
 */
class ConnectivityWatcher(
    context: Context,
    private val gateState: () -> CoverArtGate,
    private val onChanged: () -> Unit,
) {
    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastGate: CoverArtGate? = null
    private val check = Runnable {
        val gate = runCatching { gateState() }.getOrDefault(CoverArtGate.NO_NETWORK)
        if (gate != lastGate) onChanged()
        lastGate = gate
    }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleCheck()
        }

        override fun onLost(network: Network) {
            scheduleCheck()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            scheduleCheck()
        }
    }

    private fun scheduleCheck() {
        mainHandler.removeCallbacks(check)
        mainHandler.postDelayed(check, DEBOUNCE_MS)
    }

    fun start() {
        lastGate = runCatching { gateState() }.getOrDefault(CoverArtGate.NO_NETWORK)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager?.registerNetworkCallback(request, callback)
    }

    fun stop() {
        mainHandler.removeCallbacks(check)
        manager?.let { runCatching { it.unregisterNetworkCallback(callback) } }
    }

    companion object {
        /** Network events within one burst collapse to one gate check. */
        const val DEBOUNCE_MS: Long = 3_000L
    }
}
