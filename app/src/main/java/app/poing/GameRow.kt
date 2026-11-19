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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** One cover tile: the art when there is any, else the system badge on a dark tile. Shared by the row and the grid cell. The bitmap comes decoded from the store's state map — the tile never decodes in composition. */
@Composable
private fun CoverTile(game: LibraryGame, bitmap: Bitmap?, size: Dp) {
    // The asImageBitmap wrap is remembered so a recomposition
    // does not re-wrap the same bitmap.
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
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
    coverBitmap: Bitmap?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    // Phase 6 (item 5) — seconds played, shown as "Played 2h 5m"
    // when the game has been played at all.
    playtimeSeconds: Long = 0,
    // Alpha.22 — the game's cached achievement set from the hub
    // store (unlocked/total), shown as "Achievements 3/12" once
    // the set has loaded at least once. Null (never loaded, or
    // no set) shows nothing — never a misleading 0/N.
    raProgress: RaHubGame? = null,
    // Phase 6 (item 5) — open the Save Manager for this game.
    onSaveManager: () -> Unit = {},
    // Alpha.9 — open the custom-cover dialog for this game.
    onCover: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .testTag("game-row")
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverTile(game, coverBitmap, 56.dp)
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
            if (raProgress != null && raProgress.total > 0) {
                Text(
                    "Achievements ${raProgress.unlocked}/${raProgress.total}",
                    style = segmentTextStyle14(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onCover,
            modifier = Modifier.testTag("game-cover"),
        ) {
            Text("Cover")
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
    coverBitmap: Bitmap?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    playtimeSeconds: Long = 0,
    // Alpha.22 — the game's cached achievement set, as in the row.
    raProgress: RaHubGame? = null,
    onSaveManager: () -> Unit = {},
    // Alpha.9 — open the custom-cover dialog for this game.
    onCover: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .testTag("game-grid-cell")
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverTile(game, coverBitmap, 120.dp)
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
        if (raProgress != null && raProgress.total > 0) {
            Text(
                "Achievements ${raProgress.unlocked}/${raProgress.total}",
                style = segmentTextStyle14(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Alpha.30 (audit 2) — compact action links: 12sp,
        // one line, no wrap, no minimum width. A TextButton's
        // 64dp minimum width (3 x 64dp) never fit the narrow
        // grid cell, so "Remove" wrapped to "Remo"/"ve" on
        // device. The list row keeps its TextButtons (it has
        // room). Vertical padding keeps a tappable height;
        // horizontal padding is zero — the 8dp gaps between
        // links are the spacing. Ellipsis is the safety net,
        // never the plan: the three links at 12sp measure
        // ~118dp against the narrowest cell's 144dp of
        // content width (160dp adaptive cell minus the
        // cell's 8dp padding each side).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            GridActionLink(
                text = "Cover",
                onClick = onCover,
                modifier = Modifier.testTag("game-cover"),
            )
            GridActionLink(text = "Saves", onClick = onSaveManager)
            GridActionLink(text = "Remove", onClick = onRemove)
        }
    }
}

/**
 * One compact action link for the grid cell (see the
 * alpha.30 note above): 12sp, one line, no wrap, no
 * minimum width, primary-coloured like the TextButtons
 * it replaces. Role.Button keeps the accessibility
 * meaning the button had.
 */
@Composable
private fun GridActionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick, role = Role.Button),
    )
}
