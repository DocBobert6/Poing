package app.poing

/**
 * The three systems Poing! plays, all from the one mGBA core.
 *
 * Detection is by ROM header, not by file extension: a GBC game
 * in a .gb file still plays as GBC, and a file that is not a Game
 * Boy ROM at all is rejected instead of being listed and then
 * failing to boot. The header checks are the ones the hardware
 * itself enforces — the Nintendo logo (the boot ROM verifies it)
 * and the GBA fixed byte — so any bootable ROM passes them.
 */
enum class GameSystem(val label: String, val extensions: Set<String>) {
    GB("GB", setOf("gb")),
    GBC("GBC", setOf("gbc")),
    GBA("GBA", setOf("gba"));

    companion object {
        fun fromExtension(name: String): GameSystem? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }

        /**
         * The 48-byte Nintendo logo at 0x104 in every GB/GBC ROM.
         * Verified against the tree's own test ROMs
         * (cinema/gb/acid/dmg-acid2 and cgb-acid2).
         */
        private const val GB_LOGO_HEX =
            "ceed6666cc0d000b03730083000c000d0008111f8889000edccc6ee6ddddd999bbbb67636e0eecccdddc999fbbb9333e"

        private val GB_LOGO: ByteArray by lazy {
            GB_LOGO_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        /** First four bytes of the GBA Nintendo logo (0x04-0x07). */
        private val GBA_LOGO_PREFIX = byteArrayOf(0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51)

        /**
         * Detect from ROM header bytes. GBA: the Nintendo logo
         * prefix at 0x04 plus the fixed 0x96 at 0xB2 (both verified
         * against cinema/gba test ROMs). GB/GBC: the 48-byte logo
         * at 0x104, then the CGB flag at 0x143 (0x80 = GBC,
         * 0xC0 = GBC-only). Null when the bytes match no header —
         * a text file or a too-short file is not a ROM.
         */
        fun fromHeader(bytes: ByteArray): GameSystem? {
            if (bytes.size >= 0xB3) {
                val logoOk = GBA_LOGO_PREFIX.indices.all { bytes[0x04 + it] == GBA_LOGO_PREFIX[it] }
                if (logoOk && bytes[0xB2] == 0x96.toByte()) return GBA
            }
            if (bytes.size >= 0x144 && GB_LOGO.indices.all { bytes[0x104 + it] == GB_LOGO[it] }) {
                return when (bytes[0x143].toInt() and 0xFF) {
                    0x80, 0xC0 -> GBC
                    else -> GB
                }
            }
            return null
        }

        /**
         * The ROM's internal title — GB 16 bytes at 0x134, GBC 11
         * bytes at 0x134 (the manufacturer code and CGB flag sit
         * after it), GBA 12 bytes at 0xA0 — or null when blank or
         * absent. Non-printable bytes are dropped, so a flag byte
         * can never leak into a title.
         */
        fun internalTitle(bytes: ByteArray, system: GameSystem): String? {
            val (start, length) = when (system) {
                GBA -> 0xA0 to 12
                GBC -> 0x134 to 11
                GB -> 0x134 to 16
            }
            if (bytes.size < start + length) return null
            val raw = bytes.copyOfRange(start, start + length)
            val end = raw.indexOfFirst { it == 0.toByte() }.let { if (it < 0) length else it }
            val text = String(raw, 0, end, Charsets.US_ASCII)
                .filter { it.code in 0x20..0x7E }
                .trim()
            return text.ifBlank { null }
        }
    }
}
