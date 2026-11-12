package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoreOptionsStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = CoreOptionsStore(temp.root)

    @Test
    fun defaultsBeforeAnyWrite() {
        val store = store()
        assertEquals("OFF", store.get("mgba_color_correction"))
        assertEquals("Autodetect", store.get("mgba_gb_model"))
        assertEquals(CoreOptions.defaults(), store.all())
    }

    @Test
    fun setGetRoundTrip() {
        val store = store()
        assertTrue(store.set("mgba_color_correction", "Auto"))
        assertEquals("Auto", store.get("mgba_color_correction"))
        assertTrue(store.set("mgba_gb_colors", "DMG Green"))
        assertEquals("DMG Green", store.get("mgba_gb_colors"))
        // A fresh store on the same root reads the same values.
        assertEquals("Auto", CoreOptionsStore(temp.root).get("mgba_color_correction"))
        assertEquals("DMG Green", CoreOptionsStore(temp.root).get("mgba_gb_colors"))
    }

    @Test
    fun invalidValuesAreRejected() {
        val store = store()
        assertFalse(store.set("mgba_color_correction", "Neon"))
        assertEquals("OFF", store.get("mgba_color_correction"))
        assertFalse(store.set("mgba_gb_colors", "Not a palette"))
        assertEquals("Grayscale", store.get("mgba_gb_colors"))
        assertFalse(store.set("mgba_frameskip_threshold", "100"))
        assertEquals("33", store.get("mgba_frameskip_threshold"))
    }

    @Test
    fun unknownKeysAreRejected() {
        val store = store()
        assertFalse(store.set("mgba_not_a_real_option", "ON"))
        assertEquals("", store.get("mgba_not_a_real_option"))
    }

    @Test
    fun resetRestoresDefaults() {
        val store = store()
        store.set("mgba_color_correction", "GBA")
        store.set("mgba_use_bios", "OFF")
        assertEquals("GBA", store.get("mgba_color_correction"))
        store.reset()
        assertEquals(CoreOptions.defaults(), store.all())
    }

    @Test
    fun corruptFileReadsAsDefaults() {
        val store = store()
        store.set("mgba_color_correction", "GBC")
        temp.root.resolve("core-options.properties").writeText("this is not properties: \u0000\u0001")
        assertEquals("OFF", CoreOptionsStore(temp.root).get("mgba_color_correction"))
    }
}
