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
import kotlin.math.min

/**
 * Phase 8 — Screen Optics: the Virtual Worm Light. A small
 * toggle only, an easter egg about a bad screen, on a phone
 * that already has a backlight — not a headline feature.
 *
 * A draggable warm spotlight cone across the virtual screen,
 * with a subtle plastic-lens reflection. The flexible neck
 * runs from the top edge to the light head, and dragging the
 * head angles it — including away from the HUD, which lives
 * below this overlay. The position is a fraction of the
 * screen, so a rotation keeps the light where it was put.
 *
 * Nothing when off: the overlay is not composed at all, so
 * it cannot swallow a touch.
 */
@Composable
fun WormLightOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var position by remember { mutableStateOf(Offset(0.5f, 0.35f)) }
    BoxWithConstraints(modifier = modifier) {
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
            val cx = position.x * size.width
            val cy = position.y * size.height
            val radius = min(size.width, size.height) * 0.55f
            // The warm spotlight cone, fading out from the head.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFC978).copy(alpha = 0.38f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                center = Offset(cx, cy),
                radius = radius,
            )
            // The flexible neck, from the top edge to the head.
            val neck = Path().apply {
                moveTo(size.width * 0.5f, 0f)
                quadraticBezierTo(size.width * 0.5f, cy * 0.5f, cx, cy)
            }
            drawPath(neck, Color(0xFF8A8A8A), style = Stroke(width = 6f))
            // The lens: a small warm disc, with a faint
            // plastic-lens reflection arc at its top-left.
            drawCircle(Color(0xFFFFE0A0), radius = 26f, center = Offset(cx, cy))
            drawArc(
                color = Color.White.copy(alpha = 0.5f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                useCenter = false,
                topLeft = Offset(cx - 26f, cy - 26f),
                size = Size(52f, 52f),
                style = Stroke(width = 3f),
            )
        }
    }
}
