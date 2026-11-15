package app.poing

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Achievement Center's memory (hub + badges, 2026-11-14):
 * what the hub shows without launching a game. Two lists in one
 * small JSON file, filesDir/ra-hub.json:
 *
 * - one cached set per game that has been loaded at least once
 *   (title, X/Y unlocked, points earned/total, a representative
 *   badge URL), snapshotted by RaClient whenever a game's set is
 *   fresh — on load, on unlock, on quit;
 * - the most recent unlocks (title, game, points, the time it
 *   happened, the badge URL), most recent first, capped.
 *
 * Only games with cached set data appear: the hub never claims a
 * never-loaded game has no achievements — the screen says
 * plainly that unplayed games appear after their first launch.
 * Nothing here hashes a ROM or queries RA: the cache is built
 * only from data the session already held in memory.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests. The JSON is written by hand (with
 * [jsonEscape]) and read back through [RaJson], the same minimal
 * parser the native payloads use.
 */
data class RaHubGame(
    val id: String,
    val title: String,
    val total: Int,
    val unlocked: Int,
    val pointsEarned: Int,
    val pointsTotal: Int,
    /** Wall time the game was last played (a snapshot time), epoch millis. */
    val lastPlayed: Long,
    /** One badge for the row: the latest unlocked achievement's badge, else the first locked one. */
    val badgeUrl: String? = null,
) {
    /** 0..1, for the progress bar (0 when the set is empty). */
    val progress: Float get() = raProgressFraction(unlocked, total)

    /** True when every achievement in the set is unlocked (and the set is not empty). */
    val completed: Boolean get() = raIsCompleted(unlocked, total)
}

data class RaRecentUnlock(
    val achievementId: Int,
    val title: String,
    val gameId: String,
    val gameTitle: String,
    val points: Int,
    /** Wall time the unlock happened, epoch millis. */
    val unlockedAt: Long,
    val badgeUrl: String? = null,
)

data class RaHubData(
    val games: List<RaHubGame>,
    val recent: List<RaRecentUnlock>,
)

class RaHubStore(private val root: File) {
    private val file: File get() = File(root, "ra-hub.json")

    companion object {
        /** Recent unlocks are history, not a log: the list stops here. */
        const val MAX_RECENT: Int = 50
    }

    /** Everything the hub shows, in display order (games by last played, recent by unlock time). */
    fun load(): RaHubData {
        if (!file.exists()) return RaHubData(emptyList(), emptyList())
        return try {
            val obj = RaJson.parseObject(file.readText())
            val games = (obj["games"] as? List<*>)?.mapNotNull { parseGame(it) }
                ?.sortedByDescending { it.lastPlayed } ?: emptyList()
            val recent = (obj["recent"] as? List<*>)?.mapNotNull { parseRecent(it) }
                ?.sortedByDescending { it.unlockedAt } ?: emptyList()
            RaHubData(games, recent)
        } catch (_: Exception) {
            // A corrupt file reads as empty: the hub shows
            // nothing rather than crashing, and the next
            // snapshot rewrites it.
            RaHubData(emptyList(), emptyList())
        }
    }

    /** Save (or refresh) one game's cached set. */
    fun snapshotGame(game: RaHubGame) {
        val data = load()
        val games = data.games.filter { it.id != game.id } + game
        write(RaHubData(games, data.recent))
    }

    /** Record one unlock, most recent first. A re-unlock of the same achievement replaces its old entry. */
    fun recordUnlock(unlock: RaRecentUnlock) {
        val data = load()
        val recent = (listOf(unlock) + data.recent.filter {
            !(it.achievementId == unlock.achievementId && it.gameId == unlock.gameId)
        }).take(MAX_RECENT)
        write(RaHubData(data.games, recent))
    }

    /** Drop a game's cached set (the game was removed from the library). Recent unlocks stay — they are history. */
    fun removeGame(id: String) {
        val data = load()
        if (data.games.none { it.id == id }) return
        write(RaHubData(data.games.filter { it.id != id }, data.recent))
    }

    private fun parseGame(value: Any?): RaHubGame? {
        val obj = value as? Map<String, Any?> ?: return null
        return try {
            RaHubGame(
                id = obj["id"] as? String ?: return null,
                title = obj["title"] as? String ?: "",
                total = (obj["total"] as? Number)?.toInt() ?: 0,
                unlocked = (obj["unlocked"] as? Number)?.toInt() ?: 0,
                pointsEarned = (obj["pointsEarned"] as? Number)?.toInt() ?: 0,
                pointsTotal = (obj["pointsTotal"] as? Number)?.toInt() ?: 0,
                lastPlayed = (obj["lastPlayed"] as? Number)?.toLong() ?: 0L,
                badgeUrl = (obj["badgeUrl"] as? String)?.takeIf { it.isNotEmpty() },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRecent(value: Any?): RaRecentUnlock? {
        val obj = value as? Map<String, Any?> ?: return null
        return try {
            RaRecentUnlock(
                achievementId = (obj["achievementId"] as? Number)?.toInt() ?: return null,
                title = obj["title"] as? String ?: "",
                gameId = obj["gameId"] as? String ?: "",
                gameTitle = obj["gameTitle"] as? String ?: "",
                points = (obj["points"] as? Number)?.toInt() ?: 0,
                unlockedAt = (obj["unlockedAt"] as? Number)?.toLong() ?: 0L,
                badgeUrl = (obj["badgeUrl"] as? String)?.takeIf { it.isNotEmpty() },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun write(data: RaHubData) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                append("{\"games\":[")
                data.games.forEachIndexed { i, g ->
                    if (i > 0) append(',')
                    append("{\"id\":${jsonString(g.id)},")
                    append("\"title\":${jsonString(g.title)},")
                    append("\"total\":${g.total},")
                    append("\"unlocked\":${g.unlocked},")
                    append("\"pointsEarned\":${g.pointsEarned},")
                    append("\"pointsTotal\":${g.pointsTotal},")
                    append("\"lastPlayed\":${g.lastPlayed},")
                    append("\"badgeUrl\":${jsonString(g.badgeUrl)}")
                    append('}')
                }
                append("],\"recent\":[")
                data.recent.forEachIndexed { i, r ->
                    if (i > 0) append(',')
                    append("{\"achievementId\":${r.achievementId},")
                    append("\"title\":${jsonString(r.title)},")
                    append("\"gameId\":${jsonString(r.gameId)},")
                    append("\"gameTitle\":${jsonString(r.gameTitle)},")
                    append("\"points\":${r.points},")
                    append("\"unlockedAt\":${r.unlockedAt},")
                    append("\"badgeUrl\":${jsonString(r.badgeUrl)}")
                    append('}')
                }
                append("]}")
            })
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }

    private fun jsonString(value: String?): String =
        if (value == null) "null" else "\"${jsonEscape(value)}\""
}

/** Escape one string for the hand-written hub JSON. */
internal fun jsonEscape(value: String): String = buildString {
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

/** 0..1 progress for X of Y (0 when the set is empty, clamped when the counts disagree). */
fun raProgressFraction(unlocked: Int, total: Int): Float {
    if (total <= 0) return 0f
    return (unlocked.coerceIn(0, total).toFloat() / total.toFloat())
}

/** True when every achievement in the set is unlocked (and the set is not empty). */
fun raIsCompleted(unlocked: Int, total: Int): Boolean = total > 0 && unlocked >= total

/** "Nov 14, 22:30" for an unlock time — the same shape as the save-state times. */
fun formatUnlockTime(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
