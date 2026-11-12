package app.poing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 6 (item 11) — where captures go: screenshots as PNG in
 * Pictures/Poing!, videos as MP4 in Movies/Poing!, through
 * MediaStore (no permission needed on API 29+ — the app's own
 * entries). On API 24–28 MediaStore has no RELATIVE_PATH and
 * writing needs a storage permission the app does not ask for,
 * so captures fall back to the app-private filesDir/captures/
 * (still saved, just not in the gallery).
 *
 * The file name is "Poing-<game>-<yyyyMMdd-HHmmss>.<ext>" with
 * the game name sanitized (anything not a letter, digit, dash
 * or dot becomes an underscore). The caller owns the
 * ParcelFileDescriptor a video returns: close it after the
 * recorder stops.
 */
object CaptureStore {
    const val SCREENSHOT_DIR = "Pictures/Poing!"
    const val VIDEO_DIR = "Movies/Poing!"

    /** One video output: the MediaStore (or fallback) Uri and the open file descriptor the muxer writes to. */
    data class VideoOutput(val uri: Uri, val pfd: ParcelFileDescriptor)

    /** "Poing-<game>-<yyyyMMdd-HHmmss>.<ext>" — pure, so it is covered by unit tests. */
    fun captureName(gameName: String, timestampMillis: Long, extension: String): String {
        val safe = gameName
            .map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '_' }
            .joinToString("")
            .trim('_')
            .take(40)
            .ifEmpty { "game" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMillis))
        return "Poing-$safe-$stamp.$extension"
    }

    /**
     * Save a screenshot PNG. Returns the Uri it landed at (the
     * gallery entry on API 29+, the filesDir file below that),
     * or null when the save failed.
     */
    fun saveScreenshot(context: Context, gameName: String, bitmap: Bitmap): Uri? {
        val name = captureName(gameName, System.currentTimeMillis(), "png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, SCREENSHOT_DIR)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ) ?: return null
            return try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        context.contentResolver.delete(uri, null, null)
                        return null
                    }
                } ?: run {
                    context.contentResolver.delete(uri, null, null)
                    return null
                }
                uri
            } catch (_: Exception) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                null
            }
        }
        val file = File(context.filesDir.resolve("captures").apply { mkdirs() }, name)
        return try {
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Create a video output: the Uri it will land at plus the
     * open FileDescriptor the recorder's muxer writes to. The
     * caller closes the descriptor after the recorder stops.
     * Null when the entry cannot be created.
     */
    fun createVideoOutput(context: Context, gameName: String): VideoOutput? {
        val name = captureName(gameName, System.currentTimeMillis(), "mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_DIR)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
            ) ?: return null
            return try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "w") ?: run {
                    context.contentResolver.delete(uri, null, null)
                    return null
                }
                VideoOutput(uri, pfd)
            } catch (_: Exception) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
                null
            }
        }
        val file = File(context.filesDir.resolve("captures").apply { mkdirs() }, name)
        return try {
            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE,
            )
            VideoOutput(Uri.fromFile(file), pfd)
        } catch (_: Exception) {
            null
        }
    }

    /** Delete a capture (a video whose recording failed to start). Best effort. */
    fun delete(context: Context, uri: Uri) {
        try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
        }
    }
}
