package app.poing

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Overlay rework (Robert, 2026-11-13): the player is the
 * HowlingDog landscape model — the game fills the player area
 * below the top bar at its native aspect, and the controls are
 * an overlay on that area, not stacked rows under it.
 *
 * Every control sits by its centre as a fraction of the FULL
 * screen (see ControlLayout) at its own size — anywhere on
 * screen, including over the game. The fractions are of the
 * full screen, not the player area, because the player area
 * grows and shrinks as the top bar hides and shows; fractions
 * of it would make the buttons jump every time the bar
 * toggles. The overlay lives in the root Box for the same
 * reason, and takes the canvas size ([canvasWidthDp] /
 * [canvasHeightDp]) from it.
 *
 * A BoxScope extension, so the controls align inside the root
 * Box. Extracted from GameScreen so the player screen stays
 * under the dex guard (HowlingDog v1.3.0 lesson). All dragging,
 * resizing and grouping live in the editor (LayoutEditor) —
 * play mode has no drag wiring at all.
 *
 * Per-system buttons + style (Robert, 2026-11-12): the theme
 * for [system] (see ControlThemes) is resolved once here and
 * passed into the buttons — appearance only, the inputs are
 * identical on every system.
 */
@Composable
fun BoxScope.ControlOverlay(
    isGba: Boolean,
    system: GameSystem,
    layout: ControlLayout,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    // Haptics click for the D-pad and A/B, gated by the caller
    // (a running cartridge rumble wins over the click).
    onControlDown: () -> Unit,
) {
    val theme = ControlThemes.themeFor(system)
    val alpha = layout.opacity / 100f
    // One placed control per spec: the centre fraction and the
    // size come from the layout, so a resized or moved control
    // renders exactly where the editor put it. The D-pad is one
    // cross; A and B are separate controls that happen to sit
    // as a pair (the editor moves them together by default);
    // L/R and Start/Select likewise.
    for (spec in layout.controlsFor(isGba)) {
        val size = spec.sizeDp.toFloat()
        val height = if (spec.id == ControlId.L || spec.id == ControlId.R ||
            spec.id == ControlId.START || spec.id == ControlId.SELECT
        ) {
            size / 2f
        } else {
            size
        }
        PlacedControl(
            cx = spec.cx, cy = spec.cy,
            widthDp = size, heightDp = height,
            canvasWidthDp = canvasWidthDp, canvasHeightDp = canvasHeightDp,
            alpha = alpha, testTag = "controls-${spec.id.key}",
        ) {
            ControlButton(spec, theme, held, paused, onInputChanged, onControlDown)
        }
    }
}

/**
 * One control's button, shared by the play overlay and the
 * editor (LayoutEditor) so the two can never drift apart: the
 * D-pad cross, a round A/B button, a wide L/R shoulder pill,
 * or a small Start/Select pill, in the system's theme, at the
 * spec's size.
 */
@Composable
fun ControlButton(
    spec: OverlayControl,
    theme: ControlThemes.Theme,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    onControlDown: () -> Unit,
) {
    val pressed = Color(theme.pressed)
    when (spec.id) {
        ControlId.DPAD -> DpadControl(
            held, paused, onInputChanged, onDirectionChange = onControlDown,
            crossColor = Color(theme.dpadCross), wellColor = Color(theme.dpadWell),
            sizeDp = spec.sizeDp.dp,
        )
        ControlId.A -> HoldButton(
            "A", InputMask.JOY_A, held, paused, onInputChanged, onDown = onControlDown,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.buttonA.background),
            unpressedLabel = Color(theme.buttonA.label),
            pressedBackground = pressed,
        )
        ControlId.B -> HoldButton(
            "B", InputMask.JOY_B, held, paused, onInputChanged, onDown = onControlDown,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.buttonB.background),
            unpressedLabel = Color(theme.buttonB.label),
            pressedBackground = pressed,
        )
        ControlId.L -> HoldButton(
            "L", InputMask.JOY_L, held, paused, onInputChanged, shoulder = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.shoulder.background),
            unpressedLabel = Color(theme.shoulder.label),
            pressedBackground = pressed,
        )
        ControlId.R -> HoldButton(
            "R", InputMask.JOY_R, held, paused, onInputChanged, shoulder = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.shoulder.background),
            unpressedLabel = Color(theme.shoulder.label),
            pressedBackground = pressed,
        )
        ControlId.START -> HoldButton(
            "Start", InputMask.JOY_START, held, paused, onInputChanged, pill = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.startSelect.background),
            unpressedLabel = Color(theme.startSelect.label),
            pressedBackground = pressed,
        )
        ControlId.SELECT -> HoldButton(
            "Select", InputMask.JOY_SELECT, held, paused, onInputChanged, pill = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.startSelect.background),
            unpressedLabel = Color(theme.startSelect.label),
            pressedBackground = pressed,
        )
    }
}
