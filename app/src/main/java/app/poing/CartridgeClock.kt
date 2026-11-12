package app.poing

/**
 * Phase 6 (item 12) — the cartridge clock (MBC3 RTC): the 48-byte
 * GBMBCRTCSaveBuffer the core exposes as RETRO_MEMORY_RTC
 * (include/mgba/internal/gb/mbc.h:34): ten little-endian uint32
 * (sec, min, hour, days, daysHi, latchedSec, latchedMin,
 * latchedHour, latchedDays, latchedDaysHi) + a little-endian
 * uint64 unixTime.
 *
 * How the core keeps the time (verified in the pinned tree,
 * src/gb/mbc/mbc.c _GBMBCLatchRTC): the live counters sit in the
 * core's RAM (rtcRegs) and advance only when the game latches
 * them, by (now - rtcLastLatch) seconds. The buffer is the SAVE
 * format: the core reads it at load (GBMBCRTCRead) and writes it
 * on save-clean/unload (GBMBCRTCWrite). So writing the buffer
 * does NOT spin the live clock — the thumbwheel spins the SAVED
 * clock, and the game sees the new time when it next loads (the
 * panel says so). The buffer's unixTime is the latch anchor: the
 * next load takes the stored time and advances it from there.
 *
 * The day counter is 9 bits (days low 8 + daysHi bit 0); daysHi
 * bit 6 is the halt flag (the game stopped the clock) and bit 7
 * is the day-counter carry. adjust() preserves daysHi bits 1–7
 * and only sets bit 0 from the new count.
 *
 * Pure Kotlin, no Android APIs — covered by unit tests.
 */
object CartridgeClock {
    /** The GBMBCRTCSaveBuffer size, and the only size adjust() accepts. */
    const val BUFFER_SIZE = 48

    /** One day of counter (the 9-bit day counter tops out at 511). */
    const val MAX_DAYS = 511

    /** The cartridge time as the panel shows it. */
    data class RtcTime(
        val days: Int,
        val hour: Int,
        val min: Int,
        val sec: Int,
        /** True when the game halted the clock (daysHi bit 6). */
        val halted: Boolean,
    )

    private fun u32(buffer: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until 4) {
            value = value or ((buffer[offset + i].toInt() and 0xFF) shl (8 * i))
        }
        return value
    }

    private fun u64(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((buffer[offset + i].toLong() and 0xFFL) shl (8 * i))
        }
        return value
    }

    private fun putU32(buffer: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            buffer[offset + i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }
    }

    private fun putU64(buffer: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buffer[offset + i] = ((value ushr (8 * i)) and 0xFFL).toByte()
        }
    }

    /**
     * The cartridge time right now: the stored counters plus the
     * real seconds since the buffer's unixTime anchor (the core's
     * latch would advance the counters by exactly that much).
     * Null when the buffer is not a 48-byte RTC buffer.
     */
    fun currentTime(buffer: ByteArray, nowUnixSeconds: Long): RtcTime? {
        if (buffer.size != BUFFER_SIZE) return null
        val days9 = u32(buffer, 12) + ((u32(buffer, 16) and 1) shl 8)
        val stored = days9.toLong() * 86_400L +
            u32(buffer, 8) * 3_600L +
            u32(buffer, 4) * 60L +
            u32(buffer, 0)
        val total = (stored + (nowUnixSeconds - u64(buffer, 40))).coerceAtLeast(0L)
        return split(total, halted = (u32(buffer, 16) and 0x40) != 0)
    }

    /**
     * A new buffer with the clock spun by [deltaSeconds] (negative
     * spins it back). The stored and latched counters both carry
     * the new time, and unixTime is re-anchored at [nowUnixSeconds]
     * so the next load advances from the spun time. The result is
     * clamped to [0, MAX_DAYS days 23:59:59] — the 9-bit counter
     * cannot hold more. Null when the buffer is not a 48-byte RTC
     * buffer.
     */
    fun adjust(buffer: ByteArray, deltaSeconds: Long, nowUnixSeconds: Long): ByteArray? {
        val current = currentTime(buffer, nowUnixSeconds) ?: return null
        val maxTotal = MAX_DAYS.toLong() * 86_400L + 86_399L
        val total = (current.days.toLong() * 86_400L +
            current.hour * 3_600L +
            current.min * 60L +
            current.sec + deltaSeconds).coerceIn(0L, maxTotal)
        val spun = split(total, current.halted)
        val out = ByteArray(BUFFER_SIZE)
        val days9 = spun.days
        val daysHi = ((days9 shr 8) and 1) or (u32(buffer, 16) and 0xFE)
        putU32(out, 0, spun.sec)
        putU32(out, 4, spun.min)
        putU32(out, 8, spun.hour)
        putU32(out, 12, days9 and 0xFF)
        putU32(out, 16, daysHi)
        putU32(out, 20, spun.sec)
        putU32(out, 24, spun.min)
        putU32(out, 28, spun.hour)
        putU32(out, 32, days9 and 0xFF)
        putU32(out, 36, daysHi)
        putU64(out, 40, nowUnixSeconds)
        return out
    }

    /** "Day 12  14:32:07" (and " (halted)" when the game stopped the clock). */
    fun format(time: RtcTime): String {
        val clock = "%02d:%02d:%02d".format(time.hour, time.min, time.sec)
        val halted = if (time.halted) " (halted)" else ""
        return "Day ${time.days}  $clock$halted"
    }

    private fun split(totalSeconds: Long, halted: Boolean): RtcTime {
        val days = (totalSeconds / 86_400L).toInt()
        val rest = totalSeconds % 86_400L
        return RtcTime(
            days = days,
            hour = (rest / 3_600L).toInt(),
            min = ((rest % 3_600L) / 60L).toInt(),
            sec = (rest % 60L).toInt(),
            halted = halted,
        )
    }
}
