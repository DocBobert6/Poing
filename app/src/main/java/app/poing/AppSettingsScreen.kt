package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Phase 6 — the player settings screen: the app's own settings,
 * not the core's (those live on the Core options screen). This is
 * where the standing rule lives: a new feature's on-screen
 * control is NOT part of the overlay unless it is toggled on
 * here, and every toggle below defaults to off.
 *
 * Like CoreOptionsScreen this is a settings sheet, so it wears
 * the Atomic look: a translucent purple wash over the black
 * background, with DotGothic16 section titles. Body copy stays
 * the neutral sans.
 */
@Composable
fun AppSettingsScreen(
    store: AppSettingsStore,
    // Phase 8 — the Jade palette picker writes the core's
    // palette option. Null (tests) hides the picker row.
    optionsStore: CoreOptionsStore? = null,
    onCoreOptions: () -> Unit,
    onRaLogin: () -> Unit = {},
    onBack: () -> Unit,
) {
    var ffControl by remember { mutableStateOf(store.ffControlVisible()) }
    var ffSpeed by remember { mutableStateOf(store.ffSpeed()) }
    var inputDisplay by remember { mutableStateOf(store.inputDisplayVisible()) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled()) }
    var rewindControl by remember { mutableStateOf(store.rewindControlVisible()) }
    // Phase 8 — Jade, the Game Boy display chain (on by
    // default: each chain is the default look for its
    // system), and the palette it shows the game in.
    var jade by remember { mutableStateOf(store.jadeEnabled()) }
    var palette by remember {
        mutableStateOf(optionsStore?.get("mgba_gb_colors") ?: CoreOptions.GB_PALETTES.first())
    }
    var pickingPalette by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PoingPurpleDeep.copy(alpha = 0.45f))
            .testTag("settings-list"),
    ) {
        item {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Settings", style = displayTextStyle(DisplaySizeLarge))
                Spacer(Modifier.height(8.dp))
                Text("Player settings for Poing! itself. The mGBA core's own settings live under Core options.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCoreOptions) {
                    Text("Core options")
                }
            }
        }
        item {
            Text(
                "Player",
                style = displayTextStyle(DisplaySizeMedium),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Fast-forward control",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Shows the hold-to-fast-forward button and the 0.5x slow-motion toggle on the player. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = ffControl,
                    onCheckedChange = {
                        store.setFfControlVisible(it)
                        ffControl = it
                    },
                    modifier = Modifier.testTag("ff-control-switch"),
                )
            }
        }
        if (ffControl) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Fast-forward speed",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "How fast the game runs while the fast-forward button is held.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        for (speed in PlaySpeed.FAST_FORWARD) {
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = speed.multiplier.toInt() == ffSpeed,
                                        onClick = {
                                            if (store.setFfSpeed(speed.multiplier.toInt())) {
                                                ffSpeed = speed.multiplier.toInt()
                                            }
                                        },
                                        role = Role.RadioButton,
                                    )
                                    .padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(speed.label)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Input display",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Shows the buttons being pressed, above the game. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = inputDisplay,
                    onCheckedChange = {
                        store.setInputDisplayVisible(it)
                        inputDisplay = it
                    },
                    modifier = Modifier.testTag("input-display-switch"),
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Haptics",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A short click when you press the D-pad or the A and B buttons. On by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = haptics,
                    onCheckedChange = {
                        store.setHapticsEnabled(it)
                        haptics = it
                    },
                    modifier = Modifier.testTag("haptics-switch"),
                )
            }
        }
        item {
            Text(
                "RetroAchievements",
                style = displayTextStyle(DisplaySizeMedium),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        RaClient.status.ifEmpty { "Not logged in." },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Achievements, leaderboards and rich presence for the game you are playing. Your RetroAchievements account — nothing else is synced.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = onRaLogin) {
                    Text(if (RaClient.loggedIn) "Account" else "Log in")
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rewind control",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Shows the hold-to-rewind button on the player: hold it to step back through the last 30 seconds. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = rewindControl,
                    onCheckedChange = {
                        store.setRewindControlVisible(it)
                        rewindControl = it
                    },
                    modifier = Modifier.testTag("rewind-control-switch"),
                )
            }
        }
        item {
            Text(
                "Display",
                style = displayTextStyle(DisplaySizeMedium),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Jade",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The Game Boy display chain. On by default; off shows the game exactly as the core renders it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = jade,
                    onCheckedChange = {
                        store.setJadeEnabled(it)
                        jade = it
                    },
                    modifier = Modifier.testTag("jade-chain-switch"),
                )
            }
        }
        if (jade && optionsStore != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickingPalette = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("jade-palette-row"),
                ) {
                    Text(
                        "Game Boy palette",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The palette Jade shows the game in. The DMG green and the Super Game Boy palettes live here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Current: $palette",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.padding(16.dp)) {
                TextButton(onClick = {
                    store.reset()
                    ffControl = store.ffControlVisible()
                    ffSpeed = store.ffSpeed()
                    inputDisplay = store.inputDisplayVisible()
                    haptics = store.hapticsEnabled()
                    rewindControl = store.rewindControlVisible()
                    jade = store.jadeEnabled()
                    palette = optionsStore?.get("mgba_gb_colors")
                        ?: CoreOptions.GB_PALETTES.first()
                }) {
                    Text("Reset player settings")
                }
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Phase 8 — the palette picker: the core's own 48
    // palettes, same dialog shape as Core Options. A pick
    // saves immediately and takes effect on the next game
    // start (and on resume, when the chain re-reads it).
    if (pickingPalette && optionsStore != null) {
        AlertDialog(
            onDismissRequest = { pickingPalette = false },
            containerColor = PoingPurpleDeep.copy(alpha = 0.92f),
            title = { Text("Game Boy palette") },
            text = {
                LazyColumn {
                    items(CoreOptions.GB_PALETTES, key = { it }) { value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == palette,
                                    onClick = {
                                        if (optionsStore.set("mgba_gb_colors", value)) {
                                            palette = value
                                        }
                                        pickingPalette = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == palette,
                                onClick = null,
                            )
                            Text(value)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingPalette = false }) {
                    Text("Close")
                }
            },
        )
    }
}
