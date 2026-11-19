package app.poing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 6 (item 8c) — the achievements list for the game being
 * played, reached from the pause menu while the game is paused.
 * The list refreshes when the screen opens; an unlock while the
 * screen is open refreshes it too (RaClient.onUnlocked refreshes
 * the list on every unlock). Only unlocked and active
 * achievements are shown — inactive ones (unearnable in this
 * game) are hidden. Measured achievements carry their progress
 * ("3/5"); unlocked ones are gold, active ones white.
 *
 * The header carries the game's rich presence line when the game
 * has one — what RetroAchievements says the player is doing
 * right now. A game RA has not recognised, or a logged-out user,
 * gets a plain explanation instead of an empty list. Each row
 * carries the achievement's badge (the unlocked badge once
 * earned, the locked one before that), fetched on demand behind
 * the cover-art network gate and cached on the device.
 *
 * Full-screen look like the Cheats screen: black, DotGothic16
 * title, silver body text.
 */
@Composable
fun RaAchievementsScreen(
    gameName: String,
    onBack: () -> Unit,
    // Alpha.33 — the HUD pin: the pinned achievement's id
    // for this game (null when nothing is pinned) and the
    // toggle that pins/unpins it. Both default to the old
    // behaviour (no pin UI) so existing callers keep working.
    pinnedAchievementId: Int? = null,
    onTogglePin: ((Int) -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        RaClient.refreshAchievements()
        RaClient.refreshRichPresence()
    }

    val loggedIn = RaClient.loggedIn
    val gameLoaded = RaClient.gameLoaded
    val title = RaClient.gameTitle ?: gameName
    val achievements = RaClient.achievements
        .filter { it.state != RaAchievements.STATE_INACTIVE }
    // Alpha.31 — the loaded game's guide URL (null when RA
    // supplies none, which is usual): Search for Help opens it
    // when it exists and falls back to a web search otherwise.
    val guideUrl = RaClient.guideUrl
    // Alpha.31 — closest-first sort, current game only: locked
    // achievements ordered by measured percent descending, then
    // unlocked in their server order. Off by default (server
    // order); session-only, like the rest of this screen.
    var closestFirst by remember { mutableStateOf(false) }
    val displayed = if (!closestFirst) {
        achievements
    } else {
        RaAchievements.closestFirstOrder(achievements)
    }
    val richPresence = RaClient.richPresence
    // Alpha.23 — rarity tiers come from the web API (a stored
    // key + the network gate), fetched once per loaded game.
    // The fetch is gated on Idle/Failed, so a Loaded state a
    // test (or an earlier open) already filled is kept, never
    // clobbered by an Unavailable verdict on re-entry.
    val context = LocalContext.current
    val networkAvailable = remember {
        isCoverArtNetworkAvailable(
            context,
            AppSettingsStore(context.filesDir).coverArtMobileDataAllowed(),
        )
    }
    LaunchedEffect(gameLoaded, RaClient.gameId) {
        if (gameLoaded && RaClient.gameId > 0 &&
            (RaClient.rarityState == RaWebApiState.Idle ||
                RaClient.rarityState == RaWebApiState.Failed)
        ) {
            RaClient.refreshRarity(RaClient.gameId, networkAvailable)
        }
    }
    // One badge store for the screen — the rows share it, so a
    // badge decoded for one row is already there for the next.
    val badgeStore = rememberRaBadgeStore()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("ra-achievements-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Achievements",
                style = displayTextStyle(DisplaySizeLarge),
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
            )
            if (gameLoaded) {
                Text(
                    text = RaAchievements.summary(RaClient.achievements),
                    fontSize = 14.sp,
                    color = Color(0xFFC9C9CE),
                )
            }
            if (richPresence.isNotEmpty()) {
                Text(
                    text = richPresence,
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EA5),
                )
            }
        }
        when {
            !loggedIn -> {
                item {
                    Text(
                        text = "Log in to RetroAchievements in Settings to see achievements.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
            }
            !gameLoaded -> {
                item {
                    // Alpha.22 — the three ways a game can have
                    // no loaded set are three different messages,
                    // never the same line: an unrecognised ROM
                    // (the server has no set for its hash) is
                    // not a failed load (which offers a Retry)
                    // and neither is a load still in flight.
                    // The genuinely empty set — loaded, but no
                    // achievements — is the branch below, not
                    // this one.
                    when (RaClient.gameLoadState) {
                        RaGameLoadState.Unrecognised -> {
                            Text(
                                text = "RetroAchievements doesn't recognise this ROM, so it has no achievement set.",
                                fontSize = 14.sp,
                                color = Color(0xFFC9C9CE),
                            )
                        }
                        RaGameLoadState.Failed -> {
                            Column {
                                Text(
                                    text = "RetroAchievements couldn't load this game's achievements.",
                                    fontSize = 14.sp,
                                    color = Color(0xFFC9C9CE),
                                )
                                TextButton(
                                    onClick = { RaClient.retryLoadGame() },
                                    modifier = Modifier.testTag("ra-retry-load"),
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                        RaGameLoadState.Loading -> {
                            Text(
                                text = "Loading achievements…",
                                fontSize = 14.sp,
                                color = Color(0xFFC9C9CE),
                            )
                        }
                        else -> {
                            Text(
                                text = "RetroAchievements hasn't loaded this game yet.",
                                fontSize = 14.sp,
                                color = Color(0xFFC9C9CE),
                            )
                        }
                    }
                }
            }
            achievements.isEmpty() -> {
                item {
                    Text(
                        text = "This game has no achievements.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
            }
            else -> {
                item {
                    TextButton(
                        onClick = { closestFirst = !closestFirst },
                        modifier = Modifier.testTag("ra-sort-closest"),
                    ) {
                        Text(if (closestFirst) "Closest first ✓" else "Closest first")
                    }
                }
                items(displayed, key = { it.id }) { achievement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ra-achievement-${achievement.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RaBadgeImage(
                            store = badgeStore,
                            url = achievement.badgeUrlForDisplay(),
                            size = 40.dp,
                            contentDescription = "Badge for ${achievement.title}",
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = achievement.title,
                                fontSize = 15.sp,
                                color = if (achievement.isUnlocked) Color(0xFFC9A227) else Color.White,
                            )
                            Text(
                                text = achievement.description,
                                fontSize = 13.sp,
                                color = Color(0xFFC9C9CE),
                            )
                            Text(
                                text = buildString {
                                    append("${achievement.points} pts")
                                    if (achievement.isUnlocked) append(" — Unlocked")
                                    achievement.measuredProgress?.let { append(" — $it") }
                                    // Alpha.23 — the rarity tier,
                                    // once the web-API fetch has
                                    // landed: "Rare · 12%". No key
                                    // (or a failed fetch) means no
                                    // rarity line at all, never an
                                    // error.
                                    if (RaClient.rarityState == RaWebApiState.Loaded) {
                                        RaClient.rarityByAchievement[achievement.id]?.let { rarity ->
                                            append(" — ${rarity.tier.label} · ${formatRarityPercent(rarity.percent)}")
                                        }
                                    }
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF9E9EA5),
                            )
                            // Alpha.31 — live progress bars: only
                            // where the achievement reports measured
                            // progress; the rest get no bar, never
                            // an invented percentage. The value is
                            // refreshed on screen open, on unlock,
                            // and live during play by the
                            // progress-indicator event.
                            if (achievement.measuredProgress != null) {
                                val fraction = ((achievement.measuredPercent ?: 0.0) / 100.0)
                                    .coerceIn(0.0, 1.0).toFloat()
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .testTag("ra-progress-bar-${achievement.id}"),
                                    color = Color(0xFFC9A227),
                                    trackColor = Color(0xFF3A3A3A),
                                )
                            }
                        }
                        // Alpha.33 — the HUD pin, locked +
                        // measured rows only: only a measured
                        // achievement has progress to track, so
                        // unmeasured rows get no pin action at
                        // all. One pin per game — pinning a
                        // second achievement replaces the first,
                        // and tapping the pinned row unpins it.
                        if (!achievement.isUnlocked &&
                            achievement.measuredProgress != null &&
                            onTogglePin != null
                        ) {
                            val pinned = pinnedAchievementId == achievement.id
                            TextButton(
                                onClick = { onTogglePin(achievement.id) },
                                modifier = Modifier.testTag("ra-pin-${achievement.id}"),
                            ) {
                                Text(if (pinned) "Pinned ✓" else "Pin")
                            }
                        }
                        // Alpha.31 — Search for Help, locked rows
                        // only: the game's guide URL when RA
                        // supplies one, else a web search for the
                        // game + achievement. User-initiated; it
                        // sends nothing but the query asked for.
                        if (!achievement.isUnlocked) {
                            TextButton(
                                onClick = {
                                    val url = guideUrl
                                        ?: RaWebApi.helpSearchUrl(title, achievement.title)
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                    )
                                },
                                modifier = Modifier.testTag("ra-help-${achievement.id}"),
                            ) {
                                Text("Help")
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
