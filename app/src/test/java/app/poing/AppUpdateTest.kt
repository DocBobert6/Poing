package app.poing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-app updater (Step 2 of the alpha, 2026-11-15): version
 * comparison (alpha < beta < rc < release, numeric parts
 * numerically), the releases-list check against canned GitHub
 * responses, the download, and the install intent. Robolectric
 * throughout: org.json is a stub on the plain JVM classpath,
 * and the install intent needs a real Context for the
 * FileProvider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun version(text: String) = AppVersion.parseOrNull(text)!!

    @Test
    fun versionComparisonOrdersPreReleases() {
        assertTrue(version("0.1.0-alpha.1") < version("0.1.0-alpha.2"))
        assertTrue(version("0.1.0-alpha.2") < version("0.1.0-beta.1"))
        assertTrue(version("0.1.0-beta.1") < version("0.1.0-rc.1"))
        assertTrue(version("0.1.0-rc.1") < version("0.1.0"))
        assertTrue(version("0.1.0") < version("0.1.1"))
        assertTrue(version("0.1.0") < version("0.2.0"))
        assertTrue(version("0.1.0") < version("1.0.0"))
        assertEquals(0, version("0.1.0-alpha.1").compareTo(version("0.1.0-alpha.1")))
        assertEquals(0, version("1.2.3").compareTo(version("1.2.3")))
    }

    @Test
    fun versionParsingStripsTheLeadingV() {
        assertEquals(AppVersion(0, 1, 0, listOf("alpha", "1")), version("v0.1.0-alpha.1"))
        assertEquals(AppVersion(1, 2, 3), version("V1.2.3"))
        assertEquals(AppVersion(0, 1, 0), version("0.1.0"))
        assertEquals(AppVersion(0, 1, 0), version("0.1"))
        assertEquals("0.1.0-alpha.1", version("v0.1.0-alpha.1").display())
        assertEquals("1.2.3", version("1.2.3").display())
    }

    @Test
    fun versionParsingRejectsGarbage() {
        assertNull(AppVersion.parseOrNull(null))
        assertNull(AppVersion.parseOrNull(""))
        assertNull(AppVersion.parseOrNull("not-a-version"))
        assertNull(AppVersion.parseOrNull("1.2.3.4"))
        assertNull(AppVersion.parseOrNull("1.x.3"))
        assertNull(AppVersion.parseOrNull("-1.2.3"))
    }

    private val releasesJson = """
        [
          {"tag_name": "v0.1.0-alpha.1", "draft": false, "assets": [
            {"name": "poing-0.1.0-alpha.1.apk", "browser_download_url": "https://example.com/a1.apk"}
          ]},
          {"tag_name": "v0.1.0-alpha.2", "draft": false, "assets": [
            {"name": "poing-0.1.0-alpha.2.apk", "browser_download_url": "https://example.com/a2.apk"}
          ]},
          {"tag_name": "v0.1.0-beta.1", "draft": true, "assets": [
            {"name": "poing.apk", "browser_download_url": "https://example.com/draft.apk"}
          ]},
          {"tag_name": "v0.2.0", "draft": false, "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"}
          ]},
          {"tag_name": "garbage", "draft": false, "assets": [
            {"name": "poing.apk", "browser_download_url": "https://example.com/g.apk"}
          ]}
        ]
    """.trimIndent()

    @Test
    fun parseReleasesSkipsDraftsNoApkAndGarbageTags() {
        val releases = parseReleases(JSONArray(releasesJson))
        // Only the two alphas count: the beta is a draft, 0.2.0
        // carries no APK, "garbage" does not parse.
        assertEquals(2, releases.size)
        assertEquals("v0.1.0-alpha.1", releases[0].tag)
        assertEquals("https://example.com/a1.apk", releases[0].apkUrl)
        assertEquals("v0.1.0-alpha.2", releases[1].tag)
    }

    private fun fakeHttpGet(json: String): (String, Int) -> HttpFetchResult =
        { _, _ -> HttpFetchResult.success(json.toByteArray()) }

    @Test
    fun checkFindsTheNextAlpha() {
        // The alpha itself (0.1.0-alpha.1) must find the next
        // alpha (0.1.0-alpha.2) — the reason the check lists
        // releases instead of using /releases/latest, which
        // excludes prereleases.
        val result = checkForUpdates(version("0.1.0-alpha.1"), fakeHttpGet(releasesJson))
        assertTrue(result is UpdateCheckResult.Available)
        val available = result as UpdateCheckResult.Available
        assertEquals("v0.1.0-alpha.2", available.release.tag)
        assertEquals("https://example.com/a2.apk", available.release.apkUrl)
    }

    @Test
    fun checkReportsUpToDateWhenNothingIsNewer() {
        val result = checkForUpdates(version("0.1.0-alpha.2"), fakeHttpGet(releasesJson))
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertEquals(
            "v0.1.0-alpha.2",
            (result as UpdateCheckResult.UpToDate).latest?.tag,
        )
    }

    @Test
    fun checkReportsUpToDateAgainstARelease() {
        // A shipped release (0.1.0) is newer than its own
        // alphas, so no update is offered for it.
        val result = checkForUpdates(version("0.1.0"), fakeHttpGet(releasesJson))
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun checkErrorIsNotUpToDate() {
        val dead: (String, Int) -> HttpFetchResult = { _, _ -> HttpFetchResult.httpFailure(403) }
        val result = checkForUpdates(version("0.1.0-alpha.1"), dead)
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).detail.contains("403"))
    }

    @Test
    fun checkWithNoReleasesIsUpToDate() {
        val result = checkForUpdates(version("0.1.0-alpha.1"), fakeHttpGet("[]"))
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertNull((result as UpdateCheckResult.UpToDate).latest)
    }

    @Test
    fun downloadWritesTheApkBytes() {
        val bytes = "fake-apk-bytes".toByteArray()
        val file = downloadUpdateApk(
            "https://example.com/a2.apk",
            temp.root,
        ) { _, _ -> HttpFetchResult.success(bytes) }
        assertEquals(File(temp.root, AppUpdate.APK_FILE_NAME), file)
        assertTrue(file!!.isFile)
        assertEquals("fake-apk-bytes", file.readText())
    }

    @Test
    fun downloadFailureReturnsNull() {
        val file = downloadUpdateApk(
            "https://example.com/a2.apk",
            temp.root,
        ) { _, _ -> HttpFetchResult.httpFailure(404) }
        assertNull(file)
    }

    @Test
    fun installIntentTargetsTheInstallerThroughTheFileProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val apk = File(dir, AppUpdate.APK_FILE_NAME).apply { writeBytes("fake".toByteArray()) }
        val intent = buildInstallIntent(context, apk)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        val uri: Uri? = intent.data
        assertEquals("content", uri?.scheme)
        assertEquals("${context.packageName}.fileprovider", uri?.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun unknownAppsIntentPointsAtThisPackage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = unknownAppsSettingsIntent(context)
        assertEquals(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            intent.action,
        )
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun currentVersionReadsTheApkVersionName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The test APK carries the app's versionName (0.1.0-alpha.3
        // since the alpha.3 version bump). This expectation must
        // track versionName in app/build.gradle.kts.
        assertEquals(AppVersion(0, 1, 0, listOf("alpha", "3")), currentAppVersion(context))
    }

    @Test
    fun updateNetworkGateIsClosedWithNoNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric's default has no active network: the
        // launch check skips instead of waiting on a socket.
        assertFalse(isUpdateNetworkAvailable(context))
    }
}
