package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * Alpha.25 — the unlock popup is slim and movable: one line
 * (badge + title + points), no description, placed by the
 * overlay layout (ControlId.POPUP) instead of pinned to the
 * top of the game view. The description left the popup
 * (Robert, 2026-11-16: the two-line card spanned nearly the
 * full game width and covered the game's HUD) — it still
 * lives in Pause → Achievements and the hub.
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
        // name.
        val badgeUrl = RaClient.achievements.find { it.id == unlock.id }?.badgeUrl
        UnlockPopupRow(
            badgeStore = badgeStore,
            badgeUrl = badgeUrl,
            title = unlock.title,
            points = unlock.points,
        )
    }
}

/**
 * Alpha.25 — the editor's sample popup: the same slim row
 * with sample data, so the position is visible while the
 * layout is edited. The real popup only shows on an unlock
 * and never while paused — and the editor is always paused —
 * so without the sample the popup's spot would be invisible
 * while it is dragged.
 */
@Composable
fun RaUnlockPopupSample() {
    val badgeStore = rememberRaBadgeStore()
    UnlockPopupRow(
        badgeStore = badgeStore,
        badgeUrl = null,
        title = "Sample Achievement",
        points = 5,
        modifier = Modifier.testTag("ra-unlock-popup-sample"),
    )
}

/**
 * Alpha.25 — the slim unlock row, one line: badge + title +
 * points. The description is gone (see the file doc): the
 * two-line card was too wide and covered the game's HUD.
 * The row fills the width its overlay spec gives it, and the
 * text ellipsizes inside that width, so a long title never
 * pushes the chip wider than its spec.
 */
@Composable
private fun UnlockPopupRow(
    badgeStore: RaBadgeStore,
    badgeUrl: String?,
    title: String,
    points: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        RaBadgeImage(
            store = badgeStore,
            url = badgeUrl,
            size = 32.dp,
            contentDescription = "Badge for $title",
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Achievement Unlocked: $title (+$points pts)",
            color = Color(0xFFC9A227),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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
