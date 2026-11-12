package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the held-input mask (see HeldInput). */
class HeldInputTest {

    @Test
    fun setBitSetsAndClearsOneBit() {
        val held = HeldInput()
        assertEquals(0, held.mask)
        held.setBit(InputMask.JOY_A, true)
        assertEquals(1 shl InputMask.JOY_A, held.mask)
        held.setBit(InputMask.JOY_B, true)
        assertTrue(held.mask and (1 shl InputMask.JOY_A) != 0)
        assertTrue(held.mask and (1 shl InputMask.JOY_B) != 0)
        held.setBit(InputMask.JOY_A, false)
        assertEquals(1 shl InputMask.JOY_B, held.mask)
    }

    @Test
    fun setDirectionsReplacesOnlyDirections() {
        val held = HeldInput()
        held.setBit(InputMask.JOY_A, true)
        held.setDirections((1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_RIGHT))
        assertTrue(held.mask and (1 shl InputMask.JOY_A) != 0)
        assertTrue(held.mask and (1 shl InputMask.JOY_UP) != 0)
        assertTrue(held.mask and (1 shl InputMask.JOY_RIGHT) != 0)
        assertTrue(held.mask and (1 shl InputMask.JOY_DOWN) == 0)
        // A new direction set drops the old directions, keeps A.
        held.setDirections(1 shl InputMask.JOY_DOWN)
        assertTrue(held.mask and (1 shl InputMask.JOY_A) != 0)
        assertTrue(held.mask and (1 shl InputMask.JOY_UP) == 0)
        assertEquals(1 shl InputMask.JOY_DOWN, held.mask and
            ((1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_DOWN) or
                (1 shl InputMask.JOY_LEFT) or (1 shl InputMask.JOY_RIGHT)))
    }

    @Test
    fun clearDropsEverything() {
        val held = HeldInput()
        held.setBit(InputMask.JOY_A, true)
        held.setDirections((1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_LEFT))
        assertTrue(held.mask != 0)
        assertEquals(0, held.clear())
        assertEquals(0, held.mask)
    }
}
