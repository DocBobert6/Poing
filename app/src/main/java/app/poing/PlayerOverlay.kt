package app.poing

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Layout rework (Robert, 2026-11-12): the player is the
 * HowlingDog landscape model — the game fills the player area
 * below the top bar at its native aspect, and the controls are
 * an overlay on that area, not stacked rows under it. D-pad
 * centred in the left flank bar, A/B centred in the right
 * flank bar, L/R shoulder tabs at the top-left / top-right
 * corners, Start/Select as small pills side by side at the
 * bottom centre. The agreed destinations from the stacked
 * moves (L/R up top, Start/Select side by side) are kept in
 * spirit — they now sit as overlay, since there is no row
 * below the game any more.
 *
 * A BoxScope extension, so the groups align inside the player
 * Box. Extracted from GameScreen so the player screen stays
 * under the dex guard (HowlingDog v1.3.0 lesson): the four
 * movable groups and their drag wiring live here, the game
 * view and the top bar stay in GameScreen.
 *
 * Per-game saved positions (Phase 6, item 4) apply exactly as
 * before: each group's offset is a delta from its default
 * spot (see ControlLayout), so the new defaults need no
 * migration — a saved offset still applies as the same delta,
 * and Reset returns to the new defaults.
 */
@Composable
fun BoxScope.ControlOverlay(
    isGba: Boolean,
    layout: ControlLayout,
    editingLayout: Boolean,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    // Haptics click for the D-pad and A/B, gated by the caller
    // (a running cartridge rumble wins over the click).
    onControlDown: () -> Unit,
    onMove: (ControlGroup, ControlPosition) -> Unit,
    onMoveEnd: () -> Unit,
) {
    // L/R shoulder tabs (GBA only) at the top corners of the
    // player area, like the icon's shoulders. One saved group:
    // both pills share layout.lr, so dragging either moves both
    // together, exactly as the old top-bar pair did.
    if (isGba) {
        MovableControl(
            position = layout.lr,
            editing = editingLayout,
            testTag = "controls-l",
            onMove = { onMove(ControlGroup.LR, it) },
            onMoveEnd = onMoveEnd,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp),
        ) {
            HoldButton("L", InputMask.JOY_L, held, paused, onInputChanged, shoulder = true)
        }
        MovableControl(
            position = layout.lr,
            editing = editingLayout,
            testTag = "controls-r",
            onMove = { onMove(ControlGroup.LR, it) },
            onMoveEnd = onMoveEnd,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp),
        ) {
            HoldButton("R", InputMask.JOY_R, held, paused, onInputChanged, shoulder = true)
        }
    }
    // D-pad centred in the left flank bar, A/B centred in the
    // right flank bar — the black bars left and right of the
    // aspect-correct game. Sizes unchanged (D-pad 144dp, A/B
    // 64dp circles), as ordered.
    MovableControl(
        position = layout.dpad,
        editing = editingLayout,
        testTag = "controls-dpad",
        onMove = { onMove(ControlGroup.DPAD, it) },
        onMoveEnd = onMoveEnd,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
    ) {
        DpadControl(held, paused, onInputChanged, onDirectionChange = onControlDown)
    }
    MovableControl(
        position = layout.ab,
        editing = editingLayout,
        testTag = "controls-ab",
        onMove = { onMove(ControlGroup.AB, it) },
        onMoveEnd = onMoveEnd,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
    ) {
        Row {
            HoldButton("B", InputMask.JOY_B, held, paused, onInputChanged, onDown = onControlDown)
            Spacer(Modifier.width(12.dp))
            HoldButton("A", InputMask.JOY_A, held, paused, onInputChanged, onDown = onControlDown)
        }
    }
    // Start/Select as small pills, side by side, centred,
    // overlaid at the bottom centre of the player area.
    MovableControl(
        position = layout.startSelect,
        editing = editingLayout,
        testTag = "controls-startselect",
        onMove = { onMove(ControlGroup.START_SELECT, it) },
        onMoveEnd = onMoveEnd,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    ) {
        Row {
            HoldButton("Start", InputMask.JOY_START, held, paused, onInputChanged, pill = true)
            Spacer(Modifier.width(12.dp))
            HoldButton("Select", InputMask.JOY_SELECT, held, paused, onInputChanged, pill = true)
        }
    }
}
