package app.poing

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RA login fix (2026-11-12): the RetroAchievements login failed on
 * Robert's device with "Could not reach the RetroAchievements
 * server" — the app declared no INTERNET permission, so every
 * socket open failed with a permission denial before the request
 * left the phone. The server was up and the phone had Wi-Fi; the
 * app simply was not allowed to open a socket. CI's host tests
 * never enforce the manifest permission, which is how it slipped
 * through — so this pins the merged manifest the APK carries: the
 * permission must be declared, and removing it fails this test
 * instead of failing silently on device.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {

    private fun requestedPermissions(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun internetPermissionIsDeclared() {
        assertTrue(
            "INTERNET must be declared — without it no network feature " +
                "(RetroAchievements login, leaderboards, rich presence) " +
                "can open a socket on device",
            requestedPermissions().contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun existingPermissionsStillDeclared() {
        val permissions = requestedPermissions()
        assertTrue(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(permissions.contains("android.permission.VIBRATE"))
        assertTrue(permissions.contains("android.permission.CAMERA"))
    }
}
