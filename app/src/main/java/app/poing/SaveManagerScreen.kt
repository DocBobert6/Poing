package app.poing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 6 (item 5) — the Save Manager: everything a game has
 * saved, in one place — the battery save (.sav, which also holds
 * the RTC) plus the four save-state slots and the auto-save, each
 * with its size and time, a Load, and a Delete behind a confirm.
 * A state deleted here deletes its thumbnail with it.
 *
 * Load starts the game at that state (the caller wires it to the
 * player with a pending load); the battery save needs no Load —
 * it restores itself every time the game starts. The manager is
 * library-only on purpose: the pause menu already saves and
 * loads states, so this screen is the cold view of a game's
 * saves, not a second copy of the pause menu.
 *
 * Atomic sheet look, like the Cheats and Core Options screens.
 */
@Composable
fun SaveManagerScreen(
    game: LibraryGame,
    onLoadState: (Int) -> Unit,
    onLoadAuto: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SaveStore(context.filesDir.resolve("saves")) }
    val saveBase = game.fileName.substringBeforeLast('.')
    var refreshTick by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    val scope = rememberCoroutineScope()

    // Re-read on every delete, keyed by the tick, so the rows
    // always show what is actually in the saves directory.
    val entries = remember(refreshTick) { readEntries(store, saveBase) }

    fun delete(target: DeleteTarget) {
        scope.launch(Dispatchers.IO) {
            when (target) {
                DeleteTarget.Battery -> store.saveFile(saveBase).delete()
                is DeleteTarget.State -> {
                    store.stateFile(saveBase, target.slot).delete()
                    store.thumbnailFile(saveBase, target.slot).delete()
                }
                DeleteTarget.Auto -> {
                    store.autoStateFile(saveBase).delete()
                    store.autoThumbnailFile(saveBase).delete()
                }
            }
            withContext(Dispatchers.Main) {
                pendingDelete = null
                refreshTick++
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(24.dp)
            .testTag("save-manager-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Save manager",
                style = displayTextStyle(DisplaySizeLarge),
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = game.title,
                fontSize = 14.sp,
                color = Color(0xFFC9C9CE),
            )
        }
        item {
            Text(
                text = "Battery save",
                style = displayTextStyle(DisplaySizeMedium),
                color = Color.White,
            )
            if (entries.battery != null) {
                SaveRow(
                    label = "Battery save",
                    detail = formatSaveDetail(entries.battery.length(), entries.battery.lastModified()),
                    thumbnail = null,
                    onLoad = null,
                    onDelete = { pendingDelete = DeleteTarget.Battery },
                    testTag = "battery",
                )
            } else {
                Text(
                    text = "No battery save yet.",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EA5),
                )
            }
        }
        item {
            Text(
                text = "Save states",
                style = displayTextStyle(DisplaySizeMedium),
                color = Color.White,
            )
        }
        entries.slots.forEach { slot ->
            item {
                if (slot.modifiedAt != null) {
                    SaveRow(
                        label = "Slot ${slot.slot}" + if (slot.slot == SaveStore.QUICK_SLOT) " (quick)" else "",
                        detail = formatSaveDetail(slot.file.length(), slot.modifiedAt),
                        thumbnail = slot.thumbnail,
                        onLoad = { onLoadState(slot.slot) },
                        onDelete = { pendingDelete = DeleteTarget.State(slot.slot) },
                        testTag = "slot-${slot.slot}",
                    )
                } else {
                    Text(
                        text = "Slot ${slot.slot} — empty",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9EA5),
                    )
                }
            }
        }
        item {
            Text(
                text = "Auto-save",
                style = displayTextStyle(DisplaySizeMedium),
                color = Color.White,
            )
            Text(
                text = "Written when you quit, so you can pick up where you left off. It never loads itself.",
                fontSize = 14.sp,
                color = Color(0xFF9E9EA5),
            )
            if (entries.auto != null) {
                SaveRow(
                    label = "Auto-save",
                    detail = formatSaveDetail(entries.auto.length(), entries.auto.lastModified()),
                    thumbnail = store.autoThumbnailFile(saveBase),
                    onLoad = onLoadAuto,
                    onDelete = { pendingDelete = DeleteTarget.Auto },
                    testTag = "auto",
                )
            } else {
                Text(
                    text = "No auto-save yet.",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EA5),
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to library")
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${target.label()}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { delete(target) },
                    modifier = Modifier.testTag("save-delete-confirm"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private sealed interface DeleteTarget {
    data object Battery : DeleteTarget
    data class State(val slot: Int) : DeleteTarget
    data object Auto : DeleteTarget

    fun label(): String = when (this) {
        Battery -> "battery save"
        is State -> "save state ${slot}"
        Auto -> "auto-save"
    }
}

private data class SlotInfo(
    val slot: Int,
    val file: File,
    val thumbnail: File?,
    val modifiedAt: Long?,
)

private data class ManagerEntries(
    val battery: File?,
    val slots: List<SlotInfo>,
    val auto: File?,
)

/** Read the game's saves from the directory, as they are right now. */
private fun readEntries(store: SaveStore, saveBase: String): ManagerEntries {
    return ManagerEntries(
        battery = store.saveFile(saveBase).takeIf { it.exists() },
        slots = (0 until SaveStore.SLOT_COUNT).map { slot ->
            SlotInfo(
                slot = slot,
                file = store.stateFile(saveBase, slot),
                thumbnail = store.thumbnailFile(saveBase, slot),
                modifiedAt = store.stateModifiedAt(saveBase, slot),
            )
        },
        auto = store.autoStateFile(saveBase).takeIf { it.exists() },
    )
}

/** One save row: thumbnail (states), label + size + time, Load, Delete. */
@Composable
private fun SaveRow(
    label: String,
    detail: String,
    thumbnail: File?,
    onLoad: (() -> Unit)?,
    onDelete: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (thumbnail != null) {
            SaveThumbnail(thumbnail, "save-thumb-$label")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = Color(0xFFC9C9CE),
            )
            Text(
                text = detail,
                style = segmentTextStyle14(),
                color = Color(0xFF9E9EA5),
            )
        }
        if (onLoad != null) {
            Button(
                onClick = onLoad,
                modifier = Modifier.testTag("save-load-$testTag"),
            ) {
                Text("Load")
            }
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.testTag("save-delete-$testTag"),
        ) {
            Text("Delete")
        }
    }
}

/** A save-state thumbnail: the 120px-wide PNG in a small dark frame, or an empty frame when the slot has none yet. */
@Composable
private fun SaveThumbnail(file: File, testTag: String) {
    val bitmap = remember(file) {
        file.takeIf { it.exists() }?.let {
            try {
                BitmapFactory.decodeFile(it.absolutePath)
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 54.dp)
            .testTag(testTag)
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** "12 KB · Mar 4, 21:30" for a save file. */
private fun formatSaveDetail(sizeBytes: Long, modifiedAt: Long): String =
    "${formatBytes(sizeBytes)} · ${formatSaveTime(modifiedAt)}"

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
}

/** "Mar 4, 21:30" — the same time format as the pause menu's slot rows. */
private fun formatSaveTime(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}
