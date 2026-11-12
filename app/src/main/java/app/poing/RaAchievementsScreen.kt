package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * gets a plain explanation instead of an empty list.
 *
 * Full-screen look like the Cheats screen: black, DotGothic16
 * title, silver body text.
 */
@Composable
fun RaAchievementsScreen(
    gameName: String,
    onBack: () -> Unit,
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
    val richPresence = RaClient.richPresence

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
                    Text(
                        text = "RetroAchievements has no achievements for this game.",
                        fontSize = 14.sp,
                        color = Color(0xFFC9C9CE),
                    )
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
                items(achievements, key = { it.id }) { achievement ->
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF9E9EA5),
                        )
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
