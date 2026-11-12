package app.poing

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.poing.R

/**
 * The Licences / About screen (groundwork, Phase 1): the full
 * licence texts for every library compiled into the APK, plus the
 * frontend's GPL-3.0 note and the Nintendo disclaimer.
 *
 * The text is the repo-root NOTICE file, copied verbatim into
 * `res/raw/notice.txt` — CI diffs the two, so they cannot drift
 * apart. The NOTICE preamble states the frontend is GPL-3.0 and
 * that Poing! is not affiliated with or endorsed by Nintendo, so
 * the screen itself only adds a title and a back button.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notice = remember { loadNoticeText(context) }
    Column(
        // statusBarsPadding: the app draws edge-to-edge
        // (targetSdk 36 + activity 1.11 enable it by default), so
        // without this the "Licences" title sits in the status-bar
        // band. Same class of fault as the power-light fix.
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
    ) {
        Text("Licences", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(notice)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

fun loadNoticeText(context: Context): String {
    return context.resources.openRawResource(R.raw.notice).bufferedReader().use { it.readText() }
}
