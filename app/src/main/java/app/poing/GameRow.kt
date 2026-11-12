package app.poing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * One library row: cover tile (or the placeholder — the system
 * badge on a dark tile — while no art source is wired, see
 * CoverArt.kt), title, system badge + source file name, remove
 * button. Tap the row to play; Remove asks first.
 */
@Composable
fun GameRow(
    game: LibraryGame,
    coverFile: File?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    // Phase 6 (item 5) — seconds played, shown as "Played 2h 5m"
    // when the game has been played at all.
    playtimeSeconds: Long = 0,
    // Phase 6 (item 5) — open the Save Manager for this game.
    onSaveManager: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(coverFile) {
            coverFile?.takeIf { it.exists() }?.let {
                try {
                    BitmapFactory.decodeFile(it.absolutePath)
                } catch (_: Exception) {
                    null
                }
            }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Cover art for ${game.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    game.system.label,
                    color = Color.White,
                    style = displayTextStyle(DisplaySizeSmall),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium)
            Text(
                game.system.label + " · " + game.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (playtimeSeconds > 0) {
                // A numeric readout with letters — DSEG14, like the
                // save-state times (DSEG7 has no letters for "h"/"m").
                Text(
                    "Played ${formatPlaytime(playtimeSeconds)}",
                    style = segmentTextStyle14(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onSaveManager) {
            Text("Saves")
        }
        TextButton(onClick = onRemove) {
            Text("Remove")
        }
    }
}
