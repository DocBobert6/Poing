package app.poing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 3 home screen: the library IS the home screen. Games are
 * listed with their cover (or placeholder) and system badge; tap
 * one to play it. Add game (one file) and Add folder (scan a
 * folder) import into filesDir/games/; remove deletes the copy and
 * its saves behind a confirm dialog. The BIOS card sits below the
 * list, and the Licences button stays. The whole screen is one
 * lazy grid — in list view every item spans the full width, so it
 * reads exactly like the old LazyColumn; in grid view the games
 * flow into adaptive columns while the header, message and BIOS
 * card still span full width — so it scrolls on a small display
 * instead of pushing the BIOS card off-screen.
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
    // Achievement Center (hub + badges, 2026-11-14) — open the
    // hub, reachable without launching a game.
    onRaHub: () -> Unit = {},
    // Top bar / back swipe (Robert, 2026-11-12): the first
    // back swipe on the home page shows this hint instead of
    // closing the app — a second swipe within ~2s exits.
    showExitHint: Boolean = false,
    // Test seam: a fake fetcher for the cover-art tests. Null
    // (the default, and what MainActivity passes) means the real
    // libretro-thumbnails fetcher.
    coverFetcher: CoverArtFetcher? = null,
) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context.filesDir) }
    val bios = remember { BiosStore(context.filesDir) }
    val art = remember {
        val fetcher = coverFetcher ?: LibretroThumbnailsFetcher(context.filesDir)
        val artStore = CoverArtStore(context.filesDir, fetcher)
        // The diagnostic (2026-11-15): the fetcher's events
        // feed the store's visible status, so the library shows
        // the gate, the index states and the last failure —
        // a failed fetch used to leave nothing but blank tiles.
        if (fetcher is LibretroThumbnailsFetcher) fetcher.onEvent = artStore::applyEvent
        artStore
    }
    // Phase 6 (item 5) — playtime totals for the rows; the store
    // is re-read on refresh so a finished session's banked time
    // shows when the player quits back here.
    val playtime = remember { PlaytimeStore(context.filesDir) }
    var games by remember { mutableStateOf(store.list()) }
    var filter by remember { mutableStateOf<GameSystem?>(null) }
    // The library's shape (Robert, 2026-11-14): list rows or a
    // grid of cover tiles. List by default, the choice persisted
    // in AppSettingsStore, so it survives a restart.
    var libraryView by remember {
        mutableStateOf(AppSettingsStore(context.filesDir).libraryView())
    }
    var biosInstalled by remember {
        mutableStateOf(BiosKind.entries.associateWith { bios.isInstalled(it) })
    }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<LibraryGame?>(null) }
    var pendingBiosImport by remember { mutableStateOf<BiosKind?>(null) }
    val scope = rememberCoroutineScope()

    // Playtimes, read once per refresh into state — the old rows
    // called playtime.get() during composition, which parsed the
    // whole playtime.properties per row.
    var playtimes by remember { mutableStateOf(playtime.all()) }

    fun refresh() {
        games = store.list()
        playtimes = playtime.all()
        biosInstalled = BiosKind.entries.associateWith { bios.isInstalled(it) }
    }

    // A notice from the player (e.g. a load failure) is shown once.
    LaunchedEffect(notice) {
        if (notice != null) message = notice
    }

    // Cover art: fetch for games with no cached cover, on
    // unmetered Wi-Fi — or on mobile data too, when the user
    // turns that on in Settings ("Download cover art over
    // mobile data", off by default). The fetcher is the
    // libretro-thumbnails collection (see CoverArt.kt): one
    // file per game, cached on the device, so covers appear
    // as their tiles are shown.
    //
    // Two states, one for each half of the 2026-11-14 fix:
    // connectivityVersion bumps when the gate opens (the watcher
    // below), so the fetch re-runs when Wi-Fi appears while the
    // library is open — the old effect keyed only on `games`, so
    // the first run on mobile data fetched nothing and there was
    // never a retry. coversVersion bumps when a fetch pass lands
    // a cover, so the effect runs another pass — a pass fetches
    // at most MAX_COVERS_PER_PASS new covers (see CoverArt.kt),
    // and the loop continues until a pass lands nothing.
    //
    // The tiles themselves no longer key on coversVersion: each
    // reads its decoded bitmap from the store's state map, so a
    // landed cover redraws only its own row. The fetch effect
    // decodes on IO (existing covers + newly landed ones), so no
    // tile ever decodes on the main thread.
    //
    // The in-flight guard: the effect restarts when its keys
    // change, but the old pass keeps running its blocking fetch
    // loop (cancellation does not interrupt it), so two passes
    // can overlap. The guard lets one run; a trigger that arrives
    // while one is running sets fetchPending, and the finishing
    // pass bumps connectivityVersion so the pending trigger is
    // not lost.
    var coversVersion by remember { mutableStateOf(0) }
    var connectivityVersion by remember { mutableStateOf(0) }
    val fetchGuard = remember { AtomicBoolean(false) }
    val fetchPending = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        val watcher = ConnectivityWatcher(
            context,
            gateOpen = {
                isCoverArtNetworkAvailable(
                    context,
                    AppSettingsStore(context.filesDir).coverArtMobileDataAllowed(),
                )
            },
        ) { connectivityVersion++ }
        watcher.start()
        onDispose { watcher.stop() }
    }
    // The fetch pass itself, shared by the effect below and the
    // Retry button: read the setting fresh, note the gate, fetch,
    // decode, note the counts. A user-initiated Retry calls this
    // directly (scope.launch) instead of bumping an effect key:
    // routing Retry through a LaunchedEffect restart dropped the
    // restart when the tap landed while the previous pass was
    // mid-teardown (three red CI runs, 2026-11-15 — the probe
    // proved the pass itself always worked, the restart was what
    // never arrived). Guarded so two passes never overlap; a
    // trigger that arrives mid-pass sets fetchPending, and the
    // finishing pass re-runs the effect. Declared before the
    // effect: a local fun must be declared before use.
    suspend fun runFetchPass() {
        if (!fetchGuard.compareAndSet(false, true)) {
            fetchPending.set(true)
            return
        }
        try {
            val landed = withContext(Dispatchers.IO) {
                // The setting is read fresh on every run, and the
                // effect runs again when the library is re-entered
                // (returning from Settings re-composes it), so a
                // toggle change takes effect on the next entry.
                val allowMobileData =
                    AppSettingsStore(context.filesDir).coverArtMobileDataAllowed()
                val gate = coverArtGateState(context, allowMobileData)
                art.noteGate(gate)
                val didLand = fetchMissingCovers(
                    art,
                    games,
                    gate == CoverArtGate.OPEN,
                )
                // Decode on IO: existing covers + newly landed
                // ones. Already-decoded and failed decodes are
                // no-ops in the store, so a later pass costs
                // nothing for the covers it already holds.
                for (game in games) art.decodeIntoCache(game)
                art.noteCounts(games.size, games.count { art.hasCover(it) })
                didLand
            }
            if (landed) coversVersion++
        } finally {
            fetchGuard.set(false)
            if (fetchPending.compareAndSet(true, false)) connectivityVersion++
        }
    }
    LaunchedEffect(games, connectivityVersion, coversVersion) {
        runFetchPass()
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

    val pickBiosFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { importBiosFolder(context, bios, uri) }.getOrNull()
            }
            message = if (result == null) {
                "Could not read that folder."
            } else {
                val (imported, unreadable, missing) = result
                biosFolderResultMessage(imported, unreadable, missing)
            }
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
        // One lazy grid serves both views: in list view every
        // item spans the full width, so it reads exactly like
        // the old LazyColumn; in grid view only the header,
        // message and BIOS card span full width and the games
        // flow into adaptive columns. One scroll, one header.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("library-grid"),
            // Bottom room for the cover-art status bar (an
            // overlay below): the last item scrolls clear of
            // it. Trailing padding only — it moves nothing at
            // the top, so the header and first rows keep their
            // exact positions.
            contentPadding = PaddingValues(bottom = 64.dp),
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            // The Add row is its own item: it shared one item
            // with the Licences/Settings row below, and two
            // children in one lazy item stack at the same
            // position — the Licences/Settings row covered the
            // Add row, so on device the Settings, Licences and
            // Add folder buttons looked missing (2026-11-14).
            // The same class of bug as the shoulder tabs below,
            // whose comment documents it.
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
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
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
                TextButton(onClick = onRaHub) {
                    Text("Achievements")
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // The L/R shoulders as the system filter (idea 5):
            // silver shoulder tabs at the top of the library. ALL
            // shows every system; a system tab shows only its games.
            // This row is its own item: sharing one item with the
            // view row below left the shoulder tabs unclickable
            // (2026-11-14 — the later child covered the earlier
            // one, and the filter state never changed).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter-state-${filter?.label ?: "ALL"}")
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                ShoulderTab("ALL", filter == null) { filter = null }
                for (system in GameSystem.entries) {
                    ShoulderTab(system.label, filter == system) { filter = system }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // The view switch (Robert, 2026-11-14): list rows or a
            // grid of cover tiles. It sits directly under the
            // filter shoulders as its own small row: the top bar
            // holds only the power light, and the shoulder row
            // already holds four tabs, so neither is a good home.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                ViewTab("List", libraryView == LibraryView.LIST) {
                    libraryView = LibraryView.LIST
                    AppSettingsStore(context.filesDir).setLibraryView(LibraryView.LIST)
                }
                ViewTab("Grid", libraryView == LibraryView.GRID) {
                    libraryView = LibraryView.GRID
                    AppSettingsStore(context.filesDir).setLibraryView(LibraryView.GRID)
                }
            }
        }
        message?.let {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No games yet — add a game to start.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            val shown = filterGames(games, filter)
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No ${filter?.label} games yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            // The library's shape: list rows span the full width;
            // grid cells flow into the adaptive columns. Tap to
            // play, Saves and Remove (behind a confirm) work the
            // same in both views.
            if (libraryView == LibraryView.LIST) {
                items(shown, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { game ->
                    // The tile reads its decoded bitmap from the
                    // store's state map, so a landed cover
                    // redraws only this row — no global key.
                    GameRow(
                        game = game,
                        coverBitmap = art.coverBitmaps[game.id],
                        onPlay = { onPlay(game) },
                        onRemove = { pendingRemove = game },
                        playtimeSeconds = playtimes[game.id] ?: 0L,
                        onSaveManager = { onSaveManager(game) },
                    )
                }
            } else {
                items(shown, key = { it.id }) { game ->
                    GameGridCell(
                        game = game,
                        coverBitmap = art.coverBitmaps[game.id],
                        onPlay = { onPlay(game) },
                        onRemove = { pendingRemove = game },
                        playtimeSeconds = playtimes[game.id] ?: 0L,
                        onSaveManager = { onSaveManager(game) },
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
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
                onImportFolder = { pickBiosFolder.launch(null) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
        // The cover-art status bar (2026-11-15): what the fetch
        // is doing, or why it is not — the gate, the index
        // states, the last failure. Cover art used to fail
        // silently with blank tiles and no reason anywhere.
        // Retry re-runs the pass on demand: a pass that lands
        // nothing otherwise leaves no retry until the library
        // is re-entered or the network flaps. It is a bottom
        // overlay, NOT a grid item: as a grid item its 48dp
        // pushed the first game row and the BIOS card out of
        // the lazy grid's composition window in the narrow
        // Robolectric window (three red tests, 2026-11-15),
        // and as an overlay it stays visible while scrolling.
        // The grid's bottom content padding (above) lets the
        // last item scroll clear of it.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    coverArtStatusLine(art.status.value),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cover-art-status"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A small clickable text, not a TextButton: the
                // button's 48dp minimum height is what the bar
                // would otherwise cost the screen. Retry runs the
                // fetch pass directly (scope.launch): a pass that
                // lands nothing used to leave no retry until the
                // library was re-entered or the network flapped,
                // and a silent failure gave no reason to retry.
                // It deliberately does NOT bump an effect key:
                // routing Retry through a LaunchedEffect restart
                // dropped the restart when the tap landed while
                // the previous pass was mid-teardown (2026-11-15).
                // The tap cancels any in-flight download FIRST:
                // the old pass could be hung in a stalled index
                // download, and without the cancel the guard
                // would queue the restart behind it for as long
                // as the stall lasted.
                Text(
                    "Retry",
                    modifier = Modifier
                        .testTag("cover-art-retry")
                        .clickable { art.cancelFetch(); scope.launch { runFetchPass() } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showExitHint) {
            Text(
                "Press back again to exit",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
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
                            // So does its cached achievement set —
                            // a removed game is not a played game.
                            // Its recent unlocks stay: they are
                            // history, not a claim about the game.
                            RaHubStore(context.filesDir).removeGame(game.id)
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

/**
 * The view switch (Robert, 2026-11-14): list rows or a grid of
 * cover tiles. Same chrome as the shoulder tabs — selected is
 * solid silver with black dot-matrix text, unselected dark chrome
 * with silver text — so the switch reads as part of the same row
 * family rather than a new control.
 */
@Composable
private fun ViewTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("view-${label.lowercase()}")
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
