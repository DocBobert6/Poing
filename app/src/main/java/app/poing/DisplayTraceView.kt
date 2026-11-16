package app.poing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Alpha.28 — the black-screen trace viewer: the record
 * read from the pause menu, newest first, so Robert can
 * screenshot what the display path did. The game stays
 * paused while this is open, so the list is snapshotted
 * once on open — nothing new arrives under it.
 *
 * The empty state is the help: the trace records only
 * while the Settings → Display toggle is on, snapshotted
 * at game start, so an empty list means the toggle was
 * off when this game started.
 */
@Composable
fun DisplayTraceView(
    trace: DisplayTrace,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val events = remember(trace) { trace.snapshotNewestFirst() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("display-trace-view"),
    ) {
        Text(
            "Display trace",
            style = displayTextStyle(DisplaySizeMedium),
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (trace.enabled) {
                "Recording — ${events.size} event${if (events.size == 1) "" else "s"}, newest first."
            } else {
                "Recording is off for this game."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        if (events.isEmpty()) {
            Text(
                "No events recorded this session. Turn on Display trace in " +
                    "Settings → Display, then start a game — the trace records " +
                    "from session start, and the pause menu reads it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.testTag("display-trace-empty"),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(events, key = { "${it.atMs}-${it.label}" }) { event ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            formatDisplayTraceTime(event.atMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            event.label + if (event.detail.isEmpty()) "" else " — ${event.detail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.testTag("display-trace-close"),
        ) {
            Text("Close")
        }
    }
}
