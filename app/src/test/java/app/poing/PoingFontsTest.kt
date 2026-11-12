package app.poing

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 — the font system actually ships. A missing or
 * misnamed font resource fails silently in Compose (the text just
 * falls back to the default face), so these tests load every face
 * and every licence text and fail loudly instead.
 *
 * The OFL requires the licence text to travel with the font: each
 * font's full text lives in assets/fonts/ next to the font itself,
 * and NOTICE (the Licences screen) names each font with its
 * copyright. The DSEG name is reserved — the family names are kept
 * exactly as the authors gave them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PoingFontsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dotGothic16Loads() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.dotgothic16))
    }

    @Test
    fun vt323Loads() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.vt323))
    }

    @Test
    fun dseg7Loads() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.dseg7))
    }

    @Test
    fun dseg14Loads() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.dseg14))
    }

    @Test
    fun oflTextsTravelWithTheFonts() {
        val ofl = mapOf(
            "fonts/OFL-DotGothic16.txt" to "DotGothic16",
            "fonts/OFL-VT323.txt" to "VT323",
            "fonts/OFL-DSEG.txt" to "DSEG",
        )
        for ((asset, name) in ofl) {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            assertTrue("$asset must carry the OFL text", "SIL Open Font License" in text)
            assertTrue("$asset must name $name's copyright", name in text)
        }
        val dseg = context.assets.open("fonts/OFL-DSEG.txt").bufferedReader().use { it.readText() }
        assertTrue("DSEG's Reserved Font Name must be recorded",
            "Reserved Font Name \"DSEG\"" in dseg)
    }

    @Test
    fun displaySizesAreTheApprovedGridSteps() {
        displayTextStyle(DisplaySizeSmall)
        displayTextStyle(DisplaySizeMedium)
        displayTextStyle(DisplaySizeLarge)
        for (bad in listOf(12, 14, 20, 28, 36, 48)) {
            try {
                displayTextStyle(bad)
                throw AssertionError("displayTextStyle($bad) must reject a non-grid size")
            } catch (_: IllegalArgumentException) {
                // Expected: off-grid sizes go soft.
            }
        }
    }
}
