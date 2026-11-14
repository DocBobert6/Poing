package app.poing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Edge smoothing, item 5 (2026-11-14) — the dev-stats
 * readout: the diagnostic line's token for the program
 * that actually drew the last frame. The fallback case
 * is the point: edge requested but plain delivered must
 * read as "plain (edge fell back)", never as a quiet
 * "plain" — so on/off is never a guess on device again.
 *
 * Plain JVM test (no Robolectric): pure Kotlin, the same
 * as EdgeSmoothingTest.
 */
class DrawPathTest {

    @Test
    fun edgeDrawsReadAsEdge() {
        assertEquals("edge", DrawPath.EDGE.displayLabel(edgeOn = true))
        assertEquals("edge", DrawPath.EDGE.displayLabel(edgeOn = false))
    }

    @Test
    fun combinedModeReadsAsEdgePlusGrid() {
        assertEquals("edge+grid", DrawPath.EDGE_GRID.displayLabel(edgeOn = true))
    }

    @Test
    fun chainDrawsReadAsChain() {
        assertEquals("chain", DrawPath.CHAIN.displayLabel(edgeOn = false))
        assertEquals("chain", DrawPath.CHAIN.displayLabel(edgeOn = true))
    }

    @Test
    fun plainDrawsReadAsPlainWhenNothingWasRequested() {
        assertEquals("plain", DrawPath.PLAIN.displayLabel(edgeOn = false))
    }

    @Test
    fun plainDrawsNameTheFallbackWhenEdgeWasRequested() {
        assertEquals("plain (edge fell back)", DrawPath.PLAIN.displayLabel(edgeOn = true))
    }
}
