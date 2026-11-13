package app.poing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Worm Light rework (Robert, 2026-11-12): the overlay renders
 * when on and is not composed at all when off — so it cannot
 * swallow a touch while off. The cone/head geometry itself is
 * pinned by WormLightGeometryTest on the plain JVM.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WormLightOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(enabled: Boolean) {
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(enabled = enabled)
                }
            }
        }
    }

    @Test
    fun overlayPresentWhenEnabled() {
        setContent(enabled = true)
        // assertExists is a member of SemanticsNodeInteraction,
        // not a top-level import (the same trap as the
        // screen-size fix and the control-moves fix).
        composeRule.onNodeWithTag("worm-light").assertExists()
    }

    @Test
    fun overlayAbsentWhenDisabled() {
        setContent(enabled = false)
        composeRule.onAllNodesWithTag("worm-light").assertCountEquals(0)
    }

    @Test
    fun overlayReportsItsPoolToTheGlView() {
        // Worm Light realism: the overlay reports the lamp's
        // pool up to the GL view whenever the head moves, so
        // the shader lights the picture (and dragging while
        // paused still moves the pool). In a 300x200dp box
        // the default head is centred, so the pool is the
        // top-centre of the game at full intensity.
        var reported: WormLightPool? = null
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    WormLightOverlay(
                        enabled = true,
                        onPoolChange = { reported = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val pool = reported
        assertTrue("the overlay should report its pool", pool != null)
        pool!!
        assertEquals(1f, pool.intensity, 0.001f)
        assertEquals(0.5f, pool.center.x, 0.01f)
        assertEquals(0.20f, pool.center.y, 0.01f)
        assertEquals(0.55f, pool.radii.x, 0.01f)
        assertEquals(0.42f, pool.radii.y, 0.01f)
        assertEquals(0f, pool.rotationRad, 0.001f)
    }
}
