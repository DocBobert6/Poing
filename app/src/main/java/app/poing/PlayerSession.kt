package app.poing

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Phase 2 spike session: owns the emulation thread, the audio thread,
 * and the frame Bitmaps. One session per game start; stop() unloads
 * the game and tears the native side down.
 *
 * Threading: the emulation thread calls retro_run() once per frame
 * and copies the new frame into the back Bitmap, then swaps it to the
 * front and posts it to the main thread. The audio thread drains the
 * bridge's resampled ring buffer into AudioTrack at 48 kHz. Input is
 * a single volatile mask written by the touch UI.
 *
 * Pause stops the emulation loop cleanly (no retro_run calls, audio
 * silenced) while the game stays loaded, so resume picks up where
 * the game left off. Pausing also flushes the battery save to
 * <base>.sav — the core never writes it itself (Phase 4 audit of
 * libretro.c) — and quitting flushes it again before unload.
 *
 * Save states (slots 0..3, slot 0 is the quick slot) go through
 * retro_serialize/unserialize and only run while paused, so a state
 * can never race a retro_run call. The battery save is restored
 * right after load, before the first frame: the core's deferred
 * setup copies the save-RAM buffer into the emulated cart RAM.
 *
 * Core options ([options]) are pushed to the bridge after the
 * native libraries load and before the game loads; [applyOptions]
 * re-pushes them live (the core re-reads them on the next frame).
 *
 * Playback speed ([setSpeed], Phase 6 item 1) paces the loop to
 * the core's frame rate divided by the speed — fast-forward runs
 * several frames per interval, slow motion stretches one — and
 * mutes the audio off normal speed.
 *
 * Diagnostics: [onStats] is posted to the main thread about once a
 * second with the frame size, the core's fps from av_info, the
 * measured fps, and the frames drawn so far — so a no-video bug is
 * diagnosable on the device without another round trip (a game
 * that runs but draws nothing reads 0 frames drawn; a game that
 * never loads never reaches the loop at all and reports its error
 * through [onError] instead).
 */
class PlayerSession(
    private val systemDir: String,
    private val saveDir: String,
    private val gamePath: String,
    private val saveBase: String,
    private val options: Map<String, String> = emptyMap(),
    private val onFrame: (Bitmap) -> Unit,
    private val onStats: (PlayerStats) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val TAG = "Poing"
        private const val AUDIO_RATE = 48000
    }

    private val saveStore = SaveStore(java.io.File(saveDir))

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    /** True once a game is loaded and not yet unloaded. */
    @Volatile
    private var loaded = false

    val isRunning: Boolean get() = running
    val isPaused: Boolean get() = paused
    val isLoaded: Boolean get() = loaded

    /** True while a retro_run() call is in flight on the emu thread. */
    @Volatile
    private var inFrame = false

    private var emuThread: Thread? = null
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var inputMask = 0

    fun setInput(mask: Int) {
        inputMask = mask
        if (running) Bridge.nativeSetInput(mask)
    }

    /**
     * Phase 6 (item 1) — the playback speed. The emulation loop
     * paces itself to the core's frame rate divided by the speed,
     * so a change applies on the next frame; the audio is muted
     * whenever the speed is not normal (the core still produces
     * samples at the multiplied rate, and playing them at 48 kHz
     * would sound fast or slow — the audio loop drains and
     * discards them instead). Safe to call before start and while
     * paused: the loop only reads it while running.
     */
    @Volatile
    private var speed: PlaySpeed = PlaySpeed.NORMAL

    val currentSpeed: PlaySpeed get() = speed

    fun setSpeed(speed: PlaySpeed) {
        this.speed = speed
    }

    /** Pause the game: the emu loop idles (no retro_run) and the audio is silenced. The game stays loaded. */
    fun pause() {
        if (!running || paused) return
        paused = true
        // Wait out any in-flight frame so nothing races retro_run
        // (a frame takes <1/60s; the deadline is only a safety valve).
        val deadline = System.nanoTime() + 200_000_000L
        while (inFrame && System.nanoTime() < deadline) {
            try {
                Thread.sleep(5)
            } catch (e: InterruptedException) {
                break
            }
        }
        audioTrack?.let {
            try {
                it.pause()
            } catch (_: IllegalStateException) {
                // Already stopped; nothing to silence.
            }
            try {
                it.flush()
            } catch (_: IllegalStateException) {
                // Nothing buffered; nothing to drop.
            }
        }
        // The core is idle now: flush the battery save while it is
        // safe to read the live save-RAM buffer.
        flushSave()
    }

    /** Resume a paused game. */
    fun resume() {
        if (!running || !paused) return
        audioTrack?.let {
            try {
                it.play()
            } catch (_: IllegalStateException) {
                // The track is gone; the audio thread restarts it on the next frame.
            }
        }
        paused = false
    }

    /** Push core options to the bridge. The core re-reads them on the next frame; safe to call before init (the bridge seeds its table on first use) and safe to call when no game is running — it just loads the libraries, which is harmless. */
    fun applyOptions(options: Map<String, String>) {
        if (!Bridge.ensureLoaded()) return
        for ((key, value) in options) Bridge.nativeSetOption(key, value)
    }

    fun applyOptions() = applyOptions(options)

    /**
     * Write the live save-RAM + RTC buffers to <base>.sav. Only
     * safe while the emu loop is paused or stopped — never mid-frame.
     * Returns false when the game has no save RAM (most GB games
     * without a battery, and any game that never saves).
     */
    fun flushSave(): Boolean {
        if (!loaded) return false
        val saveSize = Bridge.nativeSaveRamSize()
        if (saveSize <= 0) return false
        val save = ByteArray(saveSize)
        if (!Bridge.nativeReadSaveRam(save)) return false
        val rtcSize = Bridge.nativeRtcSize()
        val bytes = if (rtcSize > 0) {
            val rtc = ByteArray(rtcSize)
            if (!Bridge.nativeReadRtc(rtc)) return false
            save + rtc
        } else {
            save
        }
        return try {
            saveStore.saveFile(saveBase).apply { parentFile?.mkdirs() }.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restore <base>.sav into the live buffers. Called right after
     * load, before the first frame — the core's deferred setup
     * copies the buffer into the emulated cart RAM. A missing or
     * short file is simply no save yet; nothing to restore.
     */
    private fun loadSave() {
        val file = saveStore.saveFile(saveBase)
        if (!file.exists()) return
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return
        }
        val saveSize = Bridge.nativeSaveRamSize()
        if (saveSize <= 0 || bytes.size < saveSize) return
        if (!Bridge.nativeWriteSaveRam(bytes.copyOfRange(0, saveSize))) return
        val rtcSize = Bridge.nativeRtcSize()
        if (rtcSize > 0 && bytes.size >= saveSize + rtcSize) {
            Bridge.nativeWriteRtc(bytes.copyOfRange(saveSize, saveSize + rtcSize))
        }
    }

    /** Write a save state to slot [slot]. Only while paused — serialize must never race retro_run. */
    fun saveState(slot: Int): Boolean {
        if (!loaded || !paused) return false
        val size = Bridge.nativeSerializeSize()
        if (size <= 0) return false
        val data = ByteArray(size)
        val written = Bridge.nativeSerialize(data)
        if (written < 0) return false
        return try {
            saveStore.stateFile(saveBase, slot).apply { parentFile?.mkdirs() }
                .writeBytes(data.copyOf(written))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Load the save state in slot [slot]. Only while paused. */
    fun loadState(slot: Int): Boolean {
        if (!loaded || !paused) return false
        val file = saveStore.stateFile(saveBase, slot)
        if (!file.exists()) return false
        val data = try {
            file.readBytes()
        } catch (_: Exception) {
            return false
        }
        return Bridge.nativeUnserialize(data)
    }

    fun stateExists(slot: Int): Boolean = saveStore.stateExists(saveBase, slot)
    fun stateModifiedAt(slot: Int): Long? = saveStore.stateModifiedAt(saveBase, slot)

    fun start() {
        if (running) return
        running = true
        emuThread = Thread(::emuLoop, "poing-emu").also { it.start() }
    }

    fun stop() {
        running = false
        paused = false
        try {
            emuThread?.join(2000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "emu thread join interrupted", e)
        }
        try {
            audioThread?.join(2000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "audio thread join interrupted", e)
        }
        emuThread = null
        audioThread = null
    }

    private fun fail(message: String) {
        running = false
        mainHandler.post { onError(message) }
    }

    private fun emuLoop() {
        if (!Bridge.ensureLoaded()) {
            fail("Native libraries failed to load.")
            return
        }
        // Core options before init: the core reads them in retro_init.
        applyOptions()
        if (!Bridge.nativeInit(systemDir, saveDir)) {
            fail("Could not initialise the core.")
            return
        }
        if (!Bridge.nativeLoadGame(gamePath)) {
            val detail = Bridge.nativeLastError().ifBlank { "unknown error" }
            Bridge.nativeDeinit()
            fail("Could not load the game. $detail")
            return
        }
        loaded = true
        val av = Bridge.nativeGetAvInfo()
        val width = av[0]
        val height = av[1]
        val fps = av[2] / 1000.0
        Log.i(TAG, "playing ${width}x${height} @ $fps fps, audio ${av[3]} Hz -> $AUDIO_RATE Hz")
        // Push any input the UI already recorded before the load finished.
        Bridge.nativeSetInput(inputMask)
        if (width <= 0 || height <= 0 || fps <= 0.0) {
            Bridge.nativeUnloadGame()
            loaded = false
            Bridge.nativeDeinit()
            fail("The core reported no video mode.")
            return
        }
        // Restore the battery save before the first frame — the
        // core's deferred setup copies the buffer into the cart RAM.
        loadSave()

        val pixels = IntArray(width * height)
        val dims = IntArray(2)
        val bitmaps = arrayOf(
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
        )
        var front = 0
        var framesDrawn = 0L
        var statWindowStart = System.nanoTime()
        var statWindowFrames = 0L

        startAudio()

        while (running) {
            if (paused) {
                // Idle: no retro_run, no new frames. The last frame
                // stays on screen behind the pause screen.
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    break
                }
                continue
            }
            val t0 = System.nanoTime()
            inFrame = true
            try {
                Bridge.nativeRunFrame()
            } finally {
                inFrame = false
            }
            if (Bridge.nativeGetFrame(pixels, dims)) {
                val w = dims[0]
                val h = dims[1]
                if (w == width && h == height) {
                    val back = 1 - front
                    bitmaps[back].setPixels(pixels, 0, width, 0, 0, width, height)
                    front = back
                    val frame = bitmaps[front]
                    mainHandler.post { onFrame(frame) }
                    framesDrawn++
                    statWindowFrames++
                } else {
                    // Geometry changed mid-game; the spike drops the
                    // frame rather than guessing.
                    Log.w(TAG, "frame geometry changed to ${w}x$h, dropping frame")
                }
            }
            // About once a second, report the diagnostic stats.
            val now = System.nanoTime()
            if (now - statWindowStart >= 1_000_000_000L) {
                val windowSecs = (now - statWindowStart) / 1_000_000_000.0
                val measuredFps = statWindowFrames / windowSecs
                val stats = PlayerStats(
                    width = width,
                    height = height,
                    coreFps = fps,
                    measuredFps = measuredFps,
                    framesDrawn = framesDrawn,
                    sampleRate = av[3],
                )
                mainHandler.post { onStats(stats) }
                statWindowStart = now
                statWindowFrames = 0
            }
            val elapsed = System.nanoTime() - t0
            // The interval follows the current speed, read fresh
            // every frame so a hold or toggle applies at once.
            val remaining = PlaySpeed.frameIntervalNs(fps, speed) - elapsed
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000, (remaining % 1_000_000).toInt())
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        stopAudio()
        // Flush the battery save before unload — the core never
        // writes it itself, and unload is the last chance.
        flushSave()
        Bridge.nativeUnloadGame()
        loaded = false
        Bridge.nativeDeinit()
        Log.i(TAG, "session stopped")
    }

    private fun startAudio() {
        val minBuffer = AudioTrack.getMinBufferSize(
            AUDIO_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer * 2, AUDIO_RATE / 10 * 2 * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(AUDIO_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        track.play()
        audioThread = Thread(::audioLoop, "poing-audio").also { it.start() }
    }

    private fun audioLoop() {
        val track = audioTrack ?: return
        val samples = ShortArray(4096 * 2) // stereo frames * 2 channels
        while (running) {
            if (paused) {
                // Idle: the track is paused and flushed, so there is
                // nothing to drain.
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    break
                }
                continue
            }
            val frames = Bridge.nativeReadAudio(samples, 4096)
            if (frames > 0) {
                // Off normal speed the audio is muted: the core
                // produces samples at the multiplied rate, and
                // playing them at 48 kHz would sound fast or slow.
                // The frames are still drained, so the ring never
                // overflows and un-muting resumes cleanly.
                if (speed == PlaySpeed.NORMAL) {
                    track.write(samples, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
                }
            } else {
                try {
                    Thread.sleep(5)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun stopAudio() {
        audioThread?.let {
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "audio thread join interrupted", e)
            }
        }
        audioThread = null
        audioTrack?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "audio track stop failed", e)
            }
            it.release()
        }
        audioTrack = null
    }
}

/** One second of diagnostic numbers for the on-screen overlay. */
data class PlayerStats(
    val width: Int,
    val height: Int,
    val coreFps: Double,
    val measuredFps: Double,
    val framesDrawn: Long,
    val sampleRate: Int,
)
