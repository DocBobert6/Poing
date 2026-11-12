package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = SaveStore(temp.root)

    @Test
    fun fileNamesKeyedByGameBase() {
        val store = store()
        assertEquals("abc.sav", store.saveFile("abc").name)
        assertEquals("abc.state0", store.stateFile("abc", 0).name)
        assertEquals("abc.state3", store.stateFile("abc", 3).name)
        assertEquals(temp.root, store.saveFile("abc").parentFile)
    }

    @Test
    fun slotRangeEnforced() {
        val store = store()
        try {
            store.stateFile("abc", 4)
            throw AssertionError("slot 4 must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: only slots 0..3 exist.
        }
        try {
            store.stateFile("abc", -1)
            throw AssertionError("slot -1 must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun stateExistsAndModifiedTrackTheFile() {
        val store = store()
        assertFalse(store.stateExists("abc", 0))
        assertNull(store.stateModifiedAt("abc", 0))
        store.stateFile("abc", 0).writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(store.stateExists("abc", 0))
        assertNotNull(store.stateModifiedAt("abc", 0))
        assertTrue(store.stateModifiedAt("abc", 0)!! > 0)
        assertFalse(store.stateExists("abc", 1))
    }

    @Test
    fun quickSlotIsZero() {
        assertEquals(0, SaveStore.QUICK_SLOT)
        assertEquals(4, SaveStore.SLOT_COUNT)
    }
}
