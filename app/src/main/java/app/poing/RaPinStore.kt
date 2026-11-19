package app.poing

import java.io.File

/**
 * Alpha.33 — Achievement Center build 2: the HUD pin store.
 * One pinned achievement per game, at most: the achievement
 * the HUD chip tracks while playing. One small file per game
 * in [pinsDir], named `<base>.pin` and holding the pinned
 * achievement's id as text — the same small-file-beside-the-
 * layout pattern the left-control, dead-zone and steering
 * overrides use (see ControlLayoutStore), so a pin can never
 * be wiped by — or wipe — a layout save. A missing file (or
 * an unreadable or non-numeric one) means no pin: the HUD
 * chip stays hidden, off by default.
 *
 * Pure java.io — no Android APIs — so the whole store runs
 * under plain JVM unit tests.
 */
class RaPinStore(private val pinsDir: File) {
    fun file(base: String): File = File(pinsDir, "$base.pin")

    /** This game's pinned achievement id, or null when nothing is pinned. */
    fun pinnedId(base: String): Int? {
        val f = file(base)
        if (!f.exists()) return null
        val raw = try {
            f.readText().trim()
        } catch (_: Exception) {
            return null
        }
        return raw.toIntOrNull()
    }

    /** Pin an achievement for this game, or clear the pin with null. */
    fun setPinnedId(base: String, achievementId: Int?) {
        try {
            val f = file(base)
            if (achievementId == null) {
                f.delete()
            } else {
                f.parentFile?.mkdirs()
                f.writeText(achievementId.toString())
            }
        } catch (_: Exception) {
            // A failed write leaves the previous pin in place.
        }
    }
}
