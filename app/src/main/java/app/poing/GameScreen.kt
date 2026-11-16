package app.poing

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Phase 2 spike player screen: a fullscreen game view, minimal
 * touch controls, pause, and a diagnostic overlay.
 *
 * The game view is letterboxed (ContentScale.Fit) at the core's
 * native aspect — 160x144 (10:9) for GB/GBC, 240x160 (3:2) for
 * GBA. Controls are D-pad + A/B + Start/Select, plus L/R for GBA
 * only. There is no library, no save states and no settings screen
 * yet — those are later phases.
 *
 * The diagnostic overlay (top-left, always on in the spike) reads
 * the frame size, the core's fps from av_info, the measured fps,
 * and the frames drawn so far — so a no-video bug is diagnosable
 * on the device without another round trip.
 *
 * Landscape + keep-screen-on while playing; both are restored on
 * exit. The activity declares configChanges, so the rotation never
 * recreates it (see MainActivityStartupTest).
 */
/**
 * Alpha.8 — the display resolution for one game, pure
 * (no native code, no Compose): the chain wanted for the
 * loaded game's system, that system's reconstruction
 * toggle, and that system's edge mode, through
 * EdgeSmoothing.resolve — the chain and the edge stay
 * alternatives, never stacked, and the reconstruction
 * wins over both. GameScreen snapshots this at game
 * start; the unit test pins it per system without
 * launching the player.
 */
internal fun displayResolutionFor(
    settings: AppSettingsStore,
    system: GameSystem,
): DisplayResolution {
    val wanted = DisplayChain.forSystem(system)
    // Each system's reconstruction toggle only
    // affects its own games (GB, GBC, GBA — Tasks
    // 5, 6, 7, same rule).
    val recon = when {
        system == GameSystem.GB && settings.reconGbEnabled() -> ReconMode.GB
        system == GameSystem.GBC && settings.reconGbcEnabled() -> ReconMode.GBC
        system == GameSystem.GBA && settings.reconGbaEnabled() -> ReconMode.GBA
        else -> ReconMode.OFF
    }
    return EdgeSmoothing.resolve(
        wanted,
        settings.chainEnabled(wanted),
        settings.edgeMode(system),
        recon,
    )
}

@Composable
fun GameScreen(
    gamePath: String,
    gameName: String,
    isGba: Boolean,
    // Phase 8 — the header-detected system (GB vs GBC matters:
    // a GBC game in a .gb file still plays as GBC). MainActivity
    // passes it; the default keeps the old call sites compiling.
    system: GameSystem = if (isGba) GameSystem.GBA else GameSystem.GB,
    optionsStore: CoreOptionsStore,
    appSettings: AppSettingsStore,
    // Phase 6 (item 5) — a pending load from the Save Manager,
    // applied by the session before the first frame.
    loadStateOnStart: Int? = null,
    loadAutoOnStart: Boolean = false,
    // Phase 6 (item 7) — haptics, injected for tests; null builds
    // one from the context (the production path).
    haptics: Haptics? = null,
    onQuit: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    // Performance fix 1 (2026-11-14) — the frame Bitmap is NOT
    // Compose state on the GL path: it changed every frame, so
    // the whole player screen re-executed 60x/s for a Bitmap
    // Compose never draws (the GL view owns the picture). The
    // GL path keeps only the frame ASPECT in state, written
    // only when the geometry changes; the Bitmap is Compose
    // state only on the no-GL fallback path, where the plain
    // Compose Image draws it.
    var frameAspect by remember { mutableStateOf<Float?>(null) }
    var fallbackBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stats by remember { mutableStateOf<PlayerStats?>(null) }
    // Edge smoothing, item 5 (2026-11-14) — the dev-stats
    // readout: which program actually drew the last frame.
    // Read from the GL view when the stats arrive (both on
    // the main thread), so the diagnostic line shows the
    // truth about the picture, not the request. Null until
    // the first stats — or when there is no GL view at all
    // (the Compose-Image fallback), when the line shows no
    // token rather than a guessed one.
    var drawPath by remember { mutableStateOf<DrawPath?>(null) }
    var paused by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCheats by remember { mutableStateOf(false) }
    // Phase 6 (item 12) — Cartridge Surgery: the slide-out
    // cartridge schematic. The hardware toggles are session-only
    // (default on, this game only); the RTC time and the solar
    // level are loaded fresh each time the panel opens.
    var showCartridge by remember { mutableStateOf(false) }
    var cartridgeTime by remember { mutableStateOf<CartridgeClock.RtcTime?>(null) }
    var solarLevel by remember { mutableStateOf(optionsStore.get("mgba_solar_sensor_level")) }
    var rumbleEnabled by remember { mutableStateOf(true) }
    var sensorsEnabled by remember { mutableStateOf(true) }
    // Phase 6 (item 10a) — the cartridge sensors (tilt /
    // gyroscope / solar): the phone's hardware, fed to the core
    // while a game is playing. Built here (not beside the
    // session lifecycle effect below) so doPause/doResume and
    // applySensors can reach it — local functions must be
    // declared before use, and the sensor state lives here.
    val cartridgeSensors = remember { CartridgeSensors(context) }
    // Phase 6 (item 8c) — the RetroAchievements screens over the
    // paused game, like the cheats screen.
    var showAchievements by remember { mutableStateOf(false) }
    var showLeaderboards by remember { mutableStateOf(false) }
    // Phase 6 (item 11) — capture: whether a recording is
    // running (the source of truth for the REC indicator and the
    // pause menu's Record button) and its output (the
    // FileDescriptor the recorder's muxer writes to — closed
    // when the recording stops or the player leaves).
    var recording by remember { mutableStateOf(false) }
    // Performance fix 9 — the REC clock state object, passed
    // to FloatingPlayerStatus so the 1s tick recomposes only
    // the tiny REC row (see PlayerTopBar.kt), not the whole
    // GameScreen scope. The delegate var stays for the
    // bar-visible TopBarRow and the tick/reset writes.
    val recordingElapsedState = remember { mutableStateOf(0L) }
    var recordingElapsed by recordingElapsedState
    var videoOutput by remember { mutableStateOf<CaptureStore.VideoOutput?>(null) }
    val captureScope = rememberCoroutineScope()
    var pauseMessage by remember { mutableStateOf<String?>(null) }
    val held = remember { HeldInput() }

    // Phase 6 (item 4) — per-game saved control positions,
    // overlay defaults (Robert, 2026-11-14): the layout is
    // resolved per game — the game's own override when it has
    // one, otherwise its handheld's default (GB / GBC / GBA,
    // adjustable in Settings), otherwise the factory default
    // (see ControlLayoutStore.loadForGame). Editing is entered
    // from the pause menu while the game stays paused. The
    // stored ROM file is <sha256>.<ext>: its stem is the stable
    // per-game key.
    val saveBase = java.io.File(gamePath).nameWithoutExtension
    val layoutStore = remember { ControlLayoutStore(context.filesDir.resolve("control-layouts").apply { mkdirs() }) }
    var layout by remember(gamePath) { mutableStateOf(layoutStore.loadForGame(saveBase, system)) }
    // Alpha.10 — the left control for this game: the game's
    // own override when it has one, otherwise the global
    // default (D-pad). Resolved once per game, like the
    // layout: it can only change in Settings or in the layout
    // editor, never mid-game. Mutable so the editor's Done
    // can commit a new choice for this game at once.
    var leftControl by remember(gamePath) {
        mutableStateOf(layoutStore.leftControlOverride(saveBase) ?: appSettings.leftControl())
    }
    // Alpha.14 — the joystick dead zone for this game, in
    // units of full travel: the game's own override when it
    // has one, otherwise the global slider value (22% by
    // default). Resolved once per game, like the left
    // control: it can only change in Settings or in the
    // layout editor, never mid-game. Mutable so the editor's
    // Done can commit a new zone for this game at once.
    var deadZone by remember(gamePath) {
        mutableStateOf(
            layoutStore.resolvedStickDeadZonePct(saveBase, appSettings.stickDeadZonePct()) / 100f,
        )
    }
    // Alpha.16 — proportional steering for this game: the
    // game's own override when it has one, otherwise the
    // global switch (off by default). Resolved once per game,
    // like the dead zone. Mutable so the editor's Done can
    // commit a new choice for this game at once, and the
    // session is told through its setter there.
    var proportionalSteering by remember(gamePath) {
        mutableStateOf(
            layoutStore.resolvedProportionalSteering(saveBase, appSettings.proportionalSteering()),
        )
    }
    // The handheld default, snapshotted with the game like the
    // other player settings: it is what the editor's Reset
    // restores, and what Done falls back to when the draft
    // lands exactly on it (see the editor's onDone below).
    val handheldDefault = remember(gamePath) { layoutStore.loadDefault(system) }
    var editingLayout by remember { mutableStateOf(false) }
    // Overlay rework (Robert, 2026-11-13): the controls are
    // fractions of the full screen, so the overlay lives in the
    // root Box — but the Worm Light's drag head is drawn in the
    // player area's coordinates, so the canvas translates it by
    // the player area's top offset: the root height minus the
    // player area's height (the bar's height, when the bar is
    // showing). Zero while playing: the bar hides.
    var playerHeightPx by remember { mutableStateOf(0f) }

    // Phase 6 (item 1) — fast-forward / slow motion. The settings
    // are snapshotted here: they can only change on the Settings
    // screen, never while the player is open, so the player must
    // not re-read the file on every frame's recomposition.
    val ffControlVisible = remember { appSettings.ffControlVisible() }
    val ffSpeed = remember { appSettings.ffSpeed() }
    var slowOn by remember { mutableStateOf(false) }
    var ffHeld by remember { mutableStateOf(false) }

    // Phase 6 (item 9) — rewind: hold the RW button to step back
    // through the last 30 seconds. The control is snapshotted
    // here like the fast-forward control (off by default, per the
    // standing overlay rule). While held, the session is paused
    // and a snapshot is unserialized every 150ms.
    val rewindControlVisible = remember { appSettings.rewindControlVisible() }
    var rewinding by remember { mutableStateOf(false) }

    // Phase 6 (item 6) — the input display: the merged mask the
    // core sees (touch + gamepad), mirrored into state on every
    // input change so the pills update live. The toggle itself is
    // snapshotted here like the fast-forward control: settings can
    // only change on the Settings screen, never while the player
    // is open.
    val inputDisplayVisible = remember { appSettings.inputDisplayVisible() }
    var displayedMask by remember { mutableStateOf(0) }

    // Phase 6 (item 7) — haptics: a short click for every
    // overlay button, on by default, toggleable in Settings.
    // Snapshotted here like the other player settings:
    // settings can only change on the Settings screen,
    // never while the player is open.
    val hapticsImpl = remember {
        haptics ?: Haptics(
            context.getSystemService(Vibrator::class.java),
            appSettings.hapticsEnabled(),
            appSettings.hapticsStrength(),
        )
    }

    // Phase 8 — the display chain: the default look for this
    // system, or clean when its toggle is off. Snapshotted
    // here like the other player settings: settings can only
    // change on the Settings screen, never while the player
    // is open. The GB palette is read live (it can change in
    // Core Options while paused — doResume re-reads it).
    //
    // Phase 9 — edge smoothing wins over the chain
    // (EdgeSmoothing.resolve): when it is on, the chain is
    // off and the edge pass draws instead. The two are never
    // stacked. Task 6 — the reconstruction wins over both:
    // when it is on, the chain is off AND the edge mode is
    // off, and the recon passes draw instead.
    val display = remember { displayResolutionFor(appSettings, system) }
    val displayChain = display.chain
    val edgeMode = display.edge
    val reconMode = display.recon
    // The LCD grid over the reconstruction (Robert,
    // 2026-11-14): snapshotted at game start like the
    // recon toggle itself — one toggle for all systems,
    // and the renderer only reads it inside drawRecon.
    val reconGrid = remember { appSettings.reconGridEnabled() }
    var palette by remember { mutableStateOf(optionsStore.get("mgba_gb_colors")) }

    // Screen Optics dim fix (Robert, 2026-11-12): the live lux
    // for the diagnostic line (dev stats only). Only the
    // rounded whole-lux value is state, so a steady room does
    // not recompose the bar on every ~50 Hz sensor reading.
    var latestLux by remember { mutableStateOf<Int?>(null) }

    // Phase 8 — Screen Optics, snapshotted like the chain:
    // the Worm Light (off by default — an easter egg) and
    // whether a dark room may dim the screen (off by
    // default — dimming can hide the game).
    val wormLight = remember { appSettings.wormLightEnabled(system) }
    // Worm Light realism (Robert, 2026-11-12): the colour of
    // the lamp's hardware — dark medium gray by default. The
    // light itself is always warm white.
    val wormLightColor = remember { appSettings.wormLightColor() }
    // Alpha.10 — the coverage: how wide the pool spreads,
    // 0.75x–1.75x, 1x by default. Global and persisted, read
    // once per game like the colour.
    val wormLightCoverage = remember { appSettings.wormLightCoverage() }
    // Worm Light improvements (Robert, 2026-11-13): while a
    // drag is in progress the overlay reports its head, and
    // the player draws it above the controls — a dragged
    // head never disappears behind the D-pad. Outside a
    // drag this is null and nothing extra is drawn.
    var wormLightDragHead by remember { mutableStateOf<WormLightDragHead?>(null) }
    val opticsDimming = remember { appSettings.opticsDimmingEnabled(system) }

    // Player chrome (Robert, 2026-11-12) — the top bar hides
    // during active gameplay unless the dev-stats toggle is
    // on. Snapshotted like the other player settings:
    // settings can only change on the Settings screen, never
    // while the player is open.
    val devStats = remember { appSettings.devStatsEnabled() }
    var barPeeked by remember { mutableStateOf(false) }
    var peekNonce by remember { mutableStateOf(0) }
    // The bar shows while paused (the Resume button), while
    // dev stats are on, or for a few seconds after a back
    // swipe (see the BackHandler below) — but NEVER while
    // editing the layout: the editor owns the top then, with
    // its own Done pill (overlay rework, Robert, 2026-11-13).
    // The power light never hides — it is detached from the
    // bar (FloatingPlayerStatus, below).
    val barVisible = (paused || devStats || barPeeked) && !editingLayout
    fun peekBar() {
        // Already showing (paused, editing, dev stats):
        // nothing to time — the bar stays until that state ends.
        if (paused || editingLayout || devStats) return
        barPeeked = true
        peekNonce++
    }

    // The peek lasts TOP_BAR_PEEK_MS: long enough to find and
    // tap Pause, short enough that the bar is gone again
    // quickly. Each back swipe restarts the clock.
    LaunchedEffect(peekNonce) {
        if (peekNonce == 0) return@LaunchedEffect
        delay(TOP_BAR_PEEK_MS)
        barPeeked = false
    }

    // Top bar / back swipe (Robert, 2026-11-12): the bar is
    // ONLY reachable by a back swipe — the system back gesture
    // (the game-area tap peek is gone). A back swipe shows the
    // bar and NEVER exits the player: the only way out is the
    // bar / pause menu's own exit control. While editing the
    // layout, back exits the editor and discards the draft —
    // the editor's Done is the commit — like Mariner's Back.
    // While a sub-screen is open (Core Options, Cheats,
    // Cartridge Surgery, Achievements, Leaderboards), back
    // closes it, like any other screen; while the bar is
    // already visible (paused, dev stats) it does nothing —
    // no exit, no resume; the bar's controls are the way out.
    BackHandler {
        when {
            editingLayout -> editingLayout = false
            showSettings -> showSettings = false
            showCheats -> showCheats = false
            showCartridge -> showCartridge = false
            showAchievements -> showAchievements = false
            showLeaderboards -> showLeaderboards = false
            paused || devStats -> Unit
            else -> peekBar()
        }
    }

    // Phase 8 — the GL view the chain draws through. Built
    // once per game; a device that cannot build one (no
    // GLES 2.0) falls back to the plain Compose Image in
    // GameDisplayView, so the game stays visible either way.
    val glView = remember {
        try {
            GameGlView(context)
        } catch (_: Exception) {
            null
        }
    }

    // Phase 8 — apply the chain whenever it or the palette
    // changes: at game start, and on resume after the palette
    // was changed in Core Options. Phase 9 carries the edge
    // mode and the system with it, so the renderer knows
    // what to smooth. The palette is still passed to the
    // chain's Jade mode; the renderer no longer takes it —
    // the rework deleted the GB palette scan with the
    // palette snap.
    LaunchedEffect(displayChain, edgeMode, reconMode, reconGrid, palette) {
        glView?.setDisplay(
            displayChain,
            DisplayChain.jadeModeForPalette(palette),
            edgeMode,
            system,
            reconMode,
            reconGrid,
        )
    }

    val session = remember(gamePath) {
        PlayerSession(
            systemDir = context.filesDir.resolve("system").apply { mkdirs() }.absolutePath,
            saveDir = context.filesDir.resolve("saves").apply { mkdirs() }.absolutePath,
            gamePath = gamePath,
            // The stored ROM file is <sha256>.<ext>: its stem is the
            // stable per-game key for <base>.sav and <base>.stateN.
            saveBase = saveBase,
            // Phase 6 (item 3) — the per-game cheat store, for
            // applying the stored cheats after load.
            cheatsDir = context.filesDir.resolve("cheats").apply { mkdirs() }.absolutePath,
            // Phase 6 (item 5) — playtime is banked on pause/stop.
            playtimeDir = context.filesDir.absolutePath,
            // Phase 6 (item 5) — a pending load from the Save Manager.
            loadStateOnStart = loadStateOnStart,
            loadAutoOnStart = loadAutoOnStart,
            // Core options as stored: pushed to the bridge after the
            // libraries load and before the game loads. While a
            // chain is on, the chain owns the colour (see
            // DisplayChain.effectiveCoreOptions).
            options = DisplayChain.effectiveCoreOptions(
                optionsStore.all(), displayChain, edgeMode, reconMode,
            ),
            onFrame = { frame ->
                val view = glView
                if (view != null) {
                    view.setFrame(frame)
                    val aspect = frame.width.toFloat() / frame.height.toFloat()
                    if (aspect != frameAspect) frameAspect = aspect
                } else {
                    fallbackBitmap = frame
                }
            },
            onStats = { stats = it; drawPath = glView?.drawPath() },
            onError = onError,
            // Performance fix 5 — backpressure: the emu loop
            // skips the copy chain while the GL thread has not
            // consumed the previous frame. No GL view (the
            // no-GL fallback path) means no consumer to wait on.
            frameConsumed = { glView?.isFrameConsumed() ?: true },
            // Performance fix 6 — the rewind ring is only
            // captured when the control is visible (same
            // snapshotted setting the overlay reads).
            rewindControlVisible = rewindControlVisible,
            // Alpha.16 — proportional steering for this game
            // at start (per-game override, or the global
            // switch). The editor's Done pushes a later
            // change through the session's setter.
            proportionalSteering = proportionalSteering,
        )
    }

    // The input the core sees is the touch mask OR the gamepad mask.
    // Either side changing re-pushes the merged mask — and the input
    // display mirrors it, so what it shows is what the game receives.
    // doPause clears both masks and then calls this, so a pause
    // clears the display too.
    // Alpha.16 — the display mirrors the RAW mask, not the pulsed
    // one: the pulse lives in the session's loop, below this
    // push, so the display never flickers with the pulse and
    // shows what the player's thumb is doing.
    fun pushInput() {
        val mask = held.mask or GamepadInput.state.mask
        session.setInput(mask)
        displayedMask = mask
    }

    // Phase 6 (item 9) — while the RW button is held, step one
    // snapshot back every 150ms: 30 seconds of game in ~4.5
    // seconds of holding. The loop ends when the ring is empty
    // (stepRewind returns false) or when the hold ends; doPause
    // cancels it too (see below).
    LaunchedEffect(rewinding) {
        if (!rewinding) return@LaunchedEffect
        while (true) {
            delay(150)
            if (!session.stepRewind()) break
        }
    }

    // Phase 6 (item 11) — the REC clock: while a recording runs,
    // the elapsed seconds tick once a second for the top-bar
    // indicator.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (true) {
            delay(1000)
            recordingElapsed = session.recordingElapsedSeconds()
        }
    }

    // Phase 6 (item 12) — the panel's live state, loaded fresh
    // each time it opens (the RTC clock keeps running while the
    // game is paused, and the solar level may have changed in
    // Core Options since the panel last opened).
    LaunchedEffect(showCartridge) {
        if (!showCartridge) return@LaunchedEffect
        cartridgeTime = session.readRtcBytes()?.let {
            CartridgeClock.currentTime(it, System.currentTimeMillis() / 1000)
        }
        solarLevel = optionsStore.get("mgba_solar_sensor_level")
    }

    // Phase 6 (item 12) — spin the cartridge clock by
    // [deltaSeconds] (the thumbwheel). Reads the live RTC buffer,
    // adjusts it, and writes it back through the session (which
    // also records it as the RTC the next flushSave writes). The
    // panel's time is re-read from the written buffer, so what it
    // shows is what the .sav will carry.
    fun spinRtc(deltaSeconds: Long) {
        val bytes = session.readRtcBytes() ?: return
        val now = System.currentTimeMillis() / 1000
        val adjusted = CartridgeClock.adjust(bytes, deltaSeconds, now) ?: return
        if (session.spinRtc(adjusted)) {
            cartridgeTime = CartridgeClock.currentTime(adjusted, now)
        }
    }

    // Phase 6 (item 1) — the effective speed: a held FF button
    // wins over the slow-motion toggle, which wins over normal.
    fun effectiveSpeed(): PlaySpeed =
        if (ffHeld) PlaySpeed.fromMultiplier(ffSpeed)
        else if (slowOn) PlaySpeed.SLOW
        else PlaySpeed.NORMAL

    fun applySpeed() {
        session.setSpeed(effectiveSpeed())
    }

    // Performance fix 4 — the cartridge sensors, gated on need
    // and stopped while paused (audit #5). The cartridge's
    // motion need is read once per game from the ROM header
    // (see CartridgeSensors.cartridgeSensorNeed) — the same
    // bytes the core's own detection uses. Light is needed
    // when the solar sensor drives the game (option "sensor")
    // or when Screen Optics is live: every draw path applies
    // the lux-driven uniforms now (the chain and edge grid
    // passes, and the plain final step, which carries
    // edge-without-grid and plain) — only the reconstruction
    // path never sets them.
    val cartSensorNeed = remember(gamePath) {
        cartridgeSensorNeed(java.io.File(gamePath), system)
    }

    fun sensorNeed(): SensorNeed {
        val cart = cartSensorNeed ?: SensorNeed.ALL
        // Alpha.12 — the optics gate follows the draw paths:
        // optics is live everywhere except reconstruction, so
        // the light sensor is registered whenever a game is
        // playing (unless the reconstruction experiment is on).
        val opticsLive = reconMode == ReconMode.OFF
        return cart.copy(light = solarLevel == "sensor" || opticsLive)
    }

    // (Re-)apply the sensor registration for the current
    // state: stopped while paused or when the user switched
    // the sensors off in the Cartridge Surgery panel, else
    // started with the current need. Called from doPause,
    // doResume, the lifecycle observer, the session effect
    // and the panel toggle, so every transition goes through
    // the one place.
    fun applySensors() {
        cartridgeSensors.paused = paused
        if (!sensorsEnabled || paused) {
            cartridgeSensors.stop()
            return
        }
        cartridgeSensors.stop()
        cartridgeSensors.start(sensorNeed())
    }

    // Pause: clear every held input FIRST (a finger that lifts while
    // the pause overlay covers the buttons never reaches the button's
    // gesture loop, so without this the button stays stuck down),
    // then pause the session (which flushes the battery save) and
    // push the cleared mask so the core sees no buttons held.
    fun doPause() {
        if (paused) return
        held.clear()
        GamepadInput.state.clear()
        // A held FF button is released by the pause too: its
        // gesture loop restarts under the overlay, so its onUp
        // never fires — drop the hold here instead.
        ffHeld = false
        // Same for a held RW button: dropping the hold cancels
        // the stepping loop, and the session stays paused under
        // the pause menu (it resumes from the rewound state).
        rewinding = false
        session.pause()
        paused = true
        pushInput()
        applySpeed()
        // Performance fix 4 — the sensors stop with the game:
        // they fired ~50 Hz while paused, the only proven
        // sustained idle load. applySensors also mirrors the
        // paused flag onto the sensors, so in-flight events
        // landing after stop() are dropped.
        applySensors()
    }

    fun doResume() {
        if (!paused) return
        // Phase 8 — the GB palette may have changed in Core
        // Options while paused; re-read it so the chain follows.
        palette = optionsStore.get("mgba_gb_colors")
        // The solar level may have changed the same way; the
        // sensor need follows it (see sensorNeed).
        solarLevel = optionsStore.get("mgba_solar_sensor_level")
        session.resume()
        paused = false
        applySensors()
        applySpeed()
    }

    // Auto-pause when the player loses focus (Home, lock, incoming
    // call): a game left running in the background would keep
    // playing unheard and unwatched.
    // Black screen on launch (Robert, 2026-11-14): after an
    // activity pause/resume the GL surface is recreated, and
    // nothing restarted the GL thread — the view was only
    // resumed once, at game start (see the gamePath effect).
    // ON_RESUME restarts it, so a game left paused behind a
    // phone call still shows its picture when the player
    // comes back. This does NOT resume the session itself:
    // the game stays paused until the player resumes it from
    // the pause menu, as the auto-pause design intends.
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                doPause()
                glView?.onPause()
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                glView?.onResume()
                // The session stays paused until the player
                // resumes it from the menu — applySensors is
                // pause-aware, so this restarts the sensors only
                // if the game is somehow not paused, and is a
                // no-op while it is.
                applySensors()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // Gamepad input lives only while the player is showing: the
    // activity's dispatch methods forward pad events to GamepadInput,
    // which calls back here to re-push the merged mask.
    DisposableEffect(Unit) {
        GamepadInput.onMaskChanged = { pushInput() }
        GamepadInput.enabled = true
        onDispose {
            GamepadInput.onMaskChanged = null
            GamepadInput.enabled = false
            GamepadInput.state.clear()
        }
    }

    // Phase 6 (item 10b) — cartridge rumble: the phone's
    // vibrator, driven by the core while a game is playing.
    // Built here so the session lifecycle effect below can wire
    // it to the bridge callback and stop it on dispose.
    val cartridgeRumble = remember {
        CartridgeRumble(context.getSystemService(Vibrator::class.java))
    }

    // Phase 6 (item 10c) — the GB Camera: the phone's camera,
    // feeding the Game Boy Camera game while a game is playing.
    // The permission launcher answers the camera's one ask per
    // game (see GbCamera); the camera itself is wired to the
    // bridge callbacks in the session lifecycle effect below.
    val gbCamera = remember { GbCamera(context) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> gbCamera.onPermissionResult(granted) }

    DisposableEffect(gamePath) {
        session.start()
        // Phase 8 — the GL thread lives only while a game is
        // playing, with the session.
        glView?.onResume()
        // Black screen on launch (Robert, 2026-11-15): when
        // the surface is created with a frame already
        // pending, the view asks for the session's latest
        // frame again — its first render request was dropped
        // before the GL thread existed. Bound to this
        // session, cleared below with the other handlers.
        glView?.onSurfaceReady = { session.replayLatestFrame() }
        // Phase 6 (item 10a) — the cartridge sensors: the phone's
        // motion/light sensors feed the core while a game is
        // playing (started and stopped with the session, and
        // gated on what the cartridge can consume — see
        // applySensors).
        applySensors()
        // Phase 8 — Screen Optics: the same light reading the
        // core already gets drives the active chain's
        // brightness / contrast / gamma (no second listener).
        // The renderer only redraws when the picture would
        // visibly change, so the ~50 Hz sensor rate costs
        // nothing while the game is already drawing frames.
        cartridgeSensors.onLux = { lux ->
            glView?.setOptics(ScreenOptics.paramsFor(lux, opticsDimming))
            val rounded = lux.takeIf { it.isFinite() && it >= 0f }?.roundToInt()
            if (rounded != latestLux) latestLux = rounded
        }
        // Phase 6 (item 10b) — the rumble handler lives only
        // while a game is playing: the core can only rumble
        // while a game is loaded, and the handler goes away
        // with the game.
        Bridge.onRumbleHandler = { strength -> cartridgeRumble.setStrength(strength) }
        // Phase 6 (item 10c) — the GB Camera handlers live only
        // while a game is playing, like the rumble handler; the
        // permission launcher is wired here too (the camera asks
        // once per game, only when the game requests an image).
        gbCamera.onRequestPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        Bridge.onCameraStartHandler = { gbCamera.start() }
        Bridge.onCameraStopHandler = { gbCamera.stop() }
        onDispose {
            Bridge.onCameraStartHandler = null
            Bridge.onCameraStopHandler = null
            gbCamera.stop()
            Bridge.onRumbleHandler = null
            glView?.onSurfaceReady = null
            cartridgeRumble.stop()
            cartridgeSensors.onLux = null
            cartridgeSensors.stop()
            glView?.onPause()
            session.stop()
            // Phase 6 (item 11): a recording left running at quit
            // was finalized by session.stop(); the FileDescriptor
            // is GameScreen's to close. A 0-frame recording (started
            // from the pause menu, quit before resume) leaves an
            // empty file — drop it rather than presenting it as saved.
            val out = videoOutput
            if (out != null && session.recordingFrameCount() == 0) {
                CaptureStore.delete(context, out.uri)
            }
            try {
                out?.pfd?.close()
            } catch (_: Exception) {
            }
            videoOutput = null
        }
    }

    // Landscape + keep the screen on while playing; restore on exit.
    DisposableEffect(Unit) {
        val previousOrientation = activity?.requestedOrientation
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = previousOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Performance fix 1 (2026-11-14) — the overlay callbacks
    // are remembered, so the control tree is not rebuilt on
    // every frame's recomposition: fresh lambdas made every
    // pass unskippable. All three close only over remembered
    // objects and state setters, so the remembered instances
    // never go stale.
    val onInputChanged: () -> Unit = remember { ::pushInput }
    val onControlDown: () -> Unit = remember {
        {
            // Phase 6 (item 10b): a click is a fresh vibrate,
            // which would kill a running rumble waveform — the
            // game's rumble wins while it is active, so the
            // click is skipped then (the motor is already
            // vibrating).
            if (cartridgeRumble.currentStrength == 0) hapticsImpl.click()
        }
    }
    // Alpha.11 — per-button character: the same rumble gate
    // as the press click, one remembered lambda per feel.
    // The release tick fires on lift; the roll tick on a
    // D-pad / stick direction change; the light click for
    // Start/Select; the shoulder click + thud for L/R.
    val onControlUp: () -> Unit = remember {
        { if (cartridgeRumble.currentStrength == 0) hapticsImpl.releaseTick() }
    }
    val onDirectionTick: () -> Unit = remember {
        { if (cartridgeRumble.currentStrength == 0) hapticsImpl.directionTick() }
    }
    val onLightDown: () -> Unit = remember {
        { if (cartridgeRumble.currentStrength == 0) hapticsImpl.lightClick() }
    }
    val onShoulderDown: () -> Unit = remember {
        { if (cartridgeRumble.currentStrength == 0) hapticsImpl.shoulderClick() }
    }
    val onWormLightDragHeadChange: (WormLightDragHead?) -> Unit = remember {
        { head -> wormLightDragHead = head }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Overlay rework (Robert, 2026-11-13): the canvas the
        // controls are fractions of — the full screen, so the
        // buttons never jump when the top bar hides and shows.
        val canvasWidthDp = maxWidth.value
        val canvasHeightDp = maxHeight.value
        Column(modifier = Modifier.fillMaxSize()) {
            // Player chrome (Robert, 2026-11-12): the top bar
            // hides during active gameplay unless dev stats
            // are on — and its height goes to the game below,
            // which re-measures at its native aspect, taller.
            // The power light never hides: it is detached from
            // the bar (FloatingPlayerStatus, in the root Box
            // below). The L/R tabs live in the player area
            // below, not in this bar (layout rework, Robert,
            // 2026-11-12).
            if (barVisible) {
                TopBarRow(
                    stats = stats,
                    // The live lux shows in the diagnostic line
                    // only when dev stats are on — the line is
                    // the dev-stats display.
                    lux = if (devStats) latestLux else null,
                    // Edge smoothing, item 5: which program
                    // actually drew the last frame — the
                    // dev-stats readout, so edge on is never
                    // again a guess on device. Task 6: the
                    // same for the reconstruction.
                    drawPath = drawPath,
                    edgeOn = edgeMode != EdgeMode.OFF,
                    reconOn = reconMode != ReconMode.OFF,
                    recording = recording,
                    recordingElapsed = recordingElapsed,
                    buttonLabel = if (paused) "Resume" else "Pause",
                    onButtonClick = {
                        if (paused) doResume() else doPause()
                    },
                )
            }
            // Input display (Phase 6, item 6): shown only when the
            // Settings toggle is on — off by default, per the
            // standing overlay rule. It sits above the game view so
            // it never covers the controls.
            if (inputDisplayVisible) {
                InputDisplay(mask = displayedMask, isGba = isGba)
            }
            // RetroAchievements (Phase 6, item 8c): the live
            // attempt tracker, under the top bar while an attempt
            // runs. Hidden while paused.
            RaTrackerLine(paused = paused)
            // Alpha.22 — the RA indicator, next to the tracker:
            // the loaded game's achievement state while playing
            // (loaded count, unrecognised ROM, or a failed load
            // with a Retry). Same gold chip family as the unlock
            // popup; hidden while paused, like the tracker.
            RaGameIndicator(paused = paused)
            // Player area (Robert, 2026-11-12: the game basically
            // the phone's height in landscape, controls overlaid):
            // one Box below the top bar. The game view fills it at
            // its native aspect — the largest ratio box in the area,
            // so it takes the full height and centres, with the
            // black bars left and right of it holding the controls.
            // Square corners, no bezel, no surround. The frame runs
            // through the display chain (Phase 8); see
            // GameDisplayView.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { playerHeightPx = it.height.toFloat() }
                    .testTag("player-area"),
            ) {
                GameDisplayView(
                    glView = glView,
                    bitmap = fallbackBitmap,
                    frameAspect = frameAspect,
                    gameName = gameName,
                    paused = paused,
                    wormLight = wormLight,
                    wormLightColor = wormLightColor,
                    wormLightCoverage = wormLightCoverage,
                    onWormLightDragHeadChange = onWormLightDragHeadChange,
                    // Top bar / back swipe (Robert, 2026-11-12):
                    // no tap hook — a tap on the game area
                    // does nothing to the bar; only a back
                    // swipe brings it back (see peekBar).
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Overlay rework (Robert, 2026-11-13): the controls live
        // in the root Box now, placed by fractions of the full
        // screen — above the game, below the pause overlay, so
        // the pause dim covers them like before. The Worm Light's
        // drag head draws above them, translated from the player
        // area's coordinates into root coordinates.
        ControlOverlay(
            isGba = isGba,
            system = system,
            layout = layout,
            canvasWidthDp = canvasWidthDp,
            canvasHeightDp = canvasHeightDp,
            held = held,
            paused = paused,
            onInputChanged = onInputChanged,
            onControlDown = onControlDown,
            onControlUp = onControlUp,
            onDirectionTick = onDirectionTick,
            onLightDown = onLightDown,
            onShoulderDown = onShoulderDown,
            leftControl = leftControl,
            // Alpha.14 — the dead zone in effect for this game
            // (per-game override, or the global slider value).
            deadZone = deadZone,
            // Alpha.16 — proportional steering: the stick's
            // live state goes straight to the session, which
            // pulses the mask the core is handed. The display
            // and the haptics never see it (see pushInput).
            onStickStateChanged = { mask, deflection ->
                session.setStickState(mask, deflection)
            },
            // Alpha.10 (Part D) — FF / 0.5x / RW live in the
            // overlay now (see ControlOverlay): the same
            // hold/toggle behaviour the fixed Row had, at the
            // spec's position and size. FF is hold-to-run at
            // the chosen speed; 0.5x is a toggle; RW is
            // hold-to-step back through the last 30 seconds.
            ffVisible = ffControlVisible,
            rewindVisible = rewindControlVisible,
            slowOn = slowOn,
            onFfDown = {
                ffHeld = true
                applySpeed()
            },
            onFfUp = {
                ffHeld = false
                applySpeed()
            },
            onSlowToggle = {
                slowOn = !slowOn
                applySpeed()
            },
            onRwDown = {
                if (session.startRewind()) {
                    rewinding = true
                    session.stepRewind()
                }
            },
            onRwUp = {
                if (rewinding) {
                    rewinding = false
                    session.endRewind()
                }
            },
        )
        // Worm Light improvements (Robert, 2026-11-13):
        // while a drag is in progress, the lamp's head
        // draws ABOVE the controls, so it never disappears
        // behind the D-pad mid-drag. No pointer input
        // here — this layer is transparent to input.
        // Outside a drag nothing is drawn and the existing
        // draw order stays.
        wormLightDragHead?.let { dragHead ->
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("worm-light-drag-head"),
            ) {
                translate(top = size.height - playerHeightPx) {
                    drawWormLightHead(dragHead)
                }
            }
        }
        // Pause overlay: the Pocket screen dimmed — the game stays
        // visible behind the dim, and the menu is an Atomic panel
        // (see PauseMenu).
        if (paused && !showSettings && !showCheats && !showAchievements && !showLeaderboards && !editingLayout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                PauseMenu(
                    gameName = gameName,
                    session = session,
                    pauseMessage = pauseMessage,
                    onMessage = { pauseMessage = it },
                    onResume = { doResume() },
                    onQuit = onQuit,
                    onCoreOptions = { showSettings = true },
                    onCheats = { showCheats = true },
                    onAchievements = { showAchievements = true },
                    onLeaderboards = { showLeaderboards = true },
                    onMoveControls = { editingLayout = true },
                    // Phase 6 (item 12) — Cartridge Surgery.
                    onCartridge = { showCartridge = true },
                    // Phase 6 (item 11) — capture: a screenshot is
                    // the latest frame as a PNG in Pictures/Poing!;
                    // recording toggles the MP4 in Movies/Poing!
                    // (video only, no audio). Both run on IO; the
                    // recording starts even while paused — frames
                    // simply start flowing on resume.
                    isRecording = recording,
                    onScreenshot = {
                        captureScope.launch(Dispatchers.IO) {
                            val frame = session.latestFrameBitmap()
                            if (frame == null) {
                                withContext(Dispatchers.Main) {
                                    pauseMessage = "No frame yet."
                                }
                                return@launch
                            }
                            val uri = CaptureStore.saveScreenshot(context, gameName, frame)
                            withContext(Dispatchers.Main) {
                                pauseMessage = if (uri != null) "Screenshot saved." else "Screenshot failed."
                            }
                        }
                    },
                    onToggleRecording = {
                        if (recording) {
                            // A 0-frame recording (started while paused,
                            // stopped before resume) is not a saved file —
                            // drop the empty entry and say so.
                            val ok = session.stopRecording()
                            val out = videoOutput
                            try {
                                out?.pfd?.close()
                            } catch (_: Exception) {
                            }
                            videoOutput = null
                            recording = false
                            pauseMessage = if (ok) {
                                "Recording saved."
                            } else {
                                if (out != null) CaptureStore.delete(context, out.uri)
                                "Recording failed."
                            }
                        } else {
                            captureScope.launch(Dispatchers.IO) {
                                val out = CaptureStore.createVideoOutput(context, gameName)
                                if (out == null) {
                                    withContext(Dispatchers.Main) {
                                        pauseMessage = "Recording failed."
                                    }
                                    return@launch
                                }
                                val ok = session.startRecording(out.pfd.fileDescriptor)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        videoOutput = out
                                        recordingElapsed = 0
                                        recording = true
                                        pauseMessage = "Recording…"
                                    } else {
                                        try {
                                            out.pfd.close()
                                        } catch (_: Exception) {
                                        }
                                        CaptureStore.delete(context, out.uri)
                                        pauseMessage = "Recording failed."
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        // The power light, detached from the bar: always on,
        // at the top-left corner (see PlayerTopBar). It draws
        // AFTER the pause overlay, so the amber light stays
        // visible while paused instead of sitting under the
        // 70%-black dim — the pause menu panel is centred, so
        // the corner light never overlaps it. Over the player
        // area only, never over the game picture itself, which
        // sits centred with black bars at the sides.
        FloatingPlayerStatus(
            paused = paused,
            recording = recording,
            recordingElapsedState = recordingElapsedState,
            barVisible = barVisible,
            modifier = Modifier.align(Alignment.TopStart),
        )
        // Core options over the paused game: a change pushes live to
        // the session (non-restart options apply on the next frame
        // after resume; restart options apply next game).
        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                CoreOptionsScreen(
                    store = optionsStore,
                    onValueChanged = {
                        session.applyOptions(
                            DisplayChain.effectiveCoreOptions(
                                optionsStore.all(), displayChain, edgeMode, reconMode,
                            ),
                        )
                    },
                    onBack = { showSettings = false },
                )
            }
        }
        // Cheats over the paused game: a change saves the list and
        // re-applies the enabled codes to the session immediately.
        if (showCheats) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                CheatsScreen(
                    gameName = gameName,
                    // Alpha.19 — the detected system, not the
                    // file extension: a GBC game in a .gb file
                    // plays as GBC, and its cheats are GBC
                    // cheats. [system] is the header-detected
                    // system MainActivity passes in.
                    system = system,
                    saveBase = saveBase,
                    store = remember { CheatStore(context.filesDir.resolve("cheats").apply { mkdirs() }) },
                    session = session,
                    onBack = { showCheats = false },
                )
            }
        }
        // Cartridge Surgery over the paused game: the slide-out
        // cartridge schematic with live hardware controls. The
        // solar level saves like any core option and pushes live
        // (non-restart, next frame); the rumble / sensor toggles
        // are session-only — off gates the hardware for this game
        // (rumble: drop the bridge callback + stop the motor;
        // sensors: stop the listeners + zero every value, so the
        // core reads flat instead of a frozen tilt).
        if (showCartridge) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                CartridgeSurgeryPanel(
                    gameName = gameName,
                    isGba = isGba,
                    rtcTime = cartridgeTime,
                    onSpinRtc = { spinRtc(it) },
                    solarLevel = solarLevel,
                    onSolarLevel = { value ->
                        if (optionsStore.set("mgba_solar_sensor_level", value)) {
                            solarLevel = value
                            session.applyOptions(
                                DisplayChain.effectiveCoreOptions(
                                    optionsStore.all(), displayChain, edgeMode, reconMode,
                                ),
                            )
                        }
                    },
                    rumbleEnabled = rumbleEnabled,
                    onRumbleEnabled = { enabled ->
                        rumbleEnabled = enabled
                        if (enabled) {
                            Bridge.onRumbleHandler = { strength ->
                                cartridgeRumble.setStrength(strength)
                            }
                        } else {
                            Bridge.onRumbleHandler = null
                            cartridgeRumble.stop()
                        }
                    },
                    sensorsEnabled = sensorsEnabled,
                    onSensorsEnabled = { enabled ->
                        sensorsEnabled = enabled
                        if (enabled) {
                            applySensors()
                        } else {
                            cartridgeSensors.stop()
                            cartridgeSensors.zero()
                        }
                    },
                    onDone = { showCartridge = false },
                )
            }
        }
        // RetroAchievements (Phase 6, item 8c) over the paused
        // game: the achievements list and the leaderboards, same
        // full-screen treatment as the cheats screen.
        if (showAchievements) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                RaAchievementsScreen(
                    gameName = gameName,
                    onBack = { showAchievements = false },
                )
            }
        }
        if (showLeaderboards) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                RaLeaderboardsScreen(
                    gameName = gameName,
                    onBack = { showLeaderboards = false },
                )
            }
        }
        // Overlay rework (Robert, 2026-11-13): the full-screen
        // layout editor over the paused game. Done commits the
        // draft and saves it; Back (or a back swipe) discards
        // it. The game stays paused throughout editing.
        // Overlay defaults (Robert, 2026-11-14): Reset in the
        // editor restores the handheld default, and a Done
        // that lands exactly on it is not an override at all —
        // the game's file is cleared, so a later change to the
        // handheld default still flows through to this game
        // instead of a stale copy shadowing it.
        if (editingLayout) {
            LayoutEditor(
                initial = layout,
                isGba = isGba,
                system = system,
                held = held,
                onInputChanged = ::pushInput,
                onControlDown = {
                    if (cartridgeRumble.currentStrength == 0) hapticsImpl.click()
                },
                onControlUp = onControlUp,
                onDirectionTick = onDirectionTick,
                onLightDown = onLightDown,
                onShoulderDown = onShoulderDown,
                // Alpha.10 — the per-game left-control choice:
                // the editor shows the D-pad / Joystick toggle
                // for this game, and Done commits the choice
                // with the layout.
                leftControlDefault = appSettings.leftControl(),
                leftControlOverride = layoutStore.leftControlOverride(saveBase),
                showLeftControlChoice = true,
                // Alpha.14 — the per-game dead zone: the editor
                // shows the stepper for this game, and Done
                // commits the override with the layout.
                deadZoneDefaultPct = appSettings.stickDeadZonePct(),
                deadZoneOverridePct = layoutStore.stickDeadZoneOverridePct(saveBase),
                showDeadZoneChoice = true,
                // Alpha.16 — proportional steering for this
                // game: the editor shows the Global / On / Off
                // row for this game, and Done commits the
                // override with the layout.
                steeringDefault = appSettings.proportionalSteering(),
                steeringOverride = layoutStore.proportionalSteeringOverride(saveBase),
                showSteeringChoice = true,
                onDone = { draft, choice, deadZonePct, steering ->
                    layout = draft
                    if (draft == handheldDefault) layoutStore.clearOverride(saveBase)
                    else layoutStore.save(saveBase, draft)
                    layoutStore.setLeftControlOverride(saveBase, choice)
                    leftControl = choice ?: appSettings.leftControl()
                    layoutStore.setStickDeadZoneOverridePct(saveBase, deadZonePct)
                    deadZone = (deadZonePct ?: appSettings.stickDeadZonePct()) / 100f
                    layoutStore.setProportionalSteeringOverride(saveBase, steering)
                    proportionalSteering = steering ?: appSettings.proportionalSteering()
                    session.setProportionalSteering(proportionalSteering)
                    editingLayout = false
                },
                onBack = { editingLayout = false },
                resetLayout = handheldDefault,
            )
        }
    }
}
