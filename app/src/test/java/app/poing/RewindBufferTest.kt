package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 6 (item 9) — the rewind ring buffer (pure Kotlin, no Android APIs). */
class RewindBufferTest {

    private fun snapshot(n: Int) = byteArrayOf(n.toByte())

    @Test
    fun emptyBufferHasNothingToPopOrPeek() {
        val buffer = RewindBuffer()
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
        assertNull(buffer.peek())
        assertNull(buffer.pop())
    }

    @Test
    fun popReturnsNewestFirst() {
        val buffer = RewindBuffer()
        buffer.push(snapshot(1), null)
        buffer.push(snapshot(2), byteArrayOf(9))
        buffer.push(snapshot(3), null)
        assertEquals(3, buffer.size)
        // A ByteArray holds Bytes, not Ints: compare as Ints, or
        // assertEquals(Integer, Byte) fails on the type alone.
        assertEquals(3, buffer.peek()?.state?.get(0)?.toInt())
        val newest = buffer.pop()
        assertEquals(3, newest?.state?.get(0)?.toInt())
        assertNull(newest?.raProgress)
        val middle = buffer.pop()
        assertEquals(2, middle?.state?.get(0)?.toInt())
        assertEquals(9, middle?.raProgress?.get(0)?.toInt())
        assertEquals(1, buffer.size)
    }

    @Test
    fun capacityDropsTheOldest() {
        val buffer = RewindBuffer(capacity = 3)
        buffer.push(snapshot(1), null)
        buffer.push(snapshot(2), null)
        buffer.push(snapshot(3), null)
        buffer.push(snapshot(4), null)
        assertEquals(3, buffer.size)
        // 1 is gone; the oldest left is 2.
        assertEquals(4, buffer.pop()?.state?.get(0)?.toInt())
        assertEquals(3, buffer.pop()?.state?.get(0)?.toInt())
        assertEquals(2, buffer.pop()?.state?.get(0)?.toInt())
        assertNull(buffer.pop())
    }

    @Test
    fun clearEmptiesTheRing() {
        val buffer = RewindBuffer()
        buffer.push(snapshot(1), null)
        buffer.push(snapshot(2), null)
        buffer.clear()
        assertTrue(buffer.isEmpty)
        assertNull(buffer.pop())
    }

    @Test
    fun defaultCapacityIsThirtySnapshots() {
        assertEquals(30, RewindBuffer.MAX_SNAPSHOTS)
        assertEquals(60, RewindBuffer.CAPTURE_EVERY_FRAMES)
    }
}
