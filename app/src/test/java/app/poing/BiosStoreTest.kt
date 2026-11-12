package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiosStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun missingThenInstalledThenDeleted() {
        val bios = BiosStore(temp.root)
        assertFalse(bios.isInstalled(BiosKind.GBA))
        bios.import(BiosKind.GBA, ByteArray(0x4000))
        assertTrue(bios.isInstalled(BiosKind.GBA))
        assertTrue(bios.delete(BiosKind.GBA))
        assertFalse(bios.isInstalled(BiosKind.GBA))
    }

    @Test
    fun eachKindHasItsOwnFile() {
        val bios = BiosStore(temp.root)
        bios.import(BiosKind.GB, byteArrayOf(1))
        assertTrue(bios.isInstalled(BiosKind.GB))
        assertFalse(bios.isInstalled(BiosKind.GBC))
        assertFalse(bios.isInstalled(BiosKind.GBA))
    }

    @Test
    fun reimportReplaces() {
        val bios = BiosStore(temp.root)
        bios.import(BiosKind.GB, byteArrayOf(1))
        bios.import(BiosKind.GB, byteArrayOf(1, 2, 3))
        assertEquals(3L, bios.file(BiosKind.GB).length())
    }
}
