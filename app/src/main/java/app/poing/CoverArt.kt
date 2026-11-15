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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.io.File
import java.net.HttpURLConnection
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

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

    /**
     * A new fetch pass starts: per-pass failure memoization (see
     * [LibretroThumbnailsFetcher]) clears, so the next pass
     * retries what the last one could not fetch. The default is
     * a no-op — fakes and NoOp never need it.
     */
    fun beginPass() {}

    /**
     * Load the indexes for these systems now, in parallel — the
     * pass calls this before any art, so one slow system cannot
     * hold the others behind it (see
     * [LibretroThumbnailsFetcher.prefetchIndexes]). The default
     * is a no-op: fakes and NoOp fetch art without an index.
     */
    fun prefetchIndexes(systems: Set<GameSystem>) {}

    /**
     * Abort any in-flight download, now — Retry calls this
     * before restarting the pass, so a hung fetch does not hold
     * the restart behind it (see
     * [LibretroThumbnailsFetcher.cancelInflight]). The default
     * is a no-op: fakes have nothing to abort.
     */
    fun cancelInflight() {}

    /** True when [cancelInflight] was called since the current pass began. The default is false. */
    fun passCancelled(): Boolean = false
}

/** One cover-art fetch event, for the library's visible fetch status (see [CoverArtStore]). */
sealed interface CoverArtFetchEvent {
    data class IndexLoaded(val system: GameSystem, val entries: Int, val fromCache: Boolean) :
        CoverArtFetchEvent

    data class IndexFailed(val system: GameSystem, val detail: String) : CoverArtFetchEvent
    data class ArtLanded(val game: LibraryGame) : CoverArtFetchEvent
    data class ArtFailed(val game: LibraryGame, val detail: String) : CoverArtFetchEvent

    /**
     * An index download just started — the status line shows
     * which system is being fetched while it runs, so a slow
     * one reads "fetching gba index…" instead of a bare
     * "fetching…" with nothing to show for it.
     */
    data class IndexStarted(val system: GameSystem) : CoverArtFetchEvent

    /** One art file download just started (see [IndexStarted]). */
    data class ArtStarted(val game: LibraryGame) : CoverArtFetchEvent
}

/**
 * The outcome of one capped GET: the bytes on success, else the
 * HTTP status (when the server answered) and a short error. The
 * old shape returned null for every failure, so a 403 rate
 * limit, a timeout and a DNS failure were indistinguishable —
 * and invisible. The reason now reaches the library's status
 * line (see [CoverArtStatus]).
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
                    // A socket closed by cancelInflight (Retry):
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

/** The index state for one system, for the library's status line. */
sealed interface CoverIndexStatus {
    data object NotTried : CoverIndexStatus
    data class Cached(val entries: Int) : CoverIndexStatus
    data class Downloaded(val entries: Int) : CoverIndexStatus
    data class Failed(val detail: String) : CoverIndexStatus
}

/**
 * The visible cover-art fetch status: the gate, one index state
 * per system, and this pass's outcome. Held in the store as
 * Compose state, so the library's status line updates as the
 * pass runs — a failed fetch used to leave nothing behind but
 * blank tiles.
 */
data class CoverArtStatus(
    val gate: CoverArtGate? = null,
    val indexes: Map<GameSystem, CoverIndexStatus> = emptyMap(),
    val fetching: Boolean = false,
    /**
     * What the pass is doing right now, e.g. "gba index" or
     * "gb art" — set by [CoverArtFetchEvent.IndexStarted] /
     * [CoverArtFetchEvent.ArtStarted], cleared when the pass
     * ends. The fetching line shows it, so a stalled download
     * names what it is stalled on.
     */
    val stage: String? = null,
    val totalGames: Int = 0,
    val haveCovers: Int = 0,
    val landedThisPass: Int = 0,
    val failedThisPass: Int = 0,
    val lastError: String? = null,
)

/** One line for the library: what the fetch is doing, or why it is not. Pure, so it is unit-testable. */
fun coverArtStatusLine(status: CoverArtStatus): String {
    if (status.fetching) {
        val what = status.stage?.let { "fetching $it… " } ?: "fetching… "
        return "Cover art: $what${status.landedThisPass} landed" +
            (if (status.failedThisPass > 0) ", ${status.failedThisPass} failed" else "") +
            (status.lastError?.let { " · $it" } ?: "")
    }
    val gatePart = when (status.gate) {
        null -> "checking…"
        CoverArtGate.OPEN -> "Wi-Fi OK"
        CoverArtGate.NO_NETWORK -> "no network"
        CoverArtGate.METERED_NO_OPT_IN -> "metered Wi-Fi — mobile data off"
    }
    val indexPart = if (status.indexes.isEmpty()) "" else " · " + status.indexes.entries
        .sortedBy { it.key.name }
        .joinToString(" ") { (system, state) ->
            val name = system.name.lowercase()
            when (state) {
                is CoverIndexStatus.Cached -> "$name index cached (${state.entries})"
                is CoverIndexStatus.Downloaded -> "$name index ${state.entries}"
                is CoverIndexStatus.Failed -> "$name index failed (${state.detail})"
                CoverIndexStatus.NotTried -> "$name index —"
            }
        }
    val err = status.lastError?.let { " · $it" } ?: ""
    return "Cover art: ${status.haveCovers}/${status.totalGames} covers · $gatePart$indexPart$err"
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
    private val httpGet: (String, Int) -> HttpFetchResult = { url, maxBytes ->
        realHttp.get(url, maxBytes)
    },
    /**
     * Total budget for one index download (connect + read).
     * The old code had only a 15s per-read stall timeout, so
     * a trickle that never stalled 15s held the pass open
     * indefinitely — the 6 MB GBA tree at a few KB/s is
     * minutes, all of it reading "fetching… 0 landed". The
     * budget is a total: slower than ~130 KB/s for the GBA
     * tree fails fast with "timed out" instead, and the other
     * systems still land. Injectable so tests can use a short
     * budget with a fake that never returns. These sit before
     * [onEvent] so the trailing-lambda form
     * `LibretroThumbnailsFetcher(root, httpGet) { events += it }`
     * keeps working — the last parameter must stay the event
     * callback.
     */
    private val indexBudgetMs: Long = INDEX_BUDGET_MS,
    /** Total budget for one art file (a few hundred KB). See [indexBudgetMs]. */
    private val artBudgetMs: Long = ART_BUDGET_MS,
    /** One event per fetch outcome, for the library's status line. No-op by default (tests, fakes). */
    var onEvent: (CoverArtFetchEvent) -> Unit = {},
) : CoverArtFetcher {
    // The parsed index, cached in memory for the fetcher's
    // lifetime — loadIndex used to re-read art-index-<system>.txt
    // and re-run normalizeIndex for every game, every pass (and
    // re-download the 7.2 MB GBA tree when the file was absent).
    // One library visit, one parse per system. Failures are not
    // cached, so a transient failure retries on the next pass.
    // Concurrent maps: prefetchIndexes loads the systems on
    // separate threads (see below).
    private val indexCache = ConcurrentHashMap<GameSystem, List<Thumbnails.IndexedArt>>()
    // Games whose titles match nothing in the index. The match is
    // deterministic for a fixed index, so a miss is memoized for
    // the fetcher's lifetime — the fetcher is remembered in
    // LibraryScreen, so a fresh library visit starts clean.
    // Download failures are NOT memoized: a transient failure
    // may succeed on the next pass.
    private val noMatchIds = mutableSetOf<String>()
    // Index downloads that failed this pass, with the reason. A
    // failed index used to be re-attempted for every game of the
    // system in the same pass — each GBA game re-downloaded the
    // 6 MB tree — which on a failing network multiplies both
    // time and GitHub API calls, and can hold a 403 rate limit
    // open across visits. One attempt per system per pass;
    // beginPass clears it, so the next pass retries.
    private val failedIndex = ConcurrentHashMap<GameSystem, String>()
    // The last index failure per system, kept past beginPass so
    // the status line can still name it until a load succeeds.
    private val lastIndexFailure = ConcurrentHashMap<GameSystem, String>()

    // Every httpGet runs on this pool with a total budget (see
    // getBudgeted): a fake or a socket that never returns is
    // bounded by the budget, not by the caller's patience.
    // Daemon threads, so a stuck worker can never hold the
    // process open; a cached pool, so the parallel prefetch
    // does not serialize on one worker.
    private val fetchExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "poing-cover-fetch").apply { isDaemon = true }
    }
    // Futures currently inside getBudgeted, so cancelInflight
    // can interrupt them (see below).
    private val inflightFutures = Collections.synchronizedSet(mutableSetOf<Future<*>>())

    // The cancel generation: cancelInflight bumps it, beginPass
    // baselines it, and the pass loop bails when it moves (see
    // passCancelled). A cancel from an old pass must not cancel
    // the new one — the baseline is taken at beginPass, after
    // any Retry tap that preceded it.
    private val cancelGen = AtomicInteger(0)

    @Volatile
    private var passGen = 0

    override fun beginPass() {
        failedIndex.clear()
        passGen = cancelGen.get()
    }

    override fun passCancelled(): Boolean = cancelGen.get() != passGen

    /**
     * Abort any in-flight download, now. Cancelling the future
     * interrupts its worker — which unblocks a sleeping fake
     * but NOT a plain socket read (socket I/O ignores thread
     * interrupt) — so the socket is disconnected too, which
     * aborts the read at once. Retry calls this before
     * restarting the pass: the old pass then unwinds promptly
     * instead of holding the restart behind a hung download.
     */
    override fun cancelInflight() {
        cancelGen.incrementAndGet()
        synchronized(inflightFutures) { inflightFutures.toList() }.forEach { it.cancel(true) }
        realHttp.cancelAll()
    }

    /**
     * Load the indexes for these systems now, one thread per
     * system, and wait for all of them. The old pass fetched
     * indexes lazily in game order, so the 6 MB GBA tree —
     * first game alphabetically — held every other system
     * behind it for the whole download, and the status sat at
     * "fetching… 0 landed". Each load is bounded by the index
     * budget, so the join is bounded by one budget, not the
     * sum of three. Systems already cached (memory or failed
     * this pass) are skipped, never re-downloaded.
     */
    override fun prefetchIndexes(systems: Set<GameSystem>) {
        val missing = systems.filter { system ->
            !indexCache.containsKey(system) && !failedIndex.containsKey(system)
        }
        if (missing.isEmpty()) return
        val threads = missing.map { system ->
            Thread({ loadIndex(system) }, "poing-index-${system.name.lowercase()}").apply {
                isDaemon = true
                start()
            }
        }
        for (t in threads) t.join()
    }

    /**
     * One capped GET with a TOTAL budget: the call returns
     * within [budgetMs] come what may — a fake that never
     * returns, a socket that trickles forever. On a timeout
     * the socket is disconnected (so the worker's blocking
     * read aborts instead of downloading in the background)
     * and the result names it "timed out".
     */
    private fun getBudgeted(url: String, maxBytes: Int, budgetMs: Long): HttpFetchResult {
        val future = fetchExecutor.submit<HttpFetchResult> { httpGet(url, maxBytes) }
        inflightFutures += future
        return try {
            future.get(budgetMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            realHttp.cancelForUrl(url)
            future.cancel(true)
            HttpFetchResult(null, null, "timed out")
        } catch (_: CancellationException) {
            HttpFetchResult(null, null, "cancelled")
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is Exception) HttpFetchResult.transportFailure(cause)
            else HttpFetchResult(null, null, cause?.javaClass?.simpleName ?: "failed")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            HttpFetchResult(null, null, "cancelled")
        } finally {
            inflightFutures -= future
        }
    }

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
            onEvent(CoverArtFetchEvent.ArtStarted(game))
            val result = getBudgeted(Thumbnails.artUrl(game.system, fileName), MAX_ART_BYTES, artBudgetMs)
            val bytes = result.bytes
            if (bytes == null) {
                val detail = result.error ?: "no bytes"
                // No Log.w here: android.util.Log throws in plain
                // JVM unit tests (RuntimeException: Stub!), and
                // the ArtFailed event below already carries the
                // reason to the library's status line — the
                // visible diagnostic.
                onEvent(CoverArtFetchEvent.ArtFailed(game, detail))
                return null
            }
            onEvent(CoverArtFetchEvent.ArtLanded(game))
            bytes
        } catch (e: CancellationException) {
            // A cancelled pass must stay cancelled: never
            // swallow it as a plain fetch failure.
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** The cached index for the system, or fetch it once and cache it. Null on any failure. */
    fun loadIndex(system: GameSystem): List<Thumbnails.IndexedArt>? {
        indexCache[system]?.let { return it }
        failedIndex[system]?.let {
            // Already failed this pass: the status line already
            // names it from the first failure — do not
            // re-download, and do not count it again.
            return null
        }
        val loaded = loadIndexUncached(system)
        if (loaded == null) {
            val detail = lastIndexFailure[system] ?: "unknown"
            failedIndex[system] = detail
            onEvent(CoverArtFetchEvent.IndexFailed(system, detail))
            return null
        }
        indexCache[system] = loaded
        return loaded
    }

    private fun loadIndexUncached(system: GameSystem): List<Thumbnails.IndexedArt>? {
        val file = indexFile(system)
        if (file.isFile) {
            try {
                val names = file.readLines().map { it.trim() }
                    .filter { it.endsWith(".png", ignoreCase = true) }
                if (names.isNotEmpty()) {
                    val loaded = Thumbnails.normalizeIndex(names)
                    onEvent(CoverArtFetchEvent.IndexLoaded(system, loaded.size, fromCache = true))
                    return loaded
                }
            } catch (_: Exception) {
                // Fall through to a fresh fetch.
            }
        }
        // The status line shows this system while the download
        // runs — a slow index names itself instead of leaving
        // the pass at a bare "fetching…".
        onEvent(CoverArtFetchEvent.IndexStarted(system))
        val result = getBudgeted(Thumbnails.indexUrl(system), MAX_TREE_BYTES, indexBudgetMs)
        val json = result.bytes?.decodeToString()
        if (json == null) {
            val detail = result.error ?: "no bytes"
            // No Log.w here, for the same reason as the art
            // failure above: the IndexFailed event (see
            // loadIndex) carries the reason to the status line.
            lastIndexFailure[system] = detail
            return null
        }
        val names = Thumbnails.parseTreeJson(json)
        if (names.isEmpty()) {
            lastIndexFailure[system] = "empty index"
            return null
        }
        try {
            file.writeText(names.joinToString("\n") + "\n")
        } catch (_: Exception) {
            // The in-memory list is still usable this run.
        }
        val loaded = Thumbnails.normalizeIndex(names)
        onEvent(CoverArtFetchEvent.IndexLoaded(system, loaded.size, fromCache = false))
        return loaded
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

        /**
         * Total budget for one index download (see
         * [LibretroThumbnailsFetcher.indexBudgetMs]): 45s at
         * the GBA tree's 6 MB is ~130 KB/s — a connection
         * slower than that fails fast with "timed out" rather
         * than holding the pass open for minutes.
         */
        const val INDEX_BUDGET_MS: Long = 45_000

        /** Total budget for one art file (see [LibretroThumbnailsFetcher.artBudgetMs]). */
        const val ART_BUDGET_MS: Long = 20_000

        /**
         * The one shared real HTTP layer. The default httpGet
         * is a constructor default, so it cannot close over a
         * per-instance layer — and there is exactly one real
         * fetcher in the app (LibraryScreen), so one shared
         * layer is the honest shape. Tests inject fakes and
         * never touch it. Its connection registry is what
         * lets a timeout or a Retry disconnect a hung socket
         * (see [RealHttpGet]).
         */
        private val realHttp = RealHttpGet()
    }
}

/**
 * A capped GET: the bytes on success, else the HTTP status or
 * the transport failure — never a bare null. The connection is
 * registered while it runs, so a timeout or a Retry can
 * disconnect it: a plain socket read ignores thread
 * interrupt, so disconnect is the only thing that aborts a
 * hung read at once.
 */
private class RealHttpGet {
    private val inflight = Collections.synchronizedSet(mutableSetOf<HttpURLConnection>())

    fun get(url: String, maxBytes: Int): HttpFetchResult {
        val conn = try {
            (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Poing")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
        } catch (e: Exception) {
            return HttpFetchResult.transportFailure(e)
        }
        inflight += conn
        return try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return HttpFetchResult.httpFailure(code)
            }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) {
                        return HttpFetchResult(null, code, "over ${maxBytes}B cap")
                    }
                }
            }
            HttpFetchResult.success(out.toByteArray())
        } catch (e: Exception) {
            HttpFetchResult.transportFailure(e)
        } finally {
            inflight -= conn
            conn.disconnect()
        }
    }

    /** Disconnect the in-flight connection for this URL, if any (see [LibretroThumbnailsFetcher.getBudgeted]). */
    fun cancelForUrl(url: String) {
        snapshot().filter { it.url.toString() == url }.forEach { it.disconnect() }
    }

    /** Disconnect every in-flight connection (see [LibretroThumbnailsFetcher.cancelInflight]). */
    fun cancelAll() {
        snapshot().forEach { it.disconnect() }
    }

    private fun snapshot(): List<HttpURLConnection> = synchronized(inflight) { inflight.toList() }
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

    /**
     * The visible fetch status (see [CoverArtStatus]): the gate,
     * one index state per system, and this pass's outcome. The
     * library's status line reads this state, so a failed fetch
     * leaves a reason on screen instead of blank tiles.
     */
    val status = mutableStateOf(CoverArtStatus())

    /** Fold one fetch event into the visible status. Wired as the fetcher's event callback in LibraryScreen. */
    fun applyEvent(event: CoverArtFetchEvent) {
        val cur = status.value
        status.value = when (event) {
            is CoverArtFetchEvent.IndexLoaded -> cur.copy(
                indexes = cur.indexes + (event.system to
                    if (event.fromCache) CoverIndexStatus.Cached(event.entries)
                    else CoverIndexStatus.Downloaded(event.entries)),
            )
            is CoverArtFetchEvent.IndexFailed -> cur.copy(
                indexes = cur.indexes + (event.system to CoverIndexStatus.Failed(event.detail)),
                failedThisPass = cur.failedThisPass + 1,
                lastError = "index ${event.system.name.lowercase()}: ${event.detail}",
                // The failure is named in lastError now; the
                // stage must not keep claiming the failed
                // download is still running.
                stage = null,
            )
            is CoverArtFetchEvent.ArtLanded -> cur.copy(
                landedThisPass = cur.landedThisPass + 1,
            )
            is CoverArtFetchEvent.ArtFailed -> cur.copy(
                failedThisPass = cur.failedThisPass + 1,
                lastError = "art ${event.game.title}: ${event.detail}",
                stage = null,
            )
            is CoverArtFetchEvent.IndexStarted -> cur.copy(
                stage = "${event.system.name.lowercase()} index",
            )
            is CoverArtFetchEvent.ArtStarted -> cur.copy(
                stage = "${event.game.system.name.lowercase()} art",
            )
        }
    }

    /** A new fetch pass starts: the fetcher's per-pass failure memo clears, and the per-pass counts reset (index states stay). */
    fun beginPass() {
        fetcher.beginPass()
        val cur = status.value
        status.value = cur.copy(
            fetching = true,
            stage = null,
            landedThisPass = 0,
            failedThisPass = 0,
            lastError = null,
        )
    }

    /** The gate for this pass, for the status line. */
    fun noteGate(gate: CoverArtGate) {
        status.value = status.value.copy(gate = gate)
    }

    /** The pass finished (landed or not): the library counts, for the status line. */
    fun noteCounts(totalGames: Int, haveCovers: Int) {
        status.value = status.value.copy(
            fetching = false,
            stage = null,
            totalGames = totalGames,
            haveCovers = haveCovers,
        )
    }

    /** Load the indexes for these systems now, in parallel (see [CoverArtFetcher.prefetchIndexes]). */
    fun prefetchIndexes(systems: Set<GameSystem>) = fetcher.prefetchIndexes(systems)

    /** Abort any in-flight download, now — Retry calls this before restarting the pass. */
    fun cancelFetch() = fetcher.cancelInflight()

    /** True when [cancelFetch] was called since the current pass began. */
    fun passCancelled(): Boolean = fetcher.passCancelled()

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
    art.beginPass()
    if (networkAvailable) {
        // The three indexes in parallel, before any art: the
        // old pass fetched them lazily in game order, so the
        // 6 MB GBA tree (first game alphabetically) held every
        // other system behind it for the whole download, and
        // the status sat at "fetching… 0 landed".
        art.prefetchIndexes(games.filter { !art.hasCover(it) }.map { it.system }.toSet())
    }
    var landed = false
    var landedCount = 0
    for (game in games) {
        // A Retry tap cancels the pass: stop after the current
        // game instead of grinding through the rest, so the
        // restart the tap queued starts promptly.
        if (art.passCancelled()) break
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
