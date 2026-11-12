package app.poing

import java.io.File
import java.util.Properties

/**
 * Phase 6 (item 4) — the per-game control-layout store: one
 * .properties file per game in filesDir/control-layouts/, named
 * by the game's base (the SHA-256 stem), eight keys:
 * `<group>.x` / `<group>.y` in dp, groups `dpad`,
 * `startselect`, `ab`, `lr`.
 *
 * A missing file means the defaults. A value that does not parse
 * (or is not finite) falls back to 0 for that axis only — a
 * hand-edited file can never strand a control group off screen,
 * because every loaded position is clamped by ControlPosition.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class ControlLayoutStore(private val layoutsDir: File) {
    fun file(base: String): File = File(layoutsDir, "$base.properties")

    fun load(base: String): ControlLayout {
        val f = file(base)
        if (!f.exists()) return ControlLayout.DEFAULT
        val props = try {
            Properties().apply { f.inputStream().use { load(it) } }
        } catch (_: Exception) {
            return ControlLayout.DEFAULT
        }
        fun read(group: String): ControlPosition {
            val x = props.getProperty("$group.x")?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
            val y = props.getProperty("$group.y")?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
            return ControlPosition(x, y).clamped()
        }
        return ControlLayout(
            dpad = read("dpad"),
            startSelect = read("startselect"),
            ab = read("ab"),
            lr = read("lr"),
        )
    }

    fun save(base: String, layout: ControlLayout) {
        try {
            val props = Properties()
            fun write(group: String, pos: ControlPosition) {
                props.setProperty("$group.x", pos.xDp.toString())
                props.setProperty("$group.y", pos.yDp.toString())
            }
            write("dpad", layout.dpad)
            write("startselect", layout.startSelect)
            write("ab", layout.ab)
            write("lr", layout.lr)
            file(base).parentFile?.mkdirs()
            file(base).outputStream().use { props.store(it, "Poing! control layout (dp offsets)") }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}
