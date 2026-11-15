package app.poing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Cover art, rebuilt 2026-11-15 (alpha.6) — Robert's order:
 * rebuild the entire fetching from the start, after research.
 * Six rounds of incremental patching never got a cover onto a
 * tile, and the last one proved the fetch worked while the
 * display didn't. The rebuild replaces the global pass
 * (threads/futures/generations, one guard, a 12-per-pass cap)
 * with a [CoverRepository] as the single source of truth.
 *
 * The design answers the six bugs structurally (see
 * COVER_ART_REBUILD_PLAN.md):
 * 1. One source of truth — [covers], [indexes] and [trace] are
 *    the only state. Tiles, status line and trace all read the
 *    same map, so if the status says a cover landed, the tile
 *    shows it — by construction.
 * 2. The disk is the authority — but verified. A file on disk IS
 *    a cover only if it decodes. A startup scan re-verifies every
 *    file and deletes + re-queues the corrupt ones. No memo can
 *    ever brick a game: the only persistent facts are the file
 *    and the index cache.
 * 3. Per-system independence, by construction. One pipeline per
 *    system, each with its own index state machine, queue and
 *    job. No global pass, no global guard, no shared deadline.
 *    A stuck GBA index is a GBA-pipeline event; GB/GBC neither
 *    know nor care.
 * 4. No hand-rolled lifecycle. Coroutines, structured
 *    concurrency: one scope (SupervisorJob + Dispatchers.IO),
 *    one Job per system pipeline, [withTimeout] for every
 *    budget, cancellation via Job.cancel() + socket disconnect.
 *    A hang is impossible to express: every suspend point has
 *    a timeout or a cancellable parent.
 * 5. Verify before you trust. Bytes are decoded IN MEMORY before
 *    they touch disk. Only decodable bytes are written. The
 *    write and the state publish happen in the same worker
 *    step, in order: bytes → decode → write file → publish
 *    Ready → trace event. There is no window in which the trace
 *    says "landed" and the tile has nothing.
 * 6. Failures are state, not silence. Every game ends in a
 *    named state; every system ends in a named index state.
 *    Retry re-enqueues exactly the failed ones.
 *
 * Kept from the old subsystem, unchanged: [Thumbnails] (the
 * matcher — measured 50/50, live-verified), the mobile-data
 * gate + toggle, the Trace dialog, the index disk-cache format
 * (`art-index-<system>.txt`, bare filenames) and the cover
 * layout (`covers/<id>.png`, id = the file's SHA-256), so
 * nothing already on Robert's device is orphaned.
 *
 * Scope: the repository is APPLICATION-scoped. It is created
 * once in PoingHome (the setContent root — see MainActivity)
 * and passed to the library, so pipelines survive navigation
 * to Settings or the player: covers keep downloading while
 * Robert is elsewhere. The old fetcher did not survive that,
 * and re-entry re-ran the pass. [close] cancels the scope;
 * PoingHome calls it when the root composition leaves.
 */

/** One game, one state. The tile reads exactly this. */
sealed interface CoverState {
    /** Never attempted, or no file yet. */
    data object Missing : CoverState

    /** Download in flight. */
    data object Loading : CoverState

    /** A decodable file exists, and this bitmap was decoded from those exact bytes. */
    data class Ready(val bitmap: Bitmap, val file: File) : CoverState

    /** The index has no art for this title. Terminal until the index reloads. */
    data class NoMatch(val detail: String) : CoverState

    /** "timed out", "HTTP 403", "undecodable", … — re-attempted only by explicit retry. */
    data class Failed(val reason: String) : CoverState
}

/** One system, one index state. Independent per system. */
sealed interface IndexState {
    data object NotLoaded : IndexState
    data object Loading : IndexState
    data class Ready(val entries: Int, val fromCache: Boolean) : IndexState
    data class Failed(val reason: String) : IndexState
}

/**
 * One line in the cover-art trace: the on-device record of
 * what the fetch did. There is no pass any more, so there are
 * no pass events — the trace records pipeline lifecycle and
 * every stage of each game's journey, so the next failure
 * names itself instead of reading "landed but invisible".
 * The stages that would have caught the 2026-11-15 failure:
 * [ArtMatched] → [ArtStarted] → [ArtDownloaded] →
 * [ArtDecoded] → [ArtReady]. A download that never becomes a
 * tile stops at [ArtDiscarded] or [ArtFailed], with the reason.
 */
sealed interface CoverTraceEvent {
    val atMs: Long

    /** Plain wording for the trace view, e.g. "art ready: TETRIS". */
    fun line(): String

    data class PipelineStarted(override val atMs: Long, val system: GameSystem) : CoverTraceEvent {
        override fun line(): String = "pipeline ${system.name.lowercase()} started"
    }

    data class PipelineParked(
        override val atMs: Long,
        val system: GameSystem,
        val reason: String,
    ) : CoverTraceEvent {
        override fun line(): String = "pipeline ${system.name.lowercase()} parked: $reason"
    }

    data class IndexStarted(override val atMs: Long, val system: GameSystem) : CoverTraceEvent {
        override fun line(): String = "index ${system.name.lowercase()} started"
    }

    data class IndexLoaded(
        override val atMs: Long,
        val system: GameSystem,
        val entries: Int,
        val fromCache: Boolean,
    ) : CoverTraceEvent {
        override fun line(): String =
            "index ${system.name.lowercase()} loaded " +
                "($entries entries${if (fromCache) ", cached" else ""})"
    }

    data class IndexFailed(
        override val atMs: Long,
        val system: GameSystem,
        val detail: String,
    ) : CoverTraceEvent {
        override fun line(): String = "index ${system.name.lowercase()} failed: $detail"
    }

    data class ArtMatched(
        override val atMs: Long,
        val game: LibraryGame,
        val fileName: String,
    ) : CoverTraceEvent {
        override fun line(): String = "art matched: ${game.title} ($fileName)"
    }

    data class ArtStarted(override val atMs: Long, val game: LibraryGame) : CoverTraceEvent {
        override fun line(): String = "art started: ${game.title}"
    }

    data class ArtDownloaded(
        override val atMs: Long,
        val game: LibraryGame,
        val bytes: Int,
    ) : CoverTraceEvent {
        override fun line(): String = "art downloaded: ${game.title} ($bytes bytes)"
    }

    data class ArtDecoded(
        override val atMs: Long,
        val game: LibraryGame,
        val width: Int,
        val height: Int,
    ) : CoverTraceEvent {
        override fun line(): String = "art decoded: ${game.title} (${width}×$height)"
    }

    data class ArtReady(
        override val atMs: Long,
        val game: LibraryGame,
        val fileSize: Long,
    ) : CoverTraceEvent {
        override fun line(): String = "art ready: ${game.title}"
    }

    data class ArtFailed(
        override val atMs: Long,
        val game: LibraryGame,
        val detail: String,
    ) : CoverTraceEvent {
        override fun line(): String = "art failed: ${game.title}: $detail"
    }

    /** Bytes arrived but failed the in-memory decode: nothing was written, the game stays re-triable. */
    data class ArtDiscarded(
        override val atMs: Long,
        val game: LibraryGame,
        val reason: String,
    ) : CoverTraceEvent {
        override fun line(): String = "art discarded: ${game.title}: $reason"
    }

    /** The startup scan: N covers restored from disk, M corrupt files deleted + re-queued. */
    data class DiskScan(
        override val atMs: Long,
        val restored: Int,
        val discarded: Int,
    ) : CoverTraceEvent {
        override fun line(): String = "disk scan: $restored restored, $discarded discarded"
    }
}

/**
 * The trace view's text: the events, newest first, under a
 * header that says whether a fetch is running — or, when the
 * trace is empty, that no fetch has started at all. Pure, so
 * it is unit-testable. The empty wording is kept from the old
 * trace ("No fetch pass has started since the library opened.")
 * — it is the text Robert already knows.
 */
fun coverTraceLines(trace: List<CoverTraceEvent>, fetching: Boolean): String {
    val header = if (trace.isEmpty()) {
        "No fetch pass has started since the library opened."
    } else if (fetching) {
        "A fetch is running."
    } else {
        "Last fetch:"
    }
    val lines = trace.asReversed().map { "${formatTraceTime(it.atMs)} ${it.line()}" }
    return (listOf(header) + lines).joinToString("\n")
}

/** One line for the library: what the fetch is doing, or why it is not. Pure, so it is unit-testable. */
fun coverArtStatusLine(
    covers: Map<String, CoverState>,
    indexes: Map<GameSystem, IndexState>,
    gate: CoverArtGate?,
    games: List<LibraryGame>,
): String {
    val ready = covers.values.count { it is CoverState.Ready }
    val failedCount = covers.values.count { it is CoverState.Failed } +
        indexes.values.count { it is IndexState.Failed }
    // The first failure, deterministically: index failures
    // first (GB, GBC, GBA), then game failures in library order.
    // The old line showed the latest failure; the new one shows
    // the first — a status that changes as failures arrive is
    // not a status the user can read.
    val firstFailure: String? =
        GameSystem.entries.firstNotNullOfOrNull { system ->
            (indexes[system] as? IndexState.Failed)?.let { "index ${system.name.lowercase()}: ${it.reason}" }
        } ?: games.firstNotNullOfOrNull { game ->
            (covers[game.id] as? CoverState.Failed)?.let { "art ${game.title}: ${it.reason}" }
        }
    val fetching = indexes.values.any { it is IndexState.Loading } ||
        covers.values.any { it is CoverState.Loading }
    if (fetching) {
        val stage = GameSystem.entries.firstNotNullOfOrNull { system ->
            if (indexes[system] is IndexState.Loading) "${system.name.lowercase()} index" else null
        } ?: games.firstNotNullOfOrNull { game ->
            if (covers[game.id] is CoverState.Loading) "${game.system.name.lowercase()} art" else null
        }
        val what = stage?.let { "fetching $it… " } ?: "fetching… "
        return "Cover art: $what$ready landed" +
            (if (failedCount > 0) ", $failedCount failed" else "") +
            (firstFailure?.let { " · $it" } ?: "")
    }
    val gatePart = when (gate) {
        null -> "checking…"
        CoverArtGate.OPEN -> "Wi-Fi OK"
        CoverArtGate.NO_NETWORK -> "no network"
        CoverArtGate.METERED_NO_OPT_IN -> "metered Wi-Fi — mobile data off"
    }
    val indexPart = if (indexes.isEmpty()) "" else " · " + GameSystem.entries.mapNotNull { system ->
        val state = indexes[system] ?: return@mapNotNull null
        val name = system.name.lowercase()
        when (state) {
            is IndexState.Ready ->
                if (state.fromCache) "$name index cached (${state.entries})" else "$name index ${state.entries}"
            is IndexState.Failed -> "$name index failed (${state.reason})"
            IndexState.NotLoaded -> "$name index —"
            IndexState.Loading -> "$name index…"
        }
    }.joinToString(" ")
    val err = firstFailure?.let { " · $it" } ?: ""
    return "Cover art: $ready/${games.size} covers · $gatePart$indexPart$err"
}

/**
 * Decode cover bytes downsampled to at most 512px on a side.
 * Null when the bytes are not a decodable image. The PNG/JPEG
 * signature is checked first: under Robolectric, BitmapFactory
 * synthesises a bitmap from garbage bytes, so without the gate
 * a corrupt download would "decode" in tests and fail on the
 * device. On a real device BitmapFactory already rejects
 * garbage; the gate makes the two agree.
 */
fun decodeCoverBytes(bytes: ByteArray, maxDim: Int = 512): Bitmap? {
    if (!isPngOrJpeg(bytes)) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }
}

/** PNG magic (8 bytes) or JPEG SOI (2 bytes). The art is always PNG from libretro; JPEG is accepted because the old fetcher contract said PNG/JPEG. */
fun isPngOrJpeg(bytes: ByteArray): Boolean {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) return true
    return bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
}

/**
 * The 403 reason when GitHub's rate limit is the cause.
 * Pure, so it is unit-testable: the client parses the
 * `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers, and
 * this formats the reason the game state and the trace carry.
 * Null when the 403 is not a rate-limit exhaustion.
 */
fun rateLimitDetail(statusCode: Int, remaining: String?, resetEpochSeconds: String?): String? {
    if (statusCode != HttpURLConnection.HTTP_FORBIDDEN || remaining != "0") return null
    val reset = resetEpochSeconds?.toLongOrNull()?.let { " — resets ${formatTraceTime(it * 1000)}" } ?: ""
    return "HTTP 403 — rate limit$reset"
}

/**
 * A capped GET that is cancellable: the blocking read runs on
 * the caller's IO thread, and coroutine cancellation
 * disconnects the live socket — a plain socket read ignores
 * thread interrupt, so disconnect is the only thing that
 * aborts a hung read at once. The connection is a local, so
 * cancellation disconnects it by identity, never by URL
 * string: after a redirect the live socket belongs to the
 * followed connection, and the old URL-match disconnect could
 * miss it. No registry, no cancel-all: structured concurrency
 * owns the lifetime.
 *
 * `Accept: application/vnd.github+json` is sent only to
 * api.github.com, not to raw.githubusercontent.com (the old
 * layer sent it on art downloads too — harmless, wrong).
 */
class RealHttpClient {
    suspend fun get(url: String, maxBytes: Int): HttpFetchResult {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Poing")
                if (url.contains("api.github.com")) {
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
            }
        } catch (e: Exception) {
            return HttpFetchResult.transportFailure(e)
        }
        try {
            return suspendCancellableCoroutine { cont ->
                cont.invokeOnCompletion { if (cont.isCancelled) conn.disconnect() }
                val result = try {
                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        rateLimitDetail(
                            code,
                            conn.getHeaderField("X-RateLimit-Remaining"),
                            conn.getHeaderField("X-RateLimit-Reset"),
                        )?.let { HttpFetchResult(null, code, it) }
                            ?: HttpFetchResult.httpFailure(code)
                    } else {
                        readCapped(conn, maxBytes)
                    }
                } catch (e: Exception) {
                    HttpFetchResult.transportFailure(e)
                }
                // A resume after cancellation is ignored — the
                // cancellation propagates instead. Normal
                // completion resumes with the result.
                if (cont.isActive) cont.resume(result) {}
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(conn: HttpURLConnection, maxBytes: Int): HttpFetchResult {
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) {
                    return HttpFetchResult(null, HttpURLConnection.HTTP_OK, "over ${maxBytes}B cap")
                }
            }
        }
        return HttpFetchResult.success(out.toByteArray())
    }

    companion object {
        /** One shared client: it holds no state, and there is exactly one repository in the app. */
        val shared = RealHttpClient()
    }
}

/**
 * The cover-art repository: the single source of truth for
 * cover state. See the file header for the design.
 *
 * @param root the app's filesDir: indexes live at
 * `art-index-<system>.txt`, covers at `covers/<id>.png` — the
 * same on-disk formats as the old pipeline, so nothing already
 * on the device is orphaned.
 * @param httpGet the network layer. The default is the real
 * client; tests inject a fake. A fake that never returns must
 * suspend cancellably ([kotlinx.coroutines.delay] or a
 * suspendCancellableCoroutine) — a blocking sleep ignores
 * coroutine cancellation, so the budget could not end it.
 * @param decode the in-memory decode. The default is
 * [decodeCoverBytes]; tests inject a controlled double for
 * the truncated-bytes case, where the platform decoder's
 * behaviour differs between Robolectric and the device.
 * @param scope the repository's scope. Pipelines run here;
 * [close] cancels it. Tests pass their own scope or use the
 * default and close it.
 */
class CoverRepository(
    private val root: File,
    private val httpGet: suspend (String, Int) -> HttpFetchResult = { url, maxBytes ->
        RealHttpClient.shared.get(url, maxBytes)
    },
    private val indexBudgetMs: Long = INDEX_BUDGET_MS,
    private val artBudgetMs: Long = ART_BUDGET_MS,
    private val decode: (ByteArray) -> Bitmap? = ::decodeCoverBytes,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** THE single source of truth. Tiles, status line, trace all read this. */
    val covers: StateFlow<Map<String, CoverState>> get() = _covers.asStateFlow()
    private val _covers = MutableStateFlow<Map<String, CoverState>>(emptyMap())

    /** One index state per system. Independent per system. */
    val indexes: StateFlow<Map<GameSystem, IndexState>> get() = _indexes.asStateFlow()
    private val _indexes = MutableStateFlow<Map<GameSystem, IndexState>>(emptyMap())

    /** The trace, newest last, capped at [MAX_TRACE_EVENTS]. */
    val trace: StateFlow<List<CoverTraceEvent>> get() = _trace.asStateFlow()
    private val _trace = MutableStateFlow<List<CoverTraceEvent>>(emptyList())

    /** The gate as last set by [setGate]; null until the screen first reads it. */
    val gate: StateFlow<CoverArtGate?> get() = _gate.asStateFlow()
    private val _gate = MutableStateFlow<CoverArtGate?>(null)

    private val coversDirFile: File by lazy { File(root, "covers").apply { mkdirs() } }

    // The parsed index, cached in memory for the repository's
    // lifetime. Failures are not cached, so a transient failure
    // retries on the next explicit retry.
    private val indexCache = ConcurrentHashMap<GameSystem, List<Thumbnails.IndexedArt>>()

    // Decoded bitmaps restored from disk by the startup scan,
    // keyed by game id. A game whose file is here reaches Ready
    // without any network, on the first setGames.
    private val diskBitmaps = ConcurrentHashMap<String, Bitmap>()

    // The games the repository knows, keyed by id. setGames
    // replaces this; a game removed from the library leaves the
    // map (its file stays on disk, as before).
    private val knownGames = ConcurrentHashMap<String, LibraryGame>()

    // One job per system pipeline, while it has work. A job
    // ends when its queue drains; a new one starts on demand.
    private val pipelineJobs = ConcurrentHashMap<GameSystem, Job>()

    // The startup scan: verifies every file in covers/ and
    // deletes the corrupt ones. setGames awaits it, so a game
    // is never classified before the disk is known.
    private val scanDone: Job = scope.launch(Dispatchers.IO) { scanDisk() }

    /** Cancel the scope: pipelines park, in-flight downloads abort. The screen's owner calls this. */
    fun close() {
        scope.cancel()
    }

    /**
     * Wait until the startup scan and every running pipeline
     * are done, or the timeout elapses. For tests (and only
     * for tests — production code observes the StateFlows).
     * False when the timeout elapsed with work still running.
     */
    suspend fun awaitIdle(timeoutMs: Long = 10_000): Boolean {
        return try {
            withTimeout(timeoutMs) {
                scanDone.join()
                while (true) {
                    val active = synchronized(pipelineJobs) {
                        pipelineJobs.values.filter { it.isActive }
                    }
                    if (active.isEmpty()) break
                    active.forEach { it.join() }
                }
                true
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    /**
     * The library's game list changed (import, remove, first
     * open). Games with a verified file on disk reach Ready at
     * once, with no network; the rest become Missing and their
     * system pipelines start, when the gate is open. States a
     * game already holds (Loading, Ready, Failed, NoMatch) are
     * never reset by a later setGames — only new games are
     * classified, and removed games leave the map.
     */
    suspend fun setGames(games: List<LibraryGame>) {
        scanDone.join()
        val byId = games.associateBy { it.id }
        knownGames.clear()
        knownGames.putAll(byId)
        _covers.update { cur -> cur.filterKeys { it in byId } }
        for (game in games) {
            if (_covers.value[game.id] != null) continue
            val disk = diskBitmaps[game.id]
            if (disk != null) {
                publish(game.id, CoverState.Ready(disk, coverFile(game)))
            } else {
                publish(game.id, CoverState.Missing)
            }
        }
        if (_gate.value == CoverArtGate.OPEN) ensurePipelines()
    }

    /**
     * The gate changed. Opening starts (or resumes) the
     * pipelines for systems with Missing games; closing parks
     * them — running jobs are cancelled, and a game that was
     * Loading returns to Missing, not Failed: a cancel is not
     * a failure. The network label is for the trace only.
     */
    suspend fun setGate(gate: CoverArtGate, networkLabel: String? = null) {
        val prev = _gate.value
        _gate.value = gate
        if (gate == CoverArtGate.OPEN && prev != CoverArtGate.OPEN) {
            scanDone.join()
            ensurePipelines()
        } else if (gate != CoverArtGate.OPEN && prev == CoverArtGate.OPEN) {
            val running = synchronized(pipelineJobs) {
                pipelineJobs.entries.filter { it.value.isActive }.map { it.key }
            }
            pipelineJobs.values.forEach { it.cancel() }
            for (system in running) {
                recordTrace(
                    CoverTraceEvent.PipelineParked(
                        System.currentTimeMillis(),
                        system,
                        "gate closed",
                    ),
                )
            }
        }
    }

    /**
     * Re-enqueue exactly the failed games — and only them.
     * A Ready game is untouched; a NoMatch game stays NoMatch
     * (terminal until its index reloads). A failed index is
     * re-attempted: the systems that have failed games have
     * their Failed index state cleared, so the next pipeline
     * run downloads the index again instead of failing fast
     * on the remembered failure.
     */
    suspend fun retryFailed() {
        scanDone.join()
        val failedIds = _covers.value.entries
            .filter { it.value is CoverState.Failed }
            .map { it.key }
        if (failedIds.isEmpty()) return
        val systems = failedIds.mapNotNull { knownGames[it]?.system }.toSet()
        for (system in systems) {
            if (_indexes.value[system] is IndexState.Failed) {
                indexCache.remove(system)
                publishIndex(system, IndexState.NotLoaded)
            }
        }
        for (id in failedIds) publish(id, CoverState.Missing)
        if (_gate.value == CoverArtGate.OPEN) ensurePipelines()
    }

    /**
     * Force a re-download of one system's index (dev/diagnostic
     * — not wired to any UI): the memory entry and the disk
     * cache go, and that system's NoMatch and Failed games
     * re-queue, so a reloaded index can match what the old one
     * could not.
     */
    suspend fun refreshIndex(system: GameSystem) {
        scanDone.join()
        indexCache.remove(system)
        runCatching { indexFile(system).delete() }
        publishIndex(system, IndexState.NotLoaded)
        for ((id, state) in _covers.value) {
            if (knownGames[id]?.system == system &&
                (state is CoverState.NoMatch || state is CoverState.Failed)
            ) {
                publish(id, CoverState.Missing)
            }
        }
        if (_gate.value == CoverArtGate.OPEN) ensurePipeline(system)
    }

    /** The cover file for a game, whether it exists or not. */
    fun coverFile(game: LibraryGame): File = File(coversDir(), "${game.id}.png")

    fun coversDir(): File = coversDirFile

    // -- internals ---------------------------------------------------------

    private fun publish(id: String, state: CoverState) {
        _covers.update { it + (id to state) }
    }

    private fun publishIndex(system: GameSystem, state: IndexState) {
        _indexes.update { it + (system to state) }
    }

    private fun recordTrace(event: CoverTraceEvent) {
        _trace.update { (it + event).takeLast(MAX_TRACE_EVENTS) }
    }

    private fun indexFile(system: GameSystem): File =
        File(root, "art-index-${system.name.lowercase()}.txt")

    /**
     * The startup scan: every file in covers/ is decoded, in
     * memory, before it is trusted. A file that decodes is
     * restored (its bitmap waits in [diskBitmaps] for the
     * first setGames); a file that does not is deleted, and
     * its game re-queues on the next setGames — the file is
     * gone, so the game is Missing again. This is the step
     * that makes the 2026-11-15 outcome impossible: a corrupt
     * file can never sit on disk looking like a cover, and no
     * memo anywhere can block its re-download. Recorded in
     * the trace only when something was restored or
     * discarded, so an empty library leaves the trace empty.
     */
    private fun scanDisk() {
        try {
            val files = coversDirFile.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
                ?: emptyList()
            var restored = 0
            var discarded = 0
            for (file in files) {
                val id = file.nameWithoutExtension
                val bytes = runCatching { file.readBytes() }.getOrNull()
                val bitmap = bytes?.let { runCatching { decode(it) }.getOrNull() }
                if (bitmap != null) {
                    diskBitmaps[id] = bitmap
                    restored++
                } else {
                    runCatching { file.delete() }
                    discarded++
                }
            }
            if (restored > 0 || discarded > 0) {
                recordTrace(
                    CoverTraceEvent.DiskScan(System.currentTimeMillis(), restored, discarded),
                )
            }
        } catch (_: Exception) {
            // A failed scan must not take the repository down:
            // the games simply fetch from the network instead.
        }
    }

    private fun ensurePipelines() {
        val systems = _covers.value.keys.mapNotNull { knownGames[it]?.system }.toSet()
        for (system in systems) ensurePipeline(system)
    }

    private fun ensurePipeline(system: GameSystem) {
        synchronized(pipelineJobs) {
            val existing = pipelineJobs[system]
            if (existing != null && existing.isActive) return
            val job = scope.launch { runPipeline(system) }
            pipelineJobs[system] = job
            job.invokeOnCompletion { pipelineJobs.remove(system, job) }
        }
    }

    /**
     * One system's pipeline: load the index once, then work
     * its queue — one game at a time, in library order — until
     * nothing is Missing. A new game that arrives mid-run is
     * picked up by the same loop; a game that arrives after
     * the queue drained starts a fresh job through
     * [ensurePipeline]. The job ends when the queue drains;
     * it never retries on its own.
     */
    private suspend fun runPipeline(system: GameSystem) {
        recordTrace(CoverTraceEvent.PipelineStarted(System.currentTimeMillis(), system))
        val index = ensureIndex(system)
        if (index == null) {
            // The index failed: every queued game of this
            // system fails with the index reason, so the tile
            // state and the trace agree — and the job parks
            // instead of spinning on a download that will not
            // succeed until an explicit retry.
            val reason = (_indexes.value[system] as? IndexState.Failed)?.reason ?: "index failed"
            for (game in queuedGames(system)) {
                publish(game.id, CoverState.Failed("index ${system.name.lowercase()}: $reason"))
            }
            recordTrace(
                CoverTraceEvent.PipelineParked(
                    System.currentTimeMillis(),
                    system,
                    "index ${system.name.lowercase()}: $reason",
                ),
            )
            return
        }
        while (true) {
            val game = claimNext(system) ?: break
            try {
                processGame(game, index)
            } catch (e: CancellationException) {
                // Gate closed, Retry, or scope cancelled: the
                // game goes back to Missing — a cancel is not a
                // failure — and the cancellation propagates.
                publish(game.id, CoverState.Missing)
                throw e
            } catch (e: Exception) {
                // A bug in the matcher or the file write must not
                // kill the queue: the game fails with the reason
                // and the next game is processed.
                publish(game.id, CoverState.Failed(e.javaClass.simpleName))
                recordTrace(
                    CoverTraceEvent.ArtFailed(
                        System.currentTimeMillis(),
                        game,
                        e.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    /** Games of this system still waiting for a cover, in library order. */
    private fun queuedGames(system: GameSystem): List<LibraryGame> =
        knownGames.values
            .filter { it.system == system && _covers.value[it.id] is CoverState.Missing }
            .sortedBy { it.title }

    /** Claim the next waiting game of this system: Missing → Loading, atomically. Null when the queue is drained. */
    private fun claimNext(system: GameSystem): LibraryGame? {
        // Only this system's job ever claims its games, so the
        // snapshot-then-update below cannot lose a claim to a
        // rival — the conditional update is a belt-and-braces
        // guard, and a lost claim just leaves the game Missing
        // for the next pipeline run.
        val next = knownGames.values
            .filter { it.system == system && _covers.value[it.id] is CoverState.Missing }
            .minByOrNull { it.title } ?: return null
        var claimed = false
        _covers.update { cur ->
            if (cur[next.id] is CoverState.Missing) {
                claimed = true
                cur + (next.id to CoverState.Loading)
            } else {
                cur
            }
        }
        return if (claimed) next else null
    }

    /**
     * The index for this system: memory first, then the disk
     * cache, then one download. A remembered failure is
     * returned as-is — no re-download until an explicit retry
     * or [refreshIndex]. The whole load (download + parse +
     * cache write) sits inside one [withTimeout]: the old
     * code's budget bounded only the download, and a thread
     * stalled outside it held the pass forever. A timeout
     * anywhere in the step is named "timed out".
     */
    private suspend fun ensureIndex(system: GameSystem): List<Thumbnails.IndexedArt>? {
        indexCache[system]?.let { return it }
        val current = _indexes.value[system]
        if (current is IndexState.Failed) return null
        if (current is IndexState.Ready) return indexCache[system]
        publishIndex(system, IndexState.Loading)
        val result = try {
            withTimeout(indexBudgetMs) { loadIndexUncached(system) }
        } catch (_: TimeoutCancellationException) {
            IndexLoadResult.Failed("timed out")
        } catch (e: CancellationException) {
            // Gate closed or scope cancelled: park, don't
            // record a failure — the next run re-attempts.
            publishIndex(system, IndexState.NotLoaded)
            throw e
        } catch (e: Exception) {
            // A throwing HTTP layer (or a bug in the parse)
            // must not kill the pipeline: the index fails
            // with the reason, and the games fail with it.
            IndexLoadResult.Failed(e.javaClass.simpleName)
        }
        return when (result) {
            is IndexLoadResult.Loaded -> {
                indexCache[system] = result.index
                publishIndex(system, IndexState.Ready(result.index.size, result.fromCache))
                result.index
            }
            is IndexLoadResult.Failed -> {
                publishIndex(system, IndexState.Failed(result.detail))
                recordTrace(
                    CoverTraceEvent.IndexFailed(System.currentTimeMillis(), system, result.detail),
                )
                null
            }
        }
    }

    private sealed interface IndexLoadResult {
        data class Loaded(val index: List<Thumbnails.IndexedArt>, val fromCache: Boolean) :
            IndexLoadResult

        data class Failed(val detail: String) : IndexLoadResult
    }

    private suspend fun loadIndexUncached(system: GameSystem): IndexLoadResult {
        val file = indexFile(system)
        if (file.isFile) {
            val names = runCatching {
                file.readLines().map { it.trim() }
                    .filter { it.endsWith(".png", ignoreCase = true) }
            }.getOrNull()
            if (!names.isNullOrEmpty()) {
                val loaded = Thumbnails.normalizeIndex(names)
                // Cooperative cancellation: the parse is
                // CPU-bound with no suspension point, so a
                // budget timeout could not land mid-parse
                // without this — the yield lets it.
                yield()
                recordTrace(
                    CoverTraceEvent.IndexLoaded(
                        System.currentTimeMillis(),
                        system,
                        loaded.size,
                        fromCache = true,
                    ),
                )
                return IndexLoadResult.Loaded(loaded, fromCache = true)
            }
            // An empty or unreadable cache falls through to a
            // fresh fetch — a non-empty-but-truncated list is
            // accepted as before (its entries still match).
        }
        recordTrace(CoverTraceEvent.IndexStarted(System.currentTimeMillis(), system))
        val result = boundedGet(Thumbnails.indexUrl(system), MAX_TREE_BYTES, indexBudgetMs)
        val json = result.bytes?.decodeToString()
            ?: return IndexLoadResult.Failed(result.error ?: "no bytes")
        if (TRUNCATED_FLAG.containsMatchIn(json)) {
            return IndexLoadResult.Failed("index truncated")
        }
        val names = Thumbnails.parseTreeJson(json)
        if (names.isEmpty()) return IndexLoadResult.Failed("empty index")
        // Cooperative cancellation (see the cache path above):
        // the parse is CPU-bound, so the budget's timeout
        // lands here, before the cache write.
        yield()
        // Write via temp + rename, so a killed write can never
        // leave a partial cache behind.
        runCatching {
            val tmp = File(root, "art-index-${system.name.lowercase()}.txt.tmp")
            tmp.writeText(names.joinToString("\n") + "\n")
            if (!tmp.renameTo(file)) tmp.delete()
        }
        val loaded = Thumbnails.normalizeIndex(names)
        recordTrace(
            CoverTraceEvent.IndexLoaded(
                System.currentTimeMillis(),
                system,
                loaded.size,
                fromCache = false,
            ),
        )
        return IndexLoadResult.Loaded(loaded, fromCache = false)
    }

    /**
     * One game, start to finish, in one worker step: match →
     * download → decode in memory → write file → publish
     * Ready → trace. The write and the publish are adjacent
     * and in order, so there is no window in which the trace
     * says the cover landed and the tile has nothing — and a
     * decode failure writes nothing at all.
     */
    private suspend fun processGame(game: LibraryGame, index: List<Thumbnails.IndexedArt>) {
        val stem = game.sourceName.substringBeforeLast('.', game.sourceName)
        val fileName = Thumbnails.resolveArtFile(stem, game.title, index)
        if (fileName == null) {
            publish(game.id, CoverState.NoMatch("no art in index"))
            return
        }
        recordTrace(
            CoverTraceEvent.ArtMatched(System.currentTimeMillis(), game, fileName),
        )
        recordTrace(CoverTraceEvent.ArtStarted(System.currentTimeMillis(), game))
        val result = boundedGet(Thumbnails.artUrl(game.system, fileName), MAX_ART_BYTES, artBudgetMs)
        val bytes = result.bytes
        if (bytes == null) {
            val detail = result.error ?: "no bytes"
            publish(game.id, CoverState.Failed(detail))
            recordTrace(
                CoverTraceEvent.ArtFailed(System.currentTimeMillis(), game, detail),
            )
            return
        }
        recordTrace(
            CoverTraceEvent.ArtDownloaded(System.currentTimeMillis(), game, bytes.size),
        )
        val bitmap = try {
            decode(bytes)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            // Verify before you trust: nothing is written, the
            // game stays re-triable, and the trace names the
            // discard — the 2026-11-15 failure was a decode
            // failure that left a file on disk and a memo that
            // blocked every retry.
            publish(game.id, CoverState.Failed("undecodable"))
            recordTrace(
                CoverTraceEvent.ArtDiscarded(System.currentTimeMillis(), game, "undecodable"),
            )
            return
        }
        recordTrace(
            CoverTraceEvent.ArtDecoded(
                System.currentTimeMillis(),
                game,
                bitmap.width,
                bitmap.height,
            ),
        )
        val file = coverFile(game)
        try {
            val tmp = File(coversDir(), "${game.id}.png.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                file.writeBytes(bytes)
            }
        } catch (_: Exception) {
            publish(game.id, CoverState.Failed("write failed"))
            recordTrace(
                CoverTraceEvent.ArtFailed(System.currentTimeMillis(), game, "write failed"),
            )
            return
        }
        diskBitmaps[game.id] = bitmap
        publish(game.id, CoverState.Ready(bitmap, file))
        recordTrace(
            CoverTraceEvent.ArtReady(System.currentTimeMillis(), game, file.length()),
        )
    }

    /**
     * One capped GET with a TOTAL budget: the call returns
     * within [budgetMs] come what may — a fake that never
     * returns, a socket that trickles forever. A timeout is
     * named "timed out"; an external cancellation propagates
     * (the caller resets the game to Missing).
     */
    private suspend fun boundedGet(url: String, maxBytes: Int, budgetMs: Long): HttpFetchResult {
        return try {
            withTimeout(budgetMs) { httpGet(url, maxBytes) }
        } catch (_: TimeoutCancellationException) {
            HttpFetchResult(null, null, "timed out")
        }
    }

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
         * [ensureIndex]): 45s at the GBA tree's 6 MB is
         * ~130 KB/s — a connection slower than that fails fast
         * with "timed out" rather than holding the pipeline
         * open for minutes. The budgets were never the problem
         * once enforced per-step; they stay.
         */
        const val INDEX_BUDGET_MS: Long = 45_000

        /** Total budget for one art file (see [processGame]). */
        const val ART_BUDGET_MS: Long = 20_000

        /** The git-trees response says whether it was cut short. A truncated tree is a failure, not a short index. */
        private val TRUNCATED_FLAG = Regex(""""truncated"\s*:\s*true""")
    }
}
