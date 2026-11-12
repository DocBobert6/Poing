package app.poing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 6 (item 8) — RetroAchievements: the Kotlin-side session.
 *
 * Owns the RA client once it is initialised: the login (username
 * + password, typed by the user), the game load, the per-frame
 * evaluation, the unlock popup data, the achievements list, and
 * the progress serialize/deserialize that the save-state sidecar
 * carries. The leaderboards and rich presence are included too:
 * the boards list, the entries fetch, the tracker and the
 * scoreboard, and the current presence message. Submission is
 * automatic (rc_client evaluates the trackers in do_frame and
 * submits through the same HTTP plumbing), and the presence is
 * sent by rc_client's periodic ping — the app only shows what
 * happened. The native client lives for the app's lifetime once
 * created (login persists); only the loaded game is unloaded
 * when a game quits (the bridge does that in nativeUnloadGame).
 * The native layer calls back into Kotlin through
 * [Bridge] (raHttpRequest / raEventUnlocked / raLeaderboardEvent
 * / raLeaderboardEntriesResult / raLoginResult /
 * raLoadGameResult); those entry points delegate here.
 *
 * The password is used once, for the login that types it, and is
 * never stored: the server hands back a session token, the app
 * keeps the token ([RaSessionStore.setToken]), and the next launch
 * re-logs in with the token. A rejected token is cleared, so the
 * user types their password again instead of retrying a dead
 * token on every launch.
 *
 * The native layer calls back into Kotlin through [Bridge]
 * (raHttpRequest / raEventUnlocked / raLoginResult /
 * raLoadGameResult); those entry points delegate here. The HTTP
 * request runs on a background thread and the response is handed
 * back to the native layer by id, so the calling thread (the emu
 * thread for do_frame, the main thread for login/load) is never
 * blocked. Logged out, achievements are simply inactive — no
 * errors, no nagging.
 */
data class RaUnlock(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
)

object RaClient {
    private const val TAG = "Poing"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True once nativeRaInit has succeeded. */
    @Volatile
    var initialised = false
        private set

    /** Logged in to RetroAchievements (a login is in progress while [loginPending] is true). */
    var loggedIn by mutableStateOf(false)
        private set

    var loginPending by mutableStateOf(false)
        private set

    /** The username the account is logged in as ("" when logged out). */
    var username by mutableStateOf("")
        private set

    /** The title of the game RA has loaded (null when none). */
    var gameTitle by mutableStateOf<String?>(null)
        private set

    /** True once the loaded game's achievements are available. */
    var gameLoaded by mutableStateOf(false)
        private set

    /** The achievements list for the loaded game (empty when none). */
    var achievements by mutableStateOf<List<RaAchievement>>(emptyList())
        private set

    /** The latest unlock, shown as the popup until cleared. */
    var lastUnlock by mutableStateOf<RaUnlock?>(null)
        private set

    // Leaderboards and rich presence.
    /** The leaderboards for the loaded game (empty when the game has none). */
    var leaderboards by mutableStateOf<List<RaLeaderboard>>(emptyList())
        private set

    /** The current rich presence message for the loaded game ("" when the game has none). */
    var richPresence by mutableStateOf("")
        private set

    /** The latest submitted scoreboard, shown as a note until cleared. */
    var lastScoreboard by mutableStateOf<RaScoreboard?>(null)
        private set

    /** The board the active tracker belongs to (0 when no attempt is running), and its current display. */
    var activeTrackerBoardId by mutableStateOf(0)
        private set

    var activeTrackerDisplay by mutableStateOf("")
        private set

    /** The entries for the board the leaderboard screen is showing. */
    var leaderboardEntries by mutableStateOf<RaLeaderboardEntryList?>(null)
        private set

    var leaderboardEntriesLoading by mutableStateOf(false)
        private set

    var leaderboardEntriesError by mutableStateOf<String?>(null)
        private set

    /** Which board the entries belong to, and whether they are the top entries or the ones around the user. */
    var leaderboardEntriesBoardId by mutableStateOf(0)
        private set

    var leaderboardEntriesAroundUser by mutableStateOf(false)
        private set

    /** The status line for the Settings screen. */
    var status by mutableStateOf("")
        private set

    /** The session store the username and token live in (set by [init]). */
    private var session: RaSessionStore? = null

    /**
     * The game whose achievements are waiting on a login still
     * in flight (see [loadGame]). Cleared when the game quits or
     * the user logs out, so a game that is no longer playing is
     * never loaded.
     */
    private var pendingGamePath: String? = null

    /**
     * A save-state sidecar's RA progress, waiting for the game's
     * load to complete (see [restoreProgressAfterLoad]): the
     * startup load from the Save Manager unserializes the state
     * before the RA load result arrives, so the restore cannot
     * apply yet. Applied (or discarded, when the load fails) in
     * [onLoadGameResult]. Null means no restore is pending.
     */
    private var pendingProgress: ByteArray? = null

    /**
     * True when a pending restore means "reset the progress"
     * (the loaded state had no sidecar), rather than "restore
     * these bytes". Only read together with [pendingProgress].
     */
    private var pendingProgressReset = false

    /**
     * Initialise the native RA client and wire the Bridge
     * callbacks. Idempotent. If [session] holds a username and
     * a session token, the login starts here (the result arrives
     * through [onLoginResult]) — unless [autoLogin] is false, in
     * which case the caller logs in explicitly (the login screen
     * does this when the user just typed a password: the typed
     * password is the fresh intent, not the stored token).
     * Returns false when the native libraries could not be
     * loaded or the client could not be created.
     */
    fun init(session: RaSessionStore, autoLogin: Boolean = true): Boolean {
        if (initialised) return true
        this.session = session
        if (!Bridge.ensureLoaded()) return false
        Bridge.raHttpRequestHandler = { id, url, post -> onHttpRequest(id, url, post) }
        Bridge.raEventUnlockedHandler = { id, title, desc, points -> onUnlocked(id, title, desc, points) }
        Bridge.raLeaderboardEventHandler = { type, id, json -> onLeaderboardEvent(type, id, json) }
        Bridge.raLeaderboardEntriesResultHandler = { ok, err, json -> onLeaderboardEntriesResult(ok, err, json) }
        Bridge.raLoginResultHandler = { ok, err -> onLoginResult(ok, err) }
        Bridge.raLoadGameResultHandler = { ok, err -> onLoadGameResult(ok, err) }
        if (!Bridge.nativeRaInit()) {
            status = "RetroAchievements is not available."
            return false
        }
        initialised = true
        if (autoLogin) {
            // The stored login is read here — the launch-time
            // auto-login — so a fresh launch re-logs in and the
            // app does not show "Not logged in." while a username
            // and token are stored.
            session.storedLogin()?.let { (user, token) ->
                loginWithToken(user, token)
            }
        }
        return true
    }

    /**
     * Log in with a username and password. The result arrives
     * asynchronously through [onLoginResult]; until then
     * [loginPending] is true. The password is NOT stored anywhere
     * — on success the session token the server returned is
     * stored instead (see [onLoginResult]). The caller stores
     * the username in [RaSessionStore] first.
     */
    fun login(user: String, password: String) {
        if (!initialised) return
        loginPending = true
        status = "Logging in…"
        Bridge.nativeRaLogin(user, password)
    }

    /**
     * Re-log in with a stored session token (the next launch
     * after a password login). Not offered in the UI — the user
     * only ever types a username and a password.
     */
    fun loginWithToken(user: String, token: String) {
        if (!initialised) return
        loginPending = true
        status = "Logging in…"
        Bridge.nativeRaLoginWithToken(user, token)
    }

    /** Log out and clear the session state, including the stored username and token. */
    fun logout() {
        if (initialised) Bridge.nativeRaLogout()
        pendingGamePath = null
        pendingProgress = null
        pendingProgressReset = false
        session?.clear()
        loggedIn = false
        loginPending = false
        username = ""
        gameTitle = null
        gameLoaded = false
        achievements = emptyList()
        lastUnlock = null
        clearLeaderboardState()
        status = "Not logged in."
    }

    /**
     * Load the achievements for a game (called once per game
     * start, after the core loads). The game is identified by
     * hashing the file; the result arrives through
     * [onLoadGameResult]. A game whose hash is not recognised
     * simply leaves RA inactive for that game.
     */
    fun loadGame(gamePath: String) {
        if (!initialised) return
        if (!loggedIn) {
            // The login is still in flight (a stored-token
            // re-login on launch, or a password login just
            // started) — remember the game and load its
            // achievements when the login completes, instead of
            // dropping the load. A logged-out user never logs in,
            // so the pending path simply never fires; quitting the
            // game or logging out clears it, so a game that is no
            // longer playing is never loaded.
            pendingGamePath = gamePath
            return
        }
        pendingGamePath = null
        gameLoaded = false
        gameTitle = null
        achievements = emptyList()
        Bridge.nativeRaLoadGame(gamePath)
    }

    /** The game quit: unload it in the native layer too, and clear the per-game state here. */
    fun onGameQuit() {
        if (initialised) Bridge.nativeRaUnloadGame()
        pendingGamePath = null
        pendingProgress = null
        pendingProgressReset = false
        gameTitle = null
        gameLoaded = false
        achievements = emptyList()
        lastUnlock = null
        clearLeaderboardState()
    }

    /** Clear the per-game leaderboard and rich presence state (logout and game quit). */
    private fun clearLeaderboardState() {
        leaderboards = emptyList()
        richPresence = ""
        lastScoreboard = null
        activeTrackerBoardId = 0
        activeTrackerDisplay = ""
        leaderboardEntries = null
        leaderboardEntriesLoading = false
        leaderboardEntriesError = null
        leaderboardEntriesBoardId = 0
        leaderboardEntriesAroundUser = false
    }

    /**
     * One displayed frame's achievement evaluation. Called from
     * the emu thread after the frame run; a cheap no-op while no
     * game is loaded in RA (the flag is only set by the load
     * callback, so re-simulation frames with run-ahead on never
     * double-evaluate — the Kotlin loop calls this once per
     * displayed frame).
     */
    fun onFrame() {
        if (initialised && gameLoaded) Bridge.nativeRaDoFrame()
    }

    /** Serialized RA progress, for the save-state sidecar. */
    fun serializeProgress(): ByteArray? =
        if (initialised && gameLoaded) Bridge.nativeRaSerializeProgress() else null

    /** Restore RA progress from a save-state sidecar. */
    fun deserializeProgress(bytes: ByteArray) {
        if (initialised && gameLoaded) Bridge.nativeRaDeserializeProgress(bytes)
    }

    /** Clear RA progress (a load with no sidecar). */
    fun resetProgress() {
        if (initialised && gameLoaded) Bridge.nativeRaReset()
    }

    /** Refresh the achievements list from the native layer. */
    fun refreshAchievements() {
        if (!initialised || !gameLoaded) return
        val json = Bridge.nativeRaGetAchievements() ?: return
        achievements = RaAchievements.parse(String(json, Charsets.UTF_8))
    }

    /** Clear the unlock popup (after its 4-second showing). */
    fun clearUnlock() {
        lastUnlock = null
    }

    // Leaderboards and rich presence.

    /** Refresh the leaderboards list from the native layer. */
    fun refreshLeaderboards() {
        if (!initialised || !gameLoaded) return
        val json = Bridge.nativeRaGetLeaderboards() ?: return
        leaderboards = RaLeaderboards.parseLeaderboards(String(json, Charsets.UTF_8))
    }

    /**
     * Refresh the current rich presence message from the native
     * layer, for display only — the presence itself is sent to
     * the server automatically by rc_client's periodic ping, so
     * this never sends anything.
     */
    fun refreshRichPresence() {
        if (!initialised || !gameLoaded) return
        richPresence = Bridge.nativeRaGetRichPresence() ?: ""
    }

    /**
     * Fetch the entries for a board: the top entries, or the
     * ones around the logged-in user. The result arrives through
     * [onLeaderboardEntriesResult].
     */
    fun fetchLeaderboardEntries(leaderboardId: Int, aroundUser: Boolean) {
        if (!initialised || !gameLoaded) return
        leaderboardEntriesBoardId = leaderboardId
        leaderboardEntriesAroundUser = aroundUser
        leaderboardEntries = null
        leaderboardEntriesError = null
        leaderboardEntriesLoading = true
        if (!Bridge.nativeRaFetchLeaderboardEntries(leaderboardId, aroundUser)) {
            leaderboardEntriesLoading = false
            leaderboardEntriesError = "Could not fetch the leaderboard."
        }
    }

    /** The entries fetch finished. */
    fun onLeaderboardEntriesResult(success: Boolean, error: String?, json: String?) {
        leaderboardEntriesLoading = false
        if (!success || json == null) {
            leaderboardEntriesError = error?.takeIf { it.isNotEmpty() }
                ?: "Could not fetch the leaderboard."
            return
        }
        val list = RaLeaderboards.parseEntries(json)
        if (list == null) {
            leaderboardEntriesError = "Could not read the leaderboard."
        } else {
            leaderboardEntries = list
        }
    }

    /**
     * A leaderboard event from the native layer. The tracker
     * show/update carries the current attempt's display; the
     * scoreboard carries the submitted score and the new rank;
     * an attempt starting, failing, or submitting refreshes
     * the boards list (the tracker's value changes). A hide
     * only clears the tracker when it belongs to the board
     * being hidden, so a second attempt cannot clear the
     * first one's display.
     */
    fun onLeaderboardEvent(type: Int, leaderboardId: Int, json: String?) {
        when (type) {
            RaLeaderboardEvents.TRACKER_SHOW, RaLeaderboardEvents.TRACKER_UPDATE -> {
                activeTrackerBoardId = leaderboardId
                activeTrackerDisplay = RaLeaderboards.parseTrackerDisplay(json) ?: ""
            }
            RaLeaderboardEvents.TRACKER_HIDE -> {
                if (activeTrackerBoardId == leaderboardId) {
                    activeTrackerBoardId = 0
                    activeTrackerDisplay = ""
                }
            }
            RaLeaderboardEvents.SCOREBOARD -> {
                RaLeaderboards.parseScoreboard(json ?: "")?.let { lastScoreboard = it }
                refreshLeaderboards()
            }
            RaLeaderboardEvents.STARTED, RaLeaderboardEvents.FAILED, RaLeaderboardEvents.SUBMITTED -> {
                refreshLeaderboards()
            }
        }
    }

    /** Clear the scoreboard note (after its 4-second showing). */
    fun clearScoreboard() {
        lastScoreboard = null
    }

    /**
     * Restore the RA progress that belongs to a save state loaded
     * at startup (the Save Manager's pending load): the state is
     * unserialized before the RA load result arrives, so the
     * restore cannot apply yet — it waits here until the load
     * completes ([onLoadGameResult]). When the game is already
     * loaded (a paused load), the restore applies at once. Pass
     * the sidecar's bytes, or null when the state had no sidecar
     * (the progress is then reset, so it matches the rewound
     * game). A no-op while RA is not initialised.
     */
    fun restoreProgressAfterLoad(bytes: ByteArray?) {
        if (!initialised) return
        if (gameLoaded) {
            if (bytes != null) deserializeProgress(bytes) else resetProgress()
            return
        }
        pendingProgress = bytes
        pendingProgressReset = bytes == null
    }

    /** A server call from the native layer: perform it, then hand the response back by id. */
    fun onHttpRequest(requestId: Int, url: String, postData: String?) {
        ioScope.launch {
            val (body, code) = httpRequest(url, postData)
            try {
                Bridge.nativeRaHttpResponse(requestId, body, code)
            } catch (_: Exception) {
                // The native layer is gone (the game quit while a
                // request was in flight); the response is dropped.
            }
        }
    }

    /** An achievement was unlocked: show the popup and refresh the list. */
    fun onUnlocked(id: Int, title: String, description: String, points: Int) {
        lastUnlock = RaUnlock(id, title, description, points)
        refreshAchievements()
    }

    /** The login result. */
    fun onLoginResult(success: Boolean, error: String?) {
        loginPending = false
        loggedIn = success
        if (success) {
            username = Bridge.nativeRaLoggedInUsername() ?: ""
            status = if (username.isNotEmpty()) "Logged in as $username." else "Logged in."
            // Keep the session token the server returned, so the
            // next launch re-logs in without the password. The
            // password itself is never stored. The username is
            // stored with it (the caller stored it before the
            // login, but a token re-login has no caller — keep
            // both together here).
            Bridge.nativeRaToken()?.takeIf { it.isNotEmpty() }?.let { session?.setToken(it) }
            if (username.isNotEmpty()) session?.setUsername(username)
            // A game start that arrived while this login was in
            // flight left its achievements waiting — load them now
            // that the login completed.
            pendingGamePath?.let { path ->
                pendingGamePath = null
                loadGame(path)
            }
        } else {
            username = ""
            // Only an explicit rejection kills the stored token.
            // A transient failure (no network, a timeout) leaves
            // the token in place, so the next launch retries it
            // instead of forcing a password re-login for a login
            // that simply did not happen.
            if (RaLoginErrors.isInvalidCredentials(error)) {
                // The saved login is dead: clear it, so the next
                // launch asks for the password instead of
                // retrying the same token.
                session?.clear()
            }
            // A failure is never silent and never vague: the raw
            // rcheevos error is mapped to a message that names
            // what went wrong (see RaLoginErrors).
            status = "Login failed: ${RaLoginErrors.describe(error)}"
        }
    }

    /** The game-load result. */
    fun onLoadGameResult(success: Boolean, error: String?) {
        gameLoaded = success
        if (success) {
            gameTitle = Bridge.nativeRaGameTitle()
            refreshAchievements()
            refreshLeaderboards()
            refreshRichPresence()
            // A save-state restore that waited for this load.
            val bytes = pendingProgress
            val reset = pendingProgressReset
            pendingProgress = null
            pendingProgressReset = false
            if (bytes != null) deserializeProgress(bytes)
            else if (reset) resetProgress()
        } else {
            gameTitle = null
            achievements = emptyList()
            clearLeaderboardState()
            pendingProgress = null
            pendingProgressReset = false
        }
    }

    /**
     * One HTTP request (GET when [postData] is null, POST
     * otherwise). Returns the response body and the HTTP status
     * code. A non-200 answer still carries a body — the server's
     * error — so it is read from the error stream, not
     * discarded: the login endpoint answers a rejected login
     * with HTTP 401 and a JSON error, and throwing that body
     * away made every rejected login surface as a bare "No
     * response". On a transport failure the body is
     * empty and the status is the classified failure
     * (RaLoginErrors: 521 unreachable, 524 timed out, 0
     * otherwise), so the native layer fails the call with a
     * named cause instead of hanging. The RA API's POST bodies
     * are form-encoded. Timeouts: 10 s to connect, 15 s to read,
     * so a login can never hang forever.
     */
    private fun httpRequest(url: String, postData: String?): Pair<ByteArray, Int> {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Poing")
                if (postData != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded",
                    )
                }
            }
        } catch (e: Exception) {
            return ByteArray(0) to RaLoginErrors.failureStatus(e)
        }
        try {
            if (postData != null) {
                conn.outputStream.use { out ->
                    out.write(postData.toByteArray(Charsets.UTF_8))
                }
            }
            return RaLoginErrors.readResponse(conn)
        } catch (e: Exception) {
            return ByteArray(0) to RaLoginErrors.failureStatus(e)
        } finally {
            conn.disconnect()
        }
    }
}
