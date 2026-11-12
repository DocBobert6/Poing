package app.poing

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * One imported game. The id is the SHA-256 of the contained ROM —
 * the same hash RetroAchievements uses (Phase 6), so a .zip and the
 * ROM inside it are the same game.
 */
data class LibraryGame(
    val id: String,
    val title: String,
    val system: GameSystem,
    val fileName: String,
    val sourceName: String,
    val sha256: String,
    val addedAt: Long,
)

/**
 * Phase 3 library store: file-based, no database. Games live as
 * copies under filesDir/games/ (the library never points at the
 * user's originals), the index is filesDir/library.txt — one
 * escaped pipe-delimited line per game. Battery saves live in
 * filesDir/saves/ (the PlayerSession save dir) and covers in
 * filesDir/covers/.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class LibraryStore(private val root: File) {

    val gamesDir: File get() = File(root, "games").apply { mkdirs() }
    private val savesDir: File get() = File(root, "saves").apply { mkdirs() }
    private val coversDir: File get() = File(root, "covers").apply { mkdirs() }
    private val indexFile: File get() = File(root, "library.txt")

    /** Games sorted by title; entries whose file is gone are dropped (self-healing). */
    fun list(): List<LibraryGame> {
        if (!indexFile.exists()) return emptyList()
        return indexFile.readLines().mapNotNull(::parseLine)
            .filter { File(gamesDir, it.fileName).exists() }
            .sortedBy { it.title.lowercase() }
    }

    fun gameFile(game: LibraryGame): File = File(gamesDir, game.fileName)

    /**
     * Import raw ROM bytes. The ROM header decides the system — a
     * GBC game in a .gb file still plays as GBC — and the stored
     * file is named by the ROM's SHA-256, so re-importing the same
     * ROM replaces it instead of duplicating. Returns null when the
     * bytes are not a Game Boy ROM.
     */
    fun addRom(bytes: ByteArray, sourceName: String): LibraryGame? {
        val system = GameSystem.fromHeader(bytes) ?: return null
        val sha = sha256Hex(bytes)
        val ext = when (system) {
            GameSystem.GB -> "gb"
            GameSystem.GBC -> "gbc"
            GameSystem.GBA -> "gba"
        }
        val fileName = "$sha.$ext"
        val title = GameSystem.internalTitle(bytes, system) ?: baseName(sourceName)
        val existing = list().firstOrNull { it.id == sha }
        val game = LibraryGame(
            id = sha,
            title = title,
            system = system,
            fileName = fileName,
            sourceName = sourceName,
            sha256 = sha,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
        )
        gamesDir.mkdirs()
        File(gamesDir, fileName).writeBytes(bytes)
        saveIndex(list().filter { it.id != sha } + game)
        return game
    }

    /**
     * Import a .zip: the first ROM entry inside wins, and the
     * contained ROM (not the zip) is what gets stored and hashed —
     * that is the hash RetroAchievements needs later. Null when the
     * zip holds no Game Boy ROM.
     */
    fun addZip(bytes: ByteArray, sourceName: String): LibraryGame? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && GameSystem.fromExtension(entry.name) != null) {
                    return addRom(zis.readBytes(), entry.name.substringAfterLast('/'))
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    /** Import from a picked file's bytes; a .zip is unwrapped, anything else is a ROM. */
    fun addStream(bytes: ByteArray, sourceName: String): LibraryGame? {
        return if (sourceName.substringAfterLast('.', "").lowercase() == "zip") {
            addZip(bytes, sourceName)
        } else {
            addRom(bytes, sourceName)
        }
    }

    /**
     * Remove a game: deletes the stored copy and everything that
     * belongs to it — the battery save and any later save states in
     * filesDir/saves/, the cached cover — then drops the index row.
     */
    fun remove(game: LibraryGame) {
        File(gamesDir, game.fileName).delete()
        val base = game.fileName.substringBeforeLast('.')
        savesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("$base.")) f.delete()
        }
        coversDir.listFiles()?.forEach { f ->
            if (f.nameWithoutExtension == game.id) f.delete()
        }
        saveIndex(list().filter { it.id != game.id })
    }

    // -- index file: one escaped pipe-delimited line per game ----

    private fun saveIndex(games: List<LibraryGame>) {
        indexFile.parentFile?.mkdirs()
        val text = games.joinToString("\n") { game ->
            listOf(
                game.id, game.title, game.system.name, game.fileName,
                game.sourceName, game.sha256, game.addedAt.toString(),
            ).joinToString("|") { escape(it) }
        }
        indexFile.writeText(if (games.isEmpty()) "" else "$text\n")
    }

    private fun parseLine(line: String): LibraryGame? {
        if (line.isBlank()) return null
        val parts = splitEscaped(line).map(::unescape)
        if (parts.size != 7) return null
        val system = try {
            GameSystem.valueOf(parts[2])
        } catch (_: IllegalArgumentException) {
            return null
        }
        val addedAt = parts[6].toLongOrNull() ?: return null
        return LibraryGame(
            id = parts[0],
            title = parts[1],
            system = system,
            fileName = parts[3],
            sourceName = parts[4],
            sha256 = parts[5],
            addedAt = addedAt,
        )
    }

    private fun escape(s: String): String {
        val out = StringBuilder()
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '|' -> out.append("\\|")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> out.append('\\')
                    '|' -> out.append('|')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> {
                        out.append(c)
                        out.append(s[i + 1])
                    }
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Split on unescaped '|' only; escape sequences stay in the parts for [unescape]. */
    private fun splitEscaped(line: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                cur.append(c)
                cur.append(line[i + 1])
                i += 2
            } else if (c == '|') {
                parts.add(cur.toString())
                cur.clear()
                i++
            } else {
                cur.append(c)
                i++
            }
        }
        parts.add(cur.toString())
        return parts
    }

    private fun baseName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
        return base.ifBlank { name }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
