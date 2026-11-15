package app.poing

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONArray

/**
 * In-app update + install (Step 2 of the alpha, 2026-11-15):
 * the app checks its public GitHub releases, and on the user's
 * tap downloads the APK and installs it through the system
 * installer. No download and no install ever happens without a
 * tap; the launch check only reads, and its result surfaces as
 * a library notice and the Settings section's initial state.
 *
 * Decisions, stated plainly:
 * - The check lists releases (`/releases?per_page=20`), it does
 *   NOT use `/releases/latest`: that endpoint excludes
 *   prereleases, and the next alpha must be found even if a
 *   future release is marked prerelease. Drafts are skipped; a
 *   release counts only when it carries an `.apk` asset and a
 *   parseable tag; the highest version wins.
 * - Version comparison is on the tag, parsed as semver
 *   (leading `v` stripped): `1.0.0-alpha.1 < 1.0.0-beta.1 <
 *   1.0.0-rc.1 < 1.0.0`, numeric pre-release parts compare
 *   numerically. The app's own version is its versionName
 *   (BuildConfig-free: read from the PackageManager, so the
 *   debug and the signed builds report what they are).
 * - The same key signs every release (Step 4's keystore), so an
 *   update installs over the previous install; a build signed
 *   with a different key cannot install over it — the system
 *   refuses, it never silently replaces the app.
 * - One unauthenticated GitHub API call per launch check (the
 *   same 60/hr per-IP budget as the cover-art index) and one
 *   per manual check; a 403 rate limit surfaces as the check
 *   error, not a silent "up to date".
 */
object AppUpdate {
    const val REPO = "DocBobert6/Poing"
    const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=20"

    /** The APK the update downloads to, under filesDir/updates/. */
    const val APK_FILE_NAME = "poing-update.apk"

    /** The download is capped: a release APK is ~20 MB; a body past this is not our APK. */
    const val MAX_APK_BYTES: Int = 256 * 1024 * 1024

    /** The release list JSON is small; the same 1 MB bound as the cover-art fetcher's index cap order. */
    const val MAX_RELEASES_BYTES: Int = 1024 * 1024
}

/**
 * A parsed version: `1.0.0`, `0.1.0-alpha.1`, `v1.2.3-rc.2`
 * (the leading `v` is stripped on parse). Comparison is
 * semver precedence: the core compares numerically, a release
 * (no pre-release) beats any pre-release of the same core, and
 * pre-release identifiers compare dot by dot — numeric parts
 * numerically, alphanumeric parts lexically (so `alpha <
 * beta < rc`), numeric before alphanumeric, and a shorter list
 * that is a prefix of the longer one is smaller. Build metadata
 * (`+…`) is ignored. A tag that does not parse returns null
 * and the release is skipped, never treated as newest.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1 // a release beats its own pre-releases
        if (other.preRelease.isEmpty()) return -1
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            if (a == b) continue
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            if (aNum != null && bNum != null) return aNum.compareTo(bNum)
            if (aNum != null) return -1 // numeric identifiers sort before alphanumeric
            if (bNum != null) return 1
            return a.compareTo(b)
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    /** The version as the user sees it: `0.1.0-alpha.1`. */
    fun display(): String =
        "$major.$minor.$patch" + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        fun parseOrNull(raw: String?): AppVersion? {
            if (raw.isNullOrBlank()) return null
            var text = raw.trim().removePrefix("v").removePrefix("V")
            text = text.substringBefore('+') // build metadata never decides an update
            val coreAndPre = text.split('-', limit = 2)
            val coreParts = coreAndPre[0].split('.')
            if (coreParts.isEmpty() || coreParts.size > 3) return null
            val numbers = coreParts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null
            val pre = if (coreAndPre.size > 1) {
                coreAndPre[1].split('.').filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            return AppVersion(
                major = numbers.getOrElse(0) { 0 },
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
                preRelease = pre,
            )
        }
    }
}

/** One release the updater can install: its tag, parsed version, and APK download URL. */
data class UpdateRelease(
    val tag: String,
    val version: AppVersion,
    val apkUrl: String,
)

/** The outcome of one update check. */
sealed interface UpdateCheckResult {
    /** A newer release with an APK is available. */
    data class Available(val release: UpdateRelease) : UpdateCheckResult

    /** Nothing newer (the newest release seen, if any, for the status line). */
    data class UpToDate(val latest: UpdateRelease?) : UpdateCheckResult

    /** The check itself failed — the reason, for the status line. Never "up to date" by accident. */
    data class Error(val detail: String) : UpdateCheckResult
}

/** The app's own version, from the PackageManager (its versionName). Unparseable reads as 0.0.0, so any release is an update. */
fun currentAppVersion(context: Context): AppVersion {
    val versionName = try {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    } catch (_: Exception) {
        null
    }
    return AppVersion.parseOrNull(versionName) ?: AppVersion(0, 0, 0)
}

/** True when any network with internet capability is active — the launch check's gate (silent skip when false). */
fun isUpdateNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun defaultUpdateHttpGet(url: String, maxBytes: Int): HttpFetchResult {
    val conn = try {
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Poing")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
    } catch (e: Exception) {
        return HttpFetchResult.transportFailure(e)
    }
    return try {
        val code = conn.responseCode
        if (code != java.net.HttpURLConnection.HTTP_OK) {
            return HttpFetchResult.httpFailure(code)
        }
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) {
                    return HttpFetchResult(null, code, "over ${maxBytes}B cap")
                }
            }
        }
        HttpFetchResult.success(out.toByteArray())
    } catch (e: Exception) {
        HttpFetchResult.transportFailure(e)
    } finally {
        conn.disconnect()
    }
}

/**
 * The newest installable release, compared with [current].
 * The fetcher is injectable so tests can answer with canned
 * release lists; production passes [defaultUpdateHttpGet].
 * A release counts only when it is not a draft, its tag
 * parses, and it carries an `.apk` asset (the first one wins
 * when a release carries several).
 */
fun checkForUpdates(
    current: AppVersion,
    httpGet: (String, Int) -> HttpFetchResult = ::defaultUpdateHttpGet,
): UpdateCheckResult {
    val result = httpGet(AppUpdate.RELEASES_URL, AppUpdate.MAX_RELEASES_BYTES)
    val bytes = result.bytes
        ?: return UpdateCheckResult.Error(result.error ?: "no bytes")
    val releases = try {
        parseReleases(JSONArray(bytes.decodeToString()))
    } catch (e: Exception) {
        return UpdateCheckResult.Error(e.javaClass.simpleName)
    }
    val latest = releases.maxByOrNull { it.version }
        ?: return UpdateCheckResult.UpToDate(null)
    return if (latest.version > current) {
        UpdateCheckResult.Available(latest)
    } else {
        UpdateCheckResult.UpToDate(latest)
    }
}

/** Pure, unit-testable: the installable releases in one GitHub releases-list response. */
fun parseReleases(array: JSONArray): List<UpdateRelease> {
    val out = mutableListOf<UpdateRelease>()
    for (i in 0 until array.length()) {
        val release = array.optJSONObject(i) ?: continue
        if (release.optBoolean("draft", false)) continue
        val tag = release.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
        val version = AppVersion.parseOrNull(tag) ?: continue
        val assets = release.optJSONArray("assets") ?: continue
        var apkUrl: String? = null
        for (j in 0 until assets.length()) {
            val asset = assets.optJSONObject(j) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                break
            }
        }
        if (apkUrl != null) out += UpdateRelease(tag, version, apkUrl)
    }
    return out
}

/**
 * Downloads the APK to [destDir]/[AppUpdate.APK_FILE_NAME]
 * (created if needed) and returns the file, or null on any
 * failure. Called only on the user's tap, on IO. The bytes
 * come through the same capped GET as the check (a release APK
 * is ~20 MB, far under the 256 MB cap).
 */
fun downloadUpdateApk(
    apkUrl: String,
    destDir: File,
    httpGet: (String, Int) -> HttpFetchResult = ::defaultUpdateHttpGet,
): File? {
    val result = httpGet(apkUrl, AppUpdate.MAX_APK_BYTES)
    val bytes = result.bytes ?: return null
    return try {
        destDir.mkdirs()
        val file = File(destDir, AppUpdate.APK_FILE_NAME)
        file.writeBytes(bytes)
        file
    } catch (_: Exception) {
        null
    }
}

/**
 * The install intent for a downloaded APK: the system
 * installer, fed through the FileProvider so no file path
 * ever leaves the app. The caller checks
 * [canInstallUnknownApps] first — when false, the system
 * refuses the install and the UI directs the user to the
 * per-app "install unknown apps" setting instead.
 */
fun buildInstallIntent(context: Context, apkFile: File): Intent {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
    )
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** True when the system will let this app install APKs (the per-app "install unknown apps" grant). */
fun canInstallUnknownApps(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

/** The system page that grants this app the "install unknown apps" permission. */
fun unknownAppsSettingsIntent(context: Context): Intent =
    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
