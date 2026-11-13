package app.poing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag

/**
 * Phase 8 — Screen Optics: the Virtual Worm Light. A small
 * toggle only, an easter egg about a bad screen, on a phone
 * that already has a backlight — not a headline feature.
 *
 * Reworked (Robert, 2026-11-12): the old light was a small
 * lens floating mid-picture with a radial glow circle around
 * it — it read as a spotlight ON the screen. The lamp is now
 * bigger and sits ABOVE the picture, like the real Worm Light
 * accessory clipped to the top of the Game Boy: a large lens
 * on the picture's top edge, a thick flexible neck reaching
 * up to it from the top edge, and a directional cone angled
 * DOWN from the head so the warm light pools on the game
 * from above, brightest where it lands and fading with the
 * angle. The lamp hardware sits outside the picture; the
 * light falls ON it.
 *
 * The game fills the player area's full height, so there is
 * no black space above the picture inside the player — the
 * head sits on the picture's top edge, not floating above
 * it in empty space (the only space outside the picture is
 * the side flanks, which hold the controls the light must
 * never cover). Dragging the head moves it and the cone
 * follows: the cone's base stays centred on the picture,
 * so a head dragged sideways angles the light across the
 * game. The position is a fraction of the screen, so a
 * rotation keeps the light where it was put.
 *
 * Nothing when off: the overlay is not composed at all, so
 * it cannot swallow a touch.
 */

// The default head: top edge, centred — clipped on, like
// the real accessory. (Was (0.5f, 0.35f), mid-picture.)
val WORM_LIGHT_DEFAULT_HEAD = Offset(0.5f, 0.12f)

// The lamp hardware, in dp. The lens was ~10dp (a 26f px
// disc at 2.625 density); it is now 32dp, about three times
// the size. The neck was ~2.3dp (a 6f px stroke); it is now
// 10dp, a thick flexible neck, not a thread.
const val WORM_LIGHT_LENS_RADIUS_DP = 32f
const val WORM_LIGHT_NECK_WIDTH_DP = 10f

// The cone: its base spans 84% of the picture width at 90%
// of its height, centred — the light pools on the game from
// above. A wider, fainter outer cone (98% at 95%) feathers
// the edges so the pool has no hard rim.
const val WORM_LIGHT_CONE_BASE_WIDTH = 0.84f
const val WORM_LIGHT_CONE_BASE_Y = 0.9f
const val WORM_LIGHT_OUTER_BASE_WIDTH = 0.98f
const val WORM_LIGHT_OUTER_BASE_Y = 0.95f

/**
 * The lamp's geometry in the picture's px space, pure so the
 * angles and sizes are unit-testable without a Canvas: the
 * head and lens, the neck width, and the cone — apex at the
 * head, base centred on the picture, opening downward.
 */
data class WormLightGeometry(
    val headPx: Offset,
    val lensRadiusPx: Float,
    val neckWidthPx: Float,
    val coneApex: Offset,
    val coneBaseLeft: Offset,
    val coneBaseRight: Offset,
    val coneBaseCenter: Offset,
    val outerBaseLeft: Offset,
    val outerBaseRight: Offset,
)

fun wormLightGeometry(
    boxWidth: Float,
    boxHeight: Float,
    head: Offset,
    density: Float,
): WormLightGeometry {
    val headPx = Offset(head.x * boxWidth, head.y * boxHeight)
    val baseHalf = boxWidth * WORM_LIGHT_CONE_BASE_WIDTH / 2f
    val outerHalf = boxWidth * WORM_LIGHT_OUTER_BASE_WIDTH / 2f
    val baseY = boxHeight * WORM_LIGHT_CONE_BASE_Y
    val outerY = boxHeight * WORM_LIGHT_OUTER_BASE_Y
    return WormLightGeometry(
        headPx = headPx,
        lensRadiusPx = WORM_LIGHT_LENS_RADIUS_DP * density,
        neckWidthPx = WORM_LIGHT_NECK_WIDTH_DP * density,
        coneApex = headPx,
        coneBaseLeft = Offset(boxWidth / 2f - baseHalf, baseY),
        coneBaseRight = Offset(boxWidth / 2f + baseHalf, baseY),
        coneBaseCenter = Offset(boxWidth / 2f, baseY),
        outerBaseLeft = Offset(boxWidth / 2f - outerHalf, outerY),
        outerBaseRight = Offset(boxWidth / 2f + outerHalf, outerY),
    )
}

@Composable
fun WormLightOverlay(
    enabled: Boolean,
    // Worm Light realism (Robert, 2026-11-12): the colour of
    // the lamp's hardware — cord, head plastic, plug. Dark
    // medium gray by default; the light itself stays warm
    // white and is never a setting.
    hardwareColor: WormLightColor = WormLightColor.DEFAULT,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var position by remember { mutableStateOf(WORM_LIGHT_DEFAULT_HEAD) }
    BoxWithConstraints(modifier = modifier.testTag("worm-light")) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val x = (position.x * width + dragAmount.x).coerceIn(0f, width)
                        val y = (position.y * height + dragAmount.y).coerceIn(0f, height)
                        position = Offset(x / width, y / height)
                    }
                },
        ) {
            val g = wormLightGeometry(size.width, size.height, position, density)
            // The outer cone first: wide and faint, feathering
            // the pool's edges so it has no hard rim.
            val outerCone = Path().apply {
                moveTo(g.coneApex.x, g.coneApex.y)
                lineTo(g.outerBaseLeft.x, g.outerBaseLeft.y)
                lineTo(g.outerBaseRight.x, g.outerBaseRight.y)
                close()
            }
            drawPath(
                outerCone,
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFC978).copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    start = g.coneApex,
                    end = Offset(size.width / 2f, size.height * WORM_LIGHT_OUTER_BASE_Y),
                ),
            )
            // The main pool: a directional wedge from the head
            // down onto the picture, warm at the head and fading
            // toward the base — brightest where the light lands,
            // fading with the angle. (The old radial glow circle
            // centred on the head is gone: that was the
            // spotlight-on-the-picture look.)
            val cone = Path().apply {
                moveTo(g.coneApex.x, g.coneApex.y)
                lineTo(g.coneBaseLeft.x, g.coneBaseLeft.y)
                lineTo(g.coneBaseRight.x, g.coneBaseRight.y)
                close()
            }
            drawPath(
                cone,
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFC978).copy(alpha = 0.32f),
                        Color.Transparent,
                    ),
                    start = g.coneApex,
                    end = g.coneBaseCenter,
                ),
            )
            // The flexible neck, from the top edge to the head —
            // in the hardware colour the player picked (dark
            // medium gray by default; clear renders faded).
            val neck = Path().apply {
                moveTo(size.width * 0.5f, 0f)
                quadraticTo(size.width * 0.5f, g.headPx.y * 0.5f, g.headPx.x, g.headPx.y)
            }
            drawPath(
                neck,
                Color(hardwareColor.argb).copy(
                    alpha = if (hardwareColor.translucent) 0.6f else 1f,
                ),
                style = Stroke(width = g.neckWidthPx),
            )
            // A soft glow around the head itself — the lens
            // spilling a little light where it sits.
            val glowRadius = g.lensRadiusPx * 1.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFC978).copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    center = g.headPx,
                    radius = glowRadius,
                ),
                center = g.headPx,
                radius = glowRadius,
            )
            // The lens: a large warm disc, with a faint
            // plastic-lens reflection arc at its top-left.
            drawCircle(Color(0xFFFFE0A0), radius = g.lensRadiusPx, center = g.headPx)
            drawArc(
                color = Color.White.copy(alpha = 0.5f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(g.headPx.x - g.lensRadiusPx, g.headPx.y - g.lensRadiusPx),
                size = Size(g.lensRadiusPx * 2f, g.lensRadiusPx * 2f),
                style = Stroke(width = g.lensRadiusPx * 0.12f),
            )
        }
    }
}
