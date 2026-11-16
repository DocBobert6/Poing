package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * the native layer pushes the events. Alpha.22 adds the game
 * indicator (RaGameIndicator): the RA state chip under the top
 * bar — loaded count, unrecognised ROM, or a failed load with
 * a Retry. Alpha.24: the chip follows the top bar (GameScreen
 * gates it on barVisible), so it hides a few seconds into
 * gameplay with the bar instead of staying up all session.
 */
/**
 * Alpha.22 — the in-game RetroAchievements indicator: one small
 * gold chip under the top bar (next to the tracker line) that
 * shows the loaded game's RA state while playing, in the same
 * gold-on-dark chip style as the unlock popup. It is state, not
 * an event, so it never auto-clears on its own — but GameScreen
 * gates it on the top bar's visibility (alpha.24), so it hides
 * a few seconds into gameplay with the bar and comes back with
 * it on a back swipe. It is hidden while paused, like the
 * popup and the tracker (the Pause menu's Achievements entry
 * carries the state then), and hidden entirely when logged out
 * (logged out, achievements are simply inactive — no errors,
 * no nagging).
 *
 * Loaded shows the count ("RA 3/12", the same unlocked/total
 * the hub snapshots) — or "RA: no achievements" when the set
 * loaded but is genuinely empty. Unrecognised and Failed are
 * different chips, never the same line: an unrecognised ROM
 * has no set to retry for, while a failed load offers a manual
 * Retry on the chip itself (no auto-retry).
 */
@Composable
fun RaGameIndicator(paused: Boolean) {
    if (paused || !RaClient.loggedIn) return
    val state = RaClient.gameLoadState
    if (state == RaGameLoadState.Idle) return
    val failed = state == RaGameLoadState.Failed
    val text = when (state) {
        RaGameLoadState.Loading -> "RA: loading…"
        RaGameLoadState.Loaded -> {
            val total = RaClient.achievements.size
            if (total == 0) "RA: no achievements"
            else "RA ${RaClient.achievements.count { it.isUnlocked }}/$total"
        }
        RaGameLoadState.Unrecognised -> "RA: unrecognised ROM"
        RaGameLoadState.Failed -> "RA: load failed — Retry"
        RaGameLoadState.Idle -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .testTag("ra-indicator")
            .then(if (failed) Modifier.clickable { RaClient.retryLoadGame() } else Modifier)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFC9A227),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun RaUnlockPopup(paused: Boolean) {
    val unlock = RaClient.lastUnlock
    // The badge store is remembered for the popup's lifetime,
    // not per unlock — a missing badge leaves the placeholder
    // box, and the disk cache survives between popups.
    val badgeStore = rememberRaBadgeStore()
    LaunchedEffect(unlock) {
        if (unlock != null) {
            delay(4000)
            RaClient.clearUnlock()
        }
    }
    if (unlock != null && !paused) {
        // The badge comes from the refreshed list (onUnlocked
        // refreshes it before the popup shows), so the chip
        // carries the achievement's own image, not just its
        // name. Alpha.22 — the chip is richer: the description
        // joins the title and the points (the hub already shows
        // all three; the popup showed only two).
        val badgeUrl = RaClient.achievements.find { it.id == unlock.id }?.badgeUrl
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 8.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            RaBadgeImage(
                store = badgeStore,
                url = badgeUrl,
                size = 32.dp,
                contentDescription = "Badge for ${unlock.title}",
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Achievement Unlocked: ${unlock.title} (+${unlock.points} pts)",
                    color = Color(0xFFC9A227),
                    fontWeight = FontWeight.Bold,
                )
                if (unlock.description.isNotEmpty()) {
                    Text(
                        text = unlock.description,
                        color = Color(0xFFC9C9CE),
                        fontSize = 13.sp,
                    )
                }
            }
        }
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
