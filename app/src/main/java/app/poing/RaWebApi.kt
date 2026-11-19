package app.poing

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Alpha.23 — RetroAchievements rarity tiers and the full unlock
 * history, from the RA *web API* (not the Connect API the
 * emulator core talks to). The web API needs a web API key — a
 * different credential from the session token, entered
 * optionally in Settings → RetroAchievements and stored in
 * [RaSessionStore]. Without a key nothing here runs: the hub
 * keeps showing exactly what it already shows, and the
 * rarity/history views simply do not appear — no error, no nag.
 *
 * Two endpoints, both read-only GETs:
 *
 * - rarity: API_GetGameInfoAndUserProgress.php (z=username,
 *   y=key, g=game id) — each achievement carries NumAwarded
 *   (softcore awards; Poing is softcore-only) and the game
 *   carries NumDistinctPlayersCasual (softcore players), so the
 *   rarity is 100 * awarded / players. The tiers are the
 *   community-standard bands (Common >= 50%, Uncommon >= 25%,
 *   Rare >= 10%, Very Rare >= 5%, Ultra Rare >= 2%, Legendary
 *   below 2%): labels only, in the existing gold/silver text —
 *   no new colour system, no redesign.
 * - history: API_GetUserRecentAchievements.php (z=username,
 *   y=key, m=minutes) — one entry per unlock with its date.
 *   The window is 10 years, so the call returns the whole
 *   history for any account under the server's 500-record cap;
 *   the list is sorted newest first and capped at that same
 *   500.
 *
 * The HTTP layer is injectable so the parsing and the tiers are
 * testable on the plain JVM without a network, the same pattern
 * as [RaBadgeStore]. The production fetcher caps the body and
 * returns null on any non-200 response or failure — a failed
 * fetch leaves the views hidden, never an error on screen.
 *
 * Pure Kotlin (java.net only) — no Android APIs — so the whole
 * file runs under plain JVM unit tests.
 */

/** The state of one web-API fetch, read by the screens that show its data. */
enum class RaWebApiState {
    /** Nothing attempted yet (or no key stored — the views simply do not appear). */
    Idle,
    /** A fetch is in flight. */
    Loading,
    /** Data is in and the views may show it. */
    Loaded,
    /** Not attempted: no key, no game, or the network gate closed. */
    Unavailable,
    /** The fetch failed — the views stay hidden, never an error on screen. */
    Failed,
}

/** One achievement's rarity: how many softcore players earned it, out of how many played. */
data class RaRarity(
    val achievementId: Int,
    val awarded: Int,
    val players: Int,
    /** 0..100, the share of softcore players who earned it (0 when no player count is known). */
    val percent: Double,
    val tier: RaRarityTier,
)

/** Rarity tiers — labels only, in the existing text colours. */
enum class RaRarityTier(val label: String) {
    Common("Common"),
    Uncommon("Uncommon"),
    Rare("Rare"),
    VeryRare("Very Rare"),
    UltraRare("Ultra Rare"),
    Legendary("Legendary"),
}

/** The tier for a rarity percent (see the class doc for the bands). */
fun raRarityTier(percent: Double): RaRarityTier = when {
    percent >= 50.0 -> RaRarityTier.Common
    percent >= 25.0 -> RaRarityTier.Uncommon
    percent >= 10.0 -> RaRarityTier.Rare
    percent >= 5.0 -> RaRarityTier.VeryRare
    percent >= 2.0 -> RaRarityTier.UltraRare
    else -> RaRarityTier.Legendary
}

/** "12%" for a common rarity, "1.4%" for a rare one — whole numbers above 10%, one decimal below. */
fun formatRarityPercent(percent: Double): String =
    if (percent >= 10.0) "${percent.toInt()}%"
    else String.format(Locale.US, "%.1f%%", percent)

/**
 * Alpha.33 — the unlock popup's rarity kind: which visual
 * variant (and which tier haptic) an unlock gets, from the
 * web-API rarity percent already in hand. Under 10% is rare
 * (Very Rare and below in the community bands); under 5% is
 * ultra-rare (Ultra Rare and Legendary). A null percent — no
 * key, no fetch, or a fetch that never landed — is the plain
 * gold popup, never an invented tier: rarity that is not
 * known is not shown.
 */
enum class RaUnlockRarityKind {
    PLAIN,
    RARE,
    ULTRA_RARE,
}

fun raUnlockRarityKind(percent: Double?): RaUnlockRarityKind = when {
    percent == null -> RaUnlockRarityKind.PLAIN
    percent < 5.0 -> RaUnlockRarityKind.ULTRA_RARE
    percent < 10.0 -> RaUnlockRarityKind.RARE
    else -> RaUnlockRarityKind.PLAIN
}

/** One unlock from the account's history, newest first. */
data class RaHistoryEntry(
    val achievementId: Int,
    val title: String,
    val gameTitle: String,
    val points: Int,
    /** Wall time the unlock happened, epoch millis. */
    val unlockedAt: Long,
    val badgeUrl: String? = null,
)

fun interface RaWebApiFetcher {
    /** Returns the response body for a web-API GET, or null when unavailable. Only ever called when a key is stored and the network gate allows it. */
    fun fetch(url: String): String?
}

/** A capped GET: null on any non-200 response, any over-cap body, or any failure. */
fun defaultRaWebApiHttpGet(url: String, maxBytes: Int = RaWebApi.MAX_RESPONSE_BYTES): String? {
    val conn = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Poing")
        }
    } catch (_: Exception) {
        return null
    }
    return try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return null // over the cap
            }
        }
        out.toString(Charsets.UTF_8.name())
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

object RaWebApi {
    const val BASE_URL = "https://retroachievements.org/API/"

    /** A game-info response is one JSON object; 2 MB sits far above any real set. */
    const val MAX_RESPONSE_BYTES: Int = 2 * 1024 * 1024

    /** The server returns at most 500 records per call — the history list stops there too. */
    const val MAX_HISTORY_ENTRIES: Int = 500

    /** 10 years of minutes: the recent-achievements window that covers a whole account history. */
    const val HISTORY_WINDOW_MINUTES: Int = 5_256_000

    fun gameInfoUrl(username: String, apiKey: String, gameId: Int): String =
        "${BASE_URL}API_GetGameInfoAndUserProgress.php" +
            "?z=${urlEncode(username)}&y=${urlEncode(apiKey)}&g=$gameId"

    fun recentAchievementsUrl(username: String, apiKey: String, minutes: Int = HISTORY_WINDOW_MINUTES): String =
        "${BASE_URL}API_GetUserRecentAchievements.php" +
            "?z=${urlEncode(username)}&y=${urlEncode(apiKey)}&m=$minutes"

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    /**
     * Parse a game-info response into per-achievement rarity,
     * keyed by achievement id. Malformed JSON (or a missing
     * player count) parses to an empty map — a bad payload must
     * never crash a list screen. A single malformed achievement
     * entry is skipped, not fatal. The achievements arrive as
     * an object keyed by achievement id, not an array.
     */
    fun parseGameRarity(json: String): Map<Int, RaRarity> {
        val obj = try {
            RaJson.parseObject(json)
        } catch (_: Exception) {
            return emptyMap()
        }
        val players = (obj["NumDistinctPlayersCasual"] as? Number)?.toInt() ?: 0
        val achievements = obj["Achievements"] as? Map<String, Any?> ?: return emptyMap()
        val out = LinkedHashMap<Int, RaRarity>()
        for ((idRaw, value) in achievements) {
            val id = idRaw.toIntOrNull() ?: continue
            val entry = value as? Map<String, Any?> ?: continue
            try {
                val awarded = (entry["NumAwarded"] as? Number)?.toInt() ?: continue
                val percent = if (players > 0) awarded.toDouble() / players.toDouble() * 100.0 else 0.0
                out[id] = RaRarity(
                    achievementId = id,
                    awarded = awarded,
                    players = players,
                    percent = percent,
                    tier = raRarityTier(percent),
                )
            } catch (_: Exception) {
                continue
            }
        }
        return out
    }

    /**
     * Alpha.31 — the loaded game's guide URL from a game-info
     * response (top-level "GuideURL"). Null when RA supplies
     * none — which is usual — or when the payload is malformed.
     * Search for Help opens this when it exists and falls back
     * to a web search otherwise.
     */
    fun parseGuideUrl(json: String): String? {
        val obj = try {
            RaJson.parseObject(json)
        } catch (_: Exception) {
            return null
        }
        return (obj["GuideURL"] as? String)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Alpha.31 — the Search for Help fallback: a web search for
     * the game and the achievement on RetroAchievements. Web
     * search, not video search — it finds text guides and videos
     * both. Pure, so the tests prove the query string without
     * launching a browser.
     */
    fun helpSearchUrl(gameTitle: String, achievementTitle: String): String =
        "https://www.google.com/search?q=" +
            urlEncode("$gameTitle $achievementTitle RetroAchievements")

    /**
     * Parse a recent-achievements response into the unlock
     * history, newest first, capped at [MAX_HISTORY_ENTRIES].
     * Malformed JSON parses to an empty list. A single malformed
     * entry is skipped, not fatal. The badge URL is built from
     * the badge name, the same media host the native layer uses.
     */
    fun parseRecentAchievements(json: String): List<RaHistoryEntry> {
        val values = try {
            RaJson.parseArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<RaHistoryEntry>(values.size)
        for (value in values) {
            val obj = value as? Map<String, Any?> ?: continue
            try {
                val id = (obj["AchievementID"] as? Number)?.toInt() ?: continue
                val dateRaw = obj["Date"] as? String ?: continue
                val at = parseRaDate(dateRaw) ?: continue
                val badgeName = (obj["BadgeName"] as? String)?.takeIf { it.isNotEmpty() }
                out.add(
                    RaHistoryEntry(
                        achievementId = id,
                        title = obj["Title"] as? String ?: "",
                        gameTitle = obj["GameTitle"] as? String ?: "",
                        points = (obj["Points"] as? Number)?.toInt() ?: 0,
                        unlockedAt = at,
                        badgeUrl = badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" },
                    ),
                )
            } catch (_: Exception) {
                continue
            }
        }
        out.sortByDescending { it.unlockedAt }
        return out.take(MAX_HISTORY_ENTRIES)
    }

    /**
     * The web API's date shape ("2026-11-16 14:32:00", UTC) to
     * epoch millis. Null when the string does not parse — the
     * entry is skipped, not fatal.
     */
    fun parseRaDate(raw: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(raw)?.time
        } catch (_: Exception) {
            null
        }
    }
}
