package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Cover-art rebuild (2026-11-15): the library reads the
 * [CoverRepository] — the fetch re-triggers when Wi-Fi appears
 * while the library is open, and the tile shows the cover once
 * it lands, with no scroll, filter tap or other recomposition
 * in between. The old build failed both halves: the effect
 * keyed only on the game list, and the fetch wrote files
 * without updating any state, so tiles kept their placeholders
 * until an unrelated recomposition.
 *
 * The tests inject a fake network layer into the repository
 * (the library's own repository in production is built with
 * the real client in PoingHome) and pass it to the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryCoverFetchTest {

    @get:Rule
    val composeRule = createComposeRule()

    companion object {
        // A 1x1 transparent PNG: the smallest decodable image,
        // so the tile's BitmapFactory decode succeeds and the
        // cover Image (not the placeholder badge) is what shows.
        private val TINY_PNG: ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )

        private val treeJson = """{"tree":[
            {"path":"Named_Boxarts/Tetris (World).png","type":"blob"},
            {"path":"Named_Boxarts/Dr. Mario (World).png","type":"blob"}
        ]}"""

        // The two-step load (alpha.7): the top-level tree, and
        // the Named_Boxarts subtree with BARE filenames.
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

    /** Tree routing for the two-step load: master → top tree, SHA → subtree. */
    private fun treeHttpResult(url: String): HttpFetchResult = when {
        url.endsWith("/git/trees/master") -> HttpFetchResult.success(topTreeJson.toByteArray())
        url.contains("/git/trees/") -> HttpFetchResult.success(subtreeJson.toByteArray())
        else -> HttpFetchResult.success(TINY_PNG)
    }

    private fun seedGame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
    }

    /** Wi-Fi appears: the shadow network becomes the active one, unmetered. */
    private fun setWifiOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = Shadows.shadowOf(manager)
        shadow.setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            ),
        )
        val network = manager.activeNetwork ?: error("no active network after Wi-Fi setup")
        // Capabilities are built through the shadow:
        // NetworkCapabilities' Builder and mutators are @hide,
        // not in the SDK stub, so app code cannot build one
        // directly — the shadow is exactly what it is for.
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps: ShadowNetworkCapabilities = Shadows.shadowOf(caps)
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadow.setNetworkCapabilities(network, caps)
    }

    /** Fire the watcher's network event and advance past its 3s debounce. */
    private fun fireWifiAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = Shadows.shadowOf(manager)
        val network = manager.activeNetwork ?: error("no active network after Wi-Fi setup")
        shadow.networkCallbacks.single().onAvailable(network)
        // The watcher debounces network events (3s) — advance
        // the main looper past the debounce so the gate check runs.
        Shadows.shadowOf(Looper.getMainLooper())
            .idle(ConnectivityWatcher.DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun coverAppearsWhenWifiArrivesWhileLibraryIsOpen() {
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            calls.incrementAndGet()
            if (url.contains("git/trees")) treeHttpResult(url)
            else HttpFetchResult.success(TINY_PNG)
        }
        val repository = CoverRepository(context.filesDir, httpGet)
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
            composeRule.waitForIdle()
            // Offline (Robolectric's default has no Wi-Fi): the
            // placeholder shows and the network layer never runs
            // — the gate is closed, so no pipeline starts,
            // whatever the background effects have done so far.
            composeRule.onAllNodesWithContentDescription("Cover art for TETRIS").assertCountEquals(0)
            assertEquals(0, calls.get())
            // Wi-Fi appears while the library is open: the shadow
            // network becomes the active one, unmetered, and the
            // registered NetworkCallback fires.
            setWifiOn()
            fireWifiAvailable()
            // The fetch runs and the tile shows the cover — with no
            // scroll, tap or other interaction in between.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                        .assertCountEquals(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertTrue("the network layer must run once Wi-Fi appears", calls.get() >= 1)
            composeRule.onNodeWithText("TETRIS").assertExists()
        } finally {
            repository.close()
        }
    }

    @Test
    fun landedCoverShowsOnItsTileWhileTheGbaIndexIsStillStuck() {
        // The device case (2026-11-15, alpha.4): 10 covers land
        // in ~4s, but the tiles keep their placeholders — the
        // decode only runs after the whole pass returns, and the
        // pass waits up to one index budget (45s) on the stuck
        // GBA index. A landed cover must show on its tile at
        // landing time, while the GBA pipeline is still running:
        // no pass end, no Retry, no scroll. The rebuild's
        // per-system pipelines make this structural: the GB
        // pipeline lands its cover on its own job, and the
        // stuck GBA index cannot hold it behind it.
        val context = ApplicationProvider.getApplicationContext<Context>()
        // GB index cached on disk, as on the device.
        File(context.filesDir, "art-index-gb.txt").writeText("Tetris (World).png\n")
        seedGame()
        // A GBA game, so the GBA pipeline starts its index load too.
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbaHeader("GOLDEN SUN"), "golden-sun.gba")
        val released = AtomicBoolean(false)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> {
                    // Hang, cancellably, until the test releases
                    // it: a blocking wait would ignore the
                    // pipeline's budget and the repository's
                    // close, so the hang is a delay loop.
                    while (!released.get()) delay(100)
                    HttpFetchResult.httpFailure(500)
                }
                url.contains("git/trees") -> treeHttpResult(url)
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        // A 60s budget on purpose: only the test's own release
        // may end the GBA load — the tile must show the cover
        // long before that.
        val repository = CoverRepository(
            context.filesDir, httpGet, indexBudgetMs = 60_000, artBudgetMs = 5_000,
        )
        setWifiOn()
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
            // The GB cover lands (index cached); the GBA
            // pipeline is still awaiting its index. The tile
            // must show the cover now.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                        .assertCountEquals(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        } finally {
            // Let the stuck GBA load unwind.
            released.set(true)
            repository.close()
        }
    }

    @Test
    fun statusShowsIndexFailureAndRetryRunsAgain() {
        // The diagnostic (2026-11-15): a failed index download
        // used to leave blank tiles with no reason anywhere.
        // The status line names the failure, and Retry re-runs
        // the fetch — the index is re-attempted, not memoized
        // across retries.
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                HttpFetchResult.httpFailure(403)
            } else {
                HttpFetchResult.success(TINY_PNG)
            }
        }
        val repository = CoverRepository(context.filesDir, httpGet)
        setWifiOn()
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
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onNodeWithText("HTTP 403", substring = true).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertEquals(1, treeCalls.get())
            composeRule.onNodeWithTag("cover-art-status").assertExists()
            // The pipeline must have parked before Retry is
            // tapped: the status line shows the failure as soon
            // as it lands, and the counts ("0/1 covers") only
            // appear once the line is idle.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onNodeWithText("0/1 covers", substring = true).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            // Retry re-enqueues the failed game: the index is attempted again.
            composeRule.onNodeWithTag("cover-art-retry").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) { treeCalls.get() == 2 }
            // The failure text only returns once the second
            // attempt finishes: while it runs, the status line
            // reads "fetching…" and hides the index state.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onNodeWithText("HTTP 403", substring = true).assertExists()
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
    fun secondEntryReusesReadyCoversWithoutRefetching() {
        // The rebuild's application-scoped repository: leaving
        // and re-entering the library must not re-download the
        // index or the art — the second entry reuses the Ready
        // states the first entry built. A screen that rebuilt
        // its fetch state on every entry would download both
        // again here.
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeCalls = AtomicInteger(0)
        val artCalls = AtomicInteger(0)
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                treeHttpResult(url)
            } else {
                artCalls.incrementAndGet()
                HttpFetchResult.success(TINY_PNG)
            }
        }
        val repository = CoverRepository(context.filesDir, httpGet)
        setWifiOn()
        // One setContent: the rule's activity takes content
        // exactly once — a second setContent throws
        // IllegalStateException. Leaving and re-entering is a
        // state flip instead: the screen leaves composition
        // (its watcher stops) and comes back, while the
        // repository — held here, as PoingHome holds it —
        // survives.
        val showLibrary = mutableStateOf(true)
        try {
            composeRule.setContent {
                if (showLibrary.value) {
                    LibraryScreen(
                        notice = null,
                        onPlay = {},
                        onLicences = {},
                        onSettings = {},
                        repository = repository,
                    )
                }
            }
            composeRule.waitForText("TETRIS")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                        .assertCountEquals(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertEquals(2, treeCalls.get())
            assertEquals(1, artCalls.get())
            // Leave and re-enter: the same repository, a fresh
            // composition — the cover must still show, with no
            // second download of either the index or the art.
            showLibrary.value = false
            composeRule.waitForIdle()
            showLibrary.value = true
            composeRule.waitForText("TETRIS")
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                .assertCountEquals(1)
            assertEquals(2, treeCalls.get())
            assertEquals(1, artCalls.get())
        } finally {
            repository.close()
        }
    }

    @Test
    fun statusBarHidesAfterAFetchFinishes() {
        // Alpha.9, part B: the status bar auto-hides a few
        // seconds after the fetch finishes, so the library is
        // clean — it is not composed at all once hidden.
        seedGame()
        setWifiOn()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) treeHttpResult(url)
            else HttpFetchResult.success(TINY_PNG)
        }
        val repository = CoverRepository(context.filesDir, httpGet)
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
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onAllNodesWithContentDescription("Cover art for TETRIS")
                        .assertCountEquals(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            // The fetch just finished: the bar is still there.
            composeRule.onNodeWithTag("cover-art-status").assertExists()
            // Past the 4s hide delay, the bar is gone.
            Shadows.shadowOf(Looper.getMainLooper()).idle(5000, TimeUnit.MILLISECONDS)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("cover-art-status").assertDoesNotExist()
        } finally {
            repository.close()
        }
    }

    @Test
    fun statusBarStaysVisibleWhileAFetchHasFailed() {
        // Alpha.9, part B: on a failed fetch the bar stays
        // visible, so Retry is always reachable — the hide
        // never fires while a failure needs attention.
        seedGame()
        setWifiOn()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) treeHttpResult(url)
            else HttpFetchResult.httpFailure(404)
        }
        val repository = CoverRepository(context.filesDir, httpGet)
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
            composeRule.waitUntil(timeoutMillis = 10_000) {
                repository.covers.value.values.any { it is CoverState.Failed }
            }
            // Past the 4s hide delay, the bar — and Retry —
            // are still there.
            Shadows.shadowOf(Looper.getMainLooper()).idle(5000, TimeUnit.MILLISECONDS)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("cover-art-status").assertExists()
            composeRule.onNodeWithTag("cover-art-retry").assertExists()
        } finally {
            repository.close()
        }
    }
}
