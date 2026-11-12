package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Phase 5 — the touch controls drawn like the icon's hardware:
 * silver cross D-pad bottom-left, round silver A/B bottom-right,
 * small silver L/R shoulder tabs at the top of the player, in the
 * icon's exact layout.
 *
 * Unpressed is silver with black lettering; pressed is the icon
 * purple with white lettering — the hardware look, not the old
 * dark-grey buttons. The gesture behaviour is unchanged from
 * Phase 2/4 (per-pointer tracking, multi-touch, pause-keyed loops
 * so a lift under the pause overlay cannot stick a button down).
 */

/**
 * Phase 6 (item 4) — a control group that can be dragged to a new
 * spot while the layout is being edited. Outside edit mode it is a
 * plain positioned Box; in edit mode a drag moves the group (the
 * offset is clamped by ControlPosition) and the end of the drag
 * saves through [onMoveEnd].
 *
 * The drag detector sits on the wrapper while the buttons inside
 * keep their own press handling: while editing the game is paused,
 * so a press that also lands on a button is inert — it sets a held
 * bit the paused session never reads, and the button clears it on
 * lift. [position] is read through rememberUpdatedState so the
 * gesture block (keyed once per edit session) always sees the
 * latest offset instead of the one from the first composition.
 */
@Composable
fun MovableControl(
    position: ControlPosition,
    editing: Boolean,
    testTag: String,
    onMove: (ControlPosition) -> Unit,
    onMoveEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val latestPosition by rememberUpdatedState(position)
    Box(
        modifier = Modifier
            .offset {
                with(density) {
                    IntOffset(position.xDp.dp.roundToPx(), position.yDp.dp.roundToPx())
                }
            }
            .testTag(testTag)
            .then(
                if (editing) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onMoveEnd() },
                            onDragCancel = { onMoveEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            val dx = with(density) { dragAmount.x.toDp().value }
                            val dy = with(density) { dragAmount.y.toDp().value }
                            onMove(latestPosition.movedBy(dx, dy))
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

/**
 * A press-and-hold button: sets its joypad bit while a finger is
 * down on it, clears it on lift. Each button tracks its own
 * pointer, so multi-touch across buttons works (hold B while
 * tapping A, hold a D-pad direction while pressing Start).
 *
 * [sizeDp] is the circle's diameter; [shoulder] makes it a wide
 * pill instead (the L/R shoulder tabs).
 */
@Composable
fun HoldButton(
    label: String,
    bit: Int,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    sizeDp: Dp = 64.dp,
    shoulder: Boolean = false,
    // Phase 6 (item 7) — haptics: fires once when the finger
    // lands. Only A/B pass one; Start/Select/L/R stay silent.
    onDown: (() -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = if (shoulder) RoundedCornerShape(12.dp) else CircleShape
    val buttonModifier = if (shoulder) {
        Modifier.size(width = 96.dp, height = 48.dp)
    } else {
        Modifier.size(sizeDp)
    }
    Box(
        modifier = buttonModifier
            .testTag("hold-$label")
            .clip(shape)
            .background(if (pressed) PoingPurple else PoingSilver)
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
                    onDown?.invoke()
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
        Text(
            label,
            color = if (pressed) Color.White else Color.Black,
            style = displayTextStyle(DisplaySizeSmall),
        )
    }
}

/**
 * Phase 6 (item 1) — a press-and-hold button that drives an
 * action instead of a joypad bit: [onDown] fires when a finger
 * lands, [onUp] when the last finger lifts. The fast-forward
 * button uses it (hold for 2x/3x/4x, release back to normal).
 * Same gesture shape as [HoldButton] — per-pointer tracking and
 * a pause-keyed loop, so a lift under the pause overlay cannot
 * leave the action stuck on.
 */
@Composable
fun HoldActionButton(
    label: String,
    paused: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    sizeDp: Dp = 48.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(sizeDp)
            .testTag("hold-action-$label")
            .clip(CircleShape)
            .background(if (pressed) PoingPurple else PoingSilver)
            // Keyed on [paused]: pausing restarts the gesture loop,
            // so a finger that lifts under the pause overlay cannot
            // leave this action stuck on (the lift never reaches
            // the loop). The visual state resets with the loop.
            .pointerInput(paused) {
                pressed = false
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onDown()
                    var stillDown = true
                    while (stillDown) {
                        val event = awaitPointerEvent()
                        stillDown = event.changes.any { it.pressed }
                    }
                    pressed = false
                    onUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) Color.White else Color.Black,
            style = displayTextStyle(DisplaySizeSmall),
        )
    }
}

/**
 * Phase 6 (item 1) — a small toggle button in the same silver
 * hardware style: [checked] is the icon purple with white
 * lettering, unchecked is silver with black lettering. The
 * slow-motion toggle uses it (0.5x on, 1x off).
 */
@Composable
fun ToggleActionButton(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    sizeDp: Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .testTag("toggle-action-$label")
            .clip(CircleShape)
            .background(if (checked) PoingPurple else PoingSilver)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (checked) Color.White else Color.Black,
            style = displayTextStyle(DisplaySizeSmall),
        )
    }
}

/**
 * The 8-way D-pad: a silver cross on a dark well, one touch area,
 * every active pointer's direction OR-ed together (see
 * DpadInput). Multi-touch inside the pad works — a second thumb's
 * direction adds to the first's.
 */
@Composable
fun DpadControl(
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    // Phase 6 (item 7) — haptics: fires when the direction set
    // changes to something non-empty — once per press and once
    // per direction change while held, never on release and never
    // on a pointer event that leaves the directions unchanged.
    onDirectionChange: (() -> Unit)? = null,
) {
    var sizePx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .testTag("dpad")
            .size(144.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .onSizeChanged { sizePx = it.width.toFloat() }
            // Keyed on [paused]: pausing restarts the loop and drops
            // its active-pointer map, so a direction held when the
            // pause overlay appears cannot stay stuck (the lift
            // under the overlay never reaches the loop).
            .pointerInput(paused) {
                awaitEachGesture {
                    val active = mutableMapOf<PointerId, androidx.compose.ui.geometry.Offset>()
                    var lastDirs = 0
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
                        if (dirs != lastDirs && dirs != 0) {
                            onDirectionChange?.invoke()
                        }
                        lastDirs = dirs
                        held.setDirections(dirs)
                        onInputChanged()
                        if (active.isEmpty()) break
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The silver cross: a vertical bar and a horizontal bar,
        // the icon's D-pad. Static — the game itself shows the
        // direction; the cross never needs a pressed state.
        Box(
            Modifier
                .size(width = 48.dp, height = 144.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PoingSilver),
        )
        Box(
            Modifier
                .size(width = 144.dp, height = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PoingSilver),
        )
    }
}
