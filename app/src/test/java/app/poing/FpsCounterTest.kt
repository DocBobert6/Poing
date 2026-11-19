package app.poing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Alpha.32 — the fps counter chip (Robert, 2026-11-19): the
 * dev-stats diagnostic line as a movable overlay chip,
 * themed per system. Pinned to emulated SDK 35, same as the
 * startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FpsCounterTest {

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

    // --- Colours: the chip is the theme's pressed colour behind white text. ---

    @Test
    fun `gb chip is maroon behind white text`() {
        val (background, text) = fpsChipColors(ControlThemes.themeFor(GameSystem.GB))
        assertEquals(Color(0xFFB03040.toInt()), background)
        assertEquals(Color.White, text)
    }

    @Test
    fun `gbc chip is purple behind white text`() {
        val (background, text) = fpsChipColors(ControlThemes.themeFor(GameSystem.GBC))
        assertEquals(Color(0xFF7E57C2.toInt()), background)
        assertEquals(Color.White, text)
    }

    @Test
    fun `gba chip is indigo behind white text`() {
        val (background, text) = fpsChipColors(ControlThemes.themeFor(GameSystem.GBA))
        assertEquals(Color(0xFF5B5BA6.toInt()), background)
        assertEquals(Color.White, text)
    }

    // --- Visibility: only while dev stats are on. ---

    private fun setOverlayContent(fpsVisible: Boolean) {
        composeRule.setContent {
            PoingTheme {
                Box(Modifier.fillMaxSize()) {
                    ControlOverlay(
                        isGba = false,
                        system = GameSystem.GB,
                        layout = ControlLayout.DEFAULT,
                        canvasWidthDp = 923f,
                        canvasHeightDp = 411f,
                        held = HeldInput(),
                        paused = false,
                        onInputChanged = {},
                        onControlDown = {},
                        fpsVisible = fpsVisible,
                        stats = stats,
                    )
                }
            }
        }
    }

    @Test
    fun `chip shows while dev stats are on`() {
        setOverlayContent(fpsVisible = true)
        composeRule.onNodeWithTag("fps-counter-placed").assertExists()
        composeRule.onNodeWithTag("fps-counter").assertExists()
        composeRule.onNodeWithText("160x144", substring = true).assertExists()
    }

    @Test
    fun `chip hides while dev stats are off`() {
        setOverlayContent(fpsVisible = false)
        composeRule.onNodeWithTag("fps-counter-placed").assertDoesNotExist()
        composeRule.onNodeWithTag("fps-counter").assertDoesNotExist()
    }

    // --- Editor: the sample is visible while editing. ---

    @Test
    fun `editor shows the sample counter`() {
        composeRule.setContent {
            PoingTheme {
                LayoutEditor(
                    initial = ControlLayout.DEFAULT,
                    isGba = false,
                    system = GameSystem.GB,
                    held = HeldInput(),
                    onInputChanged = {},
                    onControlDown = {},
                    onDone = { _, _, _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("editor-fps").assertIsDisplayed()
        composeRule.onNodeWithTag("fps-counter-sample").assertIsDisplayed()
    }

    // --- Persistence: a moved/resized chip round-trips through the store. ---

    @Test
    fun `moved fps chip round-trips through the store`() {
        val dir = Files.createTempDirectory("fps-layouts").toFile()
        try {
            val store = ControlLayoutStore(dir)
            val moved = ControlLayout.DEFAULT
                .moved(ControlId.FPS, 0.2f, 0.8f, grouped = false)
                .resized(ControlId.FPS, 400, grouped = false)
            store.save("abc123", moved)
            val loaded = store.load("abc123")
            assertEquals(0.2f, loaded.control(ControlId.FPS).cx, 0.001f)
            assertEquals(0.8f, loaded.control(ControlId.FPS).cy, 0.001f)
            assertEquals(400, loaded.control(ControlId.FPS).sizeDp)
        } finally {
            dir.deleteRecursively()
        }
    }
}
