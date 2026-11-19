package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Player chrome (Robert, 2026-11-12) — the top bar, extracted
 * from GameScreen so the player stays under the dex guard
 * (HowlingDog v1.3.0 lesson).
 *
 * The bar hides during active gameplay unless the dev-stats
 * toggle is on; the power light never hides. So the light
 * does not live in the bar any more: it is a floating status
 * at the top-left corner, always composed, and the bar Row
 * leaves a 28dp space for it (the 20dp light + the 8dp gap
 * the old Row had between the light and the diagnostic).
 * The REC indicator rides with whichever is showing: in the
 * bar Row while the bar is visible, alongside the floating
 * light while it is hidden — it is a status indicator like
 * the light, not stats.
 *
 * statusBarsPadding on both: the player is edge-to-edge, so
 * without it the light sits in the status-bar band (the
 * power-light fix, 2026-11-12).
 */

/** How long a tap on the game area keeps the hidden bar up. */
const val TOP_BAR_PEEK_MS = 5000L
@Composable
fun TopBarRow(
    recording: Boolean,
    recordingElapsed: Long,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The floating light sits here (see FloatingPlayerStatus);
        // this spacer keeps the REC indicator clear of it.
        Spacer(Modifier.width(28.dp))
        // Phase 6 (item 11) — the REC indicator while a
        // recording runs: a red dot + elapsed m:ss.
        if (recording) {
            Text(
                text = "● REC ${recordingElapsed / 60}:${"%02d".format(recordingElapsed % 60)}",
                color = Color.Red,
            )
            Spacer(Modifier.width(8.dp))
        }
        // Alpha.32 — the diagnostic line no longer lives
        // here: it is the fps counter chip in the overlay
        // (see FpsCounterChip), themed per system and
        // movable in the layout editor while dev stats are
        // on. The bar keeps only Pause/Resume + REC.
        // The tag lets tests tell the bar's button apart from
        // the pause menu's own Resume button (same text, two
        // nodes when paused).
        Button(
            onClick = onButtonClick,
            modifier = Modifier.testTag("top-bar-button"),
        ) {
            Text(buttonLabel)
        }
    }
}

/**
 * The power light, detached from the bar: green while a game
 * is playing, amber while paused — the Pocket's power LED.
 * Always composed, at the top-left corner; the REC indicator
 * joins it only while the bar is hidden (while the bar is
 * visible, REC lives in the bar Row instead).
 */
@Composable
fun FloatingPlayerStatus(
    paused: Boolean,
    recording: Boolean,
    // Performance fix 9 — the clock as a State, not a Long:
    // this composable is always composed at the root Box, so
    // a Long would re-execute the whole GameScreen scope on
    // every 1s tick. Reading .value inside the REC Text scopes
    // the recomposition to that tiny Row. (TopBarRow keeps its
    // Long: it is composed only while the bar is visible.)
    recordingElapsedState: State<Long>,
    barVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PowerLight(
            if (paused) PowerState.Paused else PowerState.Playing,
            modifier = Modifier.testTag("power-light"),
        )
        if (recording && !barVisible) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "● REC ${recordingElapsedState.value / 60}:${"%02d".format(recordingElapsedState.value % 60)}",
                color = Color.Red,
            )
        }
    }
}

/**
 * Alpha.32 — the fps counter chip (Robert, 2026-11-19): the
 * dev-stats diagnostic line, lifted out of the top bar into
 * its own overlay item, so it can be themed per system and
 * moved/resized in the layout editor while dev stats are on.
 *
 * The content is unchanged from the old bar line: terminal
 * face (VT323) for the labels, segment digits (DSEG7) for
 * the numbers — frame size, core fps, measured fps, frames
 * drawn. With dev stats on, the live ambient-light reading
 * joins them (whole lux), so the room's light — and whether
 * the dim engages — is visible on the screen itself (Screen
 * Optics dim fix, Robert, 2026-11-12). Edge smoothing, item
 * 5 (2026-11-14): which program actually drew the last
 * frame joins them too ("edge", "edge+grid", "chain",
 * "plain") — so on/off is never a guess on device again.
 * When edge was requested but the frame came out plain, the
 * token says so outright ("plain (edge fell back)"): the
 * silent fallback, named.
 *
 * The chip's colours come from the system's theme: the
 * theme's pressed colour behind white text (see
 * fpsChipColors) — so GB reads maroon, GBC purple, GBA
 * indigo. The chip is one line, always: wrapping would
 * grow it over the game.
 */
@Composable
fun FpsCounterChip(
    stats: PlayerStats?,
    theme: ControlThemes.Theme,
    // Screen Optics dim fix (Robert, 2026-11-12): the live
    // lux, shown only when dev stats are on (the caller
    // passes null otherwise). Null means no reading yet, or
    // dev stats off.
    lux: Int? = null,
    // Edge smoothing, item 5 (2026-11-14): which program
    // actually drew the last frame. Null until the first
    // stats — no token then, rather than a guessed one.
    drawPath: DrawPath? = null,
    edgeOn: Boolean = false,
    // Task 6 — the same, for the reconstruction: recon
    // requested but plain delivered reads "plain (recon
    // fell back)".
    reconOn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (background, textColor) = fpsChipColors(theme)
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("fps-counter"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fpsCounterText(stats, lux, drawPath, edgeOn, reconOn),
            style = terminalTextStyle(),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Alpha.32 — the chip's colours, from the system's theme:
 * the theme's pressed colour (one per system, see
 * ControlThemes) behind white text — the theme's own rule
 * (one pressed colour per system, label always white). Pure,
 * so the per-system mapping is unit-tested (FpsCounterTest).
 */
fun fpsChipColors(theme: ControlThemes.Theme): Pair<Color, Color> =
    Color(theme.pressed) to Color.White

/**
 * Alpha.32 — the editor's sample counter: the same chip
 * with sample data, so the position is visible while it is
 * dragged. The real chip only shows while dev stats are on
 * and never while paused — and the editor is always paused —
 * so without the sample the chip's spot would be invisible
 * while it is dragged (same shape as RaUnlockPopupSample).
 */
@Composable
fun FpsCounterSample(theme: ControlThemes.Theme) {
    FpsCounterChip(
        stats = PlayerStats(
            width = 160,
            height = 144,
            coreFps = 59.7,
            measuredFps = 58.9,
            framesDrawn = 12345,
            sampleRate = 48000,
        ),
        lux = 250,
        drawPath = DrawPath.EDGE_GRID,
        edgeOn = true,
        theme = theme,
        modifier = Modifier.testTag("fps-counter-sample"),
    )
}

/** The diagnostic text, as one annotated string. Null stats read "starting…". */
private fun fpsCounterText(
    stats: PlayerStats?,
    lux: Int? = null,
    drawPath: DrawPath? = null,
    edgeOn: Boolean = false,
    reconOn: Boolean = false,
) = buildAnnotatedString {
    if (stats == null) {
        withStyle(SpanStyle(fontFamily = Vt323)) { append("starting…") }
        return@buildAnnotatedString
    }
    withStyle(SpanStyle(fontFamily = Dseg7)) { append("${stats.width}x${stats.height}") }
    withStyle(SpanStyle(fontFamily = Vt323)) { append(" core ") }
    withStyle(SpanStyle(fontFamily = Dseg7)) { append("%.1f".format(stats.coreFps)) }
    withStyle(SpanStyle(fontFamily = Vt323)) { append("fps measured ") }
    withStyle(SpanStyle(fontFamily = Dseg7)) { append("%.1f".format(stats.measuredFps)) }
    withStyle(SpanStyle(fontFamily = Vt323)) { append("fps ") }
    withStyle(SpanStyle(fontFamily = Dseg7)) { append("${stats.framesDrawn}") }
    withStyle(SpanStyle(fontFamily = Vt323)) { append(" frames") }
    if (drawPath != null) {
        withStyle(SpanStyle(fontFamily = Vt323)) { append(" ") }
        withStyle(SpanStyle(fontFamily = Vt323)) { append(drawPath.displayLabel(edgeOn, reconOn)) }
    }
    if (lux != null) {
        withStyle(SpanStyle(fontFamily = Vt323)) { append(" ") }
        withStyle(SpanStyle(fontFamily = Dseg7)) { append("$lux") }
        withStyle(SpanStyle(fontFamily = Vt323)) { append("lx") }
    }
}
