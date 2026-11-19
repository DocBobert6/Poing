package app.poing

/**
 * Phase 6 (item 8) — RetroAchievements: the achievement model.
 *
 * Pure Kotlin, so it is covered by the pure-test harness. The JSON
 * comes from the native layer (nativeRaGetAchievements), which
 * builds it from the rc_client list; each entry carries the id,
 * title, description, points, state, and — for measured
 * achievements — the progress string and percent. Badge images
 * are the full media.retroachievements.org URLs rcheevos builds
 * from the badge name (Badge/<name>.png unlocked,
 * Badge/<name>_lock.png locked), carried here as [badgeUrl] /
 * [badgeLockedUrl] (null when the server gave no badge).
 *
 * States mirror rc_client_achievement_state: INACTIVE (0) means
 * the achievement can never be earned in this game, ACTIVE (1)
 * means it can, UNLOCKED (2) means the player earned it, and
 * DISABLED (3) means the game is not fully loaded or the
 * achievement is unsupported. The list screen shows unlocked and
 * active achievements; inactive ones are hidden.
 *
 * The JSON is parsed by the minimal parser below, not org.json:
 * org.json is a stub in android.jar (it throws "Stub!" on the
 * plain JVM), which is why UpdateChecker — the only other
 * org.json user — is not in the pure-test harness. The parser
 * only needs the native layer's shape: an array of flat objects
 * whose values are strings, numbers, or (for robustness)
 * true/false/null.
 */
data class RaAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    /** One of [RaAchievements.STATE_INACTIVE], [RaAchievements.STATE_ACTIVE], [RaAchievements.STATE_UNLOCKED], [RaAchievements.STATE_DISABLED]. */
    val state: Int,
    val unlocked: Boolean,
    val measuredProgress: String? = null,
    val measuredPercent: Double? = null,
    /** Full badge URL from the native layer, or null when the server gave no badge. */
    val badgeUrl: String? = null,
    /** Full locked-badge URL (the _lock.png variant), or null when the server gave no badge. */
    val badgeLockedUrl: String? = null,
) {
    val isUnlocked: Boolean get() = unlocked || state == RaAchievements.STATE_UNLOCKED

    /** The badge to show: the unlocked badge once earned, the locked one before that. */
    fun badgeUrlForDisplay(): String? = if (isUnlocked) badgeUrl else badgeLockedUrl ?: badgeUrl
}

object RaAchievements {
    const val STATE_INACTIVE = 0
    const val STATE_ACTIVE = 1
    const val STATE_UNLOCKED = 2
    const val STATE_DISABLED = 3

    /**
     * Parse the achievements JSON from the native layer: an array
     * of objects with id, title, description, points, state,
     * unlocked (an int, 0/1), badge_url, badge_locked_url, and
     * optionally measured_progress / measured_percent. Malformed
     * JSON (or a non-array) parses to an empty list — a bad
     * payload must never crash the list screen. A single
     * malformed entry is skipped, not fatal.
     */
    fun parse(json: String): List<RaAchievement> {
        val values = try {
            RaJson.parseArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<RaAchievement>(values.size)
        for (value in values) {
            // A non-object entry (a stray string, a number) is
            // skipped, not fatal.
            val obj = value as? Map<String, Any?> ?: continue
            try {
                out.add(parseOne(obj))
            } catch (_: Exception) {
                continue
            }
        }
        return out
    }

    private fun parseOne(obj: Map<String, Any?>): RaAchievement {
        // The native layer emits unlocked as an int (0/1) and
        // measured_progress as "" when there is none.
        val measuredProgress = (obj["measured_progress"] as? String)
            ?.takeIf { it.isNotEmpty() }
        val measuredPercent = (obj["measured_percent"] as? Number)?.toDouble()
        val unlockedRaw = obj["unlocked"]
        return RaAchievement(
            id = (obj["id"] as? Number)?.toInt() ?: 0,
            title = obj["title"] as? String ?: "",
            description = obj["description"] as? String ?: "",
            points = (obj["points"] as? Number)?.toInt() ?: 0,
            state = (obj["state"] as? Number)?.toInt() ?: STATE_DISABLED,
            unlocked = when (unlockedRaw) {
                is Number -> unlockedRaw.toInt() != 0
                is Boolean -> unlockedRaw
                else -> false
            },
            measuredProgress = measuredProgress,
            measuredPercent = measuredPercent,
            // The native layer emits "" when the server gave no
            // badge — that is no badge, not an empty URL.
            badgeUrl = (obj["badge_url"] as? String)?.takeIf { it.isNotEmpty() },
            badgeLockedUrl = (obj["badge_locked_url"] as? String)?.takeIf { it.isNotEmpty() },
        )
    }

    /** "3 / 12 unlocked" — the header line of the achievements screen. */
    fun summary(achievements: List<RaAchievement>): String {
        val unlocked = achievements.count { it.isUnlocked }
        return "$unlocked / ${achievements.size} unlocked"
    }

    /**
     * Alpha.31 — closest-first order, current game only: locked
     * achievements closest to done first (measured percent
     * descending; unmeasured locked last in their server order),
     * then unlocked in their server order. Pure, so the tests
     * prove the order without a composed list — a LazyColumn only
     * composes the rows in view, so a four-row order assertion
     * in the UI test is at the mercy of the test window's fold.
     */
    fun closestFirstOrder(achievements: List<RaAchievement>): List<RaAchievement> {
        val (unlocked, locked) = achievements.partition { it.isUnlocked }
        return locked.sortedByDescending { it.measuredPercent ?: -1.0 } + unlocked
    }
}
