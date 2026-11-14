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
        const val KEY_DUSK_CHAIN = "display_dusk"
        const val KEY_EDGE_SMOOTHING = "display_edge_smoothing"
        const val KEY_EDGE_GRID = "display_edge_grid"
        const val KEY_WORM_LIGHT = "display_worm_light"
        const val KEY_WORM_LIGHT_COLOR = "display_worm_light_color"
        const val KEY_OPTICS_DIMMING = "display_optics_dimming"
        const val KEY_DEV_STATS = "dev_stats_visible"
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
     * it.
     */
    fun jadeEnabled(): Boolean = readBoolean(KEY_JADE_CHAIN, true)
    fun setJadeEnabled(enabled: Boolean) = write(KEY_JADE_CHAIN, enabled.toString())

    /** Phase 8 — the Prism display chain (GBC): on by default, same rule as Jade. */
    fun prismEnabled(): Boolean = readBoolean(KEY_PRISM_CHAIN, true)
    fun setPrismEnabled(enabled: Boolean) = write(KEY_PRISM_CHAIN, enabled.toString())

    /** Phase 8 — the Dusk display chain (GBA): on by default, same rule as Jade. */
    fun duskEnabled(): Boolean = readBoolean(KEY_DUSK_CHAIN, true)
    fun setDuskEnabled(enabled: Boolean) = write(KEY_DUSK_CHAIN, enabled.toString())

    /**
     * Phase 9 — edge smoothing: off by default. When on, the
     * display chain turns off (see EdgeSmoothing.resolve) —
     * the two are alternatives, never stacked.
     */
    fun edgeSmoothingEnabled(): Boolean = readBoolean(KEY_EDGE_SMOOTHING, false)
    fun setEdgeSmoothingEnabled(enabled: Boolean) = write(KEY_EDGE_SMOOTHING, enabled.toString())

    /**
     * Phase 9 — the combined mode: edge smoothing with a
     * light LCD grid over it, one tap. Only in effect while
     * edge smoothing itself is on — on its own it is
     * nothing (the grid belongs to the chains, and the
     * chains are off while edge is on).
     */
    fun edgeGridEnabled(): Boolean = readBoolean(KEY_EDGE_GRID, false)
    fun setEdgeGridEnabled(enabled: Boolean) = write(KEY_EDGE_GRID, enabled.toString())

    /** Phase 9 — the edge mode in effect: OFF, EDGE, or EDGE_GRID (the combined mode). */
    fun edgeMode(): EdgeMode {
        if (!edgeSmoothingEnabled()) return EdgeMode.OFF
        return if (edgeGridEnabled()) EdgeMode.EDGE_GRID else EdgeMode.EDGE
    }

    /**
     * Phase 8 — the Virtual Worm Light: off by default. A
     * small toggle only, an easter egg, not a headline
     * feature.
     */
    fun wormLightEnabled(): Boolean = readBoolean(KEY_WORM_LIGHT, false)
    fun setWormLightEnabled(enabled: Boolean) = write(KEY_WORM_LIGHT, enabled.toString())

    /**
     * Worm Light realism (Robert, 2026-11-12) — the colour
     * of the lamp's hardware (cord, head plastic, plug).
     * Dark medium gray by default. The light itself is
     * always warm white and is never a setting.
     */
    fun wormLightColor(): WormLightColor =
        WormLightColor.fromName(readAll()[KEY_WORM_LIGHT_COLOR])

    fun setWormLightColor(color: WormLightColor) =
        write(KEY_WORM_LIGHT_COLOR, color.name)

    /**
     * Phase 8 — Screen Optics: whether a dark room may dim
     * the screen. OFF by default — dimming for authenticity
     * fights the player seeing the game, so a dark room never
     * dims the picture unless the player asked for it.
     */
    fun opticsDimmingEnabled(): Boolean = readBoolean(KEY_OPTICS_DIMMING, false)
    fun setOpticsDimmingEnabled(enabled: Boolean) = write(KEY_OPTICS_DIMMING, enabled.toString())

    /**
     * Player chrome (Robert, 2026-11-12) — the dev stats:
     * the top bar (frame size, core and measured frame rate,
     * frames drawn) stays visible during gameplay when this
     * is on. OFF by default: the bar hides while playing and
     * only the power light stays on.
     */
    fun devStatsEnabled(): Boolean = readBoolean(KEY_DEV_STATS, false)
    fun setDevStatsEnabled(enabled: Boolean) = write(KEY_DEV_STATS, enabled.toString())

    /**
     * Phase 8 — whether a chain is on. Clean is never gated
     * by a toggle.
     */
    fun chainEnabled(chain: DisplayChain): Boolean = when (chain) {
        DisplayChain.JADE -> jadeEnabled()
        DisplayChain.PRISM -> prismEnabled()
        DisplayChain.DUSK -> duskEnabled()
        DisplayChain.CLEAN -> true
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
