package app.poing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** How long the Pocket power-on boot screen shows, in milliseconds. */
const val BOOT_SPLASH_MS = 1000L

/**
 * Phase 5 — the dot-matrix boot (idea 9): a brief "Poing!" in
 * DotGothic16 on a dark screen at launch, Pocket power-on style,
 * auto-dismissed after [BOOT_SPLASH_MS]. Plain text only — no gold
 * ring here; the ring lives behind the wordmark on the home screen
 * only (idea 7).
 */
@Composable
fun BootSplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("boot-splash"),
        contentAlignment = Alignment.Center,
    ) {
        Text("Poing!", style = displayTextStyle(DisplaySizeLarge), color = Color.White)
    }
}
