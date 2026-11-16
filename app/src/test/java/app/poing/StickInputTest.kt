package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha.10 — pure JVM tests for the joystick's direction math (see StickInput). */
class StickInputTest {

    private val up = 1 shl InputMask.JOY_UP
    private val down = 1 shl InputMask.JOY_DOWN
    private val left = 1 shl InputMask.JOY_LEFT
    private val right = 1 shl InputMask.JOY_RIGHT

    @Test
    fun centreReportsNothing() {
        assertEquals(0, StickInput.direction(0f, 0f))
    }

    @Test
    fun deadZoneSwallowsSmallPushes() {
        // Inside 0.22 of full travel, a thumb resting near the
        // centre does nothing — in any direction.
        assertEquals(0, StickInput.direction(0.1f, 0f))
        assertEquals(0, StickInput.direction(0f, -0.21f))
        assertEquals(0, StickInput.direction(0.15f, 0.15f))
    }

    @Test
    fun justPastTheDeadZoneReports() {
        assertEquals(right, StickInput.direction(0.23f, 0f))
    }

    @Test
    fun cardinalsReportOneDirection() {
        assertEquals(up, StickInput.direction(0f, -1f))
        assertEquals(down, StickInput.direction(0f, 1f))
        assertEquals(left, StickInput.direction(-1f, 0f))
        assertEquals(right, StickInput.direction(1f, 0f))
    }

    @Test
    fun diagonalsReportBothDirections() {
        // Full diagonal: both axes fully engaged.
        val upRight = StickInput.direction(1f, -1f)
        assertTrue(upRight and up != 0)
        assertTrue(upRight and right != 0)
        val downLeft = StickInput.direction(-1f, 1f)
        assertTrue(downLeft and down != 0)
        assertTrue(downLeft and left != 0)
        // A shallow diagonal still counts: the smaller axis is
        // 0.75 of the larger, well past the 0.35 ratio.
        val shallow = StickInput.direction(0.4f, -0.3f)
        assertTrue(shallow and up != 0)
        assertTrue(shallow and right != 0)
    }

    @Test
    fun shallowPushesStayCardinal() {
        // The smaller axis is only 0.2 of the larger — under
        // the 0.35 ratio — so the dominant axis alone reports.
        assertEquals(right, StickInput.direction(1f, -0.2f))
        assertEquals(up, StickInput.direction(0.2f, -1f))
    }

    @Test
    fun diagonalBandEdgeIsInclusive() {
        // The smaller axis exactly 0.35 of the larger: a
        // diagonal, not a cardinal.
        val edge = StickInput.direction(1f, -0.35f)
        assertTrue(edge and up != 0)
        assertTrue(edge and right != 0)
    }

    @Test
    fun clampKeepsTheKnobInsideFullTravel() {
        assertEquals(1f to 0f, StickInput.clamp(2f, 0f))
        assertEquals(0.5f to 0.5f, StickInput.clamp(0.5f, 0.5f))
        val (x, y) = StickInput.clamp(1f, 1f)
        assertEquals(0.7071f, x, 0.001f)
        assertEquals(0.7071f, y, 0.001f)
    }

    @Test
    fun actuationFiresOnlyOnCrossingAThreshold() {
        // Neutral -> direction, and direction -> a different
        // direction, both click.
        assertTrue(ActuationDetector.isActuation(0, right))
        assertTrue(ActuationDetector.isActuation(right, up))
        assertTrue(ActuationDetector.isActuation(right, up or right))
        // The same direction again, a release back to neutral,
        // and no movement at all stay silent.
        assertFalse(ActuationDetector.isActuation(right, right))
        assertFalse(ActuationDetector.isActuation(right, 0))
        assertFalse(ActuationDetector.isActuation(0, 0))
    }

    @Test
    fun actuationIgnoresButtonBits() {
        // A button press while holding a direction never
        // re-clicks the stick; a direction change under a
        // held button still does.
        val a = 1 shl InputMask.JOY_A
        assertFalse(ActuationDetector.isActuation(right, right or a))
        assertTrue(ActuationDetector.isActuation(right or a, up))
    }
}
