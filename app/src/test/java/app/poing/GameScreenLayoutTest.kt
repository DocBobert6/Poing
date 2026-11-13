package app.poing

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Phase 6 (item 4) — per-game saved control positions, through
 * the player: pause, "Move controls", drag the D-pad, Done, and
 * the layout is in the game's file.
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

    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithText("Drag the controls to move them").assertIsDisplayed()
        composeRule.onNodeWithTag("controls-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithText("Done").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        val y = props.getProperty("dpad.y")?.toFloatOrNull()
        assertNotNull("dpad.y should be saved after the drag", y)
        assertTrue("dragging up should save a negative y, got $y", y!! < 0f)
    }

    @Test
    fun resetRestoresTheDefaults() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithTag("controls-dpad").performTouchInput {
            swipe(start = center, end = center + Offset(0f, -200f), durationMillis = 300)
        }
        composeRule.onNodeWithText("Reset").performClick()
        val props = Properties().apply {
            layoutFile("game").inputStream().use { load(it) }
        }
        assertTrue(props.getProperty("dpad.y")?.toFloatOrNull() == 0f)
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
     * of Select.
     */
    @Test
    fun startSelectSitSideBySideAtBottomCentre() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onNodeWithTag("controls-startselect").assertIsDisplayed()
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
        composeRule.onNodeWithTag("controls-ab").assertIsDisplayed()
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
        composeRule.onNodeWithTag("controls-ab").assertIsDisplayed()
        val dpad = composeRule.onNodeWithTag("controls-dpad").fetchSemanticsNode().boundsInRoot
        val ab = composeRule.onNodeWithTag("controls-ab").fetchSemanticsNode().boundsInRoot
        assertTrue("D-pad centre should sit left of the A/B centre", dpad.center.x < ab.center.x)
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
     * Player chrome (Robert, 2026-11-12): tapping the game
     * area brings the hidden bar back for a few seconds
     * (TOP_BAR_PEEK_MS = 5s), then it hides again. Each tap
     * restarts the clock.
     */
    @Test
    fun tapOnGameAreaPeeksTheBar() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath)
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
        composeRule.onNodeWithTag("game-area").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Pause").assertCountEquals(0)
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
     * and it does not hide on the peek timer.
     */
    @Test
    fun topBarStaysVisibleWhilePaused() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    /**
     * Player chrome (Robert, 2026-11-12): while editing the
     * layout the bar stays visible — the Done button lives
     * in it — and it does not hide on the peek timer.
     */
    @Test
    fun topBarStaysVisibleWhileEditingLayout() {
        val game = File(temp.root, "game.gb")
        setContent(game.absolutePath, devStats = true)
        composeRule.onNodeWithText("Pause").performClick()
        composeRule.onNodeWithText("Move controls").performScrollTo().performClick()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }
}
