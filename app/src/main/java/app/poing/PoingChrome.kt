package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phase 5 — the Pocket + Atomic chrome: translucent purple panels,
 * the faint rainbow sheen, and the power light.
 *
 * The Atomic look lives ONLY in menus and settings sheets (and
 * the pause panel): translucent deep purple over the black
 * background. The library itself stays solid and readable — it is
 * never washed in purple. Gold stays an accent (rules, the power
 * light ring, selected states), never a second primary.
 */

/**
 * The Atomic translucent purple panel, with the faint rainbow
 * sheen inside it. The sheen is the Atomic shell's iridescence —
 * five rainbow stops at 5% alpha, horizontal — kept faint on
 * purpose so text on the panel stays readable. If the sheen ever
 * fights legibility, this is the one number to pull back.
 */
@Composable
fun AtomicPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PoingPurpleDeep.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(RainbowSheenBrush, RoundedCornerShape(16.dp)),
        )
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private val RainbowSheenBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFF5252).copy(alpha = 0.05f),
        Color(0xFFFFB300).copy(alpha = 0.05f),
        Color(0xFF69F0AE).copy(alpha = 0.05f),
        Color(0xFF40C4FF).copy(alpha = 0.05f),
        Color(0xFFE040FB).copy(alpha = 0.05f),
    ),
)

/** The power light's states: green while a game is playing, amber while paused. */
enum class PowerState { Off, Playing, Paused }

/** The colour for a power-light state — pure, so it is unit-testable. */
fun powerLightColor(state: PowerState): Color = when (state) {
    PowerState.Off -> Color(0xFF3A3A3A)
    PowerState.Playing -> PoingPowerGreen
    PowerState.Paused -> PoingPowerAmber
}

/**
 * A small glowing dot: the solid dot over a soft halo of the same
 * colour. Off is a flat dark dot with no halo.
 */
@Composable
fun PowerLight(state: PowerState, modifier: Modifier = Modifier) {
    val color = powerLightColor(state)
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (state != PowerState.Off) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f)),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
