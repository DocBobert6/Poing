package app.poing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 6 (item 4) — ControlLayout: the per-group offsets and
 * their clamp.
 */
class ControlLayoutTest {
    @Test
    fun `default layout is all zeros`() {
        assertEquals(ControlLayout.DEFAULT, ControlLayout())
        assertEquals(ControlPosition.ZERO, ControlLayout.DEFAULT.dpad)
    }

    @Test
    fun `offsets inside the band are unchanged`() {
        assertEquals(
            ControlPosition(100f, -100f),
            ControlPosition(100f, -100f).clamped(),
        )
    }

    @Test
    fun `sideways clamp is 160dp`() {
        assertEquals(160f, ControlPosition(999f, 0f).clamped().xDp, 0f)
        assertEquals(-160f, ControlPosition(-999f, 0f).clamped().xDp, 0f)
    }

    @Test
    fun `vertical clamp is 140dp up and 12dp down`() {
        assertEquals(-140f, ControlPosition(0f, -999f).clamped().yDp, 0f)
        assertEquals(12f, ControlPosition(0f, 999f).clamped().yDp, 0f)
    }

    @Test
    fun `movedBy accumulates and clamps`() {
        assertEquals(
            ControlPosition(160f, -140f),
            ControlPosition.ZERO.movedBy(500f, -500f),
        )
    }

    @Test
    fun `with and position round-trip per group`() {
        val layout = ControlLayout.DEFAULT
            .with(ControlGroup.DPAD, ControlPosition(10f, -20f))
            .with(ControlGroup.AB, ControlPosition(-30f, 5f))
        assertEquals(ControlPosition(10f, -20f), layout.position(ControlGroup.DPAD))
        assertEquals(ControlPosition(-30f, 5f), layout.position(ControlGroup.AB))
        assertEquals(ControlPosition.ZERO, layout.position(ControlGroup.LR))
    }
}
