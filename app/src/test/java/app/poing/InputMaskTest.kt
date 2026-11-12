package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the libretro joypad mask (see InputMask). */
class InputMaskTest {

    @Test
    fun emptyByDefault() {
        assertEquals(0, InputMask.build())
    }

    @Test
    fun eachButtonSetsItsOwnBit() {
        assertEquals(1 shl InputMask.JOY_A, InputMask.build(a = true))
        assertEquals(1 shl InputMask.JOY_B, InputMask.build(b = true))
        assertEquals(1 shl InputMask.JOY_START, InputMask.build(start = true))
        assertEquals(1 shl InputMask.JOY_SELECT, InputMask.build(select = true))
        assertEquals(1 shl InputMask.JOY_L, InputMask.build(l = true))
        assertEquals(1 shl InputMask.JOY_R, InputMask.build(r = true))
        assertEquals(1 shl InputMask.JOY_UP, InputMask.build(up = true))
        assertEquals(1 shl InputMask.JOY_DOWN, InputMask.build(down = true))
        assertEquals(1 shl InputMask.JOY_LEFT, InputMask.build(left = true))
        assertEquals(1 shl InputMask.JOY_RIGHT, InputMask.build(right = true))
    }

    @Test
    fun buttonsCombine() {
        val mask = InputMask.build(up = true, a = true, start = true)
        assertTrue(mask and (1 shl InputMask.JOY_UP) != 0)
        assertTrue(mask and (1 shl InputMask.JOY_A) != 0)
        assertTrue(mask and (1 shl InputMask.JOY_START) != 0)
        assertTrue(mask and (1 shl InputMask.JOY_B) == 0)
    }

    @Test
    fun joypadIdsMatchLibretro() {
        // libretro.h: B=0, Y=1, SELECT=2, START=3, UP=4, DOWN=5,
        // LEFT=6, RIGHT=7, A=8, X=9, L=10, R=11.
        assertEquals(0, InputMask.JOY_B)
        assertEquals(2, InputMask.JOY_SELECT)
        assertEquals(3, InputMask.JOY_START)
        assertEquals(4, InputMask.JOY_UP)
        assertEquals(8, InputMask.JOY_A)
        assertEquals(10, InputMask.JOY_L)
        assertEquals(11, InputMask.JOY_R)
    }
}
