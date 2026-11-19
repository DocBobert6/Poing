package app.poing

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen Optics dim fix (Robert, 2026-11-12): the live lux
 * shows in the diagnostic line when dev stats are on, so the
 * room's light — and whether the dim engages — is visible on
 * the screen itself. Alpha.32 — the line is the fps counter
 * chip in the overlay now, not a row in the top bar; these
 * tests pin the chip's content. Pinned to emulated SDK 35,
 * same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stats = PlayerStats(
        width = 160,
        height = 144,
        coreFps = 59.7,
        measuredFps = 58.9,
        framesDrawn = 296,
        sampleRate = 48000,
    )

    private fun setContent(
        stats: PlayerStats? = this.stats,
        lux: Int? = null,
        drawPath: DrawPath? = null,
        edgeOn: Boolean = false,
    ) {
        composeRule.setContent {
            PoingTheme {
                FpsCounterChip(
                    stats = stats,
                    theme = ControlThemes.themeFor(GameSystem.GB),
                    lux = lux,
                    drawPath = drawPath,
                    edgeOn = edgeOn,
                )
            }
        }
    }

    @Test
    fun diagnosticLineShowsStartingBeforeTheFirstStats() {
        setContent(stats = null)
        composeRule.onNodeWithText("starting…", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineShowsLiveLux() {
        setContent(lux = 42)
        // The diagnostic is one Text built from an annotated
        // string, so "42lx" is a substring of the line, not a
        // node of its own — match by substring.
        composeRule.onNodeWithText("42lx", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineHidesLuxWithoutAReading() {
        setContent(lux = null)
        composeRule.onAllNodesWithText("lx", substring = true).assertCountEquals(0)
    }

    @Test
    fun diagnosticLineShowsEdgeWhenTheEdgeProgramDrew() {
        // Edge smoothing, item 5 (2026-11-14): the
        // dev-stats readout — the line names the program
        // that actually drew the last frame.
        setContent(drawPath = DrawPath.EDGE, edgeOn = true)
        composeRule.onNodeWithText("edge", substring = true).assertExists()
        composeRule.onAllNodesWithText("fell back", substring = true).assertCountEquals(0)
    }

    @Test
    fun diagnosticLineShowsEdgeGridForTheCombinedMode() {
        setContent(drawPath = DrawPath.EDGE_GRID, edgeOn = true)
        composeRule.onNodeWithText("edge+grid", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineShowsChainWhenTheChainDrew() {
        setContent(drawPath = DrawPath.CHAIN, edgeOn = false)
        composeRule.onNodeWithText("chain", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineNamesTheFallbackWhenEdgeWasRequestedButPlainDrew() {
        // The settler: edge on, picture plain — the line
        // must say the edge fell back, not read as a quiet
        // "plain".
        setContent(drawPath = DrawPath.PLAIN, edgeOn = true)
        composeRule.onNodeWithText("plain (edge fell back)", substring = true).assertExists()
    }

    @Test
    fun diagnosticLineShowsPlainWithoutTheFallbackNoteWhenEdgeIsOff() {
        setContent(drawPath = DrawPath.PLAIN, edgeOn = false)
        composeRule.onNodeWithText("plain", substring = true).assertExists()
        composeRule.onAllNodesWithText("fell back", substring = true).assertCountEquals(0)
    }

    @Test
    fun diagnosticLineShowsNoDrawPathTokenWithoutAReading() {
        setContent()
        composeRule.onAllNodesWithText("edge", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("chain", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("plain", substring = true).assertCountEquals(0)
    }

    @Test
    fun floatingStatusRecClockFollowsTheState() {
        // Performance fix 9 — the REC clock is a State, so
        // the 1s tick recomposes only the tiny REC row, not
        // the whole GameScreen scope. The text follows the
        // state without the caller re-composing.
        val elapsed = mutableStateOf(5L)
        composeRule.setContent {
            PoingTheme {
                FloatingPlayerStatus(
                    paused = false,
                    recording = true,
                    recordingElapsedState = elapsed,
                    barVisible = false,
                )
            }
        }
        composeRule.onNodeWithText("● REC 0:05", substring = true).assertExists()
        elapsed.value = 65L
        composeRule.onNodeWithText("● REC 1:05", substring = true).assertExists()
    }

    @Test
    fun floatingStatusRecClockHiddenWhileTheBarIsVisible() {
        // The REC text lives in the bar Row while the bar is
        // up; the floating copy only shows when it is down.
        val elapsed = mutableStateOf(5L)
        composeRule.setContent {
            PoingTheme {
                FloatingPlayerStatus(
                    paused = false,
                    recording = true,
                    recordingElapsedState = elapsed,
                    barVisible = true,
                )
            }
        }
        composeRule.onAllNodesWithText("REC", substring = true).assertCountEquals(0)
    }
}
