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
