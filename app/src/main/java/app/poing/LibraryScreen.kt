package app.poing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 3 home screen: the library IS the home screen. Games are
 * listed with their cover (or placeholder) and system badge; tap
 * one to play it. Add game (one file) and Add folder (scan a
 * folder) import into filesDir/games/; remove deletes the copy and
 * its saves behind a confirm dialog. The BIOS card sits below the
 * list, and the Licences button stays. The whole screen is one
 * LazyColumn, so it scrolls on a small display instead of pushing
 * the BIOS card off-screen.
 *
 * Imports run on Dispatchers.IO — a folder scan reads every file
 * it finds, and that must never happen on the main thread.
 */
@Composable
fun LibraryScreen(
    notice: String?,
    onPlay: (LibraryGame) -> Unit,
    onLicences: () -> Unit,
    onSettings: () -> Unit,
    // Phase 6 (item 5) — open the Save Manager for a game.
    onSaveManager: (LibraryGame) -> Unit = {},
    // Top bar / back swipe (Robert, 2026-11-12): the first
    // back swipe on the home page shows this hint instead of
    // closing the app — a second swipe within ~2s exits.
    showExitHint: Boolean = false,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context.filesDir) }
    val bios = remember { BiosStore(context.filesDir) }
    val art = remember { CoverArtStore(context.filesDir) }
    // Phase 6 (item 5) — playtime totals for the rows; the store
    // is re-read on refresh so a finished session's banked time
    // shows when the player quits back here.
    val playtime = remember { PlaytimeStore(context.filesDir) }
    var games by remember { mutableStateOf(store.list()) }
    var filter by remember { mutableStateOf<GameSystem?>(null) }
    var biosInstalled by remember {
        mutableStateOf(BiosKind.entries.associateWith { bios.isInstalled(it) })
    }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<LibraryGame?>(null) }
    var pendingBiosImport by remember { mutableStateOf<BiosKind?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        games = store.list()
        biosInstalled = BiosKind.entries.associateWith { bios.isInstalled(it) }
    }

    // A notice from the player (e.g. a load failure) is shown once.
    LaunchedEffect(notice) {
        if (notice != null) message = notice
    }

    // Cover art: Wi-Fi-only fetch for games with no cached cover.
    // With the Phase 3 no-op fetcher this does nothing; when a real
    // source plugs in, covers appear without touching this screen.
    LaunchedEffect(games) {
        withContext(Dispatchers.IO) {
            val wifi = isWifiConnected(context)
            for (game in games) {
                if (!art.hasCover(game)) art.coverOrFetch(game, wifi)
            }
        }
    }

    val pickGame = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val game = withContext(Dispatchers.IO) {
                runCatching { importSingleFile(context, store, uri) }.getOrNull()
            }
            if (game == null) {
                message = "That file could not be imported — it is not a Game Boy ROM."
            } else {
                message = "Imported ${game.title}."
                refresh()
            }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { importFolder(context, store, uri) }
                    .getOrElse { ImportResult(0, 0, 1) }
            }
            message = "Imported ${result.imported} game(s)" +
                (if (result.skipped > 0) ", skipped ${result.skipped}" else "") +
                (if (result.failed > 0) ", ${result.failed} failed" else "") + "."
            refresh()
        }
    }

    val pickBios = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val kind = pendingBiosImport ?: return@rememberLauncherForActivityResult
        pendingBiosImport = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() } ?: return@runCatching false
                    bios.import(kind, bytes)
                    true
                }.getOrDefault(false)
            }
            message = if (ok) "${kind.label} BIOS installed." else "Could not read that file."
            refresh()
        }
    }

    // The exit hint overlays the bottom of the list, so it is
    // visible without scrolling — the list itself is untouched.
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Top bar: the power light. No game is loaded on the
            // library, so the light is off — it turns green in the
            // player and amber while paused (see GameScreen).
            // statusBarsPadding: the app draws edge-to-edge
            // (targetSdk 36 + activity 1.11 enable it by default),
            // so without this the light sits in the status-bar
            // band, tight against the clock. The light's top edge
            // is now status-bar height + 16dp.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp),
            ) {
                PowerLight(PowerState.Off)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The wordmark in DotGothic16 with the one gold ring
                // behind it — home screen only, nowhere else.
                Wordmark(withRing = true)
                Spacer(Modifier.height(8.dp))
                Text("Game Boy · Game Boy Color · Game Boy Advance")
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = { pickGame.launch(arrayOf("*/*")) }) {
                    Text("Add game")
                }
                Button(onClick = { pickFolder.launch(null) }) {
                    Text("Add folder")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onLicences) {
                    Text("Licences")
                }
                TextButton(onClick = onSettings) {
                    Text("Settings")
                }
            }
        }
        item {
            // The L/R shoulders as the system filter (idea 5):
            // silver shoulder tabs at the top of the library. ALL
            // shows every system; a system tab shows only its games.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                ShoulderTab("ALL", filter == null) { filter = null }
                for (system in GameSystem.entries) {
                    ShoulderTab(system.label, filter == system) { filter = system }
                }
            }
        }
        message?.let {
            item {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (games.isEmpty()) {
            item {
                Text(
                    "No games yet — add a game to start.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            val shown = filterGames(games, filter)
            if (shown.isEmpty()) {
                item {
                    Text(
                        "No ${filter?.label} games yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { game ->
                GameRow(
                    game = game,
                    coverFile = art.coverFile(game).takeIf { it.exists() },
                    onPlay = { onPlay(game) },
                    onRemove = { pendingRemove = game },
                    playtimeSeconds = playtime.get(game.id),
                    onSaveManager = { onSaveManager(game) },
                )
            }
        }
        item {
            BiosCard(
                installed = biosInstalled,
                onImport = { kind ->
                    pendingBiosImport = kind
                    pickBios.launch(arrayOf("*/*"))
                },
                onDelete = { kind ->
                    bios.delete(kind)
                    refresh()
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
        if (showExitHint) {
            Text(
                "Press back again to exit",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .testTag("exit-hint"),
                color = Color.White,
            )
        }
    }

    pendingRemove?.let { game ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${game.title}?") },
            text = { Text("This deletes the game copy and its saves. The original file is untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.remove(game)
                            // The playtime entry goes with the game.
                            playtime.remove(game.id)
                        }
                        pendingRemove = null
                        message = "Removed ${game.title}."
                        refresh()
                    }
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** The library filter, as a pure function so it is unit-testable. */
fun filterGames(games: List<LibraryGame>, filter: GameSystem?): List<LibraryGame> =
    if (filter == null) games else games.filter { it.system == filter }

/**
 * One shoulder tab (idea 5): the L/R shoulders as the system
 * filter. Selected is solid silver with black dot-matrix text;
 * unselected is dark chrome with silver text.
 */
@Composable
private fun ShoulderTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("filter-$label")
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PoingSilver else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = displayTextStyle(DisplaySizeSmall),
            color = if (selected) Color.Black else PoingSilver,
        )
    }
}
