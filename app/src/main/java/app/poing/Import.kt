package app.poing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

/** The outcome of a folder import: how many games landed, skipped, or failed. */
data class ImportResult(val imported: Int, val skipped: Int, val failed: Int)

fun displayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

/**
 * Import one picked file into the library. Returns the game, or
 * null when the file could not be read or is not a Game Boy ROM.
 * The picked file is copied — the library never points at the
 * user's original.
 */
fun importSingleFile(context: Context, store: LibraryStore, uri: Uri): LibraryGame? {
    val name = displayName(context, uri) ?: return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null
    return store.addStream(bytes, name)
}

/**
 * Import every Game Boy ROM in a picked folder (up to 4 levels
 * deep). Files that are not .gb / .gbc / .gba / .zip are skipped,
 * not errors; a file that fails to read counts as failed.
 */
fun importFolder(context: Context, store: LibraryStore, treeUri: Uri): ImportResult {
    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?: return ImportResult(0, 0, 1)
    var imported = 0
    var skipped = 0
    var failed = 0

    fun walk(dir: DocumentFile, depth: Int) {
        if (depth > 4) return
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                walk(child, depth + 1)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in setOf("gb", "gbc", "gba", "zip")) {
                skipped++
                continue
            }
            try {
                val bytes = context.contentResolver.openInputStream(child.uri)
                    ?.use { it.readBytes() }
                if (bytes == null) {
                    failed++
                    continue
                }
                if (store.addStream(bytes, name) != null) imported++ else skipped++
            } catch (_: Exception) {
                failed++
            }
        }
    }

    walk(root, 0)
    return ImportResult(imported, skipped, failed)
}

/**
 * Import the BIOS files in a picked folder: the three expected
 * filenames (gb_bios.bin / gbc_bios.bin / gba_bios.bin,
 * case-insensitive), each replacing an already-installed one —
 * the same as the per-file Replace. Only the folder's top level
 * is scanned, and only those three names count; every other file
 * is ignored, never an error. Returns which kinds landed, which
 * were in the folder but could not be read, and which were not
 * in the folder at all. The folder's URI permission is taken as
 * persistable when the grant allows it (the scan itself runs at
 * once, on the transient grant, so a refused persist still
 * imports); BIOS is never gated and nothing is ever downloaded.
 */
fun importBiosFolder(
    context: Context,
    bios: BiosStore,
    treeUri: Uri,
): Triple<List<BiosKind>, List<BiosKind>, List<BiosKind>> {
    val root = DocumentFile.fromTreeUri(context, treeUri)
        ?: return Triple(emptyList(), emptyList(), BiosKind.entries.toList())
    try {
        context.contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: Exception) {
        // The transient grant still covers the immediate scan.
    }
    val imported = mutableListOf<BiosKind>()
    val unreadable = mutableListOf<BiosKind>()
    val found = mutableSetOf<BiosKind>()
    for (child in root.listFiles()) {
        if (child.isDirectory) continue
        val kind = child.name?.let { biosKindForFileName(it) } ?: continue
        if (kind in found) continue // First match wins per kind.
        found += kind
        try {
            val bytes = context.contentResolver.openInputStream(child.uri)
                ?.use { it.readBytes() }
            if (bytes == null) {
                unreadable += kind
                continue
            }
            bios.import(kind, bytes)
            imported += kind
        } catch (_: Exception) {
            unreadable += kind
        }
    }
    val missing = BiosKind.entries.filter { it !in found }
    return Triple(imported, unreadable, missing)
}
