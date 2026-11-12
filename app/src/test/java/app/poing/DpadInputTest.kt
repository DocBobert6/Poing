package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the 8-way D-pad math (see DpadInput). */
class DpadInputTest {

    @Test
    fun centreReportsNothing() {
        assertEquals(0, DpadInput.directionFromPress(72f, 72f, 144f))
    }

    @Test
    fun edgesReportOneDirection() {
        assertEquals(1 shl InputMask.JOY_UP, DpadInput.directionFromPress(72f, 5f, 144f))
        assertEquals(1 shl InputMask.JOY_DOWN, DpadInput.directionFromPress(72f, 139f, 144f))
        assertEquals(1 shl InputMask.JOY_LEFT, DpadInput.directionFromPress(5f, 72f, 144f))
        assertEquals(1 shl InputMask.JOY_RIGHT, DpadInput.directionFromPress(139f, 72f, 144f))
    }

    @Test
    fun cornersReportBothDirections() {
        val upRight = DpadInput.directionFromPress(139f, 5f, 144f)
        assertTrue(upRight and (1 shl InputMask.JOY_UP) != 0)
        assertTrue(upRight and (1 shl InputMask.JOY_RIGHT) != 0)
        val downLeft = DpadInput.directionFromPress(5f, 139f, 144f)
        assertTrue(downLeft and (1 shl InputMask.JOY_DOWN) != 0)
        assertTrue(downLeft and (1 shl InputMask.JOY_LEFT) != 0)
    }

    @Test
    fun invalidSizeReportsNothing() {
        assertEquals(0, DpadInput.directionFromPress(72f, 72f, 0f))
        assertEquals(0, DpadInput.directionFromPress(72f, 72f, -10f))
    }
}
