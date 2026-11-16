package app.poing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Phase 8 — the game display, extracted from GameScreen so
 * the player screen stays under the dex guard (HowlingDog
 * v1.3.0 lesson). The game view fills its slot edge-to-edge
 * at the native aspect, the way HowlingDog presents its
 * game: no bezel chrome around the frame (Robert, 2026-11-12
 * — the silver border and dark surround are gone), and no
 * corner rounding at all (Robert, 2026-11-12: a Game Boy
 * screen is a rectangle — rounding doesn't fit Game Boy).
 * The game IS the screen — no whole handheld drawn around it.
 *
 * The frame goes through the display chain ([glView]) when
 * one is running; when the GL view could not be built (a
 * device without GLES 2.0, or the Robolectric startup gate),
 * the plain Compose Image draws the frame exactly as the
 * core rendered it — the game stays visible either way.
 * Every handheld LCD here is flat.
 *
 * The RetroAchievements unlock popup and scoreboard note
 * sit over the game area, hidden while paused, each cleared
 * four seconds after it arrives.
 */
@Composable
fun GameDisplayView(
    glView: GameGlView?,
    // Performance fix 1 (2026-11-14): the Bitmap is Compose
    // state only on the no-GL fallback path, where the plain
    // Compose Image draws it. On the GL path the frame goes
    // straight to the GL view and only its ASPECT reaches
    // composition, so the player screen does not re-execute
    // on every frame.
    bitmap: Bitmap?,
    frameAspect: Float? = null,
    gameName: String,
    paused: Boolean,
    // Phase 8 — Screen Optics: the Virtual Worm Light, an
    // easter egg. Off by default; when on, its overlay sits
    // over the game area only (never over the flank controls
    // beside it).
    wormLight: Boolean = false,
    // Worm Light realism (Robert, 2026-11-12): the colour of
    // the lamp's hardware — cord, head plastic, plug. Dark
    // medium gray by default; the light itself stays warm
    // white and is never a setting.
    wormLightColor: WormLightColor = WormLightColor.DEFAULT,
    // Alpha.10 — the coverage: how wide the pool spreads,
    // 0.75x–1.75x, 1x by default. Global and persisted.
    wormLightCoverage: Float = 1f,
    // Worm Light improvements (Robert, 2026-11-13): while a
    // drag is in progress the overlay reports its head, so
    // the player can draw it above the controls — a dragged
    // head never disappears behind the D-pad.
    onWormLightDragHeadChange: (WormLightDragHead?) -> Unit = {},
    // The layout weight lives in the caller's Column (a
    // ColumnScope modifier cannot be built inside this
    // composable, so it arrives as a plain modifier).
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("game-area"),
        contentAlignment = Alignment.Center,
    ) {
        // Performance fix 1 (2026-11-14): the game shows on
        // the frame aspect — from the GL path's state, or from
        // the fallback Bitmap when there is one. The fallback
        // ImageBitmap is hoisted so a recomposition does not
        // re-wrap the Bitmap per pass.
        val aspect = frameAspect
            ?: bitmap?.let { it.width.toFloat() / it.height.toFloat() }
        val fallbackImage = remember(bitmap) { bitmap?.asImageBitmap() }
        // Black screen on launch (Robert, 2026-11-14): the view
        // used to compose only once the once-a-second stats had
        // arrived (frame != null && stats != null), so the game
        // launched black with audio for ~1s behind a small
        // "Loading…" line — and the picture appearing after a
        // button press was only a timing coincidence inside that
        // window (no button handler touches the GL view or the
        // render loop). The view now composes on the first
        // frame alone; the aspect comes from the frame (the
        // GL path's aspect state, or the fallback Bitmap),
        // and only the dev-stats diagnostic line (in the
        // top bar, which already tolerates null stats) waits
        // for the stats.
        // Worm Light realism (Robert, 2026-11-12): when the
        // lamp goes off, the pool resets to intensity 0 so it
        // never lingers on the picture. (While it is on, the
        // overlay reports its pool on every move — see below.)
        LaunchedEffect(wormLight) {
            if (!wormLight) glView?.setWormLight(WormLightPool.OFF)
        }
        // Belt & braces for the same launch: one explicit render
        // request once the view is composed, so the first draw
        // cannot be lost if the initial WHEN_DIRTY render is
        // consumed before the surface exists. The mode stays
        // RENDERMODE_WHEN_DIRTY (battery) — this is one request,
        // not a continuous loop.
        LaunchedEffect(glView) {
            glView?.requestRender()
        }
        if (aspect != null) {
            // No bezel (Robert, 2026-11-12): the silver 2dp
            // border, the dark surround and the 4dp inner padding
            // are gone, so the frame fills the aspect box
            // edge-to-edge, the way HowlingDog presents its game.
            // No corner rounding either (Robert, 2026-11-12:
            // a Game Boy screen is a rectangle) — the frame's
            // corners are square. (The screen-size fix already
            // dropped the 8dp transparent margin outside the box,
            // the same class of dead space.)
            //
            // Top bar / back swipe (Robert, 2026-11-12): a
            // tap on the game area does NOTHING to the bar —
            // the bar is only reachable by a back swipe (see
            // GameScreen). There is no tap detector here at
            // all, so a tap on the bare game area (or the
            // black bars beside it) can never peek it.
            //
            // Worm Light realism (Robert, 2026-11-12): while
            // the lamp is on, the game gives up a strip at the
            // top — the lamp's space, where the head hangs
            // above the picture. The strip is a layout element
            // above the game row, so the frame stays the largest
            // aspect box in the row below it (never stretched),
            // and the lamp overlay spans the whole player area
            // (strip + game) as a sibling above the Column, so
            // the coil can come in from the player's left edge.
            // When the lamp is off there is no strip and the
            // game takes the full height back, exactly as
            // before.
            Column(modifier = Modifier.fillMaxSize()) {
                if (wormLight) {
                    Spacer(modifier = Modifier.height(WORM_LIGHT_STRIP_DP.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(aspect)
                            .fillMaxSize()
                            .testTag("game-aspect-box"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (glView != null) {
                            AndroidView(
                                factory = { glView },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (fallbackImage != null) {
                            Image(
                                bitmap = fallbackImage,
                                contentDescription = "Game: $gameName",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        // No GL view and no fallback Bitmap:
                        // cannot happen in production (the
                        // fallback path always carries a
                        // Bitmap); the box stays black.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RaScoreboardNote(paused = paused)
                            }
                        }
                    }
                }
            }
            // Worm Light realism (Robert, 2026-11-12): the
            // lamp itself — the coil, the oval lens and the
            // pool (see WormLightOverlay). The pool reaches
            // the GL view as uniforms, so dragging the head
            // while paused still moves the pool; with no GL
            // view (the fallback Image) the overlay draws
            // the pool itself.
            WormLightOverlay(
                enabled = wormLight,
                hardwareColor = wormLightColor,
                gameAspect = aspect,
                coverage = wormLightCoverage,
                drawPool = glView == null,
                onPoolChange = { pool -> glView?.setWormLight(pool) },
                onDragHeadChange = onWormLightDragHeadChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Loading $gameName…", color = Color.White)
        }
    }
}
