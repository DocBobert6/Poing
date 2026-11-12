package app.poing

import java.io.File

/**
 * Phase 6 (item 3) — the per-game cheat store: one file per game
 * in filesDir/cheats/, named by the game's base (the SHA-256 stem),
 * one line per cheat: "1|<code>" (enabled) or "0|<code>" (off).
 * The codes are stored exactly as CheatFormat normalized them; the
 * store itself does not validate — the screen does that before
 * adding, so a hand-edited file with a bad line is simply skipped
 * on read instead of breaking the list.
 *
 * LibraryStore.remove() deletes the game's cheat file with the
 * game. Pure java.io — no Android APIs — so the whole store runs
 * under plain JVM unit tests.
 */
class CheatStore(private val cheatsDir: File) {
    fun file(base: String): File = File(cheatsDir, "$base.txt")

    fun list(base: String): List<Cheat> {
        val f = file(base)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull(::parseLine)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Replace the whole list for a game; an empty list deletes the file. */
    fun save(base: String, cheats: List<Cheat>) {
        val f = file(base)
        if (cheats.isEmpty()) {
            f.delete()
            return
        }
        try {
            f.parentFile?.mkdirs()
            f.writeText(
                cheats.joinToString("\n") { "${if (it.enabled) "1" else "0"}|${it.code}" } + "\n",
            )
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }

    private fun parseLine(line: String): Cheat? {
        if (line.length < 3 || line[1] != '|') return null
        val enabled = when (line[0]) {
            '1' -> true
            '0' -> false
            else -> return null
        }
        val code = line.substring(2)
        if (code.isBlank()) return null
        return Cheat(code, enabled)
    }
}
