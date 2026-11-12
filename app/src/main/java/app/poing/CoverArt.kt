package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File

/**
 * Cover art, Wi-Fi only, cached. No art source is wired in Phase 3:
 * no API-key-free source with reliable per-title matching was
 * chosen — libretro-thumbnails names art by No-Intro title, which
 * our ROM internal titles do not reliably match — so the fetcher
 * is a no-op and tiles render a clean placeholder (system badge +
 * title). The cache and the Wi-Fi gate are real, so a source plugs
 * in later through [CoverArtFetcher] without touching the UI.
 */
fun interface CoverArtFetcher {
    /** Returns PNG/JPEG bytes for the game, or null when unavailable. Only ever called on Wi-Fi. */
    fun fetch(game: LibraryGame): ByteArray?
}

object NoOpCoverArtFetcher : CoverArtFetcher {
    override fun fetch(game: LibraryGame): ByteArray? = null
}

class CoverArtStore(
    private val root: File,
    private val fetcher: CoverArtFetcher = NoOpCoverArtFetcher,
) {
    fun coversDir(): File = File(root, "covers").apply { mkdirs() }
    fun coverFile(game: LibraryGame): File = File(coversDir(), "${game.id}.png")
    fun hasCover(game: LibraryGame): Boolean = coverFile(game).exists()

    /** Cached cover, or a Wi-Fi-only fetch cached for next time. Null means: show the placeholder. */
    fun coverOrFetch(game: LibraryGame, wifiAvailable: Boolean): File? {
        val file = coverFile(game)
        if (file.exists()) return file
        if (!wifiAvailable) return null
        val bytes = fetcher.fetch(game) ?: return null
        return try {
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }
}

/** True only on an unmetered Wi-Fi transport — cover art never downloads over mobile data. */
fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
