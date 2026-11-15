package app.poing

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hub + badges (2026-11-14) — the decode half of the badge
 * store, under Robolectric (BitmapFactory is a stub on the plain
 * JVM): a real 1x1 PNG decodes into the state map, and a corrupt
 * file lands in the failed set instead of crashing the next pass.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaBadgeDecodeTest {

    @get:Rule
    val temp = TemporaryFolder()

    // A 1x1 transparent PNG.
    private val onePxPng = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        Base64.DEFAULT,
    )

    private val url = "https://media.retroachievements.org/Badge/12345.png"

    @Test
    fun fetchedBadgeDecodesIntoTheStateMap() {
        val s = RaBadgeStore(temp.root, RaBadgeFetcher { onePxPng })
        val file = s.badgeOrFetch(url, networkAvailable = true)
        assertNotNull(file)
        s.decodeIntoCache(url)
        val bitmap = s.badgeBitmaps[url]
        assertNotNull(bitmap)
        assertEquals(1, bitmap!!.width)
        assertEquals(1, bitmap.height)
    }

    @Test
    fun corruptBadgeFileFailsDecodeWithoutCrashing() {
        val s = RaBadgeStore(temp.root)
        s.badgeFile(url).writeBytes("not an image".toByteArray())
        s.decodeIntoCache(url)
        assertNull(s.badgeBitmaps[url])
        // A second pass is a no-op, not a second crash.
        s.decodeIntoCache(url)
        assertNull(s.badgeBitmaps[url])
    }
}
