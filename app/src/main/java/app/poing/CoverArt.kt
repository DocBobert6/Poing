package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File

/**
 * Cover art, Wi-Fi only, cached. The source is the
 * libretro-thumbnails collection ([Thumbnails]): one file per game,
 * fetched on demand when its tile is shown, then cached on the
 * device. Never a silent bulk download of the library, never
 * bundled in the repo or the APK. Offline, no match, or a bad
 * download means the placeholder stays, with no error and no
 * blocking.
 *
 * The filename index is fetched once per system and cached
 * (art-index-gb/gbc/gba.txt); the art itself is only ever fetched
 * one file at a time, per game. No API key: the public git-trees
 * listing is unauthenticated, same as HowlingDog — the
 * unauthenticated limit is 60/hr, and the cache means the call
 * happens once per system, ever.
 */
fun interface CoverArtFetcher {
    /** Returns PNG/JPEG bytes for the game, or null when unavailable. Only ever called on Wi-Fi. */
    fun fetch(game: LibraryGame): ByteArray?
}

object NoOpCoverArtFetcher : CoverArtFetcher {
    override fun fetch(game: LibraryGame): ByteArray? = null
}

/**
 * The real fetcher: the libretro-thumbnails collection for the
 * game's system. The filename stem is tried first (usually the
 * full No-Intro name), then the ROM header title (abbreviated by
 * the hardware) — see [Thumbnails.resolveArtFile]. Any failure at
 * any step returns null and the caller keeps the placeholder.
 *
 * The HTTP layer is injectable so the index cache, the match and
 * the download are testable on the plain JVM without a network.
 */
class LibretroThumbnailsFetcher(
    private val root: File,
    private val httpGet: (String, Int) -> ByteArray? = ::defaultHttpGet,
) : CoverArtFetcher {
    override fun fetch(game: LibraryGame): ByteArray? {
        return try {
            val index = loadIndex(game.system) ?: return null
            val stem = game.sourceName.substringBeforeLast('.', game.sourceName)
            val fileName = Thumbnails.resolveArtFile(stem, game.title, index) ?: return null
            httpGet(Thumbnails.artUrl(game.system, fileName), MAX_ART_BYTES)
        } catch (_: Exception) {
            null
        }
    }

    /** The cached index for the system, or fetch it once and cache it. Null on any failure. */
    fun loadIndex(system: GameSystem): List<Thumbnails.IndexedArt>? {
        val file = indexFile(system)
        if (file.isFile) {
            try {
                val names = file.readLines().map { it.trim() }
                    .filter { it.endsWith(".png", ignoreCase = true) }
                if (names.isNotEmpty()) return Thumbnails.normalizeIndex(names)
            } catch (_: Exception) {
                // Fall through to a fresh fetch.
            }
        }
        val json = httpGet(Thumbnails.indexUrl(system), MAX_TREE_BYTES)
            ?.decodeToString() ?: return null
        val names = Thumbnails.parseTreeJson(json)
        if (names.isEmpty()) return null
        try {
            file.writeText(names.joinToString("\n") + "\n")
        } catch (_: Exception) {
            // The in-memory list is still usable this run.
        }
        return Thumbnails.normalizeIndex(names)
    }

    private fun indexFile(system: GameSystem): File =
        File(root, "art-index-${system.name.lowercase()}.txt")

    companion object {
        /** Box art is a few hundred KB; the index JSON is the git tree. */
        const val MAX_ART_BYTES: Int = 2 * 1024 * 1024

        /**
         * The GBA tree JSON measured 7.2 MB on 2026-11-14 — the
         * cap sits just above it. A tree that grows past the cap
         * fails the fetch (placeholder, no crash) instead of
         * downloading without a bound.
         */
        const val MAX_TREE_BYTES: Int = 8 * 1024 * 1024
    }
}

/** A capped GET: null on any non-200 response, any over-cap body, or any failure. */
private fun defaultHttpGet(url: String, maxBytes: Int): ByteArray? {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", "Poing")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        if (conn.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return null // over the cap
            }
        }
        return out.toByteArray()
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
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
