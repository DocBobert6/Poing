package app.poing

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 6 (item 8c) — the leaderboards for the game being
 * played, reached from the pause menu while the game is paused.
 * The boards list refreshes when the screen opens; tapping a
 * board fetches its entries (the top entries, or the ones around
 * the logged-in user). Submission is automatic — rc_client
 * evaluates the trackers in do_frame and submits an attempt
 * through the same HTTP plumbing as achievements — so the app
 * never submits a score by hand. An attempt in progress shows
 * its live tracker value under its board ("Attempt: …").
 *
 * A game RA has not recognised, or a logged-out user, gets a
 * plain explanation instead of an empty list.
 *
 * Full-screen look like the Cheats screen: black, DotGothic16
 * title, silver body text.
 */
@Composable
fun RaLeaderboardsScreen(
    gameName: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        RaClient.refreshLeaderboards()
    }

    var selectedBoard by remember { mutableStateOf<RaLeaderboard?>(null) }
    var aroundUser by remember { mutableStateOf(false) }
    val loggedIn = RaClient.loggedIn
    val gameLoaded = RaClient.gameLoaded
    val title = RaClient.gameTitle ?: gameName
    val boards = RaClient.leaderboards
    val board = selectedBoard

    // The entries load when a board is picked, and again on every
    // Top / Around me switch.
    LaunchedEffect(board?.id, aroundUser) {
        val id = board?.id ?: return@LaunchedEffect
        RaClient.fetchLeaderboardEntries(id, aroundUser)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("ra-leaderboards-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Leaderboards",
                style = displayTextStyle(DisplaySizeLarge),
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
            )
        }
        when {
            !loggedIn -> {
                item {
                    Text(
                        text = "Log in to RetroAchievements in Settings to see leaderboards.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
            }
            !gameLoaded -> {
                item {
                    Text(
                        text = "RetroAchievements has no leaderboards for this game.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
            }
            board != null -> {
                item {
                    Text(
                        text = board.title,
                        style = displayTextStyle(DisplaySizeMedium),
                        color = Color.White,
                    )
                    Text(
                        text = board.description,
                        fontSize = 13.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { aroundUser = false },
                        ) {
                            Text("Top")
                        }
                        Button(
                            onClick = { aroundUser = true },
                        ) {
                            Text("Around me")
                        }
                    }
                }
                item {
                    val loading = RaClient.leaderboardEntriesLoading
                    val error = RaClient.leaderboardEntriesError
                    val list = RaClient.leaderboardEntries
                    when {
                        loading -> Text(
                            text = "Loading…",
                            fontSize = 14.sp,
                            color = Color(0xFFC9C9CE),
                        )
                        error != null -> Text(
                            text = error,
                            fontSize = 14.sp,
                            color = Color(0xFFC9C9CE),
                        )
                        list == null -> Text(
                            text = "No entries yet.",
                            fontSize = 14.sp,
                            color = Color(0xFFC9C9CE),
                        )
                        list.entries.isEmpty() -> Text(
                            text = "No entries yet.",
                            fontSize = 14.sp,
                            color = Color(0xFFC9C9CE),
                        )
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ra-entries-list"),
                        ) {
                            Text(
                                text = "${list.totalEntries} entries" +
                                    if (list.userIndex >= 0) " — you are #${list.userIndex + 1}" else "",
                                fontSize = 13.sp,
                                color = Color(0xFF9E9EA5),
                            )
                            for (entry in list.entries) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "#${entry.rank} ${entry.user}",
                                        fontSize = 14.sp,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = entry.display,
                                        fontSize = 14.sp,
                                        color = Color(0xFFC9C9CE),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { selectedBoard = null }) {
                        Text("All leaderboards")
                    }
                }
            }
            boards.isEmpty() -> {
                item {
                    Text(
                        text = "This game has no leaderboards.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
                }
            }
            else -> {
                items(boards, key = { it.id }) { b ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = b.title,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                        Text(
                            text = b.description,
                            fontSize = 13.sp,
                            color = Color(0xFFC9C9CE),
                        )
                        Text(
                            text = buildString {
                                append(RaLeaderboards.formatName(b.format))
                                if (b.id == RaClient.activeTrackerBoardId &&
                                    RaClient.activeTrackerDisplay.isNotEmpty()
                                ) {
                                    append(" — Attempt: ${RaClient.activeTrackerDisplay}")
                                } else if (b.trackerValue.isNotEmpty()) {
                                    append(" — ${b.trackerValue}")
                                }
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF9E9EA5),
                        )
                        Button(
                            onClick = { selectedBoard = b },
                            modifier = Modifier.testTag("ra-board-${b.id}"),
                        ) {
                            Text("View")
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
