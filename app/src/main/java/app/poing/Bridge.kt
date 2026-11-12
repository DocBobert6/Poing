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

    external fun nativeUnloadGame()
    external fun nativeDeinit()
    external fun nativeLastError(): String
}
