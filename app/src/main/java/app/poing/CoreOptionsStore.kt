package app.poing

import java.io.File
import java.util.Properties

/**
 * Phase 4 — the core-options store: one properties file in
 * filesDir (core-options.properties), keyed by the mgba_* key.
 * Only values the core accepts are ever stored: [set] rejects an
 * unknown key or a value outside the option's list, so a stale or
 * hand-edited file can never push garbage at the core.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class CoreOptionsStore(private val root: File) {
    private val file: File get() = File(root, "core-options.properties")

    /** Current value: the stored one when valid, else the documented default. */
    fun get(key: String): String {
        val option = CoreOptions.byKey(key) ?: return ""
        return readAll()[key]?.takeIf { it in option.values } ?: option.default
    }

    /** Every option's current value, in the core's order. */
    fun all(): Map<String, String> =
        CoreOptions.ALL.associate { it.key to get(it.key) }

    /** Store one value. False when the key is unknown or the value is not one the core accepts. */
    fun set(key: String, value: String): Boolean {
        val option = CoreOptions.byKey(key) ?: return false
        if (value !in option.values) return false
        val props = readProperties()
        props.setProperty(key, value)
        writeProperties(props)
        return true
    }

    /** Back to the documented defaults. */
    fun reset() {
        file.delete()
    }

    private fun readAll(): Map<String, String> {
        val props = readProperties()
        return CoreOptions.ALL.associate { it.key to (props.getProperty(it.key) ?: "") }
            .filterValues { it.isNotEmpty() }
    }

    private fun readProperties(): Properties {
        val props = Properties()
        if (file.exists()) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (_: Exception) {
                // A corrupt file reads as empty: defaults win.
            }
        }
        return props
    }

    private fun writeProperties(props: Properties) {
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Poing! core options (mgba_*)") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}
