package app.poing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Base64
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Cover-art fetch trace UI (2026-11-15, alpha.3): the Trace
 * button in the library's status bar opens the on-device
 * record of what the fetch did. Rebuild (2026-11-15): there
 * is no pass any more, so there are no pass lines — the trace
 * records the pipeline's per-game stage events (matched →
 * started → downloaded → decoded → ready), and the dialog
 * shows them live from the repository's trace state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverArtTraceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    companion object {
        // A 1x1 transparent PNG: the smallest decodable image,
        // so the pipeline's in-memory decode succeeds and the
        // game reaches Ready.
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

    /** Wi-Fi on: the shadow network becomes the active one, unmetered. */
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
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps: ShadowNetworkCapabilities = Shadows.shadowOf(caps)
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadow.setNetworkCapabilities(network, caps)
    }

    @Test
    fun traceButtonOpensDialogWithTheStageEvents() {
        seedGame()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpGet: suspend (String, Int) -> HttpFetchResult = { url, _ ->
            if (url.contains("git/trees")) HttpFetchResult.success(treeJson.toByteArray())
            else HttpFetchResult.success(TINY_PNG)
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
            composeRule.onNodeWithTag("cover-art-trace").performClick()
            composeRule.onNodeWithTag("cover-art-trace-dialog").assertExists()
            // The pipeline runs on composition (Wi-Fi on): the
            // dialog shows the game's stage events live, from
            // the repository's trace state — matched →
            // downloaded → decoded → ready, with the index load
            // before them.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                try {
                    composeRule.onNodeWithText("art ready: TETRIS", substring = true).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            composeRule.onNodeWithText("art matched: TETRIS", substring = true).assertExists()
            composeRule.onNodeWithText("art downloaded: TETRIS", substring = true).assertExists()
            composeRule.onNodeWithText("art decoded: TETRIS", substring = true).assertExists()
            composeRule.onNodeWithText("index gb loaded", substring = true).assertExists()
        } finally {
            repository.close()
        }
    }
}
