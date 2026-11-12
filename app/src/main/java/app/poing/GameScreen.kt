package app.poing

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
    optionsStore: CoreOptionsStore,
    appSettings: AppSettingsStore,
    // Phase 6 (item 5) — a pending load from the Save Manager,
    // applied by the session before the first frame.
    loadStateOnStart: Int? = null,
    loadAutoOnStart: Boolean = false,
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

    // Phase 6 (item 6) — the input display: the merged mask the
    // core sees (touch + gamepad), mirrored into state on every
    // input change so the pills update live. The toggle itself is
    // snapshotted here like the fast-forward control: settings can
    // only change on the Settings screen, never while the player
    // is open.
    val inputDisplayVisible = remember { appSettings.inputDisplayVisible() }
    var displayedMask by remember { mutableStateOf(0) }

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
            // libraries load and before the game loads.
            options = optionsStore.all(),
            onFrame = { bitmap = it },
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
        session.pause()
        paused = true
        pushInput()
        applySpeed()
    }

    fun doResume() {
        if (!paused) return
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

    DisposableEffect(gamePath) {
        session.start()
        onDispose { session.stop() }
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
            // Game view, letterboxed at the native aspect, in the
            // icon's screen: a thin silver bezel + dark surround.
            // The game IS the screen — no whole handheld drawn
            // around it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val frame = bitmap
                val s = stats
                if (frame != null && s != null) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(s.width.toFloat() / s.height.toFloat())
                            .fillMaxSize()
                            .padding(8.dp)
                            .background(Color(0xFF111111))
                            .border(2.dp, PoingSilver, RoundedCornerShape(4.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "Game: $gameName",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Text("Loading $gameName…", color = Color.White)
                }
            }
            // Fast-forward / slow motion (Phase 6, item 1): shown
            // only when the Settings toggle is on — off by default,
            // per the standing overlay rule. FF is hold-to-run at
            // the chosen speed; 0.5x is a toggle.
            if (ffControlVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            // L/R shoulder tabs (GBA only).
            if (isGba) {
                MovableControl(
                    position = layout.lr,
                    editing = editingLayout,
                    testTag = "controls-lr",
                    onMove = { layout = layout.with(ControlGroup.LR, it) },
                    onMoveEnd = { layoutStore.save(saveBase, latestLayout) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        HoldButton("L", InputMask.JOY_L, held, paused, ::pushInput, shoulder = true)
                        HoldButton("R", InputMask.JOY_R, held, paused, ::pushInput, shoulder = true)
                    }
                }
            }
            // Layout editing (Phase 6, item 4): drag any group to
            // move it; Reset puts every group back at its default
            // spot and saves. Done lives in the top bar.
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
            // Controls row: D-pad left, Start/Select centre, A/B right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MovableControl(
                    position = layout.dpad,
                    editing = editingLayout,
                    testTag = "controls-dpad",
                    onMove = { layout = layout.with(ControlGroup.DPAD, it) },
                    onMoveEnd = { layoutStore.save(saveBase, latestLayout) },
                ) {
                    DpadControl(held, paused, ::pushInput)
                }
                MovableControl(
                    position = layout.startSelect,
                    editing = editingLayout,
                    testTag = "controls-startselect",
                    onMove = { layout = layout.with(ControlGroup.START_SELECT, it) },
                    onMoveEnd = { layoutStore.save(saveBase, latestLayout) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HoldButton("Start", InputMask.JOY_START, held, paused, ::pushInput, sizeDp = 48.dp)
                        Spacer(Modifier.height(8.dp))
                        HoldButton("Select", InputMask.JOY_SELECT, held, paused, ::pushInput, sizeDp = 48.dp)
                    }
                }
                MovableControl(
                    position = layout.ab,
                    editing = editingLayout,
                    testTag = "controls-ab",
                    onMove = { layout = layout.with(ControlGroup.AB, it) },
                    onMoveEnd = { layoutStore.save(saveBase, latestLayout) },
                ) {
                    Row {
                        HoldButton("B", InputMask.JOY_B, held, paused, ::pushInput)
                        Spacer(Modifier.width(12.dp))
                        HoldButton("A", InputMask.JOY_A, held, paused, ::pushInput)
                    }
                }
            }
        }
        // Pause overlay: the Pocket screen dimmed — the game stays
        // visible behind the dim, and the menu is an Atomic panel
        // (see PauseMenu).
        if (paused && !showSettings && !showCheats && !editingLayout) {
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
                    onMoveControls = { editingLayout = true },
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
                    onValueChanged = { session.applyOptions(optionsStore.all()) },
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
        modifier = modifier,
    )
}
