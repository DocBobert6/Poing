package app.poing

import java.io.File
import java.util.Properties

/**
 * Phase 6 — the app-level settings store: one properties file in
 * filesDir (app-settings.properties). These are the player's own
 * settings, not the core's (those live in CoreOptionsStore): the
 * on-screen controls the standing rule keeps off the overlay
 * unless toggled on, and the speeds they run at. Only values the
 * app accepts are ever stored, so a stale or hand-edited file can
 * never turn a control on that should be off.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class AppSettingsStore(private val root: File) {
    private val file: File get() = File(root, "app-settings.properties")

    companion object {
        const val KEY_FF_CONTROL = "ff_control_visible"
        const val KEY_FF_SPEED = "ff_speed"
        const val DEFAULT_FF_SPEED = 2
        val FF_SPEEDS = setOf(2, 3, 4)
    }

    /** The fast-forward / slow-motion on-screen control: off by default (the standing overlay rule). */
    fun ffControlVisible(): Boolean = readBoolean(KEY_FF_CONTROL, false)
    fun setFfControlVisible(visible: Boolean) = write(KEY_FF_CONTROL, visible.toString())

    /** The fast-forward multiplier (2, 3 or 4); 2 by default. */
    fun ffSpeed(): Int {
        val raw = readAll()[KEY_FF_SPEED]?.toIntOrNull()
        return if (raw != null && raw in FF_SPEEDS) raw else DEFAULT_FF_SPEED
    }

    /** False (and nothing stored) when the speed is not one the app offers. */
    fun setFfSpeed(speed: Int): Boolean {
        if (speed !in FF_SPEEDS) return false
        write(KEY_FF_SPEED, speed.toString())
        return true
    }

    /** Back to the defaults. */
    fun reset() {
        file.delete()
    }

    private fun readBoolean(key: String, default: Boolean): Boolean {
        val raw = readAll()[key] ?: return default
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
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
                // A corrupt file reads as empty: defaults win.
            }
        }
        return props
    }

    private fun write(key: String, value: String) {
        try {
            val props = readProperties()
            props.setProperty(key, value)
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Poing! app settings") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}
