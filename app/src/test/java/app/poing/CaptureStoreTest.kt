package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6 (item 11) — the capture file names (pure Kotlin). */
class CaptureStoreTest {

    @Test
    fun captureNameHasGameDateAndExtension() {
        // 2026-11-12 19:00:00 UTC = 1794490800000 ms.
        val name = CaptureStore.captureName("Tetris", 1794490800000L, "png")
        assertTrue(name.startsWith("Poing-Tetris-"))
        assertTrue(name.endsWith(".png"))
    }

    @Test
    fun captureNameSanitizesTheGameTitle() {
        val name = CaptureStore.captureName("Pokémon Red / Blue!", 1794490800000L, "mp4")
        // The accented letter stays (it is a letter); spaces and
        // the slash become underscores; the trailing bang becomes
        // an underscore and is trimmed.
        assertTrue(name.startsWith("Poing-Pokémon_Red___Blue-"))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun captureNameFallsBackForAnEmptyTitle() {
        val name = CaptureStore.captureName("///", 1794490800000L, "png")
        assertTrue(name.startsWith("Poing-game-"))
    }
}
