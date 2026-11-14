package app.poing

import java.io.File
import java.util.Properties

/**
 * Overlay rework (Robert, 2026-11-13) — the per-game
 * control-layout store: one .properties file per game in
 * filesDir/control-layouts/, named by the game's base (the
 * SHA-256 stem). The new keys are one triple per control:
 * `<id>.cx` / `<id>.cy` (fractions of the screen) /
 * `<id>.size` (dp), ids `dpad`, `a`, `b`, `l`, `r`, `start`,
 * `select`, plus `opacity` (0–100, default 100).
 *
 * Migration: a file in the old Phase 6 format — one
 * `<group>.x` / `<group>.y` dp-offset pair per group
 * (`dpad`, `startselect`, `ab`, `lr`) — converts once, on
 * load: each group's offset becomes a fraction delta from
 * that group's default spot on the reference canvas, applied
 * to every member of the group, and the file is rewritten in
 * the new format. Nothing is wiped: a game whose controls
 * were dragged keeps its tuned spots.
 *
 * A missing file means the defaults. A value that does not
 * parse (or is not finite) falls back to the default for that
 * field only — a hand-edited file can never strand a control
 * off screen, because every loaded control is clamped.
 *
 * Pure java.io — no Android APIs — so the whole store runs
 * under plain JVM unit tests.
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
        // New format: per-control fractions + size + opacity.
        val hasNewKeys = ControlId.entries.any { id ->
            props.getProperty("${id.key}.cx") != null ||
                props.getProperty("${id.key}.cy") != null ||
                props.getProperty("${id.key}.size") != null
        }
        if (hasNewKeys) return readNew(props)
        // Old format: per-group dp offsets — migrate once.
        val hasOldKeys = OLD_GROUPS.any { group ->
            props.getProperty("$group.x") != null ||
                props.getProperty("$group.y") != null
        }
        if (!hasOldKeys) return ControlLayout.DEFAULT
        val migrated = migrate(props)
        save(base, migrated)
        return migrated
    }

    private fun readNew(props: Properties): ControlLayout {
        fun readControl(id: ControlId): OverlayControl {
            val def = ControlLayout.DEFAULT.control(id)
            val cx = props.getProperty("${id.key}.cx")?.toFloatOrNull()
                ?.takeIf { it.isFinite() } ?: def.cx
            val cy = props.getProperty("${id.key}.cy")?.toFloatOrNull()
                ?.takeIf { it.isFinite() } ?: def.cy
            val size = props.getProperty("${id.key}.size")?.toIntOrNull() ?: def.sizeDp
            return OverlayControl(id, cx, cy, size).clamped()
        }
        val opacity = props.getProperty("opacity")?.toIntOrNull()
            ?.coerceIn(ControlLayout.MIN_OPACITY, ControlLayout.MAX_OPACITY)
            ?: ControlLayout.DEFAULT_OPACITY
        return ControlLayout(
            controls = ControlId.entries.map { readControl(it) },
            opacity = opacity,
        )
    }

    private fun migrate(props: Properties): ControlLayout {
        fun offset(group: String): Pair<Float, Float> {
            val x = props.getProperty("$group.x")?.toFloatOrNull()
                ?.takeIf { it.isFinite() } ?: 0f
            val y = props.getProperty("$group.y")?.toFloatOrNull()
                ?.takeIf { it.isFinite() } ?: 0f
            return x to y
        }
        val groupOf = mapOf(
            "dpad" to listOf(ControlId.DPAD),
            "ab" to listOf(ControlId.A, ControlId.B),
            "lr" to listOf(ControlId.L, ControlId.R),
            "startselect" to listOf(ControlId.START, ControlId.SELECT),
        )
        var layout = ControlLayout.DEFAULT
        for ((group, members) in groupOf) {
            val (dxDp, dyDp) = offset(group)
            if (dxDp == 0f && dyDp == 0f) continue
            val dx = dxDp / ControlLayout.REFERENCE_WIDTH_DP
            val dy = dyDp / ControlLayout.REFERENCE_HEIGHT_DP
            for (id in members) {
                val old = layout.control(id)
                layout = layout.withControl(
                    OverlayControl(id, old.cx + dx, old.cy + dy, old.sizeDp).clamped(),
                )
            }
        }
        return layout
    }

    fun save(base: String, layout: ControlLayout) {
        try {
            val props = Properties()
            for (spec in layout.controls) {
                props.setProperty("${spec.id.key}.cx", spec.cx.toString())
                props.setProperty("${spec.id.key}.cy", spec.cy.toString())
                props.setProperty("${spec.id.key}.size", spec.sizeDp.toString())
            }
            props.setProperty("opacity", layout.opacity.toString())
            file(base).parentFile?.mkdirs()
            file(base).outputStream().use {
                props.store(it, "Poing! control layout (fractions of the screen)")
            }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }

    companion object {
        private val OLD_GROUPS = listOf("dpad", "startselect", "ab", "lr")
    }
}
