package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overlay rework (Robert, 2026-11-13) — ControlLayout: every
 * control's centre as a fraction of the screen plus its size,
 * clusters that move together by default, resize, snap, and
 * opacity.
 */
class ControlLayoutTest {
    @Test
    fun `defaults reproduce the old spots as fractions`() {
        val def = ControlLayout.DEFAULT
        // Reference canvas 923x411dp (Pixel 10a landscape, bar
        // hidden): D-pad centred in the left flank, A/B in the
        // right flank, L/R at the top corners, Start/Select at
        // the bottom centre.
        assertEquals(88f / 923f, def.control(ControlId.DPAD).cx, 0.001f)
        assertEquals(0.5f, def.control(ControlId.DPAD).cy, 0.001f)
        assertEquals(875f / 923f, def.control(ControlId.A).cx, 0.001f)
        assertEquals(799f / 923f, def.control(ControlId.B).cx, 0.001f)
        assertEquals(0.5f, def.control(ControlId.A).cy, 0.001f)
        assertEquals(64f / 923f, def.control(ControlId.L).cx, 0.001f)
        assertEquals(84f / 411f, def.control(ControlId.L).cy, 0.001f)
        assertEquals(859f / 923f, def.control(ControlId.R).cx, 0.001f)
        assertEquals(423.5f / 923f, def.control(ControlId.START).cx, 0.001f)
        assertEquals(499.5f / 923f, def.control(ControlId.SELECT).cx, 0.001f)
        assertEquals(379f / 411f, def.control(ControlId.START).cy, 0.001f)
    }

    @Test
    fun `default sizes and opacity`() {
        val def = ControlLayout.DEFAULT
        assertEquals(144, def.control(ControlId.DPAD).sizeDp)
        assertEquals(64, def.control(ControlId.A).sizeDp)
        assertEquals(64, def.control(ControlId.B).sizeDp)
        assertEquals(96, def.control(ControlId.L).sizeDp)
        assertEquals(96, def.control(ControlId.R).sizeDp)
        assertEquals(64, def.control(ControlId.START).sizeDp)
        assertEquals(64, def.control(ControlId.SELECT).sizeDp)
        assertEquals(100, def.opacity)
    }

    @Test
    fun `a control can sit over the game centre`() {
        val moved = ControlLayout.DEFAULT.moved(ControlId.DPAD, 0.5f, 0.5f, grouped = false)
        assertEquals(0.5f, moved.control(ControlId.DPAD).cx, 0f)
        assertEquals(0.5f, moved.control(ControlId.DPAD).cy, 0f)
    }

    @Test
    fun `centres clamp to 0-03 and 0-97`() {
        val moved = ControlLayout.DEFAULT.moved(ControlId.A, 0f, 2f, grouped = false)
        assertEquals(0.03f, moved.control(ControlId.A).cx, 0f)
        assertEquals(0.97f, moved.control(ControlId.A).cy, 0f)
    }

    @Test
    fun `sizes clamp to 36 and 220`() {
        val resized = ControlLayout.DEFAULT.resized(ControlId.A, 999, grouped = false)
        assertEquals(220, resized.control(ControlId.A).sizeDp)
        val small = ControlLayout.DEFAULT.resized(ControlId.A, 1, grouped = false)
        assertEquals(36, small.control(ControlId.A).sizeDp)
    }

    @Test
    fun `moving A moves B by the same delta when grouped`() {
        val a = ControlLayout.DEFAULT.control(ControlId.A)
        val b = ControlLayout.DEFAULT.control(ControlId.B)
        // A sits at 0.948 already, so the delta stays small
        // enough to miss the 0.97 clamp.
        val moved = ControlLayout.DEFAULT.moved(ControlId.A, a.cx + 0.01f, a.cy - 0.02f, grouped = true)
        assertEquals(a.cx + 0.01f, moved.control(ControlId.A).cx, 0.001f)
        assertEquals(b.cx + 0.01f, moved.control(ControlId.B).cx, 0.001f)
        assertEquals(b.cy - 0.02f, moved.control(ControlId.B).cy, 0.001f)
        // The other clusters do not move.
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.DPAD),
            moved.control(ControlId.DPAD),
        )
    }

    @Test
    fun `moving A alone leaves B when separate`() {
        val moved = ControlLayout.DEFAULT.moved(ControlId.A, 0.9f, 0.4f, grouped = false)
        assertEquals(0.9f, moved.control(ControlId.A).cx, 0f)
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.B),
            moved.control(ControlId.B),
        )
    }

    @Test
    fun `moving Start moves Select when grouped, and L moves R`() {
        val moved = ControlLayout.DEFAULT
            .moved(ControlId.START, 0.4f, 0.9f, grouped = true)
            .moved(ControlId.L, 0.1f, 0.3f, grouped = true)
        assertEquals(0.4f, moved.control(ControlId.START).cx, 0.001f)
        // Select keeps its offset from Start.
        val startDx = 0.4f - ControlLayout.DEFAULT.control(ControlId.START).cx
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.SELECT).cx + startDx,
            moved.control(ControlId.SELECT).cx,
            0.001f,
        )
        assertEquals(0.1f, moved.control(ControlId.L).cx, 0.001f)
        val lDx = 0.1f - ControlLayout.DEFAULT.control(ControlId.L).cx
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.R).cx + lDx,
            moved.control(ControlId.R).cx,
            0.001f,
        )
    }

    @Test
    fun `resizing A resizes B when grouped, alone when separate`() {
        val grouped = ControlLayout.DEFAULT.resized(ControlId.A, 80, grouped = true)
        assertEquals(80, grouped.control(ControlId.A).sizeDp)
        assertEquals(80, grouped.control(ControlId.B).sizeDp)
        val separate = ControlLayout.DEFAULT.resized(ControlId.A, 80, grouped = false)
        assertEquals(80, separate.control(ControlId.A).sizeDp)
        assertEquals(64, separate.control(ControlId.B).sizeDp)
    }

    @Test
    fun `snap rounds to the 8dp grid only when on`() {
        assertEquals(104f, ControlLayout.snap(100f, true), 0f)
        assertEquals(96f, ControlLayout.snap(99f, true), 0f)
        assertEquals(100f, ControlLayout.snap(100f, false), 0f)
        assertEquals(99f, ControlLayout.snap(99f, false), 0f)
    }

    @Test
    fun `opacity clamps to 0 and 100`() {
        assertEquals(0, ControlLayout.DEFAULT.withOpacity(-5).opacity)
        assertEquals(100, ControlLayout.DEFAULT.withOpacity(105).opacity)
        assertEquals(55, ControlLayout.DEFAULT.withOpacity(55).opacity)
    }

    @Test
    fun `clusters are the four pairs`() {
        assertEquals("dpad", ControlLayout.clusterOf(ControlId.DPAD))
        assertEquals("ab", ControlLayout.clusterOf(ControlId.A))
        assertEquals("ab", ControlLayout.clusterOf(ControlId.B))
        assertEquals("lr", ControlLayout.clusterOf(ControlId.L))
        assertEquals("lr", ControlLayout.clusterOf(ControlId.R))
        assertEquals("startselect", ControlLayout.clusterOf(ControlId.START))
        assertEquals("startselect", ControlLayout.clusterOf(ControlId.SELECT))
    }

    @Test
    fun `GB and GBC have no L and R controls`() {
        val gb = ControlLayout.DEFAULT.controlsFor(isGba = false)
        assertFalse(gb.any { it.id == ControlId.L || it.id == ControlId.R })
        assertEquals(5, gb.size)
        assertEquals(7, ControlLayout.DEFAULT.controlsFor(isGba = true).size)
    }

    @Test
    fun `withControl replaces one control only`() {
        val updated = ControlLayout.DEFAULT.withControl(
            OverlayControl(ControlId.A, 0.5f, 0.5f, 80),
        )
        assertEquals(OverlayControl(ControlId.A, 0.5f, 0.5f, 80), updated.control(ControlId.A))
        assertEquals(
            ControlLayout.DEFAULT.control(ControlId.B),
            updated.control(ControlId.B),
        )
    }
}
