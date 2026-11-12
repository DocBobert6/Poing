package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
    onCoreOptions: () -> Unit,
    onBack: () -> Unit,
) {
    var ffControl by remember { mutableStateOf(store.ffControlVisible()) }
    var ffSpeed by remember { mutableStateOf(store.ffSpeed()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PoingPurpleDeep.copy(alpha = 0.45f)),
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
            Row(modifier = Modifier.padding(16.dp)) {
                TextButton(onClick = {
                    store.reset()
                    ffControl = store.ffControlVisible()
                    ffSpeed = store.ffSpeed()
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
}
