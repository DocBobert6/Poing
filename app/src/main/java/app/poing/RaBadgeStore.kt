package app.poing

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * RetroAchievements badge images (hub + badges, 2026-11-14):
 * the full media.retroachievements.org URLs the native layer
 * carries per achievement (Badge/<name>.png unlocked,
 * Badge/<name>_lock.png locked — see ra_client.c), fetched on
 * demand when a badge is shown, then cached on the device.
 * Same shape as [CoverArtStore]: files under
 * filesDir/ra-badges/, decoded downsampled (badges are 64x64),
 * never a bulk download, offline or a bad download means the
 * placeholder stays, with no error and no blocking. Wi-Fi only
 * by default — the caller passes the same gate CoverArt uses
 * ([isCoverArtNetworkAvailable]), so the cover-art mobile-data
 * toggle governs badges too.
 *
 * The HTTP layer is injectable so the fetch and the cap are
 * testable on the plain JVM without a network.
 */
fun interface RaBadgeFetcher {
    /** Returns the PNG bytes for the badge URL, or null when unavailable. Only ever called when the network gate allows it. */
    fun fetch(url: String): ByteArray?
}

/** A capped GET: null on any non-200 response, any over-cap body, or any failure. */
fun defaultRaBadgeHttpGet(url: String, maxBytes: Int = RaBadgeStore.MAX_BADGE_BYTES): ByteArray? {
    val conn = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Poing")
        }
    } catch (_: Exception) {
        return null
    }
    return try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return null // over the cap
            }
        }
        out.toByteArray()
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

class RaBadgeStore(
    private val root: File,
    private val fetcher: RaBadgeFetcher = RaBadgeFetcher { url -> defaultRaBadgeHttpGet(url) },
) {
    // Resolved once — badgesDir() used to mkdirs() on every call.
    private val badgesDirFile: File by lazy { File(root, "ra-badges").apply { mkdirs() } }

    /**
     * Decoded badges, keyed by URL. The rows read this state
     * map, so a landed badge redraws only its own row. Populated
     * by [decodeIntoCache] on IO, never from composition.
     */
    val badgeBitmaps: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()

    // Decode failures are memoized for the store's lifetime, so a
    // corrupt file is not re-decoded on every pass. A newly
    // fetched badge clears the entry (see badgeOrFetch).
    private val decodeFailedUrls = mutableSetOf<String>()

    // URLs with a fetch in flight: a second caller for the same
    // URL waits for the state map instead of fetching twice.
    private val inFlight = mutableSetOf<String>()

    fun badgesDir(): File = badgesDirFile

    /**
     * The cache file for a badge URL. The name is the SHA-256 of
     * the URL: badge names are stable per achievement, the URL is
     * the identity, and the hash keeps the filename safe (the URL
     * carries slashes). Pure, so it is unit-testable.
     */
    fun badgeFile(url: String): File = File(badgesDir(), "${sha256Hex(url)}.png")

    fun hasBadge(url: String): Boolean = badgeFile(url).exists()

    /**
     * Decode the badge file into the state map. Call from IO —
     * never from composition. No-op when there is no file, when
     * it is already decoded, or when a past decode failed.
     *
     * Badges are PNGs by contract (media.retroachievements.org
     * serves Badge/<name>.png), so the file's first 8 bytes must
     * carry the PNG signature before BitmapFactory sees it: on a
     * real device BitmapFactory returns null for garbage, but
     * Robolectric's shadow synthesises a bitmap from any bytes,
     * so the signature is what makes a corrupt file fail
     * deterministically in both environments.
     */
    fun decodeIntoCache(url: String) {
        if (badgeBitmaps.containsKey(url) || url in decodeFailedUrls) return
        val file = badgeFile(url)
        if (!file.exists()) return
        if (!isPng(file)) {
            decodeFailedUrls += url
            return
        }
        val bitmap = decodeCoverFile(file, BADGE_MAX_DIM)
        if (bitmap != null) badgeBitmaps[url] = bitmap else decodeFailedUrls += url
    }

    private fun isPng(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(PNG_SIGNATURE.size)
                var offset = 0
                while (offset < header.size) {
                    val n = input.read(header, offset, header.size - offset)
                    if (n < 0) break
                    offset += n
                }
                offset == header.size && header.contentEquals(PNG_SIGNATURE)
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Cached badge, or a fetch cached for next time (when the network gate allows it). Null means: show the placeholder. */
    fun badgeOrFetch(url: String, networkAvailable: Boolean): File? {
        val file = badgeFile(url)
        if (file.exists()) {
            decodeFailedUrls -= url
            return file
        }
        if (!networkAvailable) return null
        synchronized(inFlight) {
            if (!inFlight.add(url)) return null
        }
        return try {
            val bytes = fetcher.fetch(url) ?: return null
            // The cap lives here, not just in the production HTTP
            // layer: the fetcher is injectable, and an over-cap
            // body must never land on disk whatever it returns.
            if (bytes.size > MAX_BADGE_BYTES) return null
            file.writeBytes(bytes)
            decodeFailedUrls -= url
            file
        } catch (_: Exception) {
            null
        } finally {
            synchronized(inFlight) { inFlight -= url }
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** RA badges are 64x64 PNGs; decode at that size. */
        const val BADGE_MAX_DIM: Int = 64

        /** A badge is a few KB; the cap sits far above any real one. */
        const val MAX_BADGE_BYTES: Int = 256 * 1024

        /** PNG file signature: 89 50 4E 47 0D 0A 1A 0A. */
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
