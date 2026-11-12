package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phase 6 (item 8c) — the RetroAchievements live notes over the
 * game: the unlock popup, the leaderboard scoreboard note, and
 * the active attempt tracker. Extracted from GameScreen so the
 * player screen itself stays under the dex guard.
 *
 * The unlock popup and the scoreboard note are gold chips over
 * the game area, hidden while paused, each cleared four seconds
 * after it arrives (the effect restarts when a new one arrives,
 * so a second unlock re-arms the timer). The tracker is the live
 * attempt value for the board being played ("Attempt: …"),
 * shown under the top bar while an attempt runs and hidden while
 * paused. All three are reads of RaClient's state — no polling,
 * the native layer pushes the events.
 */
@Composable
fun RaUnlockPopup(paused: Boolean) {
    val unlock = RaClient.lastUnlock
    LaunchedEffect(unlock) {
        if (unlock != null) {
            delay(4000)
            RaClient.clearUnlock()
        }
    }
    if (unlock != null && !paused) {
        Text(
            text = "Achievement Unlocked: ${unlock.title} (+${unlock.points} pts)",
            color = Color(0xFFC9A227),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 8.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun RaScoreboardNote(paused: Boolean) {
    val scoreboard = RaClient.lastScoreboard
    LaunchedEffect(scoreboard) {
        if (scoreboard != null) {
            delay(4000)
            RaClient.clearScoreboard()
        }
    }
    if (scoreboard != null && !paused) {
        Text(
            text = "Score submitted: ${scoreboard.submittedScore} — Rank #${scoreboard.newRank} of ${scoreboard.numEntries}",
            color = Color(0xFFC9A227),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 8.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun RaTrackerLine(paused: Boolean) {
    val boardId = RaClient.activeTrackerBoardId
    val display = RaClient.activeTrackerDisplay
    if (boardId != 0 && display.isNotEmpty() && !paused) {
        Text(
            text = "Attempt: $display",
            style = terminalTextStyle(),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
