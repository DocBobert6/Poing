package app.poing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 5 — the pause menu, extracted from GameScreen so the
 * player screen itself stays under the dex guard.
 *
 * Pause is the Pocket screen dimmed: the game stays visible
 * behind a dark dim, and the menu itself is an Atomic translucent
 * purple panel (with the faint sheen) — never a plain black box.
 * The title and section header are DotGothic16; save-state times
 * are DSEG14 (a month name needs letters); everything else is
 * body copy. Save/load run on IO and only ever run while paused,
 * so a state can never race a frame.
 */
@Composable
fun PauseMenu(
    gameName: String,
    session: PlayerSession,
    pauseMessage: String?,
    onMessage: (String?) -> Unit,
    onResume: () -> Unit,
    onQuit: () -> Unit,
    onCoreOptions: () -> Unit,
    onCheats: () -> Unit = {},
    onAchievements: () -> Unit = {},
    onLeaderboards: () -> Unit = {},
    onMoveControls: () -> Unit = {},
    // Phase 6 (item 12) — Cartridge Surgery: the slide-out
    // cartridge schematic with live hardware controls.
    onCartridge: () -> Unit = {},
    // Phase 6 (item 11) — capture: a screenshot (the latest
    // frame) and the video recording toggle.
    isRecording: Boolean = false,
    onScreenshot: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    AtomicPanel(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.9f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Paused", style = displayTextStyle(DisplaySizeMedium), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(gameName, color = Color.White)
            pauseMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onResume) {
                Text("Resume")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onMessage(null)
                    scope.launch(Dispatchers.IO) {
                        val ok = session.saveState(SaveStore.QUICK_SLOT)
                        withContext(Dispatchers.Main) {
                            onMessage(if (ok) "Quick saved." else "Quick save failed.")
                        }
                    }
                }) {
                    Text("Quick save")
                }
                Button(
                    onClick = {
                        onMessage(null)
                        scope.launch(Dispatchers.IO) {
                            val ok = session.loadState(SaveStore.QUICK_SLOT)
                            withContext(Dispatchers.Main) {
                                onMessage(if (ok) "Quick loaded." else "No quick save yet.")
                            }
                        }
                    },
                    enabled = session.stateExists(SaveStore.QUICK_SLOT),
                ) {
                    Text("Quick load")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Save states", style = displayTextStyle(DisplaySizeMedium), color = Color.White)
            for (slot in 0 until SaveStore.SLOT_COUNT) {
                val savedAt = session.stateModifiedAt(slot)
                // Keyed on the saved time: a fresh save re-decodes
                // the new thumbnail instead of showing the old one.
                val thumb = remember(savedAt) { session.stateThumbnail(slot) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    StateThumbnail(thumb, "state-thumb-$slot")
                    Text(
                        "Slot $slot" + if (slot == SaveStore.QUICK_SLOT) " (quick)" else "",
                        color = Color.White,
                    )
                    Text(
                        savedAt?.let { formatStateTime(it) } ?: "empty",
                        color = Color.White,
                        style = segmentTextStyle14(),
                    )
                    Button(onClick = {
                        onMessage(null)
                        scope.launch(Dispatchers.IO) {
                            val ok = session.saveState(slot)
                            withContext(Dispatchers.Main) {
                                onMessage(if (ok) "Saved to slot $slot." else "Save failed.")
                            }
                        }
                    }) {
                        Text("Save")
                    }
                    Button(
                        onClick = {
                            onMessage(null)
                            scope.launch(Dispatchers.IO) {
                                val ok = session.loadState(slot)
                                withContext(Dispatchers.Main) {
                                    onMessage(if (ok) "Loaded slot $slot." else "Load failed.")
                                }
                            }
                        },
                        enabled = savedAt != null,
                    ) {
                        Text("Load")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Auto-save", style = displayTextStyle(DisplaySizeMedium), color = Color.White)
            Text(
                "Written when you quit, so you can pick up where you left off. It never loads itself.",
                color = Color.White,
            )
            run {
                val savedAt = session.autoStateModifiedAt()
                val thumb = remember(savedAt) { session.autoStateThumbnail() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    StateThumbnail(thumb, "state-thumb-auto")
                    Text(
                        savedAt?.let { formatStateTime(it) } ?: "empty",
                        color = Color.White,
                        style = segmentTextStyle14(),
                    )
                    Button(
                        onClick = {
                            onMessage(null)
                            scope.launch(Dispatchers.IO) {
                                val ok = session.loadAutoState()
                                withContext(Dispatchers.Main) {
                                    onMessage(if (ok) "Auto-save loaded." else "Load failed.")
                                }
                            }
                        },
                        enabled = savedAt != null,
                    ) {
                        Text("Load")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCoreOptions) {
                    Text("Core options")
                }
                Button(onClick = onCheats) {
                    Text("Cheats")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAchievements) {
                    Text("Achievements")
                }
                Button(onClick = onLeaderboards) {
                    Text("Leaderboards")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onMoveControls) {
                Text("Move controls")
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCartridge) {
                Text("Cartridge")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScreenshot) {
                    Text("Screenshot")
                }
                Button(onClick = onToggleRecording) {
                    Text(if (isRecording) "Stop recording" else "Record")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onQuit) {
                Text("Quit to home")
            }
        }
    }
}

/** "Mar 4, 21:30" for a save-state slot's last-modified time. */
private fun formatStateTime(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}

/**
 * A save-state thumbnail: the 120px-wide PNG in a small dark
 * frame, or an empty dark frame when the slot has no thumbnail
 * yet (a state saved before this feature, or before the first
 * frame). The frame keeps the rows aligned either way.
 */
@Composable
private fun StateThumbnail(bitmap: android.graphics.Bitmap?, testTag: String) {
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
