package app.poing

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Startup-path gate: the activity must launch and render its home
 * screen without crashing. This exists because green CI and
 * ordinary APK checks missed a launch death before (AutoSpace r159,
 * Notscape beta.48, HowlingDog v1.3.0 VerifyError).
 *
 * The test runs with NO native code present: the mGBA core is only
 * ever loaded on game start, in a later phase — never from
 * onCreate. If MainActivity ever touches native code at launch,
 * this test fails first, before any device ever sees it.
 *
 * Pinned to emulated SDK 35: Robolectric's SDK 36 shadows are new
 * enough that the pin costs nothing (the launch path is what is
 * under test) and avoids a failure mode unrelated to the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityLaunchesAndShowsHome() {
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
        composeRule.onNodeWithText("Game Boy · Game Boy Color · Game Boy Advance")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add game").assertIsDisplayed()
        composeRule.onNodeWithText("Add folder").assertIsDisplayed()
        composeRule.onNodeWithText("No games yet — add a game to start.").assertIsDisplayed()
        composeRule.onNodeWithText("Licences").assertIsDisplayed()
        composeRule.onNodeWithText("BIOS (optional)").assertIsDisplayed()
    }

    @Test
    fun licencesScreenShowsNotice() {
        composeRule.onNodeWithText("Licences").performClick()
        composeRule.onNodeWithText("Licences").assertIsDisplayed()
        composeRule.onNodeWithText("Mozilla Public License", substring = true)
            .assertExists()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
    }

    /**
     * Regression guard for the HowlingDog Phase 2 device bug: tapping
     * Start flashed back to a fresh setup screen with no files
     * picked. Cause: MainActivity declared no configChanges, so
     * entering the game screen's forced landscape recreated the
     * activity. This test pins the fix from Phase 1 — rotation must
     * not recreate the activity.
     */
    @Test
    fun mainActivityDeclaresConfigChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        val flags = info.configChanges
        assertTrue("orientation must not recreate MainActivity",
            flags and ActivityInfo.CONFIG_ORIENTATION != 0)
        assertTrue("screenSize must not recreate MainActivity",
            flags and ActivityInfo.CONFIG_SCREEN_SIZE != 0)
        assertTrue("screenLayout must not recreate MainActivity",
            flags and ActivityInfo.CONFIG_SCREEN_LAYOUT != 0)
        assertTrue("keyboardHidden must not recreate MainActivity",
            flags and ActivityInfo.CONFIG_KEYBOARD_HIDDEN != 0)
        assertTrue("smallestScreenSize must not recreate MainActivity",
            flags and ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE != 0)
    }

    /**
     * If the activity IS ever recreated, the fallback must be a clean
     * home screen, not a crash.
     */
    @Test
    fun activitySurvivesRecreation() {
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Poing!").assertIsDisplayed()
    }
}
