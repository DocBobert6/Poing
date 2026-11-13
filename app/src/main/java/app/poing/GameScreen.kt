package app.poing

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stats by remember { mutableStateOf<PlayerStats?>(null) }
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
    var recordingElapsed by remember { mutableStateOf(0L) }
    var videoOutput by remember { mutableStateOf<CaptureStore.VideoOutput?>(null) }
    val captureScope = rememberCoroutineScope()
    var pauseMessage by remember { mutableStateOf<String?>(null) }
    val held = remember { HeldInput() }

    // Phase 6 (item 4) — per-game saved control positions. The
    // layout is loaded with the game and saved on every drag end
    // (and on Done); editing is entered from the pause menu while
    // the game stays paused. The stored ROM file is
    // <sha256>.<ext>: its stem is the stable per-game key.
    val saveBase = java.io.File(gamePath).nameWithoutExtension
    val layoutStore = remember { ControlLayoutStore(context.filesDir.resolve("control-layouts").apply { mkdirs() }) }
    var layout by remember(gamePath) { mutableStateOf(layoutStore.load(saveBase)) }
    val latestLayout by rememberUpdatedState(layout)
    var editingLayout by remember { mutableStateOf(false) }

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

    // Phase 6 (item 7) — haptics: a short click for the D-pad and
    // A/B, on by default, toggleable in Settings. Snapshotted here
    // like the other player settings: settings can only change on
    // the Settings screen, never while the player is open.
    val hapticsImpl = remember {
        haptics ?: Haptics(
            context.getSystemService(Vibrator::class.java),
            appSettings.hapticsEnabled(),
        )
    }

    // Phase 8 — the display chain: the default look for this
    // system, or clean when its toggle is off. Snapshotted
    // here like the other player settings: settings can only
    // change on the Settings screen, never while the player
    // is open. The GB palette is read live (it can change in
    // Core Options while paused — doResume re-reads it).
    val displayChain = remember {
        val wanted = DisplayChain.forSystem(system)
        if (appSettings.chainEnabled(wanted)) wanted else DisplayChain.CLEAN
    }
    var palette by remember { mutableStateOf(optionsStore.get("mgba_gb_colors")) }

    // Phase 8 — Screen Optics, snapshotted like the chain:
    // the Worm Light (off by default — an easter egg) and
    // whether a dark room may dim the screen (off by
    // default — dimming can hide the game).
    val wormLight = remember { appSettings.wormLightEnabled() }
    val opticsDimming = remember { appSettings.opticsDimmingEnabled() }

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
    // was changed in Core Options.
    LaunchedEffect(displayChain, palette) {
        glView?.setChain(displayChain, DisplayChain.jadeModeForPalette(palette))
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
            options = DisplayChain.effectiveCoreOptions(optionsStore.all(), displayChain),
            onFrame = { bitmap = it; glView?.setFrame(it) },
            onStats = { stats = it },
            onError = onError,
        )
    }

    // The input the core sees is the touch mask OR the gamepad mask.
    // Either side changing re-pushes the merged mask — and the input
    // display mirrors it, so what it shows is what the game receives.
    // doPause clears both masks and then calls this, so a pause
    // clears the display too.
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
    }

    fun doResume() {
        if (!paused) return
        // Phase 8 — the GB palette may have changed in Core
        // Options while paused; re-read it so the chain follows.
        palette = optionsStore.get("mgba_gb_colors")
        session.resume()
        paused = false
        applySpeed()
    }

    // Auto-pause when the player loses focus (Home, lock, incoming
    // call): a game left running in the background would keep
    // playing unheard and unwatched.
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) doPause()
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

    // Phase 6 (item 10a) — the cartridge sensors (tilt /
    // gyroscope / solar): the phone's hardware, fed to the core
    // while a game is playing. Built here so the session
    // lifecycle effect below can start and stop it.
    val cartridgeSensors = remember { CartridgeSensors(context) }

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
        // Phase 6 (item 10a) — the cartridge sensors: the phone's
        // motion/light sensors feed the core while a game is
        // playing (started and stopped with the session).
        cartridgeSensors.start()
        // Phase 8 — Screen Optics: the same light reading the
        // core already gets drives the active chain's
        // brightness / contrast / gamma (no second listener).
        // The renderer only redraws when the picture would
        // visibly change, so the ~50 Hz sensor rate costs
        // nothing while the game is already drawing frames.
        cartridgeSensors.onLux = { lux ->
            glView?.setOptics(ScreenOptics.paramsFor(lux, opticsDimming))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: power light + diagnostic text + pause. The
            // light is green while a game is playing, amber while
            // paused — the Pocket's power LED. statusBarsPadding:
            // the player is edge-to-edge like the library, so
            // without this the light sits in the status-bar band
            // (and, in landscape, by the punch-hole camera). The
            // light's top edge is now status-bar height + 8dp.
            // The L/R tabs live in the player area below, not
            // in this bar (layout rework, Robert, 2026-11-12).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PowerLight(if (paused) PowerState.Paused else PowerState.Playing)
                Spacer(Modifier.width(8.dp))
                // Phase 6 (item 11) — the REC indicator while a
                // recording runs: a red dot + elapsed m:ss.
                if (recording) {
                    Text(
                        text = "● REC ${recordingElapsed / 60}:${"%02d".format(recordingElapsed % 60)}",
                        color = Color.Red,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                DiagnosticLine(stats = stats, modifier = Modifier.weight(1f))
                // While editing the layout the top-bar button is
                // Done: it saves the layout and returns to the pause
                // menu (the game stays paused throughout editing).
                Button(onClick = {
                    when {
                        editingLayout -> {
                            layoutStore.save(saveBase, latestLayout)
                            editingLayout = false
                        }
                        paused -> doResume()
                        else -> doPause()
                    }
                }) {
                    Text(if (editingLayout) "Done" else if (paused) "Resume" else "Pause")
                }
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
                    .fillMaxWidth(),
            ) {
                GameDisplayView(
                    glView = glView,
                    bitmap = bitmap,
                    stats = stats,
                    gameName = gameName,
                    paused = paused,
                    wormLight = wormLight,
                    modifier = Modifier.fillMaxSize(),
                )
                // The four movable groups, overlaid (see
                // PlayerOverlay): D-pad left flank, A/B right
                // flank, L/R top corners (GBA only), Start/Select
                // bottom centre.
                ControlOverlay(
                    isGba = isGba,
                    system = system,
                    layout = layout,
                    editingLayout = editingLayout,
                    held = held,
                    paused = paused,
                    onInputChanged = ::pushInput,
                    onControlDown = {
                        // Phase 6 (item 10b): a click is a fresh
                        // vibrate, which would kill a running
                        // rumble waveform — the game's rumble wins
                        // while it is active, so the click is
                        // skipped then (the motor is already
                        // vibrating).
                        if (cartridgeRumble.currentStrength == 0) hapticsImpl.click()
                    },
                    onMove = { group, position ->
                        layout = layout.with(group, position)
                    },
                    onMoveEnd = { layoutStore.save(saveBase, latestLayout) },
                )
                // Fast-forward / slow motion (Phase 6, item 1) and
                // rewind (Phase 6, item 9): shown only when their
                // Settings toggle is on — both off by default, per
                // the standing overlay rule. FF is hold-to-run at
                // the chosen speed; 0.5x is a toggle; RW is
                // hold-to-step back through the last 30 seconds.
                // Overlaid at the bottom-right corner of the
                // player area, below the A/B cluster.
                if (ffControlVisible || rewindControlVisible) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (rewindControlVisible) {
                            HoldActionButton(
                                label = "RW",
                                paused = paused,
                                onDown = {
                                    if (session.startRewind()) {
                                        rewinding = true
                                        session.stepRewind()
                                    }
                                },
                                onUp = {
                                    if (rewinding) {
                                        rewinding = false
                                        session.endRewind()
                                    }
                                },
                            )
                        }
                        if (ffControlVisible) {
                            ToggleActionButton(
                                label = "0.5x",
                                checked = slowOn,
                                onToggle = {
                                    slowOn = !slowOn
                                    applySpeed()
                                },
                            )
                            HoldActionButton(
                                label = "FF",
                                paused = paused,
                                onDown = {
                                    ffHeld = true
                                    applySpeed()
                                },
                                onUp = {
                                    ffHeld = false
                                    applySpeed()
                                },
                            )
                        }
                    }
                }
            }
            // Layout editing (Phase 6, item 4): drag any group to
            // move it; Reset puts every group back at its default
            // spot and saves. Done lives in the top bar. The hint
            // sits below the player area while editing — the game
            // is paused then, so the lost height costs nothing.
            if (editingLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Drag the controls to move them",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                    )
                    Button(onClick = {
                        layout = ControlLayout.DEFAULT
                        layoutStore.save(saveBase, layout)
                    }) {
                        Text("Reset")
                    }
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
                            DisplayChain.effectiveCoreOptions(optionsStore.all(), displayChain),
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
                    system = GameSystem.fromExtension(gamePath)
                        ?: if (isGba) GameSystem.GBA else GameSystem.GB,
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
                                DisplayChain.effectiveCoreOptions(optionsStore.all(), displayChain),
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
                            cartridgeSensors.start()
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
    }
}

/**
 * The diagnostic overlay line: terminal face (VT323) for the
 * labels, segment digits (DSEG7) for the numbers — frame size,
 * core fps, measured fps, frames drawn. The numbers are always
 * segment, never the display face.
 */
@Composable
private fun DiagnosticLine(stats: PlayerStats?, modifier: Modifier = Modifier) {
    if (stats == null) {
        Text("starting…", style = terminalTextStyle(), color = Color.White, modifier = modifier)
        return
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = Dseg7)) { append("${stats.width}x${stats.height}") }
            withStyle(SpanStyle(fontFamily = Vt323)) { append(" core ") }
            withStyle(SpanStyle(fontFamily = Dseg7)) { append("%.1f".format(stats.coreFps)) }
            withStyle(SpanStyle(fontFamily = Vt323)) { append("fps measured ") }
            withStyle(SpanStyle(fontFamily = Dseg7)) { append("%.1f".format(stats.measuredFps)) }
            withStyle(SpanStyle(fontFamily = Vt323)) { append("fps ") }
            withStyle(SpanStyle(fontFamily = Dseg7)) { append("${stats.framesDrawn}") }
            withStyle(SpanStyle(fontFamily = Vt323)) { append(" frames") }
        },
        style = terminalTextStyle(),
        color = Color.White,
        // One line, always: with the L/R tabs in this bar the
        // diagnostic has little width in portrait, and wrapping
        // would grow the bar and eat game height.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
