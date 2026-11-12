package app.poing

import java.io.File
import java.util.Properties

/**
 * Phase 6 (item 5) — playtime: accumulated seconds played per
 * game, in filesDir/playtime.properties (`<base>=<seconds>`,
 * base = the game's SHA-256 stem). The session banks whole
 * seconds on pause/stop; the library reads the totals.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class PlaytimeStore(private val root: File) {
    private val file: File get() = File(root, "playtime.properties")

    fun get(base: String): Long {
        val props = readAll()
        return props.getProperty(base)?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
    }

    /** Add [seconds] to a game's total (ignored when <= 0). */
    fun add(base: String, seconds: Long) {
        if (seconds <= 0) return
        val props = readAll()
        val total = (props.getProperty(base)?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L) + seconds
        props.setProperty(base, total.toString())
        writeAll(props)
    }

    /** Drop a game's entry (removing the game). */
    fun remove(base: String) {
        val props = readAll()
        if (props.remove(base) != null) writeAll(props)
    }

    private fun readAll(): Properties {
        val f = file
        if (!f.exists()) return Properties()
        return try {
            Properties().apply { f.inputStream().use { load(it) } }
        } catch (_: Exception) {
            Properties()
        }
    }

    private fun writeAll(props: Properties) {
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Poing! playtime (seconds per game)") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}

/** "45s", "12m", "2h 5m" — hours only when there is at least one. */
fun formatPlaytime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val secs = s % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${secs}s"
    }
}
