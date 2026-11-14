package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 5 — the touch controls drawn like the icon's hardware:
 * silver cross D-pad bottom-left, round silver A/B bottom-right,
 * small silver L/R shoulder tabs at the top corners, small
 * Start/Select pills side by side at the bottom centre — all
 * overlaid on the player area beside the full-height game, the
 * HowlingDog landscape model (Robert, 2026-11-12: the game
 * basically the phone's height, controls in the black bars
 * left and right of it).
 *
 * Unpressed is the theme's button colour with its label colour;
 * pressed is the theme's pressed colour with a white label (see
 * ControlThemes — one theme per system; the defaults below are
 * the Phase 5 silver hardware look). The gesture behaviour is
 * unchanged from Phase 2/4 (per-pointer tracking, multi-touch,
 * pause-keyed loops so a lift under the pause overlay cannot
 * stick a button down).
 */

/**
 * Overlay rework (Robert, 2026-11-13) — a control placed by
 * its centre as a fraction of the screen (see ControlLayout),
 * at its own size. Outside the editor it is a plain positioned
 * Box: all dragging, resizing and grouping live in the editor
 * (LayoutEditor), so play mode has no drag wiring at all.
 * [widthDp]/[heightDp] are the control's own size in dp (a
 * circle's diameter, a pill's width/height); [alpha] is the
 * layout's control opacity.
 */
@Composable
fun PlacedControl(
    cx: Float,
    cy: Float,
    widthDp: Float,
    heightDp: Float,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    alpha: Float,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                x = (cx * canvasWidthDp - widthDp / 2f).dp,
                y = (cy * canvasHeightDp - heightDp / 2f).dp,
            )
            .size(widthDp.dp, heightDp.dp)
            .alpha(alpha)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
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
 * pill instead (the L/R shoulder tabs); [pill] makes it a small
 * pill (the Start/Select pair at the bottom centre of the
 * player area).
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
    pill: Boolean = false,
    // Per-system style (ControlThemes): the unpressed background
    // + label and the pressed background. Defaults are the Phase 5
    // silver hardware look, so the FF/RW action buttons — utility
    // controls, not hardware — and the existing tests stay silver.
    unpressedBackground: Color = PoingSilver,
    unpressedLabel: Color = Color.Black,
    pressedBackground: Color = PoingPurple,
    // Phase 6 (item 7) — haptics: fires once when the finger
    // lands. Only A/B pass one; Start/Select/L/R stay silent.
    onDown: (() -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val shape = when {
        shoulder -> RoundedCornerShape(12.dp)
        pill -> RoundedCornerShape(16.dp)
        else -> CircleShape
    }
    // Overlay rework (Robert, 2026-11-13): a shoulder or pill
    // is [sizeDp] wide and half as tall — the editor's size is
    // the control's size, so a resized L tab stays a wide pill
    // and a resized Start pill stays a small one. The defaults
    // keep the old spots: shoulders 96dp, pills 64dp, circles
    // 64dp — callers that resize pass the layout's size.
    val buttonModifier = when {
        shoulder -> Modifier.size(width = sizeDp, height = sizeDp / 2)
        pill -> Modifier.size(width = sizeDp, height = sizeDp / 2)
        else -> Modifier.size(sizeDp)
    }
    Box(
        modifier = buttonModifier
            .testTag("hold-$label")
            .clip(shape)
            .background(if (pressed) pressedBackground else unpressedBackground)
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
            color = if (pressed) Color.White else unpressedLabel,
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
 *
 * Overlay rework (Robert, 2026-11-13): the pad's size is the
 * layout's size for it ([sizeDp], 144dp by default) — the cross
 * bars scale with it, a third as wide as the pad, spanning it.
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
    // Per-system style (ControlThemes): the cross and the well.
    // Defaults are the Phase 5 silver cross on a dark well.
    crossColor: Color = PoingSilver,
    wellColor: Color = Color(0xFF1A1A1A),
    sizeDp: Dp = 144.dp,
) {
    var sizePx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .testTag("dpad")
            .size(sizeDp)
            .clip(RoundedCornerShape(16.dp))
            .background(wellColor)
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
        // The cross: two bars in the theme's cross colour. Static —
        // the game itself shows the direction; the cross never needs
        // a pressed state. Each bar is a third as wide as the pad
        // and spans it.
        Box(
            Modifier
                .size(width = sizeDp / 3, height = sizeDp)
                .clip(RoundedCornerShape(8.dp))
                .background(crossColor),
        )
        Box(
            Modifier
                .size(width = sizeDp, height = sizeDp / 3)
                .clip(RoundedCornerShape(8.dp))
                .background(crossColor),
        )
    }
}
