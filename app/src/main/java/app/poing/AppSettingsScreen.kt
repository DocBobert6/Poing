package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Phase 8 — the Jade palette picker writes the core's
    // palette option. Null (tests) hides the picker row.
    optionsStore: CoreOptionsStore? = null,
    // Overlay defaults (Robert, 2026-11-14) — the per-handheld
    // default control layouts. Null (tests) hides the
    // Controls section, same pattern as optionsStore.
    layoutStore: ControlLayoutStore? = null,
    // In-app updater (Step 2 of the alpha, 2026-11-15): the
    // launch check's result, if one ran (null when the launch
    // check was skipped or failed silently). The section shows
    // it until "Check for updates" refreshes it.
    initialUpdateCheck: UpdateCheckResult? = null,
    // Test seams for the updater: the check and the download,
    // defaulting to the real GitHub calls. Tests pass fakes so
    // no network is needed.
    checkUpdates: (AppVersion) -> UpdateCheckResult = { checkForUpdates(it) },
    downloadUpdate: (String, java.io.File) -> java.io.File? = { url, dir ->
        downloadUpdateApk(url, dir)
    },
    onCoreOptions: () -> Unit,
    onRaLogin: () -> Unit = {},
    onBack: () -> Unit,
) {
    var ffControl by remember { mutableStateOf(store.ffControlVisible()) }
    var ffSpeed by remember { mutableStateOf(store.ffSpeed()) }
    var inputDisplay by remember { mutableStateOf(store.inputDisplayVisible()) }
    var haptics by remember { mutableStateOf(store.hapticsEnabled()) }
    // Alpha.8 — haptic strength: the slider, 0–100, 100 by
    // default. Global, not per-system: strength is about
    // the player's hand and phone, not the handheld.
    var hapticsStrength by remember { mutableStateOf(store.hapticsStrength()) }
    var rewindControl by remember { mutableStateOf(store.rewindControlVisible()) }
    // Player chrome (Robert, 2026-11-12) — the dev stats:
    // when on, the top bar stays visible during gameplay;
    // when off, the bar hides while playing. Off by default.
    var devStats by remember { mutableStateOf(store.devStatsEnabled()) }
    // Phase 8 — Jade, the Game Boy display chain (on by
    // default: each chain is the default look for its
    // system), and the palette it shows the game in.
    var jade by remember { mutableStateOf(store.jadeEnabled()) }
    var palette by remember {
        mutableStateOf(optionsStore?.get("mgba_gb_colors") ?: CoreOptions.GB_PALETTES.first())
    }
    var pickingPalette by remember { mutableStateOf(false) }
    // Phase 8 — Prism, the Game Boy Color display chain,
    // same rule as Jade.
    var prism by remember { mutableStateOf(store.prismEnabled()) }
    // Phase 8 — Dusk, the Game Boy Advance display chain,
    // same rule as Jade.
    var dusk by remember { mutableStateOf(store.duskEnabled()) }
    // Phase 9 — edge smoothing (off by default: an
    // alternative to the chains, never stacked — on turns
    // the chain off) and the combined mode (edge + light
    // grid, one tap — shown only while edge smoothing is
    // on).
    var edgeSmoothing by remember { mutableStateOf(store.edgeSmoothingEnabled()) }
    var edgeGrid by remember { mutableStateOf(store.edgeGridEnabled()) }
    // Phase 8 — Screen Optics: the Virtual Worm Light (off
    // by default — an easter egg) and the dark-room dimming
    // toggle (off by default — dimming can hide the game).
    var wormLight by remember { mutableStateOf(store.wormLightEnabled()) }
    // Worm Light realism (Robert, 2026-11-12): the colour of
    // the lamp's hardware — dark medium gray by default. The
    // light itself stays warm white and is never a setting.
    var wormLightColor by remember { mutableStateOf(store.wormLightColor()) }
    var opticsDimming by remember { mutableStateOf(store.opticsDimmingEnabled()) }
    // Cover art (Robert, 2026-11-14): let covers fetch over
    // mobile data when the user turns it on. Off by default —
    // covers download on unmetered Wi-Fi only.
    var coverArtMobileData by remember { mutableStateOf(store.coverArtMobileDataAllowed()) }
    // Task 6 (2026-11-14) — the hidden Developer section:
    // unlocked by tapping the version footer 7 times, off
    // by default (locked). The GBC reconstruction toggle
    // lives there, off by default — an experimental quality
    // path, not straight up out in the open. Task 7 adds
    // the GBA toggle beside it, same warning, same default.
    var developerUnlocked by remember { mutableStateOf(store.developerUnlocked()) }
    var reconGbc by remember { mutableStateOf(store.reconGbcEnabled()) }
    var reconGba by remember { mutableStateOf(store.reconGbaEnabled()) }
    var reconGb by remember { mutableStateOf(store.reconGbEnabled()) }
    var reconGrid by remember { mutableStateOf(store.reconGridEnabled()) }
    var footerTaps by remember { mutableStateOf(0) }
    // In-app updater (2026-11-15): the check result the section
    // shows, the work in flight, a downloaded APK waiting to be
    // installed, and whether the install needs the per-app
    // "install unknown apps" grant first. The check runs only on
    // the user's tap (or the launch check's result, passed in) —
    // never on entering Settings, and never in the background.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { currentAppVersion(context) }
    var updateResult by remember { mutableStateOf(initialUpdateCheck) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateDownloading by remember { mutableStateOf(false) }
    var downloadedUpdate by remember { mutableStateOf<java.io.File?>(null) }
    var needsUnknownAppsGrant by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    // Overlay defaults (Robert, 2026-11-14): which handheld's
    // default layout is being edited in the full-screen
    // editor, null while the list shows. defaultsNonce is
    // bumped when an edit closes, so the section re-reads
    // which handhelds are customised. editorHeld is hoisted
    // here — remember calls must stay unconditional, and the
    // editor only exists while a system is being edited.
    var editingDefaultSystem by remember { mutableStateOf<GameSystem?>(null) }
    var defaultsNonce by remember { mutableStateOf(0) }
    val editorHeld = remember { HeldInput() }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PoingPurpleDeep.copy(alpha = 0.45f))
            .testTag("settings-list"),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Input display",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Shows the buttons being pressed, above the game. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = inputDisplay,
                    onCheckedChange = {
                        store.setInputDisplayVisible(it)
                        inputDisplay = it
                    },
                    modifier = Modifier.testTag("input-display-switch"),
                )
            }
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
                        "Haptics",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A short click when you press a button — D-pad, A/B, L/R, Start and Select. On by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = haptics,
                    onCheckedChange = {
                        store.setHapticsEnabled(it)
                        haptics = it
                    },
                    modifier = Modifier.testTag("haptics-switch"),
                )
            }
        }
        // Alpha.8 — the strength slider, shown only while
        // haptics is on (same pattern as the fast-forward
        // speed and the edge grid rows): 0–100 in steps of
        // 5, 100 by default. 0 silences the click, the same
        // as the toggle off. The value writes when the drag
        // ends, not on every step — same as the Cartridge
        // Surgery sliders.
        if (haptics) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Haptic strength",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "How strong the button click feels, from 0 (silent) to 100.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Slider(
                            value = hapticsStrength.toFloat(),
                            onValueChange = { hapticsStrength = it.toInt() },
                            onValueChangeFinished = {
                                store.setHapticsStrength(hapticsStrength)
                            },
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("haptics-strength-slider"),
                        )
                        Text(
                            "$hapticsStrength%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
        item {
            Text(
                "RetroAchievements",
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
                        RaClient.status.ifEmpty { "Not logged in." },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Achievements, leaderboards and rich presence for the game you are playing. Your RetroAchievements account — nothing else is synced.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = onRaLogin) {
                    Text(if (RaClient.loggedIn) "Account" else "Log in")
                }
            }
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
                        "Rewind control",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Shows the hold-to-rewind button on the player: hold it to step back through the last 30 seconds. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = rewindControl,
                    onCheckedChange = {
                        store.setRewindControlVisible(it)
                        rewindControl = it
                    },
                    modifier = Modifier.testTag("rewind-control-switch"),
                )
            }
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
                        "Dev stats",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Keeps the top bar — frame size, core and measured frame rate, frames drawn — visible during gameplay. Off by default: the bar hides while playing and the power light stays on.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = devStats,
                    onCheckedChange = {
                        store.setDevStatsEnabled(it)
                        devStats = it
                    },
                    modifier = Modifier.testTag("dev-stats-switch"),
                )
            }
        }
        // Overlay defaults (Robert, 2026-11-14): one adjustable
        // default control layout per handheld. A game with no
        // per-game layout of its own uses its handheld's
        // default — so this is where the defaults live, and the
        // rows open the same full-screen editor the player
        // uses, against that system's default.
        if (layoutStore != null) {
            item {
                Text(
                    "Controls",
                    style = displayTextStyle(DisplaySizeMedium),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // The key carries the edit nonce: a plain state
            // read inside the item below did NOT refresh the
            // rows after an edit closed (the "Customised"
            // label stayed "Factory default" in CI), so the
            // rows are keyed on the nonce instead — after an
            // edit the old rows leave and fresh rows compose,
            // re-reading which handhelds are customised.
            item(key = "default-layouts-$defaultsNonce") {
                DefaultLayoutRows(
                    layoutStore = layoutStore,
                    onEdit = { editingDefaultSystem = it },
                )
            }
        }
        item {
            Text(
                "Display",
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
                        "Jade",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The Game Boy display chain. On by default; off shows the game exactly as the core renders it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = jade,
                    onCheckedChange = {
                        store.setJadeEnabled(it)
                        jade = it
                    },
                    modifier = Modifier.testTag("jade-chain-switch"),
                )
            }
        }
        if (jade && optionsStore != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickingPalette = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("jade-palette-row"),
                ) {
                    Text(
                        "Game Boy palette",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The palette Jade shows the game in. The DMG green and the Super Game Boy palettes live here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Current: $palette",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
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
                        "Prism",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The Game Boy Color display chain. On by default; off shows the game exactly as the core renders it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = prism,
                    onCheckedChange = {
                        store.setPrismEnabled(it)
                        prism = it
                    },
                    modifier = Modifier.testTag("prism-chain-switch"),
                )
            }
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
                        "Dusk",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The Game Boy Advance display chain. On by default; off shows the game exactly as the core renders it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = dusk,
                    onCheckedChange = {
                        store.setDuskEnabled(it)
                        dusk = it
                    },
                    modifier = Modifier.testTag("dusk-chain-switch"),
                )
            }
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
                        "Edge smoothing",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Smooths sprite edges the way a handheld would — dither-aware, so Pokémon water and Zelda shadows stay dithered, not blurred. Off by default; on turns the display chain off.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = edgeSmoothing,
                    onCheckedChange = {
                        store.setEdgeSmoothingEnabled(it)
                        edgeSmoothing = it
                    },
                    modifier = Modifier.testTag("edge-smoothing-switch"),
                )
            }
        }
        if (edgeSmoothing) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Edge smoothing + grid",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Edge smoothing with a light LCD grid over it — the curated combined mode, one tap.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = edgeGrid,
                        onCheckedChange = {
                            store.setEdgeGridEnabled(it)
                            edgeGrid = it
                        },
                        modifier = Modifier.testTag("edge-grid-switch"),
                    )
                }
            }
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
                        "Worm Light",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A warm spotlight you can drag across the screen. An easter egg, off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = wormLight,
                    onCheckedChange = {
                        store.setWormLightEnabled(it)
                        wormLight = it
                    },
                    modifier = Modifier.testTag("worm-light-switch"),
                )
            }
        }
        item {
            // Worm Light realism (Robert, 2026-11-12): the
            // hardware colour — the lamp's cord, head and plug.
            // A row of swatches beside the switch; the light
            // itself stays warm white and is never a choice.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Worm Light colour",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The lamp's cord, head and plug. The light itself stays warm white.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (color in WormLightColor.SWATCHES) {
                            val selected = wormLightColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color(color.argb).copy(
                                            alpha = if (color.translucent) 0.55f else 1f,
                                        ),
                                    )
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White
                                        else Color.White.copy(alpha = 0.35f),
                                        shape = CircleShape,
                                    )
                                    .semantics {
                                        contentDescription = color.label
                                    }
                                    .clickable {
                                        store.setWormLightColor(color)
                                        wormLightColor = color
                                    }
                                    .testTag("worm-light-color-${color.name}"),
                            )
                        }
                    }
                }
            }
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
                        "Dim in dark rooms",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Let the ambient light dim the screen in a dark room. Off by default — dimming can hide the game.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = opticsDimming,
                    onCheckedChange = {
                        store.setOpticsDimmingEnabled(it)
                        opticsDimming = it
                    },
                    modifier = Modifier.testTag("optics-dimming-switch"),
                )
            }
        }
        item {
            Text(
                "Library",
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
                        "Download cover art over mobile data",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Cover art usually downloads on unmetered Wi-Fi only. On, it may also download over mobile data — which can use your data allowance. Off by default.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = coverArtMobileData,
                    onCheckedChange = {
                        store.setCoverArtMobileDataAllowed(it)
                        coverArtMobileData = it
                    },
                    modifier = Modifier.testTag("cover-art-mobile-data-switch"),
                )
            }
        }
        // Task 6 (2026-11-14) — the hidden Developer
        // section: only after the version footer has been
        // tapped 7 times. The reconstruction toggles live
        // here, off by default — an experimental quality
        // path: GBC (Task 6), GBA (Task 7), GB (Task 5),
        // same warning, same default.
        if (developerUnlocked) {
            item {
                Text(
                    "Developer — experimental",
                    style = displayTextStyle(DisplaySizeMedium),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Text(
                    "Experimental. May look wrong and may use more battery. Replaces edge smoothing while on.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                            "Reconstruction (GBC)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "The quality path for Game Boy Color games — line reconstruction instead of edge blending. Experimental, off by default.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = reconGbc,
                        onCheckedChange = {
                            store.setReconGbcEnabled(it)
                            reconGbc = it
                        },
                        modifier = Modifier.testTag("recon-gbc-switch"),
                    )
                }
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
                            "Reconstruction (GBA)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "The quality path for Game Boy Advance games — line reconstruction instead of edge blending. Experimental, off by default.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = reconGba,
                        onCheckedChange = {
                            store.setReconGbaEnabled(it)
                            reconGba = it
                        },
                        modifier = Modifier.testTag("recon-gba-switch"),
                    )
                }
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
                            "Reconstruction (GB)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "The quality path for Game Boy games — dither reconstruction instead of edge blending. Experimental, off by default.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = reconGb,
                        onCheckedChange = {
                            store.setReconGbEnabled(it)
                            reconGb = it
                        },
                        modifier = Modifier.testTag("recon-gb-switch"),
                    )
                }
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
                            "LCD grid on reconstruction",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Layers the LCD grid at 15% over the reconstruction. One toggle for all systems.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = reconGrid,
                        onCheckedChange = {
                            store.setReconGridEnabled(it)
                            reconGrid = it
                        },
                        modifier = Modifier.testTag("recon-grid-switch"),
                    )
                }
            }
        }
        // In-app updater (Step 2 of the alpha, 2026-11-15):
        // "Check for updates" reads the public GitHub releases
        // and compares the newest tag with this build; nothing
        // downloads or installs without a tap. A found update
        // downloads on tap and installs through the system
        // installer; when the per-app "install unknown apps"
        // grant is missing, the section says so and opens the
        // system page for it instead of failing silently.
        item {
            Text(
                "Updates",
                style = displayTextStyle(DisplaySizeMedium),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Poing! ${currentVersion.display()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        updateChecking -> "Checking for updates…"
                        updateDownloading -> "Downloading update…"
                        updateError != null -> updateError!!
                        downloadedUpdate != null -> "Update downloaded — tap Install to update Poing!."
                        updateResult is UpdateCheckResult.Available ->
                            "Update available: ${(updateResult as UpdateCheckResult.Available).release.tag}"
                        updateResult is UpdateCheckResult.UpToDate -> {
                            val latest = (updateResult as UpdateCheckResult.UpToDate).latest
                            if (latest != null) "You're up to date. Latest is ${latest.tag}."
                            else "You're up to date."
                        }
                        updateResult is UpdateCheckResult.Error ->
                            "Couldn't check for updates: ${(updateResult as UpdateCheckResult.Error).detail}"
                        else -> "Check for updates to see if a newer Poing! is available. Nothing downloads without your tap."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("update-status"),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                updateChecking = true
                                updateError = null
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { checkUpdates(currentVersion) }.getOrElse {
                                        UpdateCheckResult.Error(it.javaClass.simpleName)
                                    }
                                }
                                updateResult = result
                                updateChecking = false
                            }
                        },
                        enabled = !updateChecking && !updateDownloading,
                        modifier = Modifier.testTag("check-updates-button"),
                    ) {
                        Text("Check for updates")
                    }
                    if (updateResult is UpdateCheckResult.Available && downloadedUpdate == null) {
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = {
                                val release =
                                    (updateResult as UpdateCheckResult.Available).release
                                scope.launch {
                                    updateDownloading = true
                                    updateError = null
                                    val file = withContext(Dispatchers.IO) {
                                        runCatching {
                                            downloadUpdate(
                                                release.apkUrl,
                                                context.filesDir.resolve("updates"),
                                            )
                                        }.getOrNull()
                                    }
                                    updateDownloading = false
                                    if (file == null) {
                                        updateError = "Download failed."
                                    } else {
                                        downloadedUpdate = file
                                        if (canInstallUnknownApps(context)) {
                                            context.startActivity(
                                                buildInstallIntent(context, file),
                                            )
                                        } else {
                                            needsUnknownAppsGrant = true
                                        }
                                    }
                                }
                            },
                            enabled = !updateChecking && !updateDownloading,
                            modifier = Modifier.testTag("download-update-button"),
                        ) {
                            Text("Download & install")
                        }
                    }
                    if (downloadedUpdate != null) {
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = {
                                val file = downloadedUpdate ?: return@Button
                                if (canInstallUnknownApps(context)) {
                                    context.startActivity(buildInstallIntent(context, file))
                                } else {
                                    needsUnknownAppsGrant = true
                                }
                            },
                            modifier = Modifier.testTag("install-update-button"),
                        ) {
                            Text("Install")
                        }
                    }
                }
                if (needsUnknownAppsGrant) {
                    Text(
                        "Poing! needs permission to install apps before it can update itself.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(
                            onClick = {
                                context.startActivity(unknownAppsSettingsIntent(context))
                            },
                            modifier = Modifier.testTag("unknown-apps-settings-button"),
                        ) {
                            Text("Allow installing apps")
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
                    inputDisplay = store.inputDisplayVisible()
                    haptics = store.hapticsEnabled()
                    hapticsStrength = store.hapticsStrength()
                    rewindControl = store.rewindControlVisible()
                    devStats = store.devStatsEnabled()
                    jade = store.jadeEnabled()
                    prism = store.prismEnabled()
                    dusk = store.duskEnabled()
                    wormLight = store.wormLightEnabled()
                    wormLightColor = store.wormLightColor()
                    opticsDimming = store.opticsDimmingEnabled()
                    coverArtMobileData = store.coverArtMobileDataAllowed()
                    developerUnlocked = store.developerUnlocked()
                    reconGbc = store.reconGbcEnabled()
                    reconGba = store.reconGbaEnabled()
                    reconGb = store.reconGbEnabled()
                    reconGrid = store.reconGridEnabled()
                    palette = optionsStore?.get("mgba_gb_colors")
                        ?: CoreOptions.GB_PALETTES.first()
                }) {
                    Text("Reset player settings")
                }
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            // Task 6 — the version footer: seven taps here
            // unlock the Developer section above. Small and
            // out of the way, not a settings row.
            Text(
                "Poing! 0.1.0",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("version-footer")
                    .clickable {
                        footerTaps += 1
                        if (footerTaps >= 7 && !developerUnlocked) {
                            store.setDeveloperUnlocked(true)
                            developerUnlocked = true
                        }
                    },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // Overlay defaults (Robert, 2026-11-14): the full-screen
    // editor over Settings, against the handheld's default —
    // the same editor the player uses, so a default is edited
    // exactly the way a per-game layout is. Done commits it
    // as that system's default; Back discards the draft. A
    // Done that lands exactly on the factory default clears
    // the customised default instead of saving a copy of it,
    // so the factory default is what a fresh read finds.
    val editingSystem = editingDefaultSystem
    if (editingSystem != null && layoutStore != null) {
        LayoutEditor(
            initial = layoutStore.loadDefault(editingSystem),
            isGba = editingSystem == GameSystem.GBA,
            system = editingSystem,
            held = editorHeld,
            onInputChanged = {},
            onControlDown = {},
            onDone = { draft ->
                if (draft == ControlLayout.DEFAULT) layoutStore.clearDefault(editingSystem)
                else layoutStore.saveDefault(editingSystem, draft)
                defaultsNonce++
                editingDefaultSystem = null
            },
            onBack = { editingDefaultSystem = null },
            // Resetting a handheld default restores the factory default.
            resetLayout = ControlLayout.DEFAULT,
        )
    }

    // Phase 8 — the palette picker: the core's own 48
    // palettes, same dialog shape as Core Options. A pick
    // saves immediately and takes effect on the next game
    // start (and on resume, when the chain re-reads it).
    if (pickingPalette && optionsStore != null) {
        AlertDialog(
            onDismissRequest = { pickingPalette = false },
            containerColor = PoingPurpleDeep.copy(alpha = 0.92f),
            title = { Text("Game Boy palette") },
            text = {
                LazyColumn {
                    items(CoreOptions.GB_PALETTES, key = { it }) { value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == palette,
                                    onClick = {
                                        if (optionsStore.set("mgba_gb_colors", value)) {
                                            palette = value
                                        }
                                        pickingPalette = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == palette,
                                onClick = null,
                            )
                            Text(value)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingPalette = false }) {
                    Text("Close")
                }
            },
        )
    }
    } // Box
}

/**
 * Overlay defaults (Robert, 2026-11-14): the per-handheld
 * default-layout rows. Kept out of AppSettingsScreen itself
 * so the settings method stays under the dex guard (HowlingDog
 * v1.3.0 lesson). The rows are keyed on the edit nonce at the
 * call site, so they re-read which handhelds are customised
 * after an edit closes.
 */
@Composable
private fun DefaultLayoutRows(
    layoutStore: ControlLayoutStore,
    onEdit: (GameSystem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        for (system in GameSystem.entries) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(system) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("default-layout-${system.name.lowercase()}"),
            ) {
                Text(
                    "${system.label} default layout",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "The default button positions, sizes and opacity for ${system.label} games. A game with no layout of its own uses this — tap to edit it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (layoutStore.hasDefault(system)) "Customised" else "Factory default",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

