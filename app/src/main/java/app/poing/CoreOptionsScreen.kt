package app.poing

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Phase 4 — the core options screen. Every audited mgba_* option,
 * grouped by the core's own categories, with the value list the
 * core accepts and the documented default. Nothing here is
 * invented: the model is CoreOptions, copied from the pinned
 * core's header.
 *
 * Changing a value saves it immediately. [onValueChanged] fires
 * after each change so the player can push it live to a running
 * game (non-restart options take effect on the next frame; restart
 * options apply the next time a game starts, and are tagged).
 */
@Composable
fun CoreOptionsScreen(
    store: CoreOptionsStore,
    onValueChanged: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var values by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf<CoreOption?>(null) }

    fun refresh() {
        values = store.all()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Core options", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The mGBA core's own settings. Options marked (restart) apply " +
                        "the next time a game starts; the rest take effect right away.",
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    store.reset()
                    refresh()
                    onValueChanged?.invoke()
                }) {
                    Text("Reset to defaults")
                }
            }
        }
        for (category in CoreOptionCategory.entries) {
            val options = CoreOptions.ALL.filter { it.category == category }
            item {
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(options, key = { it.key }) { option ->
                val current = values[option.key] ?: option.default
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = option }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        option.label + if (option.restart) " (restart)" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(option.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Current: ${option.display(current)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.padding(16.dp)) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { option ->
        val current = values[option.key] ?: option.default
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(option.label) },
            text = {
                LazyColumn {
                    items(option.values, key = { it }) { value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == current,
                                    onClick = {
                                        if (store.set(option.key, value)) {
                                            refresh()
                                            onValueChanged?.invoke()
                                        }
                                        editing = null
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == current,
                                onClick = null,
                            )
                            Text(option.display(value))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null }) {
                    Text("Close")
                }
            },
        )
    }
}
