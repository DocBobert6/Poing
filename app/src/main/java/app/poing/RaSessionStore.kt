package app.poing

import java.io.File
import java.util.Properties

/**
 * Phase 6 (item 8) — the RetroAchievements session store: one
 * properties file in filesDir (ra-session.properties) holding the
 * logged-in username and the session token. It is a separate store
 * from AppSettingsStore on purpose: a player-settings reset must
 * never wipe the login.
 *
 * Only the session token is stored — the password is used once,
 * for the login that types it, and is never written anywhere
 * (see RaClient). A rejected token is cleared, so the user types
 * their password again instead of retrying a dead token on every
 * launch.
 *
 * Alpha.23 — the RetroAchievements *web API* key lives here too:
 * a different credential from the session token, entered
 * optionally in Settings → RetroAchievements. It unlocks rarity
 * tiers and the full unlock history (both read-only web-API
 * calls). It is stored the same way as the token, in the same
 * file — a player-settings reset never touches this store — and
 * [clear] wipes it with the login, since the key belongs to that
 * account. It is never shown in full after entry: the UI shows
 * only [maskApiKey].
 *
 * Alpha.34 — the unlock-screenshot toggle lives here too, for
 * the same reason: a player-settings reset must never turn it
 * off, and logout wipes it with the login.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class RaSessionStore(private val root: File) {
    private val file: File get() = File(root, "ra-session.properties")

    companion object {
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"

        /** Alpha.23 — the web API key (see the class doc). */
        const val KEY_API_KEY = "api_key"

        /** Alpha.34 — the unlock-screenshot toggle (see the class doc). */
        const val KEY_UNLOCK_SCREENSHOT = "unlock_screenshot"
    }

    /** The logged-in username ("" when logged out). */
    fun username(): String = readAll()[KEY_USERNAME].orEmpty()

    /** The session token ("" when logged out). */
    fun token(): String = readAll()[KEY_TOKEN].orEmpty()

    /** The stored web API key ("" when none is saved). */
    fun apiKey(): String = readAll()[KEY_API_KEY].orEmpty()

    fun setApiKey(key: String) = write(KEY_API_KEY, key)

    /** Alpha.34 — the unlock-screenshot toggle (false when never set). */
    fun unlockScreenshot(): Boolean = readAll()[KEY_UNLOCK_SCREENSHOT] == "true"

    fun setUnlockScreenshot(enabled: Boolean) = write(KEY_UNLOCK_SCREENSHOT, enabled.toString())

    /** Drop the stored web API key (the Remove button). The login is untouched. */
    fun clearApiKey() {
        val props = readProperties()
        if (props.remove(KEY_API_KEY) == null) return
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Poing! RetroAchievements session") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }

    /** The stored login (username + token), or null when either is missing. */
    fun storedLogin(): Pair<String, String>? {
        val user = username().takeIf { it.isNotEmpty() } ?: return null
        val token = token().takeIf { it.isNotEmpty() } ?: return null
        return user to token
    }

    fun setUsername(username: String) = write(KEY_USERNAME, username)

    fun setToken(token: String) = write(KEY_TOKEN, token)

    /** Clear the stored login (logout). */
    fun clear() {
        file.delete()
    }

    private fun readAll(): Map<String, String> {
        val props = readProperties()
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun readProperties(): Properties {
        val props = Properties()
        if (file.exists()) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (_: Exception) {
                // A corrupt file reads as empty: logged out.
            }
        }
        return props
    }

    private fun write(key: String, value: String) {
        try {
            val props = readProperties()
            props.setProperty(key, value)
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Poing! RetroAchievements session") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}

/**
 * Alpha.23 — the masked form of a stored web API key, the only
 * form the UI ever shows: the first four and last four
 * characters, with the middle hidden. A key too short to split
 * shows as bullets only. The full key never appears on screen,
 * in a log, or in a test assertion.
 */
fun maskApiKey(key: String): String {
    if (key.isEmpty()) return ""
    if (key.length <= 8) return "•".repeat(key.length)
    return "${key.take(4)}…${key.takeLast(4)}"
}
