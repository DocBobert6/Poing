package app.poing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Overlay rework (Robert, 2026-11-13): the Mariner-parity
 * control editor. A full-screen editor over the paused game —
 * the game shows dimly behind it, so a layout is judged
 * against the real picture while it is edited. The controls
 * drag anywhere on screen, including over the game; clusters
 * (D-pad cross, A/B pair, L/R pair, Start/Select pair) move
 * together by default, with a Together switch to move buttons
 * separately. A selected control shows its exact size in dp
 * inside itself, and the stepper changes it 1dp at a time.
 * Snap is an 8dp grid (on by default); opacity is a stepper
 * over the whole layout. Back discards the draft, Done
 * commits it — Mariner's model.
 *
 * Extracted from GameScreen so the player screen stays under
 * the dex guard (HowlingDog v1.3.0 lesson). The editor holds
 * its own draft: nothing reaches the play overlay until Done.
 */
@Composable
fun LayoutEditor(
    initial: ControlLayout,
    isGba: Boolean,
    system: GameSystem,
    held: HeldInput,
    onInputChanged: () -> Unit,
    onControlDown: () -> Unit,
    // Alpha.11 — per-button character (see ControlButton):
    // the release tick, the roll tick, the light click, the
    // shoulder click. Defaults keep the editor preview on
    // the single press click unless the caller passes them.
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.10 — the left-control choice for the game being
    // edited. [leftControlDefault] is the global default (what
    // "Global" previews to); [leftControlOverride] is this
    // game's stored override, null when it follows the global
    // default. [showLeftControlChoice] shows the toggle row —
    // the per-game editor only; the Settings default editor
    // has no per-game choice to make, so it passes nothing
    // and the row stays hidden. [onDone] commits the layout
    // AND the draft override (null = follow global).
    leftControlDefault: LeftControl = LeftControl.DPAD,
    leftControlOverride: LeftControl? = null,
    showLeftControlChoice: Boolean = false,
    onDone: (ControlLayout, LeftControl?) -> Unit,
    onBack: () -> Unit,
    // Overlay defaults (Robert, 2026-11-14): what Reset
    // restores. The player passes the game's handheld
    // default, so Reset in the player restores that; the
    // Settings default editor passes the factory default,
    // so Reset there restores the factory layout.
    resetLayout: ControlLayout = ControlLayout.DEFAULT,
) {
    var draft by remember { mutableStateOf(initial) }
    // Alpha.10 — the left-control draft: the override until
    // Done, like the layout draft. Null means this game
    // follows the global default. Reset is layout-only (see
    // the Reset pill): it never touches this draft.
    var leftControlDraft by remember { mutableStateOf(leftControlOverride) }
    // The control the editor previews: the draft override,
    // or the global default while the game follows it.
    val leftControlPreview = leftControlDraft ?: leftControlDefault
    var selectedId by remember { mutableStateOf<ControlId?>(null) }
    var snapOn by remember { mutableStateOf(true) }
    var grouped by remember { mutableStateOf(true) }
    val theme = ControlThemes.themeFor(system)
    val alpha = draft.opacity / 100f
    val selected = draft.controls.firstOrNull { it.id == selectedId }

    // Back discards the draft, like Mariner's Back pill.
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .testTag("layout-editor"),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasWDp = maxWidth.value
            val canvasHDp = maxHeight.value
            for (spec in draft.controlsFor(isGba)) {
                EditorControl(
                    spec = spec,
                    selected = spec.id == selectedId,
                    canvasWDp = canvasWDp,
                    canvasHDp = canvasHDp,
                    snapOn = snapOn,
                    alpha = alpha,
                    theme = theme,
                    held = held,
                    onInputChanged = onInputChanged,
                    onControlDown = onControlDown,
                    onControlUp = onControlUp,
                    onDirectionTick = onDirectionTick,
                    onLightDown = onLightDown,
                    onShoulderDown = onShoulderDown,
                    // Alpha.10 — the editor previews the draft
                    // choice, so switching D-pad / Joystick
                    // shows the live control in its slot.
                    leftControl = leftControlPreview,
                    onSelect = { selectedId = spec.id },
                    onMove = { cx, cy ->
                        draft = draft.moved(spec.id, cx, cy, grouped)
                    },
                )
            }
        }
        // Floating pills at the top, Mariner-style: only the
        // small pills block touches, and the stepper rows
        // appear only while they apply. The start padding
        // clears the floating power light in the top-left
        // corner, which draws under this editor.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorPill("Back", "editor-back") { onBack() }
                EditorPill("Done", "editor-done") { onDone(draft, leftControlDraft) }
                EditorPill("Reset", "editor-reset") {
                    draft = resetLayout
                    selectedId = null
                }
                EditorTogglePill("Snap", snapOn, "editor-snap") { snapOn = it }
                EditorTogglePill("Together", grouped, "editor-together") { grouped = it }
            }
            // Alpha.10 — the left-control choice for this game:
            // the D-pad cross, the joystick, or Global (follow
            // the Settings default). The preview below switches
            // with the draft, so the choice is judged live.
            if (showLeftControlChoice) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorPillLabel("Left control", "editor-left-control-label")
                    EditorChoicePill("Global", leftControlDraft == null, "editor-left-control-global") {
                        leftControlDraft = null
                    }
                    EditorChoicePill("D-pad", leftControlDraft == LeftControl.DPAD, "editor-left-control-dpad") {
                        leftControlDraft = LeftControl.DPAD
                    }
                    EditorChoicePill("Joystick", leftControlDraft == LeftControl.STICK, "editor-left-control-stick") {
                        leftControlDraft = LeftControl.STICK
                    }
                }
            }
            // Opacity, over the whole layout (Mariner's
            // per-console alpha, here per game): 0–100% in
            // steps of 5.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorPill("−", "editor-opacity-minus") {
                    draft = draft.withOpacity(draft.opacity - 5)
                }
                EditorPillLabel("Opacity ${draft.opacity}%", "editor-opacity-value")
                EditorPill("+", "editor-opacity-plus") {
                    draft = draft.withOpacity(draft.opacity + 5)
                }
            }
            // Size stepper, only while a control is selected:
            // 1dp at a time, Mariner's 36–220dp range, grouped
            // resize when Together is on.
            if (selected != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorPill("−", "editor-size-minus") {
                        draft = draft.resized(selected.id, selected.sizeDp - 1, grouped)
                    }
                    EditorPillLabel("${selected.sizeDp}dp", "editor-size-value")
                    EditorPill("+", "editor-size-plus") {
                        draft = draft.resized(selected.id, selected.sizeDp + 1, grouped)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPill(label: String, testTag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .background(PoingPurpleDeep.copy(alpha = 0.55f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun EditorPillLabel(label: String, testTag: String) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .background(PoingPurpleDeep.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun EditorTogglePill(
    label: String,
    checked: Boolean,
    testTag: String,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .background(PoingPurpleDeep.copy(alpha = 0.55f), RoundedCornerShape(50))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (checked) PoingGold else Color.White)
    }
}

/**
 * Alpha.10 — one choice in a small set (the left-control
 * toggle): gold text while selected, white otherwise. Unlike
 * [EditorTogglePill] it does not negate — the caller sets
 * the choice outright.
 */
@Composable
private fun EditorChoicePill(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .background(PoingPurpleDeep.copy(alpha = 0.55f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) PoingGold else Color.White)
    }
}

/**
 * One control in the editor: the real button (see
 * ControlButton — the editor shows what the player shows),
 * draggable anywhere on the canvas. A drag selects the
 * control first, Mariner's model, so the size stepper
 * appears; the drag then moves the control — and its whole
 * cluster, when Together is on — snapping the centre to the
 * 8dp grid when Snap is on, clamped to the screen.
 *
 * The drag detector sits on the wrapper while the button
 * inside keeps its own press handling: while editing the game
 * is paused, so a press that also lands on a button is inert
 * — it sets a held bit the paused session never reads, and the
 * button clears it on lift. The button never consumes the
 * down, so the wrapper's drag detector sees the same gesture.
 */
@Composable
private fun EditorControl(
    spec: OverlayControl,
    selected: Boolean,
    canvasWDp: Float,
    canvasHDp: Float,
    snapOn: Boolean,
    alpha: Float,
    theme: ControlThemes.Theme,
    held: HeldInput,
    onInputChanged: () -> Unit,
    onControlDown: () -> Unit,
    // Alpha.11 — per-button character (see ControlButton).
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.10 — which control the editor previews in the
    // left-hand slot (the draft choice, or the global default
    // while the game follows it).
    leftControl: LeftControl = LeftControl.DPAD,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    val currentSpec by rememberUpdatedState(spec)
    val density = LocalDensity.current.density
    val size = spec.sizeDp.toFloat()
    val height = if (spec.id == ControlId.L || spec.id == ControlId.R ||
        spec.id == ControlId.START || spec.id == ControlId.SELECT
    ) {
        size / 2f
    } else {
        size
    }
    Box(
        modifier = Modifier
            .offset(
                x = (spec.cx * canvasWDp - size / 2f).dp,
                y = (spec.cy * canvasHDp - height / 2f).dp,
            )
            .size(size.dp, height.dp)
            .then(
                if (selected) {
                    Modifier.border(2.dp, PoingGold, if (height < size) RoundedCornerShape(50) else CircleShape)
                } else {
                    Modifier
                },
            )
            .alpha(alpha)
            .testTag("editor-${spec.id.key}")
            .pointerInput(spec.id, snapOn, canvasWDp, canvasHDp) {
                var baseCxDp = 0f
                var baseCyDp = 0f
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                        baseCxDp = currentSpec.cx * canvasWDp
                        baseCyDp = currentSpec.cy * canvasHDp
                        accX = 0f
                        accY = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        accX += drag.x / density
                        accY += drag.y / density
                        val cxDp = ControlLayout.snap(baseCxDp + accX, snapOn)
                        val cyDp = ControlLayout.snap(baseCyDp + accY, snapOn)
                        onMove(
                            cxDp / canvasWDp,
                            cyDp / canvasHDp,
                        )
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ControlButton(
            spec, theme, held, true, onInputChanged, onControlDown, leftControl,
            onControlUp = onControlUp,
            onDirectionTick = onDirectionTick,
            onLightDown = onLightDown,
            onShoulderDown = onShoulderDown,
        )
        // A selected control shows its exact size in dp inside
        // itself, Mariner's model — a small gold label at its
        // bottom edge, inside the control.
        if (selected) {
            Text(
                "${spec.sizeDp}dp",
                color = PoingGold,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .testTag("editor-selected-size"),
            )
        }
    }
}
