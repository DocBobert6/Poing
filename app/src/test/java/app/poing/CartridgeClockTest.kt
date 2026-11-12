package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6 (item 12) — the cartridge clock math (pure Kotlin). */
class CartridgeClockTest {

    /** A buffer at day 5, 10:15:30, anchored at [anchor]. */
    private fun bufferAt(anchor: Long, daysHi: Int = 0): ByteArray {
        val buffer = ByteArray(CartridgeClock.BUFFER_SIZE)
        fun putU32(offset: Int, value: Int) {
            for (i in 0 until 4) buffer[offset + i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }
        fun putU64(offset: Int, value: Long) {
            for (i in 0 until 8) buffer[offset + i] = ((value ushr (8 * i)) and 0xFFL).toByte()
        }
        putU32(0, 30)
        putU32(4, 15)
        putU32(8, 10)
        putU32(12, 5)
        putU32(16, daysHi)
        putU32(20, 30)
        putU32(24, 15)
        putU32(28, 10)
        putU32(32, 5)
        putU32(36, daysHi)
        putU64(40, anchor)
        return buffer
    }

    @Test
    fun currentTimeAddsTheElapsedSeconds() {
        val anchor = 1_000_000L
        val time = CartridgeClock.currentTime(bufferAt(anchor), anchor + 90L)
        assertNotNull(time)
        assertEquals(5, time!!.days)
        assertEquals(10, time.hour)
        assertEquals(17, time.min)
        assertEquals(0, time.sec)
        assertFalse(time.halted)
    }

    @Test
    fun currentTimeReturnsNullForAWrongSizeBuffer() {
        assertNull(CartridgeClock.currentTime(ByteArray(0), 0L))
        assertNull(CartridgeClock.currentTime(ByteArray(47), 0L))
        assertNull(CartridgeClock.adjust(ByteArray(0), 3_600L, 0L))
    }

    @Test
    fun adjustSpinsForwardAnHour() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor), 3_600L, anchor)
        assertNotNull(spun)
        val time = CartridgeClock.currentTime(spun!!, anchor)
        assertEquals(5, time!!.days)
        assertEquals(11, time.hour)
        assertEquals(15, time.min)
        assertEquals(30, time.sec)
    }

    @Test
    fun adjustSpinsBackADay() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor), -86_400L, anchor)
        assertNotNull(spun)
        val time = CartridgeClock.currentTime(spun!!, anchor)
        assertEquals(4, time!!.days)
        assertEquals(10, time.hour)
    }

    @Test
    fun adjustReAnchorsUnixTimeAndMatchesTheLatchedFields() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor), 3_600L, anchor + 500L)!!
        // Reading it back at the new anchor shows the spun time.
        val time = CartridgeClock.currentTime(spun, anchor + 500L)
        assertEquals(11, time!!.hour)
        // The latched fields (offset 20..39) carry the same time.
        fun u32(offset: Int): Int {
            var value = 0
            for (i in 0 until 4) value = value or ((spun[offset + i].toInt() and 0xFF) shl (8 * i))
            return value
        }
        assertEquals(u32(0), u32(20))
        assertEquals(u32(4), u32(24))
        assertEquals(u32(8), u32(28))
        assertEquals(u32(12), u32(32))
        assertEquals(u32(16), u32(36))
    }

    @Test
    fun adjustClampsAtZero() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor), -10_000_000L, anchor)
        val time = CartridgeClock.currentTime(spun!!, anchor)
        assertEquals(0, time!!.days)
        assertEquals(0, time.hour)
        assertEquals(0, time.min)
        assertEquals(0, time.sec)
    }

    @Test
    fun adjustClampsAtMaxDays() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor), 600L * 86_400L, anchor)
        val time = CartridgeClock.currentTime(spun!!, anchor)
        assertEquals(CartridgeClock.MAX_DAYS, time!!.days)
        assertEquals(23, time.hour)
        assertEquals(59, time.min)
        assertEquals(59, time.sec)
    }

    @Test
    fun adjustPreservesTheHaltBit() {
        val anchor = 1_000_000L
        val spun = CartridgeClock.adjust(bufferAt(anchor, daysHi = 0x40), 3_600L, anchor)!!
        val time = CartridgeClock.currentTime(spun, anchor)
        assertTrue(time!!.halted)
        assertEquals("Day 5  11:15:30 (halted)", CartridgeClock.format(time))
    }

    @Test
    fun dayCounterBitEightRoundTrips() {
        // days 200 + daysHi bit 0 = day 456.
        val anchor = 1_000_000L
        val buffer = bufferAt(anchor, daysHi = 1)
        // The stored days field is 5 in bufferAt; set it to 200.
        buffer[12] = 200.toByte()
        buffer[32] = 200.toByte()
        val time = CartridgeClock.currentTime(buffer, anchor)
        assertEquals(456, time!!.days)
        val spun = CartridgeClock.adjust(buffer, 0L, anchor)!!
        val back = CartridgeClock.currentTime(spun, anchor)
        assertEquals(456, back!!.days)
    }

    @Test
    fun formatShowsDayAndClock() {
        val time = CartridgeClock.RtcTime(days = 12, hour = 14, min = 32, sec = 7, halted = false)
        assertEquals("Day 12  14:32:07", CartridgeClock.format(time))
    }
}
