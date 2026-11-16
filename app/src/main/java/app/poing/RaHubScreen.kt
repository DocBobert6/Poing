package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The RetroAchievements Achievement Center (hub + badges,
 * 2026-11-14): the account's achievements without launching a
 * game, reached from the library. Three parts:
 *
 * - the header: the logged-in username and the account's total
 *   softcore points (rc_client's user score — see
 *   [RaClient.userScoreSoftcore]; the number is read fresh when
 *   the hub opens, and is live without another server call
 *   because rc_client updates it on login and on every award);
 * - recent unlocks, most recent first, with the badge, the game,
 *   the points and the time;
 * - per-game progress, only for games with cached set data —
 *   title, X/Y, progress bar, points earned/total, and a
 *   "Completed" marker when X == Y.
 *
 * The hub never claims a never-loaded game has no achievements:
 * games appear here only after their first launch, and the
 * screen says so plainly. A logged-out user gets the same plain
 * explanation instead of an empty list.
 *
 * Full-screen look like the Achievements screen: black,
 * DotGothic16 title, silver body text.
 */
@Composable
fun RaHubScreen(
    username: String,
    totalPoints: Int?,
    hubStore: RaHubStore,
    badgeStore: RaBadgeStore,
    onBack: () -> Unit,
) {
    // Alpha.23 — the network gate for the web-API history
    // fetch, read once (the same gate the badges use).
    val context = LocalContext.current
    val networkAvailable = remember {
        isCoverArtNetworkAvailable(
            context,
            AppSettingsStore(context.filesDir).coverArtMobileDataAllowed(),
        )
    }
    LaunchedEffect(Unit) {
        RaClient.refreshUserScore()
        // Alpha.23 — the full unlock history, from the web API:
        // a stored key + the network gate. Fetched once (Idle)
        // or after a failure (Failed); a Loaded state is kept,
        // never clobbered by an Unavailable verdict on re-entry.
        // Without a key the fetch no-ops and the section below
        // simply does not appear — the hub keeps showing exactly
        // what it already showed.
        if (RaClient.historyState == RaWebApiState.Idle ||
            RaClient.historyState == RaWebApiState.Failed
        ) {
            RaClient.refreshUnlockHistory(networkAvailable)
        }
    }
    val data = remember { hubStore.load() }
    val loggedIn = username.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("ra-hub-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Achievements",
                style = displayTextStyle(DisplaySizeLarge),
                color = Color(0xFFC9C9CE),
            )
            if (loggedIn) {
                Text(
                    text = "Logged in as $username",
                    fontSize = 14.sp,
                    color = Color(0xFFC9C9CE),
                )
                if (totalPoints != null) {
                    Text(
                        text = "$totalPoints points",
                        fontSize = 14.sp,
                        color = Color(0xFFC9A227),
                    )
                }
            } else {
                Text(
                    text = "Log in to RetroAchievements in Settings to see your achievements.",
                    fontSize = 14.sp,
                    color = Color(0xFFC9C9CE),
                )
            }
        }
        if (loggedIn) {
            item {
                Text(
                    text = "Recent unlocks",
                    style = displayTextStyle(DisplaySizeSmall),
                    color = Color(0xFFC9C9CE),
                )
            }
            if (data.recent.isEmpty()) {
                item {
                    Text(
                        text = "No unlocks yet — play a game to earn your first achievement.",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9EA5),
                    )
                }
            } else {
                items(data.recent, key = { "${it.gameId}:${it.achievementId}" }) { unlock ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ra-hub-recent"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RaBadgeImage(
                            store = badgeStore,
                            url = unlock.badgeUrl,
                            size = 40.dp,
                            contentDescription = "Badge for ${unlock.title}",
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = unlock.title,
                                fontSize = 15.sp,
                                color = Color(0xFFC9A227),
                            )
                            Text(
                                text = "${unlock.gameTitle} — ${unlock.points} pts — ${formatUnlockTime(unlock.unlockedAt)}",
                                fontSize = 13.sp,
                                color = Color(0xFF9E9EA5),
                            )
                        }
                    }
                }
            }
            // Alpha.23 — the full unlock history, from the web
            // API: the account's unlocks, newest first, with
            // dates. It appears only when the fetch has landed;
            // without a stored API key the fetch never runs and
            // this section is absent — the hub keeps showing
            // exactly what it already showed. The local "Recent
            // unlocks" above stay as they are: the unlocks
            // earned in Poing, with their local timestamps.
            if (RaClient.historyState == RaWebApiState.Loaded &&
                RaClient.unlockHistory.isNotEmpty()
            ) {
                item {
                    Text(
                        text = "Unlock history",
                        style = displayTextStyle(DisplaySizeSmall),
                        color = Color(0xFFC9C9CE),
                    )
                }
                items(RaClient.unlockHistory, key = { "${it.achievementId}:${it.unlockedAt}" }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ra-hub-history"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RaBadgeImage(
                            store = badgeStore,
                            url = entry.badgeUrl,
                            size = 40.dp,
                            contentDescription = "Badge for ${entry.title}",
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                fontSize = 15.sp,
                                color = Color(0xFFC9A227),
                            )
                            Text(
                                text = "${entry.gameTitle} — ${entry.points} pts — ${formatUnlockTime(entry.unlockedAt)}",
                                fontSize = 13.sp,
                                color = Color(0xFF9E9EA5),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Games",
                    style = displayTextStyle(DisplaySizeSmall),
                    color = Color(0xFFC9C9CE),
                )
            }
            if (data.games.isEmpty()) {
                item {
                    Text(
                        text = "No games yet — achievements appear here after you play a game for the first time.",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9EA5),
                    )
                }
            } else {
                items(data.games, key = { it.id }) { game ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ra-hub-game"),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RaBadgeImage(
                                store = badgeStore,
                                url = game.badgeUrl,
                                size = 40.dp,
                                contentDescription = "Badge for ${game.title}",
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = game.title,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = "${game.unlocked} / ${game.total} unlocked — ${game.pointsEarned} / ${game.pointsTotal} pts",
                                    fontSize = 13.sp,
                                    color = Color(0xFF9E9EA5),
                                )
                            }
                            if (game.completed) {
                                Text(
                                    text = "Completed",
                                    fontSize = 13.sp,
                                    color = Color(0xFFC9A227),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // The progress bar: a plain dark track
                        // with a gold fill, the same gold as an
                        // unlocked achievement.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF2A2A2A)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(game.progress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFC9A227)),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Games appear here after their first launch — a game you have not played yet is not listed.",
                    fontSize = 13.sp,
                    color = Color(0xFF9E9EA5),
                )
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
