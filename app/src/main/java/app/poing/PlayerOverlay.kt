package app.poing

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    // Haptics click for every overlay button, gated by the
    // caller (a running cartridge rumble wins over the click).
    onControlDown: () -> Unit,
    // Alpha.11 — per-button character: the release tick, the
    // D-pad / stick roll tick, the Start/Select light click,
    // the L/R shoulder click. Defaults keep every existing
    // caller on the single press click.
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.10 — which control lives in the left-hand slot:
    // the D-pad cross, or a joystick. One slot holds EITHER,
    // never both; the caller resolves the global default +
    // per-game override (see GameScreen).
    leftControl: LeftControl = LeftControl.DPAD,
    // Alpha.10 (Part D) — the FF / 0.5x / RW buttons live in
    // the overlay now: they render from the layout like every
    // other control, and only when their Settings toggle is on
    // (both off by default, per the standing overlay rule).
    // FF is hold-to-run at the chosen speed, 0.5x is a toggle,
    // RW is hold-to-step back; the behaviour is the caller's.
    ffVisible: Boolean = false,
    rewindVisible: Boolean = false,
    slowOn: Boolean = false,
    onFfDown: () -> Unit = {},
    onFfUp: () -> Unit = {},
    onSlowToggle: () -> Unit = {},
    onRwDown: () -> Unit = {},
    onRwUp: () -> Unit = {},
) {
    val theme = ControlThemes.themeFor(system)
    val alpha = layout.opacity / 100f
    // Performance fix 1 (2026-11-14): the filtered list is
    // built once per layout/system, not once per pass.
    val specs = remember(layout, isGba, ffVisible, rewindVisible) {
        layout.controlsFor(isGba).filter { spec ->
            when (spec.id) {
                ControlId.FF, ControlId.SLOW -> ffVisible
                ControlId.RW -> rewindVisible
                else -> true
            }
        }
    }
    // One placed control per spec: the centre fraction and the
    // size come from the layout, so a resized or moved control
    // renders exactly where the editor put it. The D-pad is one
    // cross; A and B are separate controls that happen to sit
    // as a pair (the editor moves them together by default);
    // L/R and Start/Select likewise.
    for (spec in specs) {
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
            ControlButton(
                spec, theme, held, paused, onInputChanged, onControlDown, leftControl,
                onControlUp = onControlUp,
                onDirectionTick = onDirectionTick,
                onLightDown = onLightDown,
                onShoulderDown = onShoulderDown,
                slowOn = slowOn,
                onFfDown = onFfDown, onFfUp = onFfUp, onSlowToggle = onSlowToggle,
                onRwDown = onRwDown, onRwUp = onRwUp,
            )
        }
    }
}

/**
 * One control's button, shared by the play overlay and the
 * editor (LayoutEditor) so the two can never drift apart: the
 * D-pad cross — or the joystick, when the left-control choice
 * says so — a round A/B button, a wide L/R shoulder pill,
 * or a small Start/Select pill, in the system's theme, at the
 * spec's size. The FF / 0.5x / RW buttons are 48dp circles
 * here too: FF and RW hold, 0.5x toggles — the same
 * HoldActionButton / ToggleActionButton the fixed Row used,
 * so their tags and behaviour are unchanged by the move.
 */
@Composable
fun ControlButton(
    spec: OverlayControl,
    theme: ControlThemes.Theme,
    held: HeldInput,
    paused: Boolean,
    onInputChanged: () -> Unit,
    onControlDown: () -> Unit,
    // Alpha.11 — per-button character (see ControlOverlay):
    // the release tick, the roll tick, the light click, the
    // shoulder click. Defaults keep every existing caller on
    // the single press click.
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.10 — which control lives in the left-hand slot.
    // The D-pad is the default, so existing callers (the
    // editor's tests, the Settings default editor) keep the
    // cross unless they pass the choice.
    leftControl: LeftControl = LeftControl.DPAD,
    // Alpha.10 (Part D) — the FF / 0.5x / RW actions. The
    // editor previews with the defaults (no-ops): a press
    // there is inert, the game is paused. Visibility is the
    // overlay's job, not this button's — ControlOverlay
    // filters hidden buttons before this is reached.
    slowOn: Boolean = false,
    onFfDown: () -> Unit = {},
    onFfUp: () -> Unit = {},
    onSlowToggle: () -> Unit = {},
    onRwDown: () -> Unit = {},
    onRwUp: () -> Unit = {},
) {
    val pressed = Color(theme.pressed)
    when (spec.id) {
        ControlId.DPAD -> when (leftControl) {
            LeftControl.DPAD -> DpadControl(
                held, paused, onInputChanged, onDirectionChange = onDirectionTick,
                onDirectionsEmpty = onControlUp,
                crossColor = Color(theme.dpadCross), wellColor = Color(theme.dpadWell),
                pressedColor = pressed,
                sizeDp = spec.sizeDp.dp,
            )
            // Alpha.10 — the joystick lives in the same slot:
            // the same spec (position, size) and the same
            // layout opacity, so a game that switches keeps its
            // tuned spot, and the editor moves and resizes it
            // exactly like the D-pad.
            LeftControl.STICK -> StickControl(
                held, paused, onInputChanged, onDirectionChange = onDirectionTick,
                onDirectionsEmpty = onControlUp,
                baseColor = Color(theme.dpadWell), nubColor = Color(theme.dpadCross),
                accentColor = pressed,
                sizeDp = spec.sizeDp.dp,
            )
        }
        ControlId.A -> HoldButton(
            "A", InputMask.JOY_A, held, paused, onInputChanged, onDown = onControlDown, onUp = onControlUp,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.buttonA.background),
            unpressedLabel = Color(theme.buttonA.label),
            pressedBackground = pressed,
        )
        ControlId.B -> HoldButton(
            "B", InputMask.JOY_B, held, paused, onInputChanged, onDown = onControlDown, onUp = onControlUp,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.buttonB.background),
            unpressedLabel = Color(theme.buttonB.label),
            pressedBackground = pressed,
        )
        ControlId.L -> HoldButton(
            "L", InputMask.JOY_L, held, paused, onInputChanged, onDown = onShoulderDown, onUp = onControlUp, shoulder = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.shoulder.background),
            unpressedLabel = Color(theme.shoulder.label),
            pressedBackground = pressed,
        )
        ControlId.R -> HoldButton(
            "R", InputMask.JOY_R, held, paused, onInputChanged, onDown = onShoulderDown, onUp = onControlUp, shoulder = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.shoulder.background),
            unpressedLabel = Color(theme.shoulder.label),
            pressedBackground = pressed,
        )
        ControlId.START -> HoldButton(
            "Start", InputMask.JOY_START, held, paused, onInputChanged, onDown = onLightDown, onUp = onControlUp, pill = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.startSelect.background),
            unpressedLabel = Color(theme.startSelect.label),
            pressedBackground = pressed,
        )
        ControlId.SELECT -> HoldButton(
            "Select", InputMask.JOY_SELECT, held, paused, onInputChanged, onDown = onLightDown, onUp = onControlUp, pill = true,
            sizeDp = spec.sizeDp.dp,
            unpressedBackground = Color(theme.startSelect.background),
            unpressedLabel = Color(theme.startSelect.label),
            pressedBackground = pressed,
        )
        // Alpha.10 (Part D) — the FF / 0.5x / RW trio, moved
        // out of the fixed Row into the overlay: the same
        // buttons, the same tags, at the spec's size.
        ControlId.FF -> HoldActionButton(
            "FF", paused, onDown = onFfDown, onUp = onFfUp,
            sizeDp = spec.sizeDp.dp,
        )
        ControlId.SLOW -> ToggleActionButton(
            "0.5x", checked = slowOn, onToggle = onSlowToggle,
            sizeDp = spec.sizeDp.dp,
        )
        ControlId.RW -> HoldActionButton(
            "RW", paused, onDown = onRwDown, onUp = onRwUp,
            sizeDp = spec.sizeDp.dp,
        )
    }
}
