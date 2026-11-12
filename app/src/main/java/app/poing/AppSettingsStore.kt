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
        const val KEY_INPUT_DISPLAY = "input_display_visible"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_REWIND_CONTROL = "rewind_control_visible"
        const val KEY_JADE_CHAIN = "display_jade"
        const val KEY_PRISM_CHAIN = "display_prism"
        const val DEFAULT_FF_SPEED = 2
        val FF_SPEEDS = setOf(2, 3, 4)
    }

    /** The fast-forward / slow-motion on-screen control: off by default (the standing overlay rule). */
    fun ffControlVisible(): Boolean = readBoolean(KEY_FF_CONTROL, false)
    fun setFfControlVisible(visible: Boolean) = write(KEY_FF_CONTROL, visible.toString())

    /** The input display (pressed buttons above the game): off by default (the standing overlay rule). */
    fun inputDisplayVisible(): Boolean = readBoolean(KEY_INPUT_DISPLAY, false)
    fun setInputDisplayVisible(visible: Boolean) = write(KEY_INPUT_DISPLAY, visible.toString())

    /** Haptics (D-pad and A/B clicks): on by default — it is
     * tactile feedback, not an overlay control, so the standing
     * overlay rule does not apply. */
    fun hapticsEnabled(): Boolean = readBoolean(KEY_HAPTICS, true)
    fun setHapticsEnabled(enabled: Boolean) = write(KEY_HAPTICS, enabled.toString())

    /** The rewind control (hold to step back through the last 30 seconds): off by default (the standing overlay rule). */
    fun rewindControlVisible(): Boolean = readBoolean(KEY_REWIND_CONTROL, false)
    fun setRewindControlVisible(visible: Boolean) = write(KEY_REWIND_CONTROL, visible.toString())

    /**
     * Phase 8 — the Jade display chain (GB): on by default,
     * because each chain is the default look for its system.
     * Off runs the game clean, exactly as the core rendered
     * it. Dusk gains its own key in its commit.
     */
    fun jadeEnabled(): Boolean = readBoolean(KEY_JADE_CHAIN, true)
    fun setJadeEnabled(enabled: Boolean) = write(KEY_JADE_CHAIN, enabled.toString())

    /** Phase 8 — the Prism display chain (GBC): on by default, same rule as Jade. */
    fun prismEnabled(): Boolean = readBoolean(KEY_PRISM_CHAIN, true)
    fun setPrismEnabled(enabled: Boolean) = write(KEY_PRISM_CHAIN, enabled.toString())

    /**
     * Phase 8 — whether a chain is on. A chain this build
     * does not know yet (Dusk before its commit) reads as
     * on: the mapping in DisplayChain.forSystem runs it
     * clean until its commit lands, and this is the
     * backstop.
     */
    fun chainEnabled(chain: DisplayChain): Boolean = when (chain) {
        DisplayChain.JADE -> jadeEnabled()
        DisplayChain.PRISM -> prismEnabled()
        DisplayChain.DUSK, DisplayChain.CLEAN -> true
    }

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
