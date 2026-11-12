package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 6 (item 6) — the input display: a row of small pills
 * above the game view showing the buttons currently held, for
 * recordings and streams. Pressed is the icon purple with white
 * lettering; released is dark chrome with dim lettering. It
 * shows the merged mask (touch + gamepad) — the same mask the
 * core sees — so what it shows is what the game receives.
 *
 * A pure function of [mask] and [isGba]: the player owns the
 * mask state and passes it in on every input change.
 */
@Composable
fun InputDisplay(
    mask: Int,
    isGba: Boolean,
    modifier: Modifier = Modifier,
) {
    val pills = listOf(
        "up" to InputMask.JOY_UP,
        "down" to InputMask.JOY_DOWN,
        "left" to InputMask.JOY_LEFT,
        "right" to InputMask.JOY_RIGHT,
        "a" to InputMask.JOY_A,
        "b" to InputMask.JOY_B,
        "start" to InputMask.JOY_START,
        "select" to InputMask.JOY_SELECT,
    ) + if (isGba) listOf("l" to InputMask.JOY_L, "r" to InputMask.JOY_R) else emptyList()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("input-display")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No spacer after the last pill: with all ten GBA pills
        // the row must fit a 320dp window (the Robolectric test
        // window is portrait-narrow; the player itself is
        // landscape and wider, but the row fits either way).
        pills.forEachIndexed { index, (id, bit) ->
            InputPill(id = id, pressed = mask and (1 shl bit) != 0)
            if (index < pills.lastIndex) {
                Spacer(Modifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun InputPill(id: String, pressed: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (pressed) PoingPurpleDeep else Color(0xFF2A2A2A))
            .testTag(if (pressed) "input-pressed-$id" else "input-released-$id"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (id) {
                "up" -> "↑"
                "down" -> "↓"
                "left" -> "←"
                "right" -> "→"
                "a" -> "A"
                "b" -> "B"
                "start" -> "St"
                "select" -> "Se"
                "l" -> "L"
                "r" -> "R"
                else -> id
            },
            fontSize = 12.sp,
            color = if (pressed) Color.White else Color(0xFF9E9EA5),
        )
    }
}
