package app.poing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    stats: PlayerStats?,
    recording: Boolean,
    recordingElapsed: Long,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    // Screen Optics dim fix (Robert, 2026-11-12): the live lux
    // for the diagnostic line, shown only when dev stats are
    // on (the caller passes null otherwise). Null means no
    // reading yet, or dev stats off.
    lux: Int? = null,
    // Edge smoothing, item 5 (2026-11-14): which program
    // actually drew the last frame, for the diagnostic
    // line. Null until the first stats — no token then,
    // rather than a guessed one.
    drawPath: DrawPath? = null,
    edgeOn: Boolean = false,
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
        // this spacer keeps the diagnostic clear of it.
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
        DiagnosticLine(stats = stats, lux = lux, drawPath = drawPath, edgeOn = edgeOn, modifier = Modifier.weight(1f))
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
    recordingElapsed: Long,
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
                text = "● REC ${recordingElapsed / 60}:${"%02d".format(recordingElapsed % 60)}",
                color = Color.Red,
            )
        }
    }
}

/**
 * The diagnostic overlay line: terminal face (VT323) for the
 * labels, segment digits (DSEG7) for the numbers — frame size,
 * core fps, measured fps, frames drawn. The numbers are always
 * segment, never the display face. With dev stats on, the live
 * ambient-light reading joins them (whole lux), so the room's
 * light — and whether the dim engages — is visible on the
 * screen itself (Screen Optics dim fix, Robert, 2026-11-12).
 * Edge smoothing, item 5 (2026-11-14): which program actually
 * drew the last frame joins them too ("edge", "edge+grid",
 * "chain", "plain") — so on/off is never a guess on device
 * again. When edge was requested but the frame came out
 * plain, the token says so outright ("plain (edge fell
 * back)"): the silent fallback, named.
 */
@Composable
private fun DiagnosticLine(stats: PlayerStats?, lux: Int? = null, drawPath: DrawPath? = null, edgeOn: Boolean = false, modifier: Modifier = Modifier) {
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
            if (drawPath != null) {
                withStyle(SpanStyle(fontFamily = Vt323)) { append(" ") }
                withStyle(SpanStyle(fontFamily = Vt323)) { append(drawPath.displayLabel(edgeOn)) }
            }
            if (lux != null) {
                withStyle(SpanStyle(fontFamily = Vt323)) { append(" ") }
                withStyle(SpanStyle(fontFamily = Dseg7)) { append("$lux") }
                withStyle(SpanStyle(fontFamily = Vt323)) { append("lx") }
            }
        },
        style = terminalTextStyle(),
        color = Color.White,
        // One line, always: with the L/R tabs in this bar the
        // diagnostic has little width in portrait, and wrapping
        // would grow the bar and eat game height.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
