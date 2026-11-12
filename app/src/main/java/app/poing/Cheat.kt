package app.poing

/**
 * Phase 6 (item 3) — cheats: GameShark / Game Genie / CodeBreaker
 * via mGBA's retro_cheat_set.
 *
 * One cheat is one code plus its on/off switch. The core applies
 * the enabled ones in list order (reset, then re-add — the
 * libretro call ignores its enabled flag, so disabling one cheat
 * means re-applying the rest).
 *
 * Pure Kotlin, no Android APIs, so the format rules are
 * unit-tested on the JVM (CheatFormatTest).
 */
data class Cheat(val code: String, val enabled: Boolean)

object CheatFormat {
    // GBA: GameShark "XXXXXXXX XXXXXXXX", CodeBreaker "XXXXXXXX YYYY".
    private val GBA_GAMESHARK = Regex("^[0-9A-F]{8} [0-9A-F]{8}$")
    private val GBA_CODEBREAKER = Regex("^[0-9A-F]{8} [0-9A-F]{4}$")

    // GB/GBC: Game Genie "XXX-XXX" / "XXX-XXX-XXX", GameShark 8 hex, VBA "XXXX:XX".
    private val GB_GAME_GENIE = Regex("^[0-9A-F]{3}-[0-9A-F]{3}(-[0-9A-F]{3})?$")
    private val GB_GAMESHARK = Regex("^[0-9A-F]{8}$")
    private val GB_VBA = Regex("^[0-9A-F]{4}:[0-9A-F]{2}$")

    /** True when [rawCode] is a cheat the core accepts for [system]. */
    fun isValid(rawCode: String, system: GameSystem): Boolean =
        normalize(rawCode, system) != null

    /**
     * The code as the core wants it (uppercase, one separator), or
     * null when it is not a valid cheat for [system]. A '+' reads
     * as a space (the core's converter treats them the same).
     *
     * GB CodeBreaker ("XXXX-XXXX-XX") is deliberately rejected:
     * the converter in retro_cheat_set splits GB codes into
     * 11-char lines, so a 12-char CodeBreaker code would be
     * truncated before the core ever sees it whole.
     */
    fun normalize(rawCode: String, system: GameSystem): String? {
        val code = rawCode.trim().uppercase()
        return when (system) {
            GameSystem.GBA -> {
                val spaced = code.replace(Regex("[+\\s]+"), " ")
                if (GBA_GAMESHARK.matches(spaced) || GBA_CODEBREAKER.matches(spaced)) spaced else null
            }
            GameSystem.GB, GameSystem.GBC -> {
                if (GB_GAME_GENIE.matches(code) || GB_GAMESHARK.matches(code) || GB_VBA.matches(code)) code else null
            }
        }
    }

    /** The accepted formats, for the hint line on the Cheats screen. */
    fun hint(system: GameSystem): String = when (system) {
        GameSystem.GBA -> "GameShark: XXXXXXXX XXXXXXXX · CodeBreaker: XXXXXXXX YYYY"
        GameSystem.GB, GameSystem.GBC -> "Game Genie: XXX-XXX-XXX · GameShark: XXXXXXXX · VBA: XXXX:XX"
    }
}
