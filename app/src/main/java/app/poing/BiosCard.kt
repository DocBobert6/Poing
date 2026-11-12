package app.poing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The BIOS status card. Each of the three optional BIOS files
 * shows Installed / Missing with Import (or Replace) and Delete.
 * BIOS is never gated — a missing BIOS never blocks starting a
 * game, and this card says so.
 */
@Composable
fun BiosCard(
    installed: Map<BiosKind, Boolean>,
    onImport: (BiosKind) -> Unit,
    onDelete: (BiosKind) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("BIOS (optional)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "mGBA runs on its built-in BIOS — these are never required to play.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            for (kind in BiosKind.entries) {
                BiosRow(kind, installed[kind] == true, onImport, onDelete)
            }
        }
    }
}

@Composable
private fun BiosRow(
    kind: BiosKind,
    installed: Boolean,
    onImport: (BiosKind) -> Unit,
    onDelete: (BiosKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (installed) "Installed" else "Missing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { onImport(kind) }) {
            Text(if (installed) "Replace" else "Import")
        }
        if (installed) {
            TextButton(onClick = { onDelete(kind) }) {
                Text("Delete")
            }
        }
    }
}
