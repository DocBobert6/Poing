package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Base64
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
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
    }

    private fun seedGame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LibraryStore(context.filesDir)
            .addRom(GameSystemTest.fakeGbHeader(0x00, "TETRIS"), "tetris.gb")
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
        shadow.setNetworkCapabilities(
            network,
            NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .build(),
        )
        shadow.networkCallbacks.single().onAvailable(network)
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
}
