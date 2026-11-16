package app.poing

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Custom artwork (alpha.9, 2026-11-15): the user picks an image
 * for any game from the system photo picker. The image is
 * decoded and downscaled to 512px PNG and stored in its own
 * directory (`custom-covers/`), never in `covers/`, so a
 * re-fetch can never clobber it. Custom art wins over fetched
 * art on the tile, is removable (falling back to fetched art
 * or the placeholder), and works for games with no boxart to
 * fetch — fan translations and hacks are the point of it.
 *
 * The photo picker itself is a system activity and is not
 * driven here: the repository (decode, store, restore,
 * remove) and the dialog/tile wiring are what these tests
 * prove.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomCoverTest {

    @get:Rule
    val temp = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    companion object {
        // A 1x1 transparent PNG: the smallest decodable image.
        private val TINY_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )

        private val topTreeJson = """{"tree":[
            {"path":".gitignore","type":"blob"},
            {"path":"Named_Boxarts","type":"tree","sha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
            {"path":"Named_Snaps","type":"tree","sha":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
        ]}"""

        private val subtreeJson = """{"tree":[
            {"path":"Tetris (World).png","type":"blob"},
            {"path":"Dr. Mario (World).png","type":"blob"}
        ]}"""
    }

    private fun treeHttpResult(url: String): HttpFetchResult = when {
        url.endsWith("/git/trees/master") -> HttpFetchResult.success(topTreeJson.toByteArray())
        url.contains("/git/trees/") -> HttpFetchResult.success(subtreeJson.toByteArray())
        else -> HttpFetchResult.success(TINY_PNG)
    }

    private fun game(id: String, title: String, sourceName: String, system: GameSystem) =
        LibraryGame(
            id = id,
            title = title,
            system = system,
            fileName = "$id.gb",
            sourceName = sourceName,
            sha256 = id,
            addedAt = 0L,
        )

    private fun repo(
        httpGet: suspend (String, Int) -> HttpFetchResult,
        root: File = temp.root,
    ) = CoverRepository(
        root, httpGet, indexBudgetMs = 500, artBudgetMs = 500,
    )

    /** A 1024x768 image as PNG bytes: bigger than the 512px gate. */
    private fun largePngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
    }

    // --- Repository: store, downscale, restore, remove ---

    @Test
    fun setCustomCoverStoresDownscaledPngAndPublishesIt() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val repository = repo({ _, _ -> HttpFetchResult.httpFailure(403) })
        try {
            assertTrue(repository.setCustomCover(g, largePngBytes()))
            val file = repository.customCoverFile(g)
            assertTrue("the custom file must exist", file.exists())
            // The stored file decodes, and the published
            // bitmap is downscaled to at most 512px a side.
            assertNotNull(decodeCoverFile(file))
            val published = repository.customCovers.value[g.id]
            assertNotNull(published)
            assertTrue(published!!.width <= 512)
            assertTrue(published.height <= 512)
            assertEquals(512, published.width)
            assertTrue(repository.hasCustomCover(g))
        } finally {
            repository.close()
        }
    }

    @Test
    fun setCustomCoverRejectsGarbageBytes() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val repository = repo({ _, _ -> HttpFetchResult.httpFailure(403) })
        try {
            assertFalse(repository.setCustomCover(g, "not an image".toByteArray()))
            assertFalse(repository.customCoverFile(g).exists())
            assertFalse(repository.hasCustomCover(g))
            assertNull(repository.customCovers.value[g.id])
        } finally {
            repository.close()
        }
    }

    @Test
    fun customCoverRestoresFromDiskWithNoNetwork() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val calls = AtomicInteger(0)
        val first = repo({ _, _ ->
            calls.incrementAndGet()
            HttpFetchResult.httpFailure(403)
        })
        try {
            assertTrue(first.setCustomCover(g, TINY_PNG))
        } finally {
            first.close()
        }
        // A fresh repository on the same root, with a network
        // layer that must never run: the cover comes from disk.
        val second = repo({ _, _ ->
            calls.incrementAndGet()
            HttpFetchResult.httpFailure(403)
        })
        try {
            second.setGames(listOf(g))
            assertNotNull(second.customCovers.value[g.id])
            assertTrue(second.hasCustomCover(g))
            assertEquals(0, calls.get())
        } finally {
            second.close()
        }
    }

    @Test
    fun corruptCustomFileIsDeletedByTheStartupScan() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val dir = File(temp.root, "custom-covers").apply { mkdirs() }
        File(dir, "tetris.png").writeBytes("not an image".toByteArray())
        val repository = repo({ _, _ -> HttpFetchResult.httpFailure(403) })
        try {
            repository.setGames(listOf(g))
            assertFalse(repository.customCoverFile(g).exists())
            assertFalse(repository.hasCustomCover(g))
            assertNull(repository.customCovers.value[g.id])
        } finally {
            repository.close()
        }
    }

    @Test
    fun setGamesDropsRemovedGamesButKeepsTheirCustomFile() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val repository = repo({ _, _ -> HttpFetchResult.httpFailure(403) })
        try {
            assertTrue(repository.setCustomCover(g, TINY_PNG))
            repository.setGames(listOf(g))
            assertNotNull(repository.customCovers.value[g.id])
            // The game leaves the library: the published entry
            // goes, the file stays — re-import restores it.
            repository.setGames(emptyList())
            assertNull(repository.customCovers.value[g.id])
            assertTrue(repository.customCoverFile(g).exists())
            repository.setGames(listOf(g))
            assertNotNull(repository.customCovers.value[g.id])
        } finally {
            repository.close()
        }
    }

    @Test
    fun customCoverSurvivesAFetchedPipelineRun() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val repository = repo({ url, _ -> treeHttpResult(url) })
        try {
            assertTrue(repository.setCustomCover(g, TINY_PNG))
            val customBytes = repository.customCoverFile(g).readBytes()
            repository.setGames(listOf(g))
            repository.setGate(CoverArtGate.OPEN)
            // Wait for the fetched art to land.
            val deadline = System.currentTimeMillis() + 10_000
            while (repository.covers.value[g.id] !is CoverState.Ready &&
                System.currentTimeMillis() < deadline
            ) delay(50)
            assertTrue(
                "the fetched cover must land",
                repository.covers.value[g.id] is CoverState.Ready,
            )
            // The fetched run wrote to covers/, never to
            // custom-covers/: the custom file is byte-identical
            // and still published.
            assertTrue(
                repository.customCoverFile(g).readBytes().contentEquals(customBytes),
            )
            assertTrue(repository.hasCustomCover(g))
            assertNotNull(repository.customCovers.value[g.id])
        } finally {
            repository.close()
        }
    }

    @Test
    fun removeCustomCoverDeletesFileAndFallsBack() = runBlocking {
        val g = game("tetris", "TETRIS", "tetris.gb", GameSystem.GB)
        val repository = repo({ url, _ -> treeHttpResult(url) })
        try {
            assertTrue(repository.setCustomCover(g, TINY_PNG))
            repository.setGames(listOf(g))
            repository.setGate(CoverArtGate.OPEN)
            val deadline = System.currentTimeMillis() + 10_000
            while (repository.covers.value[g.id] !is CoverState.Ready &&
                System.currentTimeMillis() < deadline
            ) delay(50)
            assertTrue(repository.covers.value[g.id] is CoverState.Ready)
            val fetchedFile = repository.coverFile(g)
            assertTrue(fetchedFile.exists())
            // Remove the custom cover: its file goes, the
            // published entry goes, the fetched file stays —
            // the tile falls back to fetched art.
            repository.removeCustomCover(g)
            assertFalse(repository.customCoverFile(g).exists())
            assertFalse(repository.hasCustomCover(g))
            assertNull(repository.customCovers.value[g.id])
            assertTrue(fetchedFile.exists())
            assertTrue(repository.covers.value[g.id] is CoverState.Ready)
        } finally {
            repository.close()
        }
    }

    // --- Screen: dialog and tile ---

    private fun seedGame(title: String = "TETRIS") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbHeader(0x00, title), "${title.lowercase()}.gb")
    }

    private fun seededGame(): LibraryGame {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return LibraryStore(context.filesDir).list().single()
    }

    private fun deadNetwork(): suspend (String, Int) -> HttpFetchResult =
        { _, _ -> HttpFetchResult.httpFailure(403) }

    @Test
    fun customCoverShowsOnTileForAGameWithNoBoxart() {
        // The point of custom art: a game with no boxart to
        // fetch (a fan translation by any other name) still
        // gets a cover — with no network at all.
        seedGame("ZZZ UNKNOWN")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val game = seededGame()
        val repository = CoverRepository(context.filesDir, deadNetwork())
        try {
            runBlocking { assertTrue(repository.setCustomCover(game, TINY_PNG)) }
            composeRule.setContent {
                LibraryScreen(
                    notice = null,
                    onPlay = {},
                    onLicences = {},
                    onSettings = {},
                    repository = repository,
                )
            }
            composeRule.waitForText("ZZZ UNKNOWN")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onAllNodesWithContentDescription("Cover art for ZZZ UNKNOWN")
                        .assertCountEquals(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        } finally {
            repository.close()
        }
    }

    @Test
    fun coverDialogOffersRemoveOnlyWhenACustomCoverExists() {
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val game = seededGame()
        val repository = CoverRepository(context.filesDir, deadNetwork())
        try {
            composeRule.setContent {
                LibraryScreen(
                    notice = null,
                    onPlay = {},
                    onLicences = {},
                    onSettings = {},
                    repository = repository,
                )
            }
            composeRule.waitForText("TETRIS")
            composeRule.onNodeWithTag("game-cover").performClick()
            composeRule.waitForText("Set cover")
            composeRule.onNodeWithText("Remove custom cover").assertDoesNotExist()
            // Set a custom cover behind the dialog's back, then
            // reopen it: Remove appears.
            runBlocking { assertTrue(repository.setCustomCover(game, TINY_PNG)) }
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.onNodeWithTag("game-cover").performClick()
            composeRule.waitForText("Remove custom cover")
            // Removing it falls back to the placeholder and says so.
            composeRule.onNodeWithText("Remove custom cover").performClick()
            composeRule.waitForText("Custom cover removed for TETRIS.")
            composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                .assertCountEquals(0)
            assertFalse(repository.hasCustomCover(game))
        } finally {
            repository.close()
        }
    }
}
