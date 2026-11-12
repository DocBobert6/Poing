package app.poing

import java.io.File

/**
 * Phase 4 — per-game save files in filesDir/saves/ (the
 * PlayerSession save dir). Two kinds, both keyed by the stored ROM
 * file's stem (the SHA-256 — stable, unique per game):
 *
 *   <base>.sav     — battery save: save RAM + RTC bytes, one file.
 *   <base>.stateN  — save state N (slots 0..3); slot 0 is the quick slot.
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
}
