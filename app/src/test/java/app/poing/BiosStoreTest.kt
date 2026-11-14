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

    @Test
    fun folderMatcherFindsAllThreeNames() {
        assertEquals(BiosKind.GB, biosKindForFileName("gb_bios.bin"))
        assertEquals(BiosKind.GBC, biosKindForFileName("gbc_bios.bin"))
        assertEquals(BiosKind.GBA, biosKindForFileName("gba_bios.bin"))
    }

    @Test
    fun folderMatcherIsCaseInsensitive() {
        assertEquals(BiosKind.GB, biosKindForFileName("GB_BIOS.BIN"))
        assertEquals(BiosKind.GBC, biosKindForFileName("Gbc_Bios.Bin"))
        assertEquals(BiosKind.GBA, biosKindForFileName("gba_BIOS.bin"))
    }

    @Test
    fun folderMatcherIgnoresUnrelatedFiles() {
        assertEquals(null, biosKindForFileName("readme.txt"))
        assertEquals(null, biosKindForFileName("bios.bin"))
        assertEquals(null, biosKindForFileName("gb_bios"))
        assertEquals(null, biosKindForFileName("gb_bios.bin.bak"))
        assertEquals(null, biosKindForFileName("gba_bios (1).bin"))
        assertEquals(null, biosKindForFileName("pokemon-red.gb"))
    }

    @Test
    fun folderMessageAllThreeImported() {
        assertEquals(
            "Imported Game Boy, Game Boy Color and Game Boy Advance BIOS.",
            biosFolderResultMessage(
                listOf(BiosKind.GBA, BiosKind.GB, BiosKind.GBC),
                emptyList(), emptyList(),
            ),
        )
    }

    @Test
    fun folderMessagePartialImportNamesWhatIsMissing() {
        assertEquals(
            "Imported Game Boy and Game Boy Advance BIOS. " +
                "Game Boy Color BIOS not found in that folder.",
            biosFolderResultMessage(
                listOf(BiosKind.GB, BiosKind.GBA),
                emptyList(), listOf(BiosKind.GBC),
            ),
        )
    }

    @Test
    fun folderMessageEmptyFolder() {
        assertEquals(
            "No BIOS files found in that folder.",
            biosFolderResultMessage(
                emptyList(), emptyList(),
                listOf(BiosKind.GB, BiosKind.GBC, BiosKind.GBA),
            ),
        )
    }

    @Test
    fun folderMessageUnreadableFileIsNamed() {
        assertEquals(
            "Imported Game Boy BIOS. " +
                "Game Boy Color BIOS could not be read. " +
                "Game Boy Advance BIOS not found in that folder.",
            biosFolderResultMessage(
                listOf(BiosKind.GB), listOf(BiosKind.GBC), listOf(BiosKind.GBA),
            ),
        )
    }
}
