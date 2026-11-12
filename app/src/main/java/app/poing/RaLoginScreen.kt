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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 6 (item 8c) — the RetroAchievements login screen, reached
 * from Settings. The username and password are typed here, once:
 * the password is used for this login only and is never stored —
 * the server hands back a session token, the app keeps the token
 * (RaSessionStore), and the next launch re-logs in with it. A
 * rejected token is cleared, so the user types their password
 * again instead of retrying a dead token on every launch.
 *
 * The status line is RaClient's own status ("Not logged in.",
 * "Logging in…", "Logged in as …", "Login failed: …") — a
 * failure is never silent and never vague. Logged in, the screen
 * offers only a logout (which clears the username and the token).
 *
 * Full-screen look like the Cheats screen: black, DotGothic16
 * title, silver body text.
 */
@Composable
fun RaLoginScreen(
    session: RaSessionStore,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf(session.username()) }
    var password by remember { mutableStateOf("") }
    val loggedIn = RaClient.loggedIn
    val loginPending = RaClient.loginPending
    val status = RaClient.status.ifEmpty { "Not logged in." }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "RetroAchievements",
                style = displayTextStyle(DisplaySizeLarge),
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = "Achievements, leaderboards and rich presence for the game you are playing. Your RetroAchievements account — nothing else is synced.",
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
            )
        }
        if (!loggedIn) {
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ra-username-field"),
                    label = { Text("Username") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ra-password-field"),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            item {
                Button(
                    onClick = {
                        // The caller stores the username first:
                        // the password is used once for this
                        // login and is never stored.
                        session.setUsername(username)
                        RaClient.login(username, password)
                    },
                    enabled = username.isNotBlank() && password.isNotEmpty() && !loginPending,
                    modifier = Modifier.testTag("ra-login-button"),
                ) {
                    Text(if (loginPending) "Logging in…" else "Log in")
                }
            }
        } else {
            item {
                Text(
                    text = "Logged in as ${RaClient.username}.",
                    fontSize = 14.sp,
                    color = Color(0xFFC9C9CE),
                )
            }
            item {
                Button(
                    onClick = { RaClient.logout() },
                    modifier = Modifier.testTag("ra-logout-button"),
                ) {
                    Text("Log out")
                }
            }
        }
        item {
            Text(
                text = status,
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
                modifier = Modifier.testTag("ra-status"),
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
