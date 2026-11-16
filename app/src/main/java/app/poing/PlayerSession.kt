package app.poing

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.Executors

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
 * Save-state thumbnails (Phase 6, item 2): every saved state gets
 * a small PNG of the frame it was saved on, written beside the
 * state file. Quitting writes an auto-save to a dedicated slot
 * (the latest quit wins) — the pause menu offers it back as a
 * Load, it never auto-loads on the next start.
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
    private val cheatsDir: String = "",
    private val playtimeDir: String = "",
    // Phase 6 (item 5) — a pending load from the Save Manager:
    // a save-state slot, or the auto-save. Applied in the emu
    // loop before the first frame, so the game starts exactly
    // where the state left it.
    private val loadStateOnStart: Int? = null,
    private val loadAutoOnStart: Boolean = false,
    private val options: Map<String, String> = emptyMap(),
    private val onFrame: (Bitmap) -> Unit,
    private val onStats: (PlayerStats) -> Unit,
    private val onError: (String) -> Unit,
    // Performance fix 5 — backpressure: true when the GL
    // thread has consumed the previous frame. The emu loop
    // skips the copy chain while it is false (frames are
    // dropped, not queued), unless a recording is running —
    // the encoder needs every frame it can get. Defaults to
    // always-true: the no-GL fallback path has no consumer
    // to wait on, and tests construct sessions without a view.
    private val frameConsumed: () -> Boolean = { true },
    // Performance fix 6 — whether the rewind control is
    // visible (snapshotted per game, like GameScreen's own
    // copy). The rewind ring is only captured when it is
    // true: with the control hidden (the default) the
    // snapshots could never be used, so capturing one every
    // second was pure waste.
    private val rewindControlVisible: Boolean = false,
    // Alpha.16 — proportional steering for this game at
    // start: the per-game override when it has one,
    // otherwise the global switch (see GameScreen, which
    // resolves it the same way it resolves the dead zone).
    // Snapshotted like the rewind control; the editor's Done
    // pushes a later change through [setProportionalSteering].
    // Volatile: written by the UI thread, read by the emu
    // loop once per step.
    @Volatile
    private var proportionalSteering: Boolean = false,
) {
    companion object {
        private const val TAG = "Poing"
        private const val AUDIO_RATE = 48000

        /** Width of a save-state thumbnail in pixels (aspect kept from the frame). */
        const val THUMBNAIL_WIDTH = 120
    }

    private val saveStore = SaveStore(java.io.File(saveDir))

    /** The per-game cheat store, when the caller supplies a cheats dir (empty means no cheats). */
    private val cheatStore: CheatStore? =
        cheatsDir.takeIf { it.isNotBlank() }?.let { CheatStore(java.io.File(it)) }

    /** The playtime store, when the caller supplies a playtime dir (empty means no tracking). */
    private val playtimeStore: PlaytimeStore? =
        playtimeDir.takeIf { it.isNotBlank() }?.let { PlaytimeStore(java.io.File(it)) }

    /**
     * Phase 6 (item 5) — when the current run started, in wall
     * time. Set when the loop starts running and on every resume;
     * cleared when the seconds are banked. Volatile: written on
     * the emu thread, read on the UI thread (pause/stop).
     * Wall time is the honest measure — it is the time the user
     * spent playing, at whatever speed the game ran.
     */
    @Volatile
    private var playStartMillis: Long? = null

    /** Seconds played for this game: banked total + the current unbanked run. */
    fun playtimeSeconds(): Long {
        val banked = playtimeStore?.get(saveBase) ?: 0L
        val start = playStartMillis ?: return banked
        return banked + (System.currentTimeMillis() - start) / 1000
    }

    /** Bank the current run's whole seconds into the store. */
    private fun bankPlaytime() {
        val store = playtimeStore ?: return
        val start = playStartMillis ?: return
        playStartMillis = null
        store.add(saveBase, (System.currentTimeMillis() - start) / 1000)
    }

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    /**
     * The paused loops park here (see emuLoop/audioLoop): while
     * paused they wait instead of waking every 50 ms, and
     * resume()/stop() notify. The wait loop re-checks the
     * flags, so a notify that lands before the wait is not
     * lost; both flags are @Volatile, so a parked thread sees
     * the change.
     */
    private val pauseLock = Object()

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

    /**
     * Performance fix 9 — the save writes (battery flush on
     * pause, auto-save on quit) run on a single background
     * thread, not the UI thread: the file write, the full
     * serialize and the PNG thumbnail encode used to hitch
     * pause/quit. One thread, in order, so a pause flush and
     * a quit auto-save can never race each other; the lock
     * guards flushSave against a direct call from the emu
     * loop's tail. The thread is a daemon, so it can never
     * hold the process open; stop() shuts the executor down
     * after the joins, so a session leaves no parked thread
     * behind.
     */
    private val saveIoExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "poing-save-io").apply { isDaemon = true }
    }
    private val saveFlushLock = Any()

    @Volatile
    private var inputMask = 0

    /**
     * Alpha.16 — proportional steering state, written by the
     * UI thread (StickControl reports it) and read by the emu
     * loop once per step. [stickMask] is the stick's current
     * direction mask (0 when centred or released) and
     * [stickDeflection] its normalised deflection t in 0..1.
     * The D-pad never writes either, so the toggle does
     * nothing while it is in the slot — the pulse gates only
     * bits the stick itself holds. The toggle itself is the
     * constructor property above.
     */
    @Volatile
    private var stickMask: Int = 0

    @Volatile
    private var stickDeflection: Float = 0f

    /**
     * Alpha.16 — the pulse's step within its 6-frame period.
     * Emu-thread only: it advances once per emulated step in
     * [nextPulsedMask] and restarts at 0 on resume, so a
     * resumed game never picks up mid-period.
     */
    private var steeringStep: Int = 0

    /** The mask the loop last pushed to the bridge (emu-thread only). */
    private var lastPulsedMask: Int = -1

    /** The latest frame drawn, for save-state thumbnails. Written by the emu loop, read only while paused (the loop is idle then, so the Bitmap is stable). */
    @Volatile
    private var latestFrame: Bitmap? = null

    /**
     * Performance fix 8 — the frame post reuses one Runnable:
     * the emu loop used to allocate a posting lambda every
     * frame (60x/s). The Runnable reads the latest frame the
     * loop just published (volatile, double-buffered, so the
     * Bitmap it hands over is stable); if the main thread is
     * behind and two posts queue up, the second simply
     * re-delivers the newest frame — a drop, never a stale
     * one, which is what the consumed-flag fix already does.
     */
    private val framePostRunnable = Runnable {
        latestFrame?.let { onFrame(it) }
    }

    /** The latest frame drawn, for screenshots (null before the first frame). */
    fun latestFrameBitmap(): Bitmap? = latestFrame

    /**
     * Black screen on launch (Robert, 2026-11-15): the
     * surface-ready half of the fix. Re-delivers the latest
     * frame through the normal onFrame path (setFrame +
     * requestRender, now that the GL thread exists), so a
     * first frame whose render request was dropped before
     * the surface existed still draws. Null-safe: before
     * the first frame there is nothing to replay. A second
     * post racing a fresh frame only re-delivers the newest
     * one — a drop, never a stale one (see framePostRunnable).
     * Safe to call from any thread (Handler.post).
     */
    fun replayLatestFrame() {
        mainHandler.post(framePostRunnable)
    }

    /**
     * Phase 6 (item 11) — video recording: the game's frames into
     * an H.264 MP4 (see VideoRecorder — video only, no audio).
     * The recorder is created by [startRecording] and fed one
     * frame per displayed frame by the emu loop; [stopRecording]
     * finalizes the file. The caller owns the FileDescriptor
     * (see CaptureStore) and closes it after stop.
     */
    private val videoRecorder = VideoRecorder()

    /** The game's native frame size, once loaded (0 before load). */
    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    /** True while a recording is running. */
    val isRecording: Boolean get() = videoRecorder.isRecording

    /** Seconds since the recording started (0 when not recording). */
    fun recordingElapsedSeconds(): Long = videoRecorder.elapsedSeconds()

    /**
     * Start recording into [outputFd]. False when the game is not
     * loaded yet (no frame size to configure the encoder with)
     * or a recording is already running. The recording starts
     * even while paused — frames simply start flowing on resume.
     */
    fun startRecording(outputFd: java.io.FileDescriptor): Boolean {
        if (!loaded) return false
        if (videoWidth <= 0 || videoHeight <= 0) return false
        return videoRecorder.start(outputFd, videoWidth, videoHeight)
    }

    /** Stop recording and finalize the MP4. False when nothing was recording, or when no frame was ever recorded. */
    fun stopRecording(): Boolean = videoRecorder.stop()

    /** Frames recorded in the current (or just-finished) recording — 0 when it never saw a frame. */
    fun recordingFrameCount(): Int = videoRecorder.framesRecorded

    /**
     * Phase 6 (item 9) — the rewind ring: one serialized state a
     * second while the game runs (30 snapshots = 30 seconds).
     * Captured on the emu thread, between frames — no retro_run
     * in flight there, so the serialize is safe (the paused-only
     * rule for saveState is about the UI thread racing the loop).
     * The ring is in-memory only: quitting never writes it, and
     * a state load clears it — the future diverges there.
     */
    private val rewindBuffer = RewindBuffer()
    private var framesSinceRewindCapture = 0

    /**
     * Performance fix 6 — the serialized-state size for the
     * loaded game, cached once after load. The size is stable
     * per game (same save RAM + RTC every call), and measuring
     * it costs a full throwaway serialize — retro_serialize_
     * size() saves the whole state into a VFileMemChunk just
     * to read its length — so calling it per snapshot doubled
     * the work. 0 when unknown; set in the emu loop after the
     * game loads, and re-measured by serializeState() if a
     * serialize ever fails for size.
     */
    private var serializeSize = 0

    /**
     * Performance fix 6 — one reusable scratch buffer for
     * serializing, always sized exactly to [serializeSize]
     * (exact, not at-least: the bridge returns the array
     * length as the bytes written, so a larger array would
     * over-report). Shared by the rewind capture (emu thread,
     * while running) and the save paths (while paused) — the
     * two never overlap, so one buffer is safe. Snapshots and
     * save files keep their own copyOf, so nothing aliases
     * the scratch.
     */
    private var serializeScratch = ByteArray(0)

    /**
     * Performance fix 6 — serialize the core state exactly
     * once into the reusable scratch and return an owned
     * copy, or null. One core serialize on the fast path: the
     * size comes from the cache, and the bridge writes
     * straight into the array (it refuses, writing nothing,
     * when the array is too small). If the state outgrew the
     * cached size, the cache is re-measured once and the
     * serialize retried. Only on the emu thread between
     * frames, or while paused — never racing retro_run.
     */
    private fun serializeState(): ByteArray? {
        if (serializeSize <= 0) {
            serializeSize = Bridge.nativeSerializeSize()
            if (serializeSize <= 0) return null
        }
        if (serializeScratch.size != serializeSize) serializeScratch = ByteArray(serializeSize)
        val written = Bridge.nativeSerialize(serializeScratch)
        if (written >= 0) return serializeScratch.copyOf(written)
        // The state outgrew the cached size: re-measure once and retry.
        serializeSize = Bridge.nativeSerializeSize()
        if (serializeSize <= 0) return null
        if (serializeScratch.size != serializeSize) serializeScratch = ByteArray(serializeSize)
        val retried = Bridge.nativeSerialize(serializeScratch)
        if (retried < 0) return null
        return serializeScratch.copyOf(retried)
    }

    /** Snapshots held for rewind (0 when the game just started). */
    fun rewindSize(): Int = rewindBuffer.size

    /**
     * Capture one rewind snapshot (the serialized core state plus
     * the RA progress that belongs to it). Only on the emu thread,
     * between frames — never call it from the UI thread.
     * Performance fix 6 — one core serialize into the reusable
     * scratch (see serializeState), not the old two serializes
     * plus a fresh ByteArray per snapshot.
     */
    private fun captureRewindSnapshot() {
        val state = serializeState() ?: return
        rewindBuffer.push(state, RaClient.serializeProgress())
    }

    /**
     * Start rewinding: pause the loop (which flushes the battery
     * save and banks the playtime, like any pause) and hold the
     * game still for [stepRewind]. False when there is nothing to
     * rewind to, or the game is already paused — the rewind
     * control only runs while playing.
     */
    @Volatile
    private var rewinding = false

    fun startRewind(): Boolean {
        if (!loaded || !running || paused) return false
        if (rewindBuffer.isEmpty) return false
        pause()
        rewinding = true
        return true
    }

    /**
     * Step one snapshot back: unserialize the newest snapshot
     * and restore the RA progress that belongs to it (or reset
     * the progress when the snapshot had none, so the progress
     * matches the rewound game). Only while rewinding — the loop
     * is paused, so no frame races the unserialize. A failed
     * unserialize leaves the snapshot in place for a retry.
     * False when the ring is empty or the game is not rewinding.
     */
    fun stepRewind(): Boolean {
        if (!loaded || !paused || !rewinding) return false
        val snapshot = rewindBuffer.peek() ?: return false
        if (!Bridge.nativeUnserialize(snapshot.state)) return false
        rewindBuffer.pop()
        if (snapshot.raProgress != null) RaClient.deserializeProgress(snapshot.raProgress)
        else RaClient.resetProgress()
        // The rewound state's own RTC replaces any spun clock
        // (see rtcOverride).
        rtcOverride = null
        return true
    }

    /** Stop rewinding and resume from the rewound state. A no-op when not rewinding — a pause from the pause menu never resumes through here. */
    fun endRewind() {
        if (!rewinding) return
        rewinding = false
        resume()
    }

    fun setInput(mask: Int) {
        inputMask = mask
        // Guard on loaded, not running: running is true from
        // start() until the emu thread's failed ensureLoaded
        // clears it, so a UI-thread input in that window would
        // call a native method whose library never loaded
        // (UnsatisfiedLinkError — seen once in CI run
        // 43509040676, topBarVisibleWithDevStatsOn). The mask
        // is stored first and the emu loop pushes it after a
        // successful load, so nothing is lost by waiting.
        if (loaded) Bridge.nativeSetInput(mask)
    }

    /**
     * Alpha.16 — proportional steering: the toggle in effect
     * for this game, after the constructor snapshot. Called
     * from the layout editor's Done when the per-game
     * override changes mid-session; the game is paused there,
     * so the loop picks the new value up on resume with the
     * pulse counter restarted.
     */
    fun setProportionalSteering(enabled: Boolean) {
        proportionalSteering = enabled
    }

    /**
     * Alpha.16 — the stick's live state, reported by
     * StickControl on every knob move (and on release, as
     * 0 / 0f): its direction mask and its normalised
     * deflection t in 0..1. Plain field writes — the loop
     * reads them once per step, so a report that lands
     * between steps takes effect on the next one.
     */
    fun setStickState(mask: Int, deflection: Float) {
        stickMask = mask
        stickDeflection = deflection.coerceIn(0f, 1f)
    }

    /**
     * Alpha.16 — the mask the core is handed this step, with
     * the stick's direction bits gated by the steering pulse;
     * advances the pulse counter. The loop calls this once per
     * emulated step, so the pulse advances in game time (a
     * fast-forward step is a step, not a displayed frame).
     * Internal so the per-step sequence is testable without
     * the native loop (see PlayerSessionTest).
     */
    internal fun nextPulsedMask(): Int {
        val pulsed = ProportionalSteering.pulsedMask(
            inputMask, stickMask, steeringStep, stickDeflection, proportionalSteering,
        )
        steeringStep = (steeringStep + 1) % ProportionalSteering.PERIOD_FRAMES
        return pulsed
    }

    /** Alpha.16 — push the pulsed mask to the bridge when it changed since the last step. */
    private fun pushPulsedInput() {
        val pulsed = nextPulsedMask()
        if (pulsed != lastPulsedMask) {
            lastPulsedMask = pulsed
            Bridge.nativeSetInput(pulsed)
        }
    }

    /** Alpha.16 — restart the pulse counter (the loop calls this on resume from pause). */
    private fun resetSteeringPulse() {
        steeringStep = 0
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
        // Performance fix 9 — off-speed, the audio ring is
        // drained and discarded, so the native resampler
        // stops doing work nobody hears. Guard on loaded, as
        // setInput does: a native call before load would hit
        // a library that never loaded.
        if (loaded) Bridge.nativeSetAudioDiscard(speed != PlaySpeed.NORMAL)
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
        // safe to read the live save-RAM buffer. Performance fix 9
        // — the write runs on the save-IO thread, not the UI thread
        // (the file write used to hitch pause); the emu-loop tail
        // drains the queue before unload, so the flush provably
        // finishes before the game goes away.
        saveIoExecutor.submit(Runnable { synchronized(saveFlushLock) { flushSave() } })
        // Bank the run's playtime while the core is idle.
        bankPlaytime()
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
        // Wake the parked loops (see pauseLock).
        synchronized(pauseLock) { pauseLock.notifyAll() }
        // A new run starts now.
        playStartMillis = System.currentTimeMillis()
    }

    /** Push core options to the bridge. The core re-reads them on the next frame; safe to call before init (the bridge seeds its table on first use) and safe to call when no game is running — it just loads the libraries, which is harmless. */
    fun applyOptions(options: Map<String, String>) {
        if (!Bridge.ensureLoaded()) return
        for ((key, value) in options) Bridge.nativeSetOption(key, value)
    }

    /**
     * Phase 6 (item 3) — apply a cheat list to the running game:
     * reset all, then set each enabled code. False (and nothing
     * applied) when no game is loaded. Safe to call while paused
     * (no retro_run in flight) and from the emu thread right after
     * load; never call it mid-frame.
     */
    fun applyCheats(cheats: List<Cheat>): Boolean {
        if (!loaded) return false
        Bridge.nativeClearCheats()
        for (cheat in cheats) {
            if (cheat.enabled) Bridge.nativeSetCheat(cheat.code)
        }
        return true
    }

    /**
     * Phase 6 (item 12) — the cartridge clock (MBC3 RTC): read
     * the live 48-byte RTC buffer, or null when this cartridge
     * has no clock (every GBA game, and GB/GBC games without an
     * MBC3 RTC) or the read fails. Only safe while paused or
     * stopped — never mid-frame. The panel reads it when it
     * opens, and again for each thumbwheel spin.
     */
    fun readRtcBytes(): ByteArray? {
        if (!loaded) return null
        val rtcSize = Bridge.nativeRtcSize()
        if (rtcSize <= 0) return null
        val bytes = ByteArray(rtcSize)
        return if (Bridge.nativeReadRtc(bytes)) bytes else null
    }

    /**
     * The RTC the next flushSave() writes, when the panel spun
     * the clock (see [spinRtc]). The live clock is untouched —
     * the core only reads the RTC buffer at load — so the spun
     * time must survive in the .sav even if the core's periodic
     * save-clean overwrites the live buffer with the unspun time
     * after the game resumes. Cleared whenever a state is loaded
     * or the game is rewound: the loaded state's own RTC replaces
     * it, and the spun time belongs to the timeline that was left.
     */
    @Volatile
    private var rtcOverride: ByteArray? = null

    /**
     * Phase 6 (item 12) — spin the cartridge clock: write the
     * adjusted RTC buffer ([CartridgeClock.adjust] builds it)
     * and record it as the RTC the next flushSave() writes, so
     * the game sees the new time when it next loads. False (and
     * nothing recorded) when no game is loaded or the write
     * fails. Only safe while paused — never mid-frame.
     */
    fun spinRtc(adjusted: ByteArray): Boolean {
        if (!loaded || !paused) return false
        if (!Bridge.nativeWriteRtc(adjusted)) return false
        rtcOverride = adjusted.copyOf()
        return true
    }

    fun applyOptions() = applyOptions(options)

    /**
     * Write the live save-RAM + RTC buffers to <base>.sav. Only
     * safe while the emu loop is paused or stopped — never mid-frame.
     * Returns false when the game has no save RAM (most GB games
     * without a battery, and any game that never saves).
     */
    fun flushSave(): Boolean {
        // Performance fix 9 — the lock guards the write
        // against a concurrent submit from pause()/stop():
        // the save-IO thread runs one at a time, but a
        // direct call from the emu-loop tail could overlap
        // one still queued.
        synchronized(saveFlushLock) { return flushSaveLocked() }
    }

    private fun flushSaveLocked(): Boolean {
        if (!loaded) return false
        val saveSize = Bridge.nativeSaveRamSize()
        if (saveSize <= 0) return false
        val save = ByteArray(saveSize)
        if (!Bridge.nativeReadSaveRam(save)) return false
        val rtcSize = Bridge.nativeRtcSize()
        val bytes = if (rtcSize > 0) {
            // Phase 6 (item 12): a spun clock (rtcOverride) wins
            // over the live buffer — the core's periodic save-clean
            // may already have overwritten the buffer with the
            // unspun time after the game resumed.
            val rtc = rtcOverride?.takeIf { it.size == rtcSize } ?: run {
                val live = ByteArray(rtcSize)
                if (!Bridge.nativeReadRtc(live)) return false
                live
            }
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
        if (!serializeTo(saveStore.stateFile(saveBase, slot))) return false
        // A missing thumbnail never fails the save itself.
        try {
            writeThumbnail(saveStore.thumbnailFile(saveBase, slot))
        } catch (_: Exception) {
        }
        // The RA progress sidecar (Phase 6, item 8): written beside
        // the state, or a stale one deleted when RA is not tracking
        // this game. A failed sidecar never fails the save.
        writeRaSidecar(saveStore.raSidecarFile(saveBase, slot))
        return true
    }

    /** Write the auto-save state (the dedicated auto slot). Only while paused — same rule as saveState. */
    fun saveAutoState(): Boolean {
        if (!loaded || !paused) return false
        return saveAutoStateNow()
    }

    /**
     * Performance fix 9 — the auto-save body, with no
     * loaded/paused guards: the caller evaluates the guards
     * at submit time, and the write runs on the save-IO
     * thread. Only while the emu loop is idle (pause, or
     * the stop() tail draining the queue before unload).
     */
    private fun saveAutoStateNow(): Boolean {
        if (!serializeTo(saveStore.autoStateFile(saveBase))) return false
        try {
            writeThumbnail(saveStore.autoThumbnailFile(saveBase))
        } catch (_: Exception) {
        }
        writeRaSidecar(saveStore.autoRaSidecarFile(saveBase))
        return true
    }

    /** Load the auto-save state. Only while paused. */
    fun loadAutoState(): Boolean {
        if (!loaded || !paused) return false
        if (!unserializeStateFile(saveStore.autoStateFile(saveBase))) return false
        // The RA progress belongs to this state, not to the game
        // as it was before the load (a paused load: the game is
        // already loaded in RA, so the restore applies at once).
        readRaSidecar(saveStore.autoRaSidecarFile(saveBase))
        // The timeline diverges here: snapshots from before the
        // load belong to a future that no longer exists.
        rewindBuffer.clear()
        // The loaded state's own RTC replaces any spun clock
        // (see rtcOverride).
        rtcOverride = null
        return true
    }

    /**
     * Read a state file and unserialize it into the core. False
     * when the file is missing or unreadable, or the core refuses
     * it. Shared by the paused loads and the pending load at
     * startup (where no frame is running yet, so the paused guard
     * does not apply).
     */
    private fun unserializeStateFile(file: java.io.File): Boolean {
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
    fun thumbnailExists(slot: Int): Boolean = saveStore.thumbnailExists(saveBase, slot)
    fun autoStateExists(): Boolean = saveStore.autoStateExists(saveBase)
    fun autoStateModifiedAt(): Long? = saveStore.autoStateModifiedAt(saveBase)
    fun autoThumbnailExists(): Boolean = saveStore.autoThumbnailFile(saveBase).exists()

    /**
     * The thumbnail PNG for a save state, decoded — or null when
     * the slot has no thumbnail yet (states saved before this
     * feature, or saved before the first frame). Decodes on the
     * caller's thread; the files are ~120px wide, so this is
     * milliseconds — the pause menu calls it during composition.
     */
    fun stateThumbnail(slot: Int): Bitmap? {
        val file = saveStore.thumbnailFile(saveBase, slot)
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    /** The auto-save's thumbnail, decoded — or null when there is none. */
    fun autoStateThumbnail(): Bitmap? {
        val file = saveStore.autoThumbnailFile(saveBase)
        if (!file.exists()) return null
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Serialize the core state into [file]. Only valid while the
     * emu loop is paused — serialize must never race retro_run.
     * Performance fix 6 — one core serialize into the reusable
     * scratch (see serializeState).
     */
    private fun serializeTo(file: java.io.File): Boolean {
        val state = serializeState() ?: return false
        return try {
            file.apply { parentFile?.mkdirs() }.writeBytes(state)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Write a small PNG of the latest frame to [file]. Nothing when
     * no frame has been drawn yet (a state saved before the first
     * frame has no picture to show). Runs on the caller's thread —
     * the pause menu calls saveState on IO, and the auto-save runs
     * on the quit path, where the battery save already does file IO.
     */
    private fun writeThumbnail(file: java.io.File) {
        val frame = latestFrame ?: return
        val scaled = Bitmap.createScaledBitmap(
            frame,
            THUMBNAIL_WIDTH,
            (frame.height * THUMBNAIL_WIDTH) / frame.width,
            false,
        )
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            if (scaled != frame) scaled.recycle()
        }
    }

    /** Load the save state in slot [slot]. Only while paused. */
    fun loadState(slot: Int): Boolean {
        if (!loaded || !paused) return false
        if (!unserializeStateFile(saveStore.stateFile(saveBase, slot))) return false
        // The RA progress belongs to this state (see loadAutoState).
        readRaSidecar(saveStore.raSidecarFile(saveBase, slot))
        // The timeline diverges here (see loadAutoState).
        rewindBuffer.clear()
        // The loaded state's own RTC replaces any spun clock
        // (see rtcOverride).
        rtcOverride = null
        return true
    }

    /**
     * Phase 6 (item 8) — write the RA progress to a save state's
     * sidecar (or delete a stale one when RA is not tracking this
     * game, so a state never carries progress from an older
     * session). Only call while paused, like the state snapshot
     * itself. A failed sidecar never fails the save.
     */
    private fun writeRaSidecar(sidecar: java.io.File) {
        try {
            val bytes = RaClient.serializeProgress()
            if (bytes != null) {
                sidecar.parentFile?.mkdirs()
                sidecar.writeBytes(bytes)
            } else if (sidecar.isFile) {
                sidecar.delete()
            }
        } catch (_: Exception) {
            // The state itself is already saved; a failed sidecar
            // must not fail the save.
        }
    }

    /**
     * Phase 6 (item 8) — restore the RA progress from a save
     * state's sidecar (or reset it when the state has none, so
     * the progress matches the rewound game). Only call after a
     * successful unserialize, while paused. When RA has not
     * loaded the game yet (the startup pending load), the
     * restore waits for the load result instead — see
     * [restoreRaProgressOnLoad].
     */
    private fun readRaSidecar(sidecar: java.io.File) {
        if (!RaClient.gameLoaded) return
        val bytes = try {
            if (sidecar.isFile) sidecar.readBytes() else null
        } catch (_: Exception) {
            null
        }
        if (bytes != null) RaClient.deserializeProgress(bytes)
        else RaClient.resetProgress()
    }

    /**
     * Phase 6 (item 8) — the startup pending load's RA restore:
     * the state was unserialized before the RA load result
     * arrived, so the sidecar's progress waits in RaClient until
     * the load completes (or is discarded when it fails). Pass
     * the sidecar's bytes, or null when the state had none.
     */
    private fun restoreRaProgressOnLoad(sidecar: java.io.File) {
        val bytes = try {
            if (sidecar.isFile) sidecar.readBytes() else null
        } catch (_: Exception) {
            null
        }
        RaClient.restoreProgressAfterLoad(bytes)
    }

    fun start() {
        if (running) return
        running = true
        emuThread = Thread(::emuLoop, "poing-emu").also { it.start() }
    }

    fun stop() {
        // Quit auto-save (Phase 6, item 2): the game is paused
        // first if it is still running, so the serialize can never
        // race a frame — then the auto state is written before the
        // game unloads, which is the last chance to capture it.
        // Never started (or a failed start) is a no-op: loaded is
        // false, so nothing is written.
        if (loaded && running && !paused) pause()
        // Performance fix 9 — the auto-save write runs on
        // the save-IO thread; the emu-loop tail drains the
        // queue before unload, so the write provably finishes
        // before the game goes away.
        if (loaded && paused) saveIoExecutor.submit(Runnable { saveAutoStateNow() })
        // Any run not already banked by pause() (a no-op when the
        // stamp is already cleared).
        bankPlaytime()
        // Phase 6 (item 11): a recording left running at quit is
        // finalized here, so the MP4 is a whole file (the caller
        // still closes the FileDescriptor).
        videoRecorder.stop()
        running = false
        paused = false
        // Wake any loop parked in pauseLock.wait() so the joins
        // below do not wait out their timeouts.
        synchronized(pauseLock) { pauseLock.notifyAll() }
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
        // Performance fix 9 — the save-IO thread is done: the
        // emu-loop tail already drained the queue before the
        // joins returned, so nothing is left to write. Shut
        // it down, or every game start would leave a parked
        // daemon thread behind. (shutdown lets any task
        // submitted before it finish; nothing submits after
        // stop — the session is dead.)
        saveIoExecutor.shutdown()
        // RetroAchievements (Phase 6, item 8): the game is gone,
        // so the per-game RA state goes with it (the login stays).
        RaClient.onGameQuit()
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
        // Phase 6 (item 11) — the recording size, for the encoder
        // (configured at the game's native size when a recording
        // starts).
        videoWidth = width
        videoHeight = height
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
        // Stored cheats for this game, applied before the first
        // frame so they are live from the start.
        cheatStore?.let { applyCheats(it.list(saveBase)) }
        // A pending load from the Save Manager: the state is
        // unserialized before the first frame, so the game starts
        // exactly where the state left it. The RA progress
        // belongs to that state, not to the game as it was — but
        // the RA load below has not completed yet, so the restore
        // waits for it (see restoreRaProgressOnLoad).
        loadStateOnStart?.let {
            if (unserializeStateFile(saveStore.stateFile(saveBase, it))) {
                restoreRaProgressOnLoad(saveStore.raSidecarFile(saveBase, it))
            }
        }
        if (loadAutoOnStart) {
            if (unserializeStateFile(saveStore.autoStateFile(saveBase))) {
                restoreRaProgressOnLoad(saveStore.autoRaSidecarFile(saveBase))
            }
        }
        // RetroAchievements (Phase 6, item 8): load this game's
        // achievements. The load is async; a game whose hash is
        // not recognised simply leaves RA inactive for it.
        RaClient.loadGame(gamePath)

        // Performance fix 6 — cache the serialized-state size
        // once, now the game is fully loaded (battery save,
        // cheats and any pending state are in): the size is
        // stable per game, and measuring it costs a full
        // throwaway serialize. On the emu thread, before the
        // first retro_run, so nothing races it. A 0 here (the
        // measure failed) just leaves the cache empty and
        // serializeState() measures lazily on first use.
        serializeSize = Bridge.nativeSerializeSize()

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

        // The first run starts now (playtime banks on pause/stop).
        playStartMillis = System.currentTimeMillis()

        while (running) {
            if (paused) {
                // Parked: no retro_run, no new frames, and no
                // wakeups at all while paused. The last frame
                // stays on screen behind the pause screen.
                // resume()/stop() notify; the while re-checks
                // the flags, so a notify that lands before the
                // wait is not lost.
                try {
                    synchronized(pauseLock) {
                        while (paused && running) pauseLock.wait()
                    }
                } catch (e: InterruptedException) {
                    break
                }
                // Alpha.16 — proportional steering: the pulse
                // counter restarts cleanly on resume, so a
                // resumed game never picks up mid-period.
                resetSteeringPulse()
                continue
            }
            val t0 = System.nanoTime()
            inFrame = true
            try {
                // Alpha.16 — proportional steering: the mask
                // the core is handed this step, pulsed from
                // the stick's deflection. The haptics and the
                // input display read the raw mask the UI
                // pushed, so they never see the pulsing.
                pushPulsedInput()
                Bridge.nativeRunFrame()
            } finally {
                inFrame = false
            }
            // RetroAchievements (Phase 6, item 8): one evaluation
            // per displayed frame, after the frame run. A no-op
            // while no game is loaded in RA.
            RaClient.onFrame()
            // Rewind (Phase 6, item 9): one snapshot a second, on
            // this thread between frames — no retro_run in flight.
            // Performance fix 6 — only when the rewind control
            // is visible: with it hidden (the default) the
            // snapshots could never be used, so capturing them
            // was pure waste. The control is shown from the same
            // snapshotted setting, so the gate can never strand
            // a visible control.
            if (rewindControlVisible &&
                ++framesSinceRewindCapture >= RewindBuffer.CAPTURE_EVERY_FRAMES
            ) {
                framesSinceRewindCapture = 0
                captureRewindSnapshot()
            }
            // Performance fix 5 — the counters measure frames
            // the core PRODUCED (one per retro_run above), not
            // frames handed out: after this fix a static screen
            // skips the copy and a behind GL thread drops the
            // frame, so counting hand-outs would read 0 fps on
            // a title screen while the game is running fine.
            // Before this fix produced == handed out, so the
            // dev-stats meaning is unchanged.
            framesDrawn++
            statWindowFrames++
            // Performance fix 5 — backpressure + dirty
            // detection: nativeGetFrame returns false when the
            // picture is unchanged (the bridge compares it
            // against the last handed-out frame), so a static
            // screen skips the whole copy chain below. When the
            // GL thread has not consumed the previous frame,
            // do not even ask the bridge — the frame would be
            // overwritten before it is ever drawn, so the copy
            // is pure waste; the game still advanced (retro_run
            // above), the dropped frame is simply never shown.
            // A running recording bypasses the consumed check:
            // the encoder needs every frame it can get, and its
            // timestamps are wall-clock, so a skipped duplicate
            // only holds the previous frame longer.
            val mayCopy = videoRecorder.isRecording || frameConsumed()
            if (mayCopy && Bridge.nativeGetFrame(pixels, dims)) {
                val w = dims[0]
                val h = dims[1]
                if (w == width && h == height) {
                    val back = 1 - front
                    bitmaps[back].setPixels(pixels, 0, width, 0, 0, width, height)
                    front = back
                    val frame = bitmaps[front]
                    latestFrame = frame
                    // Phase 6 (item 11): the recording eats one
                    // frame per displayed frame, drawn onto the
                    // encoder's surface on this thread (the front
                    // Bitmap is stable until the next swap — the
                    // next frame writes the other buffer).
                    if (videoRecorder.isRecording) videoRecorder.recordFrame(frame)
                    mainHandler.post(framePostRunnable)
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
        // Performance fix 9 — drain the save-IO queue before
        // unload: a pause flush or a quit auto-save submitted
        // above must finish before the game unloads, or the
        // serialize could race nativeUnloadGame. The no-op
        // task waits for everything queued ahead of it.
        try {
            saveIoExecutor.submit(Runnable {}).get()
        } catch (_: Exception) {
        }
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
        // Performance fix 9 — a game loaded at non-normal
        // speed starts discarded, matching setSpeed.
        Bridge.nativeSetAudioDiscard(speed != PlaySpeed.NORMAL)
        audioThread = Thread(::audioLoop, "poing-audio").also { it.start() }
    }

    private fun audioLoop() {
        // Performance fix 9 — the audio thread gets the
        // audio priority, so the mixer never starves behind
        // the emu loop.
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = audioTrack ?: return
        val samples = ShortArray(4096 * 2) // stereo frames * 2 channels
        while (running) {
            if (paused) {
                // Parked: the track is paused and flushed, so
                // there is nothing to drain — and no wakeups at
                // all while paused. resume()/stop() notify; the
                // while re-checks the flags, so a notify that
                // lands before the wait is not lost.
                try {
                    synchronized(pauseLock) {
                        while (paused && running) pauseLock.wait()
                    }
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
                // Performance fix 9 — the ring is empty, so
                // there is nothing to drain: 20 ms, not 5.
                // The ring holds ~341 ms, so a 20 ms wait
                // cannot starve it, and it cuts the polling
                // wakeups from up to 200/s to up to 50/s.
                try {
                    Thread.sleep(20)
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
