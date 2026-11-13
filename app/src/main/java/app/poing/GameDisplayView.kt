package app.poing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
    bitmap: Bitmap?,
    stats: PlayerStats?,
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
    // Player chrome (Robert, 2026-11-12): a tap on the game
    // area brings the hidden top bar back for a few seconds.
    // The detector sits on this outer Box — the whole player
    // area — so a tap on a control (a sibling overlay, not a
    // child of this Box) can never reach it, and the bar only
    // peeks for a tap on the game itself.
    onGameAreaTap: () -> Unit = {},
    // The layout weight lives in the caller's Column (a
    // ColumnScope modifier cannot be built inside this
    // composable, so it arrives as a plain modifier).
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("game-area")
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onGameAreaTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        val frame = bitmap
        val s = stats
        // Worm Light realism (Robert, 2026-11-12): when the
        // lamp goes off, the pool resets to intensity 0 so it
        // never lingers on the picture. (While it is on, the
        // overlay reports its pool on every move — see below.)
        LaunchedEffect(wormLight) {
            if (!wormLight) glView?.setWormLight(WormLightPool.OFF)
        }
        if (frame != null && s != null) {
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
            // Player chrome (Robert, 2026-11-12): a tap on the
            // game area brings the hidden top bar back for a few
            // seconds. The detector sits on this outer Box — the
            // whole player area. A tap on a control can never
            // reach it: the controls are siblings overlaid on
            // top of this Box, not children of it, so a tap on
            // one is not in this Box's hit path. Only a tap on
            // the bare game area (or the black bars beside it)
            // peeks the bar.
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
                            .aspectRatio(s.width.toFloat() / s.height.toFloat())
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (glView != null) {
                            AndroidView(
                                factory = { glView },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = "Game: $gameName",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RaUnlockPopup(paused = paused)
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
                gameAspect = s.width.toFloat() / s.height.toFloat(),
                drawPool = glView == null,
                onPoolChange = { pool -> glView?.setWormLight(pool) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Loading $gameName…", color = Color.White)
        }
    }
}
