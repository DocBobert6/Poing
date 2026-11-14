package app.poing

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Overlay rework (Robert, 2026-11-13) — per-game saved control
 * layouts, through the player: pause, "Move controls", drag a
 * control in the full-screen editor, Done, and the layout is in
 * the game's file.
 *
 * The session is never started (no native code under Robolectric),
 * so the game itself never runs — the test pins the layout flow,
 * not playback. The layout file lives in the app context's
 * filesDir, like on the device.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameScreenLayoutTest {

    // Top bar / back swipe (Robert, 2026-11-12): the back
    // tests press the system back gesture through the
    // activity's dispatcher, so this file uses the Android
    // rule (which exposes .activity) instead of the plain
    // compose rule — the setContent / clock API is the same.
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temp = TemporaryFolder()

    private fun setContent(
        gamePath: String,
        isGba: Boolean = false,
        system: GameSystem? = null,
        // Player chrome (Robert, 2026-11-12): the top bar
        // hides during gameplay unless dev stats are on.
        // The two layout-flow tests below seed it on, so the
        // Pause button is there to open the pause menu with;
        // the bar tests below leave it off (the default) and
        // pin the hiding instead.
        devStats: Boolean = false,
    ) {
        val root = File(temp.root, "stores").apply { mkdirs() }
        if (devStats) AppSettingsStore(root).setDevStatsEnabled(true)
        composeRule.setContent {
            PoingTheme {
                GameScreen(
                    gamePath = gamePath,
                    gameName = "TETRIS",
                    isGba = isGba,
                    system = system ?: if (isGba) GameSystem.GBA else GameSystem.GB,
                    optionsStore = CoreOptionsStore(root),
                    appSettings = AppSettingsStore(root),
                    onQuit = {},
                    onError = {},
                )
            }
        }
    }

    private fun layoutFile(base: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.filesDir.resolve("control-layouts").resolve("$base.properties")
    }

    @Test
    fun moveControlsDragSavesPerGameLayout() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor-dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithTag("editor-done").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        val cy = props.getProperty("dpad.cy")?.toFloatOrNull()
        assertNotNull("dpad.cy should be saved after the drag", cy)
        assertTrue("dragging up should save a smaller cy than the 0.5 default, got $cy", cy!! < 0.5f)
    }

    @Test
    fun resetRestoresTheDefaults() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithTag("editor-reset").performClick()
        composeRule.onNodeWithTag("editor-done").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        assertEquals(0.5f, props.getProperty("dpad.cy")?.toFloatOrNull() ?: -1f, 0.001f)
        assertEquals(144, props.getProperty("dpad.size")?.toIntOrNull())
    }

    /**
     * Layout rework (Robert, 2026-11-12): the L/R tabs live at
     * the top corners of the player area for GBA games — and
     * nowhere for GB/GBC games, which have no L/R. assertExists,
     * not assertIsDisplayed: the Robolectric window is narrower
     * than the full player area, so a corner tab can sit outside
     * it — what this pins is that the tabs are in the overlay,
     * not in the top bar or a row mid-screen.
     */
    @Test
    fun lrTabsSitAtTheTopCornersForGba() {
        val game = File(temp.root, "game.gba")
        setContent(game.absolutePath, isGba = true)
        composeRule.onNodeWithTag("controls-l").assertExists()
        composeRule.onNodeWithTag("controls-r").assertExists()
        composeRule.onNodeWithTag("hold-L").assertExists()
        composeRule.onNodeWithTag("hold-R").assertExists()
    }

    @Test
    fun noLrTabsForGbGames() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithTag("controls-l").assertDoesNotExist()
        composeRule.onNodeWithTag("controls-r").assertDoesNotExist()
    }

    /**
     * Layout rework (Robert, 2026-11-12): Start/Select are small
     * pills side by side, overlaid at the bottom centre of the
     * player area, not stacked in the middle of a bottom row.
     * Pinned by their bounds: same top edge, Start entirely left
     * of Select. Overlay rework (Robert, 2026-11-13): each is
     * its own control now, placed by its own fraction.
     */
    @Test
    fun startSelectSitSideBySideAtBottomCentre() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithTag("controls-start").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-select").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-Start").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-Select").assertIsDisplayed()
        val d = composeRule.density.density
        val start = composeRule.onNodeWithTag("hold-Start").fetchSemanticsNode().boundsInRoot
        val select = composeRule.onNodeWithTag("hold-Select").fetchSemanticsNode().boundsInRoot
        assertEquals(start.top, select.top, 2f * d)
        assertTrue("Start should sit left of Select", start.right <= select.left)
    }

    /**
     * Per-system styles (Robert, 2026-11-12): the overlay renders
     * for every system — including GBC, whose theme (dark grey
     * buttons on the Atomic Purple well) is a third path through
     * ControlOverlay, not just GB/GBA. The theme VALUES are pinned
     * by ControlThemesTest; this pins that the GBC wiring reaches
     * the player at all.
     */
    @Test
    fun overlayRendersForEverySystem() {
        val game = File(temp.root, "game.gbc")
        setContent(game.absolutePath, system = GameSystem.GBC)
        composeRule.onNodeWithTag("controls-dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-a").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-b").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-A").assertIsDisplayed()
        composeRule.onNodeWithTag("hold-B").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-l").assertDoesNotExist()
        composeRule.onNodeWithTag("controls-r").assertDoesNotExist()
    }

    /**
     * Layout rework (Robert, 2026-11-12): the D-pad lives in
     * the left flank bar and A/B in the right flank bar — the
     * black bars left and right of the aspect-correct game.
     * Pinned by their centres, not their edges: in the narrow
     * Robolectric window the game is width-limited, so the
     * flanks shrink to a sliver, but the D-pad's centre is still
     * left of the A/B cluster's centre.
     */
    @Test
    fun dpadSitsInTheLeftFlankAndAbInTheRight() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithTag("controls-dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-a").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-b").assertIsDisplayed()
        val dpad = composeRule.onNodeWithTag("controls-dpad").fetchSemanticsNode().boundsInRoot
        val a = composeRule.onNodeWithTag("controls-a").fetchSemanticsNode().boundsInRoot
        assertTrue("D-pad centre should sit left of the A centre", dpad.center.x < a.center.x)
    }

    /**
     * Player chrome (Robert, 2026-11-12): with dev stats off
     * (the default), the top bar hides during active gameplay
     * — no Pause button, no diagnostic line — and the game
     * takes the bar's height: the player area fills the whole
     * root. The power light detaches from the bar and stays
     * visible, pinned to the top-left corner.
     */
    @Test
    fun topBarHiddenDuringGameplayWithDevStatsOff() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
        composeRule.onNodeWithTag("power-light").assertIsDisplayed()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val area = composeRule.onNodeWithTag("player-area").fetchSemanticsNode().boundsInRoot
        assertEquals(root.height, area.height, 2f)
    }

    /**
     * Player chrome (Robert, 2026-11-12): with dev stats on,
     * the bar stays visible during gameplay — the stats are
     * the point — so the Pause button is there and the player
     * area is shorter than the root by the bar's height.
     */
    @Test
    fun topBarVisibleWithDevStatsOn() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        val d = composeRule.density.density
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val area = composeRule.onNodeWithTag("player-area").fetchSemanticsNode().boundsInRoot
        assertTrue("player area should be shorter than the root by the bar", area.height < root.height - 10f * d)
    }

    /**
     * Top bar / back swipe (Robert, 2026-11-12): tapping the
     * game area does NOTHING to the bar any more — the tap
     * peek is gone. The bar is only reachable by a back
     * swipe (see backSwipeShowsTheBar below).
     */
    @Test
    fun tapOnGameAreaDoesNotShowTheBar() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
        composeRule.onNodeWithTag("game-area").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
    }

    /**
     * Top bar / back swipe (Robert, 2026-11-12): a back
     * swipe is the ONLY way to bring the hidden bar back —
     * it shows for a few seconds (TOP_BAR_PEEK_MS = 5s),
     * then hides again. Each swipe restarts the clock.
     */
    @Test
    fun backSwipeShowsTheBar() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
    }

    /**
     * Top bar / back swipe (Robert, 2026-11-12): while the
     * bar is already visible (paused here), a back swipe
     * does nothing — no exit, no resume; the bar's controls
     * are the way out. The game stays paused (both Resume
     * nodes, bar + menu, still there).
     */
    @Test
    fun backSwipeWhilePausedDoesNothing() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithTag("top-bar-button").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top-bar-button").assertIsDisplayed()
        composeRule.onAllNodesWithText("Resume").assertCountEquals(2)
    }

    /**
     * Top bar / back swipe (Robert, 2026-11-12): while a
     * sub-screen is open — Core Options here — back closes
     * it, like any other screen, and lands back on the pause
     * menu (its "Move controls" row is there again).
     */
    @Test
    fun backSwipeClosesCoreOptionsOverlay() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Core options").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset to defaults").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        // The back DID close Core Options — the "Reset to
        // defaults" count below is what proves it — and the
        // pause menu is back. "Move controls" needs a scroll
        // first: the menu is a long scrollable column and the
        // row sits below the fold in the narrow Robolectric
        // window (the BackHandler and the showSettings
        // rendering were verified correct; the failure was
        // this assertion demanding a below-fold row be
        // displayed without scrolling). Scrolling is
        // navigation, not a weaker assertion — the same
        // pattern the layout-flow tests in this file use.
        composeRule.onNodeWithText("Move controls").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Reset to defaults").assertCountEquals(0)
    }

    /**
     * Player chrome (Robert, 2026-11-12): tapping a control
     * does NOT peek the bar — the controls are siblings
     * overlaid on top of the game area, not children of it,
     * so a tap on one is not in the game area's hit path.
     * This pins that separation: a D-pad tap leaves the bar
     * hidden.
     */
    @Test
    fun tapOnControlDoesNotPeekTheBar() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithTag("controls-dpad").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
    }

    /**
     * Player chrome (Robert, 2026-11-12): while paused the
     * bar stays visible — the Resume button must be there —
     * and it does not hide on the peek timer. The bar's button
     * is asserted by its tag, not its text: the pause menu has
     * its own Resume button with the same text, so a text
     * lookup finds two nodes. The count pins both are there
     * (bar + menu).
     */
    @Test
    fun topBarStaysVisibleWhilePaused() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithTag("top-bar-button").assertIsDisplayed()
        composeRule.onAllNodesWithText("Resume").assertCountEquals(2)
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top-bar-button").assertIsDisplayed()
        composeRule.onAllNodesWithText("Resume").assertCountEquals(2)
    }

    /**
     * Overlay rework (Robert, 2026-11-13): while editing the
     * layout the top bar hides — the editor owns the top, with
     * its own Done pill — even with dev stats on, and Done
     * commits the layout and returns to the pause menu.
     */
    @Test
    fun editorHidesTheTopBarAndDoneExitsEditing() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor-done").assertIsDisplayed()
        composeRule.onNodeWithTag("top-bar-button").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor-done").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-done").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().assertIsDisplayed()
    }
}
