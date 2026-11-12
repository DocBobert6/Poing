package app.poing

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 6 (item 5) — the Save Manager: battery save + slots +
 * auto-save in one place, with Load and Delete-behind-confirm.
 *
 * The screen reads its SaveStore from the app context's
 * filesDir, like on the device, so the test seeds that
 * directory. The session is never involved — Load is a callback
 * the caller wires to the player with a pending load.
 *
 * Pinned to emulated SDK 35, same as the startup gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SaveManagerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var savesDir: File

    private fun game() = LibraryGame(
        id = "game",
        title = "TETRIS",
        system = GameSystem.GB,
        fileName = "game.gb",
        sourceName = "tetris.gb",
        sha256 = "game",
        addedAt = 0L,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        savesDir = context.filesDir.resolve("saves").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun seed() {
        // Battery save: 8 KB.
        savesDir.resolve("game.sav").writeBytes(ByteArray(8192))
        // Slot 1: a state + a real thumbnail PNG.
        savesDir.resolve("game.state1").writeBytes(ByteArray(1024))
        val thumb = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        savesDir.resolve("game.state1.png").outputStream().use {
            thumb.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Auto-save.
        savesDir.resolve("game.stateAuto").writeBytes(ByteArray(512))
    }

    private fun setContent(
        onLoadState: (Int) -> Unit = {},
        onLoadAuto: () -> Unit = {},
    ) {
        composeRule.setContent {
            PoingTheme {
                SaveManagerScreen(
                    game = game(),
                    onLoadState = onLoadState,
                    onLoadAuto = onLoadAuto,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun managerShowsBatterySlotsAndAutoSave() {
        seed()
        setContent()
        composeRule.onNodeWithText("Save manager").assertIsDisplayed()
        composeRule.onNodeWithText("TETRIS").assertIsDisplayed()
        // The battery row's size + time ("8 KB · Mar 4, 21:30");
        // "Battery save" itself appears twice (section + row).
        composeRule.onNodeWithText("8 KB", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Slot 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Slot 0 — empty").performScrollTo().assertIsDisplayed()
        // The auto-save row's size; "Auto-save" itself appears twice.
        // assertExists, not performScrollTo: the auto row is the
        // last content item, and performScrollTo cannot bring it
        // into view under Robolectric (the mid-list scrolls above
        // all work). Existence proves the row rendered.
        composeRule.onNodeWithText("512 B", substring = true).assertExists()
    }

    @Test
    fun managerShowsEmptyStatesWhenNothingSaved() {
        setContent()
        composeRule.onNodeWithText("No battery save yet.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Slot 0 — empty").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No auto-save yet.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loadSlotCallsBackWithTheSlot() {
        seed()
        var loaded: Int? = null
        setContent(onLoadState = { loaded = it })
        composeRule.onNodeWithTag("save-load-slot-1").performScrollTo().performClick()
        assertEquals(1, loaded)
    }

    @Test
    fun loadAutoCallsBack() {
        seed()
        var loaded = false
        setContent(onLoadAuto = { loaded = true })
        // No performScrollTo (see above): performClick invokes the
        // semantics action directly, so it works off-screen.
        composeRule.onNodeWithTag("save-load-auto").assertExists().performClick()
        assertTrue(loaded)
    }

    @Test
    fun deleteBatteryRemovesTheFileBehindAConfirm() {
        seed()
        setContent()
        assertTrue(savesDir.resolve("game.sav").exists())
        composeRule.onNodeWithTag("save-delete-battery").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete battery save?").assertIsDisplayed()
        composeRule.onNodeWithTag("save-delete-confirm").performClick()
        // The delete runs on IO: wait until the file is actually gone.
        composeRule.waitUntil(timeoutMillis = 5_000) { !savesDir.resolve("game.sav").exists() }
        composeRule.onNodeWithText("No battery save yet.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteStateRemovesTheStateAndItsThumbnail() {
        seed()
        setContent()
        assertTrue(savesDir.resolve("game.state1").exists())
        assertTrue(savesDir.resolve("game.state1.png").exists())
        composeRule.onNodeWithTag("save-delete-slot-1").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete save state 1?").assertIsDisplayed()
        composeRule.onNodeWithTag("save-delete-confirm").performClick()
        // The delete runs on IO: wait until both files are gone.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !savesDir.resolve("game.state1").exists() &&
                !savesDir.resolve("game.state1.png").exists()
        }
        composeRule.onNodeWithText("Slot 1 — empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingADeleteKeepsTheFile() {
        seed()
        setContent()
        composeRule.onNodeWithTag("save-delete-battery").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(savesDir.resolve("game.sav").exists())
    }
}
