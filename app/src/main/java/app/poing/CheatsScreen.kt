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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Phase 6 (item 3) — the per-game cheats screen, reached from the
 * pause menu while the game is paused. The list is the game's own
 * file in filesDir/cheats/; every change saves it immediately and
 * re-applies the enabled codes to the running game (reset, then
 * re-add — the libretro call ignores its enabled flag).
 *
 * A new code is accepted only when CheatFormat says the core
 * understands it for this system; anything else is refused with a
 * message, never silently stored. Cheats only work while a game
 * is loaded — this screen is only reachable from a paused game,
 * so that is always true here.
 *
 * Atomic sheet look, like the Core Options screen.
 */
@Composable
fun CheatsScreen(
    gameName: String,
    system: GameSystem,
    saveBase: String,
    store: CheatStore,
    session: PlayerSession,
    onBack: () -> Unit,
) {
    var cheats by remember { mutableStateOf(store.list(saveBase)) }
    var newCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun apply(list: List<Cheat>) {
        cheats = list
        store.save(saveBase, list)
        scope.launch(Dispatchers.IO) { session.applyCheats(list) }
    }

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
                text = "Cheats",
                fontFamily = FontFamily(FontResources.DotGothic16),
                fontSize = 28.sp,
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = gameName,
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = CheatFormat.hint(system),
                fontSize = 13.sp,
                color = Color(0xFF9E9EA5),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newCode,
                    onValueChange = { newCode = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cheat-code-field"),
                    label = { Text("Add a cheat code") },
                    singleLine = true,
                )
                Button(onClick = {
                    val code = CheatFormat.normalize(newCode, system)
                    if (code == null) {
                        message = "That is not a valid ${system.label} cheat code."
                    } else if (cheats.any { it.code == code }) {
                        message = "That code is already in the list."
                    } else {
                        message = null
                        newCode = ""
                        apply(cheats + Cheat(code, enabled = true))
                    }
                }) {
                    Text("Add")
                }
            }
            message?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = Color(0xFFC9A227),
                )
            }
        }
        if (cheats.isEmpty()) {
            item {
                Text(
                    text = "No cheats for this game yet.",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EA5),
                )
            }
        }
        itemsIndexed(cheats, key = { _, cheat -> cheat.code }) { index, cheat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cheat.code,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    color = Color(0xFFC9C9CE),
                )
                Switch(
                    checked = cheat.enabled,
                    onCheckedChange = { enabled ->
                        apply(cheats.toMutableList().also { it[index] = cheat.copy(enabled = enabled) })
                    },
                    modifier = Modifier.testTag("cheat-switch-${cheat.code}"),
                )
                TextButton(onClick = {
                    apply(cheats.filterIndexed { i, _ -> i != index })
                }) {
                    Text("Delete")
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to game")
            }
        }
    }
}
