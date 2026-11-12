package app.poing

import android.util.Log

/**
 * JNI entry points into the Phase 2 bridge (bridge/jni/bridge.cpp).
 *
 * Native libraries are loaded LAZILY, only when a game is started —
 * never in a static init and never from MainActivity.onCreate — so
 * the app launches (and the Robolectric startup gate runs) with no
 * native code present.
 *
 * Load order matters: the bridge .so has a DT_NEEDED on the core
 * .so, so the core library loads first.
 */
object Bridge {
    private const val TAG = "Poing"

    @Volatile
    private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("mgba_libretro")
            System.loadLibrary("poing-bridge")
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            // Log.e is a stub that throws RuntimeException on the
            // plain JVM unit tests, so guard it: the false return
            // already reports the failure to the caller.
            try {
                Log.e(TAG, "failed to load native libraries", e)
            } catch (_: RuntimeException) {
            }
            false
        }
    }

    external fun nativeInit(systemDir: String, saveDir: String): Boolean
    external fun nativeLoadGame(path: String): Boolean

    /** [width, height, fps * 1000, sample rate], from av_info after load. */
    external fun nativeGetAvInfo(): IntArray
    external fun nativeRunFrame()

    /** Copies the latest frame into [pixels]; [dims] gets [width, height]. */
    external fun nativeGetFrame(pixels: IntArray, dims: IntArray): Boolean

    /** Drains up to [maxFrames] stereo frames into [samples]; returns frames read. */
    external fun nativeReadAudio(samples: ShortArray, maxFrames: Int): Int
    external fun nativeSetInput(mask: Int)

    /** Set one core option (see bridge.cpp). False for an unknown key. */
    external fun nativeSetOption(key: String, value: String): Boolean

    /** Save states (see bridge.cpp). Only valid while the emu loop is paused. */
    external fun nativeSerializeSize(): Int

    /** Fills [data] with the state; returns bytes written, or -1. */
    external fun nativeSerialize(data: ByteArray): Int
    external fun nativeUnserialize(data: ByteArray): Boolean

    /** Battery save RAM + RTC (see bridge.cpp). Sizes are 0 when the game has none. */
    external fun nativeSaveRamSize(): Int
    external fun nativeReadSaveRam(data: ByteArray): Boolean
    external fun nativeWriteSaveRam(data: ByteArray): Boolean
    external fun nativeRtcSize(): Int
    external fun nativeReadRtc(data: ByteArray): Boolean
    external fun nativeWriteRtc(data: ByteArray): Boolean

    /** Cheats (see bridge.cpp). Only valid while a game is loaded; no-ops otherwise. */
    external fun nativeSetCheat(code: String)
    external fun nativeClearCheats()

    /** Cartridge sensors (Phase 6, item 10a): one sensor value at a time (see sensors.c for the ids). */
    external fun nativeSetSensorValue(sensorId: Int, value: Float)

    external fun nativeUnloadGame()
    external fun nativeDeinit()
    external fun nativeLastError(): String

    // Phase 6 (item 8) — RetroAchievements (bridge/jni/ra_client.c).
    // The client is created once and lives for the app's lifetime;
    // login persists, so it is NOT shut down in nativeDeinit. Only
    // the loaded game is unloaded in nativeRaUnloadGame. The memory
    // maps come from the core's SET_MEMORY_MAPS capture; HTTP
    // responses and the achievements JSON cross as byte[] (UTF-8,
    // encoding-safe).
    external fun nativeRaInit(): Boolean
    // Password login: the password is used once for this login and
    // is never stored. The session token the server returns is kept
    // instead, via nativeRaToken, and the next launch re-logs in
    // with nativeRaLoginWithToken.
    external fun nativeRaLogin(username: String, password: String)
    external fun nativeRaLoginWithToken(username: String, token: String)
    external fun nativeRaToken(): String?
    external fun nativeRaLogout()
    external fun nativeRaIsLoggedIn(): Boolean
    external fun nativeRaLoggedInUsername(): String?
    external fun nativeRaLoadGame(path: String)
    external fun nativeRaUnloadGame()
    external fun nativeRaIsGameLoaded(): Boolean
    external fun nativeRaGameTitle(): String?
    external fun nativeRaDoFrame()
    external fun nativeRaReset()
    external fun nativeRaSerializeProgress(): ByteArray?
    external fun nativeRaDeserializeProgress(data: ByteArray)
    external fun nativeRaGetAchievements(): ByteArray?
    // Leaderboards and rich presence. The entries fetch is async:
    // the result arrives through raLeaderboardEntriesResult, and
    // nativeRaFetchLeaderboardEntries returns false when no game
    // is loaded (no callback then).
    external fun nativeRaHasLeaderboards(): Boolean
    external fun nativeRaGetLeaderboards(): ByteArray?
    external fun nativeRaFetchLeaderboardEntries(leaderboardId: Int, aroundUser: Boolean): Boolean
    external fun nativeRaHasRichPresence(): Boolean
    external fun nativeRaGetRichPresence(): String?
    external fun nativeRaHttpResponse(requestId: Int, body: ByteArray, httpStatus: Int)

    /**
     * The Kotlin-side handlers for the RA callbacks the native
     * layer calls (looked up by GetMethodID in nativeRaInit, so
     * the function names and signatures below must match
     * bridge.cpp exactly). They are nullable lambdas, set by
     * [RaClient] when it initialises — Bridge itself stays free
     * of the RA session, so the pure-test harness still compiles
     * this file. A callback with no handler set is a no-op (the
     * native client is only created after the handlers are set).
     */
    var raHttpRequestHandler: ((requestId: Int, url: String, postData: String?) -> Unit)? = null
    var raEventUnlockedHandler: ((id: Int, title: String, description: String, points: Int) -> Unit)? = null
    // A leaderboard event (tracker show/hide/update, scoreboard)
    // and the entries-fetch result. The event's json is the
    // tracker {"display"} or the scoreboard payload, or null when
    // the event carries none.
    var raLeaderboardEventHandler: ((type: Int, leaderboardId: Int, json: String?) -> Unit)? = null
    var raLeaderboardEntriesResultHandler: ((success: Boolean, error: String?, json: String?) -> Unit)? = null
    var raLoginResultHandler: ((success: Boolean, error: String?) -> Unit)? = null
    var raLoadGameResultHandler: ((success: Boolean, error: String?) -> Unit)? = null

    /** A server call from the native layer (url + optional POST body). */
    fun raHttpRequest(requestId: Int, url: String, postData: String?) {
        raHttpRequestHandler?.invoke(requestId, url, postData)
    }

    /** An achievement was unlocked. */
    fun raEventUnlocked(id: Int, title: String, description: String, points: Int) {
        raEventUnlockedHandler?.invoke(id, title, description, points)
    }

    /** A leaderboard event (tracker show/hide/update, scoreboard). */
    fun raLeaderboardEvent(type: Int, leaderboardId: Int, json: String?) {
        raLeaderboardEventHandler?.invoke(type, leaderboardId, json)
    }

    /** The leaderboard entries fetch finished. */
    fun raLeaderboardEntriesResult(success: Boolean, error: String?, json: String?) {
        raLeaderboardEntriesResultHandler?.invoke(success, error, json)
    }

    /** The login result. */
    fun raLoginResult(success: Boolean, error: String?) {
        raLoginResultHandler?.invoke(success, error)
    }

    /** The game-load result. */
    fun raLoadGameResult(success: Boolean, error: String?) {
        raLoadGameResultHandler?.invoke(success, error)
    }
}
