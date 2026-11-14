package app.poing

import java.io.File

/** The three optional BIOS files. mGBA looks for these exact names in the system directory. */
enum class BiosKind(val fileName: String, val label: String) {
    GB("gb_bios.bin", "Game Boy"),
    GBC("gbc_bios.bin", "Game Boy Color"),
    GBA("gba_bios.bin", "Game Boy Advance"),
}

/**
 * BIOS status + import, in filesDir/system/ — the same directory
 * the player hands the core as its system directory, so an
 * imported BIOS is picked up automatically (mgba_use_bios defaults
 * ON in the bridge). BIOS is OPTIONAL and NEVER gated: a missing
 * BIOS never blocks starting a game, no BIOS is ever bundled, and
 * nothing here refuses an import for being the wrong size — a file
 * the core cannot use simply is not used.
 */
class BiosStore(private val root: File) {
    fun systemDir(): File = File(root, "system").apply { mkdirs() }
    fun file(kind: BiosKind): File = File(systemDir(), kind.fileName)
    fun isInstalled(kind: BiosKind): Boolean = file(kind).exists()

    fun import(kind: BiosKind, bytes: ByteArray) {
        file(kind).writeBytes(bytes)
    }

    fun delete(kind: BiosKind): Boolean = file(kind).delete()
}

/**
 * Match a filename to its BIOS kind, case-insensitively — the
 * folder import's matcher. Null for anything else: a BIOS file
 * with a different name, a ROM, a readme. Pure, so it runs
 * under plain JVM unit tests.
 */
fun biosKindForFileName(name: String): BiosKind? =
    BiosKind.entries.firstOrNull { it.fileName.equals(name, ignoreCase = true) }

/**
 * The result line for a folder import: which BIOS files landed,
 * which were in the folder but could not be read, which were
 * not in the folder at all. Nothing found at all reads as one
 * line, not a list of three missing files. Pure, so it runs
 * under plain JVM unit tests.
 */
fun biosFolderResultMessage(
    imported: List<BiosKind>,
    unreadable: List<BiosKind>,
    missing: List<BiosKind>,
): String {
    val imp = imported.sortedBy { it.ordinal }
    val un = unreadable.sortedBy { it.ordinal }
    val miss = missing.sortedBy { it.ordinal }
    if (imp.isEmpty() && un.isEmpty()) return "No BIOS files found in that folder."
    fun join(kinds: List<BiosKind>): String = when (kinds.size) {
        1 -> kinds[0].label
        2 -> "${kinds[0].label} and ${kinds[1].label}"
        else -> kinds.dropLast(1).joinToString(", ") { it.label } +
            " and " + kinds.last().label
    }
    val parts = mutableListOf<String>()
    if (imp.isNotEmpty()) parts += "Imported ${join(imp)} BIOS."
    if (un.isNotEmpty()) parts += "${join(un)} BIOS could not be read."
    if (miss.isNotEmpty()) parts += "${join(miss)} BIOS not found in that folder."
    return parts.joinToString(" ")
}
