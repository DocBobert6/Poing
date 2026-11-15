package app.poing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The full name of a handheld, for the Handheld section rows and the per-system pages. */
internal fun GameSystem.displayName(): String = when (this) {
    GameSystem.GB -> "Game Boy"
    GameSystem.GBC -> "Game Boy Color"
    GameSystem.GBA -> "Game Boy Advance"
}

/**
 * Alpha.8 — one small page per handheld, holding exactly
 * the settings that are per-system: the display chain
 * (already per-system keys), edge smoothing (+ grid),
 * the Worm Light on/off, dimming on/off, and the default
 * control layout (already per-system). Everything else
 * stays on the main Settings page — the worm-light
 * colour is global (one light, one colour), and the
 * haptic strength is global too.
 *
 * Kept out of AppSettingsScreen itself so the settings
 * method stays under the dex guard (the DefaultLayoutRows
 * precedent). The page's state is remember(system): it
 * reads the store fresh every time it opens, so a value
 * changed elsewhere — or by Reset — shows up at once.
 * The caller keys the page on the layout-edit nonce, so
 * the layout row's "Customised" label refreshes after
 * an edit closes.
 */
@Composable
fun HandheldSettingsScreen(
    system: GameSystem,
    store: AppSettingsStore,
    // Phase 8 — the Jade palette picker writes the core's
    // palette option. Null (tests) hides the picker row.
    optionsStore: CoreOptionsStore? = null,
    // Overlay defaults (Robert, 2026-11-14) — the
    // per-handheld default control layout. Null (tests)
    // hides the layout row, same pattern as optionsStore.
    layoutStore: ControlLayoutStore? = null,
    onEditLayout: (GameSystem) -> Unit = {},
    onPickPalette: () -> Unit = {},
    onBack: () -> Unit,
) {
    val suffix = system.name.lowercase()
    val chain = DisplayChain.forSystem(system)
    val chainName = chain.name.lowercase().replaceFirstChar { it.uppercase() }
    var chainOn by remember(system) { mutableStateOf(store.chainEnabled(system)) }
    var palette by remember(system) {
        mutableStateOf(optionsStore?.get("mgba_gb_colors") ?: CoreOptions.GB_PALETTES.first())
    }
    var edgeSmoothing by remember(system) { mutableStateOf(store.edgeSmoothingEnabled(system)) }
    var edgeGrid by remember(system) { mutableStateOf(store.edgeGridEnabled(system)) }
    var wormLight by remember(system) { mutableStateOf(store.wormLightEnabled(system)) }
    var opticsDimming by remember(system) { mutableStateOf(store.opticsDimmingEnabled(system)) }

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PoingPurpleDeep.copy(alpha = 0.45f))
            .testTag("handheld-settings-page"),
    ) {
        item {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(system.displayName(), style = displayTextStyle(DisplaySizeLarge))
                Spacer(Modifier.height(8.dp))
                Text("Settings for ${system.displayName()} games only — the other handhelds keep their own.")
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text("Back to Settings")
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
                        chainName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The ${system.displayName()} display chain. On by default; off shows the game exactly as the core renders it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = chainOn,
                    onCheckedChange = {
                        store.setChainEnabled(system, it)
                        chainOn = it
                    },
                    modifier = Modifier.testTag("handheld-$suffix-chain-switch"),
                )
            }
        }
        if (system == GameSystem.GB && chainOn && optionsStore != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickPalette() }
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
                        store.setEdgeSmoothingEnabled(system, it)
                        edgeSmoothing = it
                    },
                    modifier = Modifier.testTag("handheld-$suffix-edge-smoothing-switch"),
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
                            store.setEdgeGridEnabled(system, it)
                            edgeGrid = it
                        },
                        modifier = Modifier.testTag("handheld-$suffix-edge-grid-switch"),
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
                        store.setWormLightEnabled(system, it)
                        wormLight = it
                    },
                    modifier = Modifier.testTag("handheld-$suffix-worm-light-switch"),
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
                        store.setOpticsDimmingEnabled(system, it)
                        opticsDimming = it
                    },
                    modifier = Modifier.testTag("handheld-$suffix-optics-dimming-switch"),
                )
            }
        }
        if (layoutStore != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditLayout(system) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("handheld-$suffix-default-layout"),
                ) {
                    Text(
                        "Default layout",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "The default button positions, sizes and opacity for ${system.displayName()} games. A game with no layout of its own uses this — tap to edit it.",
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
}
