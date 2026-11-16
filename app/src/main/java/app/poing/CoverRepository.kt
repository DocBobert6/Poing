package app.poing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
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
 * 1. One source of truth — [covers] and [indexes] are
 *    the only state. Tiles and the status line read the
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
 *    And one place where a plain withTimeout is NOT enough:
 *    the network call itself. A withTimeout around a blocking
 *    call cannot unwind the thread it is stuck on — and a
 *    call stuck in DNS/connect, before any socket exists for
 *    disconnect() to close, ignores both. (Device evidence
 *    2026-11-15 16:46: alpha.4's pass started 16:46:09 and
 *    was still "fetching gba index… 0 landed" ~2.5 minutes
 *    later, with no "timed out" — the 45s join deadline never
 *    fired.) So [boundedGet] RACES the call: it runs on its
 *    own IO thread, the pipeline awaits it with a timeout,
 *    and on timeout the thread is abandoned, not awaited —
 *    the pipeline moves on at the budget, whatever the thread
 *    is stuck in. The connection's own timeouts (10s connect,
 *    15s read) and disconnect-on-cancel are the first line of
 *    defence — they abort a real socket stall promptly — but
 *    the race is the guarantee, not the fallback.
 * 5. Verify before you trust. Bytes are decoded IN MEMORY before
 *    they touch disk. Only decodable bytes are written. The
 *    write and the state publish happen in the same worker
 *    step, in order: bytes → decode → write file → publish
 *    Ready. There is no window in which the status
 *    says "landed" and the tile has nothing.
 * 6. Failures are state, not silence. Every game ends in a
 *    named state; every system ends in a named index state.
 *    Retry re-enqueues exactly the failed ones.
 *
 * Kept from the old subsystem, unchanged: [Thumbnails] (the
 * matcher — measured 50/50, live-verified), the mobile-data
 * gate + toggle, the index disk-cache format
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
 * this formats the reason the game state carries.
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
                cont.invokeOnCancellation { conn.disconnect() }
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
 * on the device is orphaned. Attempted outcomes persist at
 * `cover-attempts.properties` (alpha.9, part B), so a second
 * app open with no new games makes no network fetch at all.
 * @param httpGet the network layer. The default is the real
 * client; tests inject a fake. A fake that blocks (a latch,
 * a sleep) no longer holds a pipeline: [boundedGet] races
 * the call and abandons its thread on timeout — but a
 * blocking fake still leaks that thread until it returns,
 * so cancellable fakes ([kotlinx.coroutines.delay] or a
 * suspendCancellableCoroutine) are still preferred.
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
    /** THE single source of truth. Tiles and the status line read this. */
    val covers: StateFlow<Map<String, CoverState>> get() = _covers.asStateFlow()
    private val _covers = MutableStateFlow<Map<String, CoverState>>(emptyMap())

    /** One index state per system. Independent per system. */
    val indexes: StateFlow<Map<GameSystem, IndexState>> get() = _indexes.asStateFlow()
    private val _indexes = MutableStateFlow<Map<GameSystem, IndexState>>(emptyMap())

    /** The gate as last set by [setGate]; null until the screen first reads it. */
    val gate: StateFlow<CoverArtGate?> get() = _gate.asStateFlow()
    private val _gate = MutableStateFlow<CoverArtGate?>(null)

    /**
     * Custom art (alpha.9): the user's own image per game,
     * game id → bitmap. Tiles prefer this over fetched art.
     * It lives in its own directory (`custom-covers/`, see
     * below), so a re-fetch can never clobber it — the
     * fetched pipeline neither reads nor writes there.
     */
    val customCovers: StateFlow<Map<String, Bitmap>> get() = _customCovers.asStateFlow()
    private val _customCovers = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    // Decoded custom bitmaps, keyed by game id: the
    // in-memory authority for the session, filled by the
    // startup scan (from disk) and by [setCustomCover].
    // The published flow is derived from this map, so a
    // game that leaves the library and returns re-publishes
    // without touching disk.
    private val customBitmaps = ConcurrentHashMap<String, Bitmap>()

    private val coversDirFile: File by lazy { File(root, "covers").apply { mkdirs() } }

    private val customCoversDirFile: File by lazy {
        File(root, "custom-covers").apply { mkdirs() }
    }

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

    // Attempted outcomes, persisted (alpha.9, part B): game id
    // → "NoMatch", or "Failed:<reason>". A game whose attempt
    // is recorded is never fetched again on app open — setGames
    // restores its terminal state instead of Missing — until an
    // explicit retry or index refresh clears the record. Ready
    // is implied by the file on disk and needs no record. The
    // file is `cover-attempts.properties` in the filesDir root.
    // The map is guarded by [attemptsLock]: [publish] runs from
    // three parallel pipeline coroutines and from the UI scope.
    private val attemptsLock = Any()
    private val attempts = mutableMapOf<String, String>()

    init {
        loadAttempts()
    }

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
                    // Join jobs that are not COMPLETED, not just
                    // jobs that are ACTIVE: a cancelled job is
                    // inactive while it unwinds, and returning
                    // before the unwind leaves a Loading game
                    // behind (2026-11-15: gateClosedParksAndGate-
                    // OpenedResumes failed on exactly this — the
                    // job was still publishing Missing when
                    // awaitIdle had already returned true).
                    val active = synchronized(pipelineJobs) {
                        pipelineJobs.values.filter { !it.isCompleted }
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
     * once, with no network; games with a recorded attempt
     * reach their terminal state at once, with no network
     * either (alpha.9, part B); the rest become Missing and
     * their system pipelines start, when the gate is open.
     * States a game already holds (Loading, Ready, Failed,
     * NoMatch) are never reset by a later setGames — only new
     * games are classified, and removed games leave the map.
     */
    suspend fun setGames(games: List<LibraryGame>) {
        scanDone.join()
        val byId = games.associateBy { it.id }
        knownGames.clear()
        knownGames.putAll(byId)
        _covers.update { cur -> cur.filterKeys { it in byId } }
        // Custom art (alpha.9): the published map follows
        // the library — a game that returns re-publishes its
        // cover from the in-memory map, a game that left
        // drops from the flow. The file stays on disk either
        // way, so a removed game that is re-imported gets
        // its cover back for free.
        _customCovers.value = customBitmaps.filterKeys { it in byId.keys }
        for (game in games) {
            if (_covers.value[game.id] != null) continue
            val disk = diskBitmaps[game.id]
            if (disk != null) {
                publish(game.id, CoverState.Ready(disk, coverFile(game)))
            } else {
                // A previous attempt is restored, not
                // re-fetched: the game reached a terminal state
                // in an earlier session (or this one, before the
                // library was re-entered). Only a game with no
                // record is Missing — and only a Missing game
                // ever starts a fetch.
                val attempt = synchronized(attemptsLock) { attempts[game.id] }
                when {
                    attempt == "NoMatch" ->
                        publish(game.id, CoverState.NoMatch("no art in index"))
                    attempt != null && attempt.startsWith("Failed:") ->
                        publish(game.id, CoverState.Failed(attempt.removePrefix("Failed:")))
                    else -> publish(game.id, CoverState.Missing)
                }
            }
        }
        if (_gate.value == CoverArtGate.OPEN) ensurePipelines()
    }

    /**
     * The gate changed. Opening starts (or resumes) the
     * pipelines for systems with Missing games; closing parks
     * them — running jobs are cancelled, and a game that was
     * Loading returns to Missing, not Failed: a cancel is not
     * a failure.
     */
    suspend fun setGate(gate: CoverArtGate) {
        val prev = _gate.value
        _gate.value = gate
        if (gate == CoverArtGate.OPEN && prev != CoverArtGate.OPEN) {
            scanDone.join()
            ensurePipelines()
        } else if (gate != CoverArtGate.OPEN && prev == CoverArtGate.OPEN) {
            val toJoin = pipelineJobs.values.toList()
            toJoin.forEach { it.cancel() }
            // Join the cancelled jobs: the unwind (a Loading
            // game back to Missing) must have happened by the
            // time this returns — otherwise a caller that reads
            // the covers right after the gate closes still sees
            // a stale Loading, and a reopen in the same breath
            // would race the old job. A cancelled job unwinds
            // in milliseconds (one state publish), so this does
            // not stall the caller.
            toJoin.forEach { it.join() }
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

    /**
     * Set the user's own cover for a game (alpha.9): the
     * picked image is decoded in memory — downsampled to
     * 512px on a side, the same gate as fetched art — and
     * stored as a PNG in `custom-covers/`, never in
     * `covers/`, so a re-fetch can never replace it. False
     * when the bytes are not a decodable image or the write
     * fails; nothing is written then, and the previous
     * custom cover (if any) stays.
     */
    suspend fun setCustomCover(game: LibraryGame, bytes: ByteArray): Boolean {
        val bitmap = try {
            decode(bytes)
        } catch (_: Exception) {
            null
        } ?: return false
        val pngBytes = try {
            ByteArrayOutputStream().also { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            }.toByteArray()
        } catch (_: Exception) {
            return false
        }
        if (pngBytes.isEmpty()) return false
        return try {
            val file = customCoverFile(game)
            val tmp = File(customCoversDir(), "${game.id}.png.tmp")
            tmp.writeBytes(pngBytes)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                file.writeBytes(pngBytes)
            }
            customBitmaps[game.id] = bitmap
            _customCovers.update { it + (game.id to bitmap) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Remove the user's cover for a game: the file goes and
     * the tile falls back to fetched art — or the placeholder
     * when the game has none. The fetched file is untouched.
     */
    fun removeCustomCover(game: LibraryGame) {
        runCatching { customCoverFile(game).delete() }
        customBitmaps.remove(game.id)
        _customCovers.update { it - game.id }
    }

    /** True when the user has set their own cover for this game. */
    fun hasCustomCover(game: LibraryGame): Boolean = _customCovers.value.containsKey(game.id)

    /** The user's cover file for a game, whether it exists or not. */
    fun customCoverFile(game: LibraryGame): File = File(customCoversDir(), "${game.id}.png")

    fun customCoversDir(): File = customCoversDirFile

    /** The cover file for a game, whether it exists or not. */
    fun coverFile(game: LibraryGame): File = File(coversDir(), "${game.id}.png")

    fun coversDir(): File = coversDirFile

    // -- internals ---------------------------------------------------------

    private fun attemptsFile(): File = File(root, "cover-attempts.properties")

    /** Read the persisted attempts, if any. A corrupt or unreadable file is treated as no attempts. */
    private fun loadAttempts() {
        val file = attemptsFile()
        if (!file.exists()) return
        val props = Properties()
        val loaded = runCatching {
            FileInputStream(file).use { props.load(it) }
        }.isSuccess
        if (!loaded) return
        synchronized(attemptsLock) {
            for ((k, v) in props) attempts[k as String] = v as String
        }
    }

    /** Write the attempts file. Call with [attemptsLock] held. A failed write is swallowed: the in-memory map still governs this session, and the next change retries the write. */
    private fun saveAttemptsLocked() {
        val props = Properties()
        for ((k, v) in attempts) props[k] = v
        val tmp = File(root, "cover-attempts.properties.tmp")
        try {
            FileOutputStream(tmp).use { props.store(it, null) }
            if (!tmp.renameTo(attemptsFile())) {
                tmp.delete()
                FileOutputStream(attemptsFile()).use { props.store(it, null) }
            }
        } catch (_: Exception) {
            runCatching { tmp.delete() }
        }
    }

    private fun publish(id: String, state: CoverState) {
        _covers.update { it + (id to state) }
        // Persisted attempts (alpha.9, part B): a terminal
        // outcome is recorded so the next app open restores it
        // instead of re-fetching; Ready and Missing clear the
        // record. Only rewrite the file when the map actually
        // changes, so a restore re-publishing the same value
        // costs no write.
        val record = when (state) {
            is CoverState.NoMatch -> "NoMatch"
            is CoverState.Failed -> "Failed:${state.reason}"
            else -> null
        }
        synchronized(attemptsLock) {
            val changed = if (record != null) {
                if (attempts[id] != record) {
                    attempts[id] = record
                    true
                } else {
                    false
                }
            } else {
                attempts.remove(id) != null
            }
            if (changed) saveAttemptsLocked()
        }
    }

    private fun publishIndex(system: GameSystem, state: IndexState) {
        _indexes.update { it + (system to state) }
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
     * memo anywhere can block its re-download.
     */
    private fun scanDisk() {
        try {
            scanCoverDir(coversDirFile, diskBitmaps)
            // Custom art (alpha.9): the same verify-before-
            // trust rule — a file that decodes IS a custom
            // cover, a file that does not is deleted and its
            // game falls back to fetched art.
            scanCoverDir(customCoversDirFile, customBitmaps)
            if (customBitmaps.isNotEmpty()) {
                _customCovers.value = customBitmaps.toMap()
            }
        } catch (_: Exception) {
            // A failed scan must not take the repository down:
            // the games simply fetch from the network instead.
        }
    }

    /**
     * Verify every file in one cover directory: a file that
     * decodes is kept (its bitmap waits in [into] for the
     * first setGames); a file that does not is deleted.
     * Returns (restored, discarded).
     */
    private fun scanCoverDir(
        dir: File,
        into: MutableMap<String, Bitmap>,
    ): Pair<Int, Int> {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
            ?: emptyList()
        var restored = 0
        var discarded = 0
        for (file in files) {
            val id = file.nameWithoutExtension
            val bytes = runCatching { file.readBytes() }.getOrNull()
            val bitmap = bytes?.let { runCatching { decode(it) }.getOrNull() }
            if (bitmap != null) {
                into[id] = bitmap
                restored++
            } else {
                runCatching { file.delete() }
                discarded++
            }
        }
        return restored to discarded
    }

    private fun ensurePipelines() {
        // A system with no Missing game starts no pipeline —
        // so a second app open with no new games makes no
        // network fetch at all, not even an index download: the
        // index is not persisted across opens, so ensureIndex
        // would otherwise re-download a never-loaded index on
        // every open. A new Missing game is picked up because
        // setGames calls this when the gate is open, and
        // retryFailed publishes Missing before calling it.
        val systems = _covers.value.entries
            .filter { it.value is CoverState.Missing }
            .mapNotNull { knownGames[it.key]?.system }
            .toSet()
        for (system in systems) ensurePipeline(system)
    }

    private fun ensurePipeline(system: GameSystem) {
        synchronized(pipelineJobs) {
            val existing = pipelineJobs[system]
            // A previous run that is not COMPLETED — running, or
            // cancelled but still unwinding — owns this system:
            // do not launch a duplicate while it unwinds. (A
            // cancelled job is not "active", so an isActive
            // guard would launch a second job that sees the
            // still-Loading game and breaks immediately, and the
            // re-queued game would never be picked up.)
            if (existing != null && !existing.isCompleted) return
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
        val index = ensureIndex(system)
        if (index == null) {
            // The index failed: every queued game of this
            // system fails with the index reason, so the tile
            // state and the index state agree — and the job parks
            // instead of spinning on a download that will not
            // succeed until an explicit retry.
            val reason = (_indexes.value[system] as? IndexState.Failed)?.reason ?: "index failed"
            for (game in queuedGames(system)) {
                publish(game.id, CoverState.Failed("index ${system.name.lowercase()}: $reason"))
            }
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
                return IndexLoadResult.Loaded(loaded, fromCache = true)
            }
            // An empty or unreadable cache falls through to a
            // fresh fetch — a non-empty-but-truncated list is
            // accepted as before (its entries still match).
        }
        // The two-step load (alpha.7): the whole-repo tree is
        // 7.19 MB for GBA and needs ~160 KB/s to finish in the
        // budget — Robert's speed to api.github.com is bounded
        // ~45–160 KB/s, so GBA always timed out while GB/GBC
        // made it. Step (a) fetches the top-level tree
        // (~1.7 KB) and takes the Named_Boxarts subtree SHA
        // from it, fresh on every load, never hardcoded; step
        // (b) fetches that subtree (GBA 2.09 MB / 5,961
        // entries). Each step goes through boundedGet with its
        // own budget, and the whole load still sits inside the
        // one withTimeout in ensureIndex.
        val topResult = boundedGet(Thumbnails.topTreeUrl(system), MAX_TREE_BYTES, indexBudgetMs)
        val topJson = topResult.bytes?.decodeToString()
            ?: return IndexLoadResult.Failed(topResult.error ?: "no bytes")
        if (TRUNCATED_FLAG.containsMatchIn(topJson)) {
            return IndexLoadResult.Failed("index truncated")
        }
        val sha = Thumbnails.parseBoxartsTreeSha(topJson)
            ?: return IndexLoadResult.Failed("no Named_Boxarts tree")
        val result = boundedGet(Thumbnails.subtreeUrl(system, sha), MAX_TREE_BYTES, indexBudgetMs)
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
        return IndexLoadResult.Loaded(loaded, fromCache = false)
    }

    /**
     * One game, start to finish, in one worker step: match →
     * download → decode in memory → write file → publish
     * Ready. The write and the publish are adjacent
     * and in order, so there is no window in which the status
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
        val result = boundedGet(Thumbnails.artUrl(game.system, fileName), MAX_ART_BYTES, artBudgetMs)
        val bytes = result.bytes
        if (bytes == null) {
            val detail = result.error ?: "no bytes"
            publish(game.id, CoverState.Failed(detail))
            return
        }
        val bitmap = try {
            decode(bytes)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            // Verify before you trust: nothing is written, the
            // game stays re-triable — the 2026-11-15 failure
            // was a decode failure that left a file on disk
            // and a memo that blocked every retry.
            publish(game.id, CoverState.Failed("undecodable"))
            return
        }
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
            return
        }
        diskBitmaps[game.id] = bitmap
        publish(game.id, CoverState.Ready(bitmap, file))
    }

    /**
     * One capped GET with a TOTAL budget: the call returns
     * within [budgetMs] come what may — a fake that never
     * returns, a socket that trickles forever. A timeout is
     * named "timed out"; an external cancellation propagates
     * (the caller resets the game to Missing).
     *
     * The budget is enforced by a RACE, not by awaiting the
     * call under a withTimeout: a withTimeout around a
     * blocking call cannot unwind the thread it is stuck on,
     * and a call stuck in DNS/connect — before any socket
     * exists for disconnect() to close — ignores both the
     * coroutine cancellation and the connection timeouts.
     * (Device evidence 2026-11-15 16:46: alpha.4's pass
     * started 16:46:09 and was still "fetching gba index… 0
     * landed" ~2.5 minutes later, with no "timed out" — the
     * 45s join deadline never fired.) So the call runs on its
     * own IO thread and the pipeline awaits the DEFERRED with
     * a timeout: on timeout the thread is abandoned — its Job
     * is cancelled (best effort: a socket read aborts on
     * disconnect, a pre-socket stall ignores it) and the
     * pipeline moves on at the budget. The deferred is a
     * child of the repository scope, not of the pipeline job,
     * so an abandoned child never blocks the caller — and the
     * finally-cancel means a gate close abandons it too.
     */
    private suspend fun boundedGet(url: String, maxBytes: Int, budgetMs: Long): HttpFetchResult {
        val deferred = scope.async(Dispatchers.IO) { httpGet(url, maxBytes) }
        try {
            return withTimeout(budgetMs) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            return HttpFetchResult(null, null, "timed out")
        } finally {
            deferred.cancel()
        }
    }

    companion object {
        /** Box art is a few hundred KB; the index JSON is the git tree. */
        const val MAX_ART_BYTES: Int = 2 * 1024 * 1024

        /**
         * The GBA subtree JSON measured 2.09 MB on 2026-11-15
         * (the whole-repo tree it replaced was 7.19 MB and
         * growing against this cap) — the cap sits ~2x above
         * the largest legitimate subtree response. A response
         * past the cap fails the fetch (placeholder, no crash)
         * instead of downloading without a bound.
         */
        const val MAX_TREE_BYTES: Int = 4 * 1024 * 1024

        /**
         * Total budget for one index download (see
         * [ensureIndex]): 60s at the GBA subtree's 2.09 MB is
         * ~35 KB/s — below the ~45 KB/s floor the GB tree
         * already proves on Robert's device — while still
         * failing fast with "timed out" instead of holding
         * the pipeline open for minutes. (The old 45s was set
         * for a 6–7 MB payload that needed ~160 KB/s and
         * never made it.) Each step of the two-step load gets
         * this budget, and the whole load sits inside one
         * withTimeout of the same length.
         */
        const val INDEX_BUDGET_MS: Long = 60_000

        /** Total budget for one art file (see [processGame]). */
        const val ART_BUDGET_MS: Long = 20_000

        /** The git-trees response says whether it was cut short. A truncated tree is a failure, not a short index. */
        private val TRUNCATED_FLAG = Regex(""""truncated"\s*:\s*true""")
    }
}
