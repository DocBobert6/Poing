package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Looper
import android.util.Base64
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
 * Cover-art fix, commit A (2026-11-14): the fetch re-triggers
 * when Wi-Fi appears while the library is open, and the tile
 * shows the cover once it lands — with no scroll, filter tap or
 * other recomposition in between. The old build failed both
 * halves: the effect keyed only on the game list, and the fetch
 * wrote files without updating any state, so tiles kept their
 * placeholders until an unrelated recomposition.
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
        val calls = AtomicInteger(0)
        val fetcher = CoverArtFetcher { calls.incrementAndGet(); TINY_PNG }
        composeRule.setContent {
            LibraryScreen(
                notice = null,
                onPlay = {},
                onLicences = {},
                onSettings = {},
                coverFetcher = fetcher,
            )
        }
        composeRule.waitForText("TETRIS")
        composeRule.waitForIdle()
        // Offline (Robolectric's default has no Wi-Fi): the
        // placeholder shows and the fetcher never runs — the
        // gate short-circuits before the fetcher, so this holds
        // whatever the background effect has done so far.
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
        assertTrue("the fetcher must run once Wi-Fi appears", calls.get() >= 1)
        composeRule.onNodeWithText("TETRIS").assertExists()
    }

    @Test
    fun landedCoverShowsOnItsTileWhileTheGbaIndexIsStillStuck() {
        // The device case (2026-11-15, alpha.4): 10 covers land
        // in ~4s, but the tiles keep their placeholders — the
        // decode only runs after the whole pass returns, and the
        // pass waits up to one index budget (45s) on the stuck
        // GBA index. A landed cover must show on its tile at
        // landing time, while the pass is still running: no pass
        // end, no Retry, no scroll.
        val context = ApplicationProvider.getApplicationContext<Context>()
        // GB index cached on disk, as on the device.
        File(context.filesDir, "art-index-gb.txt").writeText("Tetris (World).png\n")
        seedGame()
        // A GBA game, so the pass starts the GBA index load too.
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbaHeader("GOLDEN SUN"), "golden-sun.gba")
        val gbaStuck = CountDownLatch(1)
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            when {
                url.contains("Nintendo_-_Game_Boy_Advance") -> {
                    try {
                        gbaStuck.await()
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    HttpFetchResult.httpFailure(500)
                }
                url.contains("git/trees") -> HttpFetchResult.success(treeJson.toByteArray())
                else -> HttpFetchResult.success(TINY_PNG)
            }
        }
        // A 60s budget on purpose: only the test's own release
        // may end the GBA load — the tile must show the cover
        // long before that.
        val fetcher = LibretroThumbnailsFetcher(
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
                    coverFetcher = fetcher,
                )
            }
            composeRule.waitForText("TETRIS")
            // The GB cover lands in phase 1 (index cached); the
            // pass is still awaiting the GBA index. The tile must
            // show the cover now.
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
            gbaStuck.countDown()
            fetcher.cancelInflight()
        }
    }

    @Test
    fun statusShowsIndexFailureAndRetryRunsAgain() {
        // The diagnostic (2026-11-15): a failed index download
        // used to leave blank tiles with no reason anywhere.
        // The status line names the failure, and Retry re-runs
        // the pass — the index is re-attempted, not memoized
        // across passes.
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treeCalls = AtomicInteger(0)
        val httpGet: (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) {
                treeCalls.incrementAndGet()
                HttpFetchResult.httpFailure(403)
            } else {
                HttpFetchResult.success(TINY_PNG)
            }
        }
        val fetcher = LibretroThumbnailsFetcher(context.filesDir, httpGet)
        setWifiOn()
        composeRule.setContent {
            LibraryScreen(
                notice = null,
                onPlay = {},
                onLicences = {},
                onSettings = {},
                coverFetcher = fetcher,
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
        // The pass must have finished before Retry is tapped:
        // the status line shows the failure as soon as the
        // event lands, mid-pass, and a tap then relies on the
        // pending-restart path. The counts ("0/1 covers") only
        // appear once the pass is done and the guard is free.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("0/1 covers", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        // Retry re-runs the pass: the index is attempted again.
        // (Retry runs the pass directly — see LibraryScreen — so
        // the tap cannot be dropped by an effect restart.)
        composeRule.onNodeWithTag("cover-art-retry").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { treeCalls.get() == 2 }
        // The failure text only returns once the second pass
        // finishes: while it runs, the status line reads
        // "fetching…" and hides the index state.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("HTTP 403", substring = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
