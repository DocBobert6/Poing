package app.poing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * The downsample factor for a cover image of [width] x [height]:
 * the smallest power of two that brings the long side to at most
 * [maxDim]. Pure math, so the tile never holds a full-size image
 * and the factor itself is unit-testable.
 */
fun sampleSizeFor(width: Int, height: Int, maxDim: Int = 512): Int {
    var sample = 1
    while (width / sample > maxDim || height / sample > maxDim) sample *= 2
    return sample
}

/** Decode a cover file downsampled to at most 512px on a side. Null when the file is not a decodable image. */
fun decodeCoverFile(file: File, maxDim: Int = 512): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: Exception) {
        null
    }
}

/** One cover tile: the art when there is any, else the system badge on a dark tile. Shared by the row and the grid cell. */
@Composable
private fun CoverTile(game: LibraryGame, coverFile: File?, size: Dp) {
    val bitmap = remember(coverFile) {
        coverFile?.takeIf { it.exists() }?.let { decodeCoverFile(it) }
    }
    Box(
        modifier = Modifier
            .size(size)
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
}

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
            .testTag("game-row")
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverTile(game, coverFile, 56.dp)
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

/**
 * One library grid cell: the same cover tile (larger), title,
 * system badge + source file name, playtime, and the same Saves
 * and Remove actions as the row. Tap the cell to play; Remove
 * asks first, exactly as in the list.
 */
@Composable
fun GameGridCell(
    game: LibraryGame,
    coverFile: File?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    playtimeSeconds: Long = 0,
    onSaveManager: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .testTag("game-grid-cell")
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverTile(game, coverFile, 120.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            game.title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            game.system.label + " · " + game.sourceName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playtimeSeconds > 0) {
            Text(
                "Played ${formatPlaytime(playtimeSeconds)}",
                style = segmentTextStyle14(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onSaveManager) {
                Text("Saves")
            }
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}
