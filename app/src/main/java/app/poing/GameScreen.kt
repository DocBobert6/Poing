package app.poing

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
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
    optionsStore: CoreOptionsStore,
    onQuit: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stats by remember { mutableStateOf<PlayerStats?>(null) }
    var paused by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pauseMessage by remember { mutableStateOf<String?>(null) }
    val held = remember { HeldInput() }
    val scope = rememberCoroutineScope()

    val session = remember(gamePath) {
        PlayerSession(
            systemDir = context.filesDir.resolve("system").apply { mkdirs() }.absolutePath,
            saveDir = context.filesDir.resolve("saves").apply { mkdirs() }.absolutePath,
            gamePath = gamePath,
            // The stored ROM file is <sha256>.<ext>: its stem is the
            // stable per-game key for <base>.sav and <base>.stateN.
            saveBase = java.io.File(gamePath).nameWithoutExtension,
            // Core options as stored: pushed to the bridge after the
            // libraries load and before the game loads.
            options = optionsStore.all(),
            onFrame = { bitmap = it },
            onStats = { stats = it },
            onError = onError,
        )
    }

    // The input the core sees is the touch mask OR the gamepad mask.
    // Either side changing re-pushes the merged mask.
    fun pushInput() {
        session.setInput(held.mask or GamepadInput.state.mask)
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
        session.pause()
        paused = true
        pushInput()
    }

    fun doResume() {
        if (!paused) return
        session.resume()
        paused = false
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
            // Top bar: diagnostic text + pause.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stats?.let {
                        "${it.width}x${it.height} core ${"%.1f".format(it.coreFps)}fps " +
                            "measured ${"%.1f".format(it.measuredFps)}fps " +
                            "${it.framesDrawn} frames"
                    } ?: "starting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
                Button(onClick = {
                    if (paused) doResume() else doPause()
                }) {
                    Text(if (paused) "Resume" else "Pause")
                }
            }
            // Game view, letterboxed at the native aspect.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val frame = bitmap
                val s = stats
                if (frame != null && s != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Game: $gameName",
                        modifier = Modifier
                            .aspectRatio(s.width.toFloat() / s.height.toFloat())
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("Loading $gameName…", color = Color.White)
                }
            }
            // L/R row (GBA only).
            if (isGba) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    HoldButton("L", InputMask.JOY_L, held, paused, ::pushInput)
                    HoldButton("R", InputMask.JOY_R, held, paused, ::pushInput)
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
                DpadControl(held, paused, ::pushInput)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HoldButton("Start", InputMask.JOY_START, held, paused, ::pushInput)
                    Spacer(Modifier.height(8.dp))
                    HoldButton("Select", InputMask.JOY_SELECT, held, paused, ::pushInput)
                }
                Row {
                    HoldButton("B", InputMask.JOY_B, held, paused, ::pushInput)
                    Spacer(Modifier.width(12.dp))
                    HoldButton("A", InputMask.JOY_A, held, paused, ::pushInput)
                }
            }
        }
        // Pause overlay: resume, quick save/load (slot 0), the four
        // save-state slots, core options, quit. Save/load run on IO
        // (serialize + file write) and only ever run while paused,
        // so a state can never race a frame.
        if (paused && !showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("Paused", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(gameName, color = Color.White)
                    pauseMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color.White)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { doResume() }) {
                        Text("Resume")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            pauseMessage = null
                            scope.launch(Dispatchers.IO) {
                                val ok = session.saveState(SaveStore.QUICK_SLOT)
                                withContext(Dispatchers.Main) {
                                    pauseMessage = if (ok) "Quick saved." else "Quick save failed."
                                }
                            }
                        }) {
                            Text("Quick save")
                        }
                        Button(
                            onClick = {
                                pauseMessage = null
                                scope.launch(Dispatchers.IO) {
                                    val ok = session.loadState(SaveStore.QUICK_SLOT)
                                    withContext(Dispatchers.Main) {
                                        pauseMessage = if (ok) "Quick loaded." else "No quick save yet."
                                    }
                                }
                            },
                            enabled = session.stateExists(SaveStore.QUICK_SLOT),
                        ) {
                            Text("Quick load")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Save states", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    for (slot in 0 until SaveStore.SLOT_COUNT) {
                        val savedAt = session.stateModifiedAt(slot)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Text(
                                "Slot $slot" + if (slot == SaveStore.QUICK_SLOT) " (quick)" else "",
                                color = Color.White,
                            )
                            Text(
                                savedAt?.let { formatStateTime(it) } ?: "empty",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                pauseMessage = null
                                scope.launch(Dispatchers.IO) {
                                    val ok = session.saveState(slot)
                                    withContext(Dispatchers.Main) {
                                        pauseMessage = if (ok) "Saved to slot $slot." else "Save failed."
                                    }
                                }
                            }) {
                                Text("Save")
                            }
                            Button(
                                onClick = {
                                    pauseMessage = null
                                    scope.launch(Dispatchers.IO) {
                                        val ok = session.loadState(slot)
                                        withContext(Dispatchers.Main) {
                                            pauseMessage = if (ok) "Loaded slot $slot." else "Load failed."
                                        }
                                    }
                                },
                                enabled = savedAt != null,
                            ) {
                                Text("Load")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showSettings = true }) {
                        Text("Core options")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onQuit) {
                        Text("Quit to home")
                    }
                }
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
    }
}

/** "Mar 4, 21:30" for a save-state slot's last-modified time. */
private fun formatStateTime(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}

/**
 * The currently held inputs, as one mutable mask. Held in
 * `remember` (not Compose state — the mask itself never needs a
 * recomposition) and shared by the touch controls and the session.
 * The pointer-input callbacks read the mask at call time, so they
 * never go stale.
 */
class HeldInput {
    @Volatile
    var mask: Int = 0
        private set

    fun setBit(bit: Int, held: Boolean): Int {
        mask = if (held) mask or (1 shl bit) else mask and (1 shl bit).inv()
        return mask
    }

    fun setDirections(dirMask: Int): Int {
        val dirBits = (1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_DOWN) or
            (1 shl InputMask.JOY_LEFT) or (1 shl InputMask.JOY_RIGHT)
        mask = (mask and dirBits.inv()) or dirMask
        return mask
    }

    /** Clear every held input (pause / quit). Returns the new mask (0). */
    fun clear(): Int {
        mask = 0
        return mask
    }
}

/**
 * A press-and-hold button: sets its joypad bit while a finger is
 * down on it, clears it on lift. Each button tracks its own
 * pointer, so multi-touch across buttons works (hold B while
 * tapping A, hold a D-pad direction while pressing Start).
 */
@Composable
private fun HoldButton(
    label: String,
    bit: Int,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (pressed) Color(0xFF6750A4) else Color(0xFF3A3A3A))
            // Keyed on [paused]: pausing restarts the gesture loop,
            // so a finger that lifts under the pause overlay cannot
            // leave this button stuck down (the lift never reaches
            // the loop). The visual state resets with the loop.
            .pointerInput(bit, paused) {
                pressed = false
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    held.setBit(bit, true)
                    onInputChanged()
                    var stillDown = true
                    while (stillDown) {
                        val event = awaitPointerEvent()
                        stillDown = event.changes.any { it.pressed }
                    }
                    pressed = false
                    held.setBit(bit, false)
                    onInputChanged()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White)
    }
}

/**
 * The 8-way D-pad: one touch area, every active pointer's
 * direction OR-ed together (see DpadInput). Multi-touch inside
 * the pad works — a second thumb's direction adds to the first's.
 */
@Composable
private fun DpadControl(held: HeldInput, paused: Boolean, onInputChanged: () -> Unit) {
    var sizePx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .size(144.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3A3A3A))
            .onSizeChanged { sizePx = it.width.toFloat() }
            // Keyed on [paused]: pausing restarts the loop and drops
            // its active-pointer map, so a direction held when the
            // pause overlay appears cannot stay stuck (the lift
            // under the overlay never reaches the loop).
            .pointerInput(paused) {
                awaitEachGesture {
                    val active = mutableMapOf<PointerId, androidx.compose.ui.geometry.Offset>()
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed) {
                                active[change.id] = change.position
                            } else {
                                active.remove(change.id)
                            }
                        }
                        val dirs = active.values.fold(0) { acc, pos ->
                            acc or DpadInput.directionFromPress(pos.x, pos.y, sizePx)
                        }
                        held.setDirections(dirs)
                        onInputChanged()
                        if (active.isEmpty()) break
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, style = MaterialTheme.typography.displayMedium)
    }
}
