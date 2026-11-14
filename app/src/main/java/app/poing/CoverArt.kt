package app.poing

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.io.File

/**
 * Cover art, Wi-Fi by default, cached. The source is the
 * libretro-thumbnails collection ([Thumbnails]): one file per game,
 * fetched on demand when its tile is shown, then cached on the
 * device. Never a silent bulk download of the library, never
 * bundled in the repo or the APK. Offline, no match, or a bad
 * download means the placeholder stays, with no error and no
 * blocking. By default the fetch runs on unmetered Wi-Fi only;
 * the "Download cover art over mobile data" setting (off by
 * default) lets it run on mobile data too — see
 * [isCoverArtNetworkAvailable].
 *
 * The filename index is fetched once per system and cached
 * (art-index-gb/gbc/gba.txt); the art itself is only ever fetched
 * one file at a time, per game. No API key: the public git-trees
 * listing is unauthenticated, same as HowlingDog — the
 * unauthenticated limit is 60/hr, and the cache means the call
 * happens once per system, ever.
 */
fun interface CoverArtFetcher {
    /** Returns PNG/JPEG bytes for the game, or null when unavailable. Only ever called when the network gate allows it. */
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
    // The parsed index, cached in memory for the fetcher's
    // lifetime — loadIndex used to re-read art-index-<system>.txt
    // and re-run normalizeIndex for every game, every pass (and
    // re-download the 7.2 MB GBA tree when the file was absent).
    // One library visit, one parse per system. Failures are not
    // cached, so a transient failure retries on the next pass.
    private val indexCache = mutableMapOf<GameSystem, List<Thumbnails.IndexedArt>>()
    // Games whose titles match nothing in the index. The match is
    // deterministic for a fixed index, so a miss is memoized for
    // the fetcher's lifetime — the fetcher is remembered in
    // LibraryScreen, so a fresh library visit starts clean.
    // Download failures are NOT memoized: a transient failure
    // may succeed on the next pass.
    private val noMatchIds = mutableSetOf<String>()

    override fun fetch(game: LibraryGame): ByteArray? {
        if (game.id in noMatchIds) return null
        return try {
            val index = loadIndex(game.system) ?: return null
            val stem = game.sourceName.substringBeforeLast('.', game.sourceName)
            val fileName = Thumbnails.resolveArtFile(stem, game.title, index)
            if (fileName == null) {
                noMatchIds += game.id
                return null
            }
            httpGet(Thumbnails.artUrl(game.system, fileName), MAX_ART_BYTES)
        } catch (_: Exception) {
            null
        }
    }

    /** The cached index for the system, or fetch it once and cache it. Null on any failure. */
    fun loadIndex(system: GameSystem): List<Thumbnails.IndexedArt>? {
        indexCache[system]?.let { return it }
        val loaded = loadIndexUncached(system) ?: return null
        indexCache[system] = loaded
        return loaded
    }

    private fun loadIndexUncached(system: GameSystem): List<Thumbnails.IndexedArt>? {
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
    return try {
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
        out.toByteArray()
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
    // Resolved once — coversDir() used to mkdirs() on every call,
    // including from tile composition.
    private val coversDirFile: File by lazy { File(root, "covers").apply { mkdirs() } }

    /**
     * Decoded covers, keyed by game id. The tiles read this state
     * map, so a landed cover redraws only its own row — the old
     * tiles decoded in composition and keyed on a global version,
     * so one landed cover recomposed every row. Populated by the
     * library's IO fetch effect (see LibraryScreen), never from
     * composition.
     */
    val coverBitmaps: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()

    // Decode failures are memoized for the store's lifetime, so a
    // corrupt file is not re-decoded on every fetch pass. A newly
    // fetched cover clears the entry (see coverOrFetch).
    private val decodeFailedIds = mutableSetOf<String>()

    fun coversDir(): File = coversDirFile
    fun coverFile(game: LibraryGame): File = File(coversDir(), "${game.id}.png")
    fun hasCover(game: LibraryGame): Boolean = coverFile(game).exists()

    /**
     * Decode the game's cover file into the state map. Call from
     * IO — never from composition. No-op when there is no file,
     * when it is already decoded, or when a past decode failed.
     */
    fun decodeIntoCache(game: LibraryGame) {
        if (coverBitmaps.containsKey(game.id) || game.id in decodeFailedIds) return
        val file = coverFile(game)
        if (!file.exists()) return
        val bitmap = decodeCoverFile(file)
        if (bitmap != null) coverBitmaps[game.id] = bitmap else decodeFailedIds += game.id
    }

    /** Cached cover, or a fetch cached for next time (when the network gate allows it). Null means: show the placeholder. */
    fun coverOrFetch(game: LibraryGame, networkAvailable: Boolean): File? {
        val file = coverFile(game)
        if (file.exists()) {
            decodeFailedIds -= game.id
            return file
        }
        if (!networkAvailable) return null
        val bytes = fetcher.fetch(game) ?: return null
        return try {
            file.writeBytes(bytes)
            decodeFailedIds -= game.id
            file
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * True when cover art may be fetched. Unmetered Wi-Fi always
 * qualifies. Any other connected network qualifies only when
 * [allowMobileData] is on — the user opted into data use (the
 * "Download cover art over mobile data" setting, off by
 * default). False when there is no active network at all.
 * (2026-11-14: this was `isWifiConnected`, Wi-Fi-only; the
 * setting widened the gate, so the name follows the behaviour.)
 */
fun isCoverArtNetworkAvailable(context: Context, allowMobileData: Boolean): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    ) return true
    return allowMobileData
}

/**
 * Fetches covers for the games that lack one. Returns true when at
 * least one cover landed on disk (the caller bumps its covers
 * version so the effect runs another pass — see LibraryScreen).
 * Pure apart from the store and fetcher it is given, so it is
 * unit-testable with a fake fetcher.
 *
 * A pass fetches at most [maxNew] new covers: a library with
 * twenty missing covers does not block one giant pass — the
 * effect re-runs until a pass lands nothing.
 */
fun fetchMissingCovers(
    art: CoverArtStore,
    games: List<LibraryGame>,
    networkAvailable: Boolean,
    maxNew: Int = MAX_COVERS_PER_PASS,
): Boolean {
    var landed = false
    var landedCount = 0
    for (game in games) {
        if (landedCount >= maxNew) break
        if (!art.hasCover(game) && art.coverOrFetch(game, networkAvailable) != null) {
            landed = true
            landedCount++
        }
    }
    return landed
}

/** A pass fetches at most this many new covers (see [fetchMissingCovers]). */
const val MAX_COVERS_PER_PASS: Int = 12

/**
 * Calls [onChanged] when the cover-art gate opens — the network
 * goes from unusable to usable for cover fetches. Three fixes
 * over the old watcher, which fired on every network event:
 * (1) debounce — a burst of onAvailable/onLost/onCapabilitiesChanged
 * within [DEBOUNCE_MS] collapses to one gate check, so one network
 * flap does not re-run the whole fetch pass several times;
 * (2) gate transition — only closed→open fires, not every
 * capability change (a metered→unmetered flap with the gate still
 * closed is not a retry); (3) the request asks only for networks
 * with internet capability. It deliberately does NOT filter to
 * Wi-Fi: the mobile-data setting lets the gate open on cellular
 * too, and a Wi-Fi-only request would never fire for that user.
 * Start/stop are the caller's lifecycle (a DisposableEffect);
 * stop cancels a pending debounced check and tolerates an
 * already-unregistered callback.
 */
class ConnectivityWatcher(
    context: Context,
    private val gateOpen: () -> Boolean,
    private val onChanged: () -> Unit,
) {
    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastGateOpen = false
    private val check = Runnable {
        val open = runCatching { gateOpen() }.getOrDefault(false)
        if (open && !lastGateOpen) onChanged()
        lastGateOpen = open
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
        lastGateOpen = runCatching { gateOpen() }.getOrDefault(false)
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
