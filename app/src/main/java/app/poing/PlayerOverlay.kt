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
    // Alpha.10 — which control lives in the left-hand slot:
    // the D-pad cross, or a joystick. One slot holds EITHER,
    // never both; the caller resolves the global default +
    // per-game override (see GameScreen).
    leftControl: LeftControl = LeftControl.DPAD,
    // Alpha.11 — per-button character: the release tick, the
    // D-pad / stick roll tick, the Start/Select light click,
    // the L/R shoulder click. Defaults keep every existing
    // caller on the single press click. These sit after
    // leftControl so the positional callers keep working.
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.14 — the joystick dead zone in effect for this
    // game, in units of full travel: the per-game override
    // when it has one, otherwise the global slider value.
    // The default is the shipped 0.22. It reaches only the
    // stick — the D-pad keeps its own fixed dead zone.
    deadZone: Float = StickInput.DEAD_ZONE,
    // Alpha.16 — proportional steering: the stick's live
    // state (mask + deflection), handed to the session by the
    // caller. Null keeps the editor preview on the old
    // behaviour — the game is paused there, so the loop
    // never pulses.
    onStickStateChanged: ((mask: Int, deflection: Float) -> Unit)? = null,
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
    // Alpha.25 — the game width in dp, for the unlock popup's
    // width cap (75% of the game width, see popupWidthDp).
    // Defaults to the canvas width, so callers without a game
    // (tests) keep working.
    gameWidthDp: Float = canvasWidthDp,
    // Alpha.27 — the D-pad envelope split: the routed
    // direction callback (previous mask, new mask) — the
    // press from neutral and the cardinal roll take the
    // envelope, a diagonal roll the plain tick. Null keeps
    // callers without a routing (the editor preview, tests)
    // on the plain tick for every direction change.
    onDpadDirectionChange: ((Int, Int) -> Unit)? = null,
    // Alpha.32 — the fps counter chip: shown only while dev
    // stats are on (fpsVisible), at its spec's centre. The
    // stats are the same once-a-second values the old top-bar
    // diagnostic line read; the lux is the live reading (the
    // caller passes null when there is none). These sit after
    // onDpadDirectionChange so the positional callers keep
    // working.
    fpsVisible: Boolean = false,
    stats: PlayerStats? = null,
    lux: Int? = null,
    drawPath: DrawPath? = null,
    edgeOn: Boolean = false,
    reconOn: Boolean = false,
    // Alpha.33 — the HUD pin chip: the pinned achievement
    // (title + live measured progress), at its spec's centre.
    // Null means nothing is pinned for this game, so no chip.
    // The unlock hook fires once per unlock pop, so the caller
    // can play the rarity haptic for the unlock's tier.
    hudAchievement: RaAchievement? = null,
    onUnlockShown: ((RaUnlock) -> Unit)? = null,
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
                // Alpha.25 — the popup is not a button: it
                // renders separately below, at its spec.
                ControlId.POPUP -> false
                // Alpha.32 — the fps counter is not a button
                // either: it renders separately below, at
                // its spec.
                ControlId.FPS -> false
                // Alpha.33 — the HUD pin chip is not a button
                // either: it renders separately below, at
                // its spec.
                ControlId.HUD -> false
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
                deadZone = deadZone,
                onStickStateChanged = onStickStateChanged,
                slowOn = slowOn,
                onFfDown = onFfDown, onFfUp = onFfUp, onSlowToggle = onSlowToggle,
                onRwDown = onRwDown, onRwUp = onRwUp,
                onDpadDirectionChange = onDpadDirectionChange,
            )
        }
    }
    // Alpha.25 — the unlock popup lives in the overlay now,
    // not in the game view: it floats over the game at its
    // spec's centre (fractions of the full screen, like every
    // other overlay item) and never resizes the game — the
    // same rule as the alpha.24 chip fix. The width is the
    // spec's, capped at 75% of the game width, so it cannot
    // run edge to edge the way the old full-width card did.
    // The popup itself still clears after 4s and hides while
    // paused (see RaUnlockPopup); this layer only places it.
    val popupSpec = layout.control(ControlId.POPUP)
    PlacedControl(
        cx = popupSpec.cx, cy = popupSpec.cy,
        widthDp = ControlLayout.popupWidthDp(popupSpec.sizeDp, gameWidthDp),
        heightDp = ControlLayout.POPUP_HEIGHT_DP,
        canvasWidthDp = canvasWidthDp, canvasHeightDp = canvasHeightDp,
        alpha = alpha, testTag = "ra-unlock-popup-placed",
    ) {
        RaUnlockPopup(paused = paused, onUnlockShown = onUnlockShown)
    }
    // Alpha.32 — the fps counter lives in the overlay now,
    // not in the top bar: a themed chip at its spec's centre
    // (fractions of the full screen, like every other overlay
    // item), shown only while dev stats are on. Its width is
    // the spec's — no game-width cap, so the diagnostic text
    // fits on one line (see FPS_MIN_WIDTH_DP). It shows
    // while paused too, like the old bar line did (dimmed
    // under the pause overlay, like the buttons) — the
    // numbers are the last live window then, same as before.
    if (fpsVisible) {
        val fpsSpec = layout.control(ControlId.FPS)
        PlacedControl(
            cx = fpsSpec.cx, cy = fpsSpec.cy,
            widthDp = fpsSpec.sizeDp.toFloat(),
            heightDp = ControlLayout.FPS_HEIGHT_DP,
            canvasWidthDp = canvasWidthDp, canvasHeightDp = canvasHeightDp,
            alpha = alpha, testTag = "fps-counter-placed",
        ) {
            FpsCounterChip(
                stats = stats,
                theme = theme,
                lux = lux,
                drawPath = drawPath,
                edgeOn = edgeOn,
                reconOn = reconOn,
            )
        }
    }
    // Alpha.33 — the HUD pin chip: the pinned achievement's
    // title + live progress bar, at its spec's centre
    // (fractions of the full screen, like every other overlay
    // item). Its width is the spec's — no game-width cap, so
    // the title fits (see HUD_MIN_WIDTH_DP). It shows only
    // while an achievement is pinned for this game and never
    // while paused, like the unlock popup: paused, the
    // achievements screen carries the state.
    val hud = hudAchievement
    if (hud != null && !paused) {
        val hudSpec = layout.control(ControlId.HUD)
        PlacedControl(
            cx = hudSpec.cx, cy = hudSpec.cy,
            widthDp = hudSpec.sizeDp.toFloat(),
            heightDp = ControlLayout.HUD_HEIGHT_DP,
            canvasWidthDp = canvasWidthDp, canvasHeightDp = canvasHeightDp,
            alpha = alpha, testTag = "ra-hud-chip-placed",
        ) {
            RaHudChip(achievement = hud)
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
    // Alpha.10 — which control lives in the left-hand slot.
    // The D-pad is the default, so existing callers (the
    // editor's tests, the Settings default editor) keep the
    // cross unless they pass the choice.
    leftControl: LeftControl = LeftControl.DPAD,
    // Alpha.11 — per-button character (see ControlOverlay):
    // the release tick, the roll tick, the light click, the
    // shoulder click. Defaults keep every existing caller on
    // the single press click. These sit after leftControl so
    // the positional callers keep working.
    onControlUp: () -> Unit = {},
    onDirectionTick: () -> Unit = onControlDown,
    onLightDown: () -> Unit = onControlDown,
    onShoulderDown: () -> Unit = onControlDown,
    // Alpha.14 — the joystick dead zone (see ControlOverlay):
    // the per-game override when it has one, otherwise the
    // global slider value. The D-pad never reads it.
    deadZone: Float = StickInput.DEAD_ZONE,
    // Alpha.16 — proportional steering (see ControlOverlay):
    // the stick's live state for the session. The editor
    // preview keeps the null default.
    onStickStateChanged: ((mask: Int, deflection: Float) -> Unit)? = null,
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
    // Alpha.27 — the D-pad envelope split (see
    // ControlOverlay): the routed direction callback.
    // Null falls back to the plain roll tick.
    onDpadDirectionChange: ((Int, Int) -> Unit)? = null,
) {
    val pressed = Color(theme.pressed)
    when (spec.id) {
        ControlId.DPAD -> when (leftControl) {
            LeftControl.DPAD -> DpadControl(
                held, paused, onInputChanged,
                // Alpha.27 — the split: the routed callback
                // when the caller supplies one, else the
                // plain roll tick for every direction change.
                onDirectionChange = onDpadDirectionChange ?: { _, _ -> onDirectionTick() },
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
                deadZone = deadZone,
                onStickStateChanged = onStickStateChanged,
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
        // Alpha.25 — the popup is not a button and never
        // reaches this function: ControlOverlay filters it
        // out of the button loop and LayoutEditor branches
        // to EditorPopup. The branch exists because this
        // when is the function's last statement, so Kotlin
        // requires it to be exhaustive.
        ControlId.POPUP -> Unit
        // Alpha.32 — the fps counter is not a button either:
        // ControlOverlay filters it out of the button loop
        // and LayoutEditor branches to EditorFps, same as
        // the popup.
        ControlId.FPS -> Unit
        // Alpha.33 — the HUD pin chip is not a button either:
        // ControlOverlay filters it out of the button loop
        // and LayoutEditor branches to EditorHud, same as
        // the popup and the fps counter.
        ControlId.HUD -> Unit
    }
}
