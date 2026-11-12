package app.poing

import android.content.Context
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
