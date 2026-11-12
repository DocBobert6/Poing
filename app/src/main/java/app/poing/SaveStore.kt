package app.poing

import java.io.File

/**
 * Phase 4 — per-game save files in filesDir/saves/ (the
 * PlayerSession save dir). Two kinds, both keyed by the stored ROM
 * file's stem (the SHA-256 — stable, unique per game):
 *
 *   <base>.sav     — battery save: save RAM + RTC bytes, one file.
 *   <base>.stateN  — save state N (slots 0..3); slot 0 is the quick slot.
 *   <base>.stateN.png — the thumbnail for state N (Phase 6, item 2),
 *     a small PNG of the frame the state was saved on.
 *   <base>.stateAuto / <base>.stateAuto.png — the auto-save (Phase 6,
 *     item 2), written when the player quits; the latest quit wins.
 *
 * LibraryStore.remove() already deletes every savesDir file starting
 * with "<base>." — both kinds are cleaned up with the game, so
 * nothing here needs its own delete path.
 *
 * Pure java.io — no Android APIs — so the whole store runs under
 * plain JVM unit tests.
 */
class SaveStore(private val savesDir: File) {
    companion object {
        const val SLOT_COUNT = 4
        const val QUICK_SLOT = 0
    }

    fun saveFile(base: String): File = File(savesDir, "$base.sav")

    fun stateFile(base: String, slot: Int): File {
        require(slot in 0 until SLOT_COUNT) {
            "slot $slot out of range 0..${SLOT_COUNT - 1}"
        }
        return File(savesDir, "$base.state$slot")
    }

    fun stateExists(base: String, slot: Int): Boolean = stateFile(base, slot).exists()

    /** Last-modified time of a state, or null when the slot is empty. */
    fun stateModifiedAt(base: String, slot: Int): Long? =
        stateFile(base, slot).takeIf { it.exists() }?.lastModified()

    /** The thumbnail PNG for a save state (written beside the state file). */
    fun thumbnailFile(base: String, slot: Int): File {
        require(slot in 0 until SLOT_COUNT) {
            "slot $slot out of range 0..${SLOT_COUNT - 1}"
        }
        return File(savesDir, "$base.state$slot.png")
    }

    fun thumbnailExists(base: String, slot: Int): Boolean = thumbnailFile(base, slot).exists()

    /** The auto-save state, written when the player quits (latest quit wins). */
    fun autoStateFile(base: String): File = File(savesDir, "$base.stateAuto")

    /** The auto-save's thumbnail PNG. */
    fun autoThumbnailFile(base: String): File = File(savesDir, "$base.stateAuto.png")

    fun autoStateExists(base: String): Boolean = autoStateFile(base).exists()

    /** Last-modified time of the auto-save, or null when there is none. */
    fun autoStateModifiedAt(base: String): Long? =
        autoStateFile(base).takeIf { it.exists() }?.lastModified()
}
