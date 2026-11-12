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
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class RaSessionStore(private val root: File) {
    private val file: File get() = File(root, "ra-session.properties")

    companion object {
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
    }

    /** The logged-in username ("" when logged out). */
    fun username(): String = readAll()[KEY_USERNAME].orEmpty()

    /** The session token ("" when logged out). */
    fun token(): String = readAll()[KEY_TOKEN].orEmpty()

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
