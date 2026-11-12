package app.poing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Poing! Phase 1 — minimal skeleton. The home screen only: the app
 * name, the phase status, and the Licences screen. No games, no
 * player, no native code — the mGBA core is built by CI and is only
 * ever loaded on game start, in a later phase.
 *
 * Phase 2 fix: the content sits in [PoingTheme], which paints an
 * explicit dark scheme on a root Surface. A bare MaterialTheme
 * (default light scheme) on the black window background made all
 * plain text invisible on Robert's phone (black-on-black) — see
 * Theme.kt. Never go back to a bare MaterialTheme here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PoingTheme {
                PoingHome()
            }
        }
    }
}

@Composable
fun PoingHome() {
    var showLicences by remember { mutableStateOf(false) }
    if (showLicences) {
        LicencesScreen(onBack = { showLicences = false })
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Poing!", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("Game Boy · Game Boy Color · Game Boy Advance")
        Spacer(Modifier.height(24.dp))
        Text("Phase 1 — mGBA core import. No games yet.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = { showLicences = true }) {
            Text("Licences")
        }
    }
}
