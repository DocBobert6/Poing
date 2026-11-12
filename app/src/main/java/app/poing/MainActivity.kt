package app.poing

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Poing! Phase 2 — bare player spike. The home screen picks a game
 * (from the user's own files, via the system document picker) and
 * boots it in the player screen; the Licences screen stays. No
 * library, no save states, no settings screen yet — those are later
 * phases. No native code is loaded at launch — the core is only ever
 * loaded on game start, so the Robolectric startup gate still runs
 * with no native code present.
 *
 * Phase 2 fix (see Theme.kt): the content sits in [PoingTheme],
 * which paints an explicit dark scheme on a root Surface. A bare
 * MaterialTheme (default light scheme) on the black window
 * background made all plain text invisible on Robert's phone
 * (black-on-black). Never go back to a bare MaterialTheme here.
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

private sealed interface Screen {
    data object Home : Screen
    data object Licences : Screen
    data class Player(val path: String, val name: String, val isGba: Boolean) : Screen
}

@Composable
fun PoingHome() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // The spike's game picker: the system document picker, any file.
    // The picked file is copied into the app's cache (the core loads
    // by path) and booted. .zip lands in Phase 3, not the spike.
    val pickGame = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri) ?: "game"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "zip") {
            error = "ZIP files land in Phase 3 — pick a .gb, .gbc or .gba file."
            return@rememberLauncherForActivityResult
        }
        if (ext !in setOf("gb", "gbc", "gba")) {
            error = "That is not a Game Boy file — pick a .gb, .gbc or .gba file."
            return@rememberLauncherForActivityResult
        }
        val staged = stagePickedGame(context, uri, name)
        if (staged == null) {
            error = "Could not read that file."
            return@rememberLauncherForActivityResult
        }
        error = null
        screen = Screen.Player(staged.absolutePath, name, ext == "gba")
    }

    when (val s = screen) {
        is Screen.Player -> GameScreen(
            gamePath = s.path,
            gameName = s.name,
            isGba = s.isGba,
            onQuit = { screen = Screen.Home },
            onError = { message ->
                error = message
                screen = Screen.Home
            },
        )
        is Screen.Licences -> LicencesScreen(onBack = { screen = Screen.Home })
        is Screen.Home -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Poing!", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text("Game Boy · Game Boy Color · Game Boy Advance")
            Spacer(Modifier.height(24.dp))
            Text("Phase 2 — bare player spike. Pick a game to boot it.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = { pickGame.launch(arrayOf("*/*")) }) {
                Text("Play a game")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { screen = Screen.Licences }) {
                Text("Licences")
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it)
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

/** Copy the picked file into the app cache so the core can load it by path. */
private fun stagePickedGame(context: Context, uri: Uri, name: String): File? {
    return try {
        val dir = File(context.cacheDir, "spike").apply { mkdirs() }
        val out = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out
    } catch (_: Exception) {
        null
    }
}
