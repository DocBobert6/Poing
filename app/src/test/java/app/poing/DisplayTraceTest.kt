package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alpha.28 — the black-screen trace recorder. Pure (no
 * Android framework calls except the wall clock in the
 * default clock), so no Robolectric is needed — the clock
 * is injected to pin ordering and formatting.
 */
class DisplayTraceTest {

    @Test
    fun disabledByDefaultRecordsNothing() {
        val trace = DisplayTrace()
        trace.record("session start", "TETRIS (GB)")
        assertEquals(0, trace.size)
    }

    @Test
    fun enablingStartsRecordingInOrderWithTimestamps() {
        var now = 1_000L
        val trace = DisplayTrace(clock = { now })
        trace.enabled = true
        trace.record("session start", "TETRIS (GB)")
        now += 250
        trace.record("first frame", "160x144")
        val events = trace.snapshot()
        assertEquals(2, events.size)
        assertEquals("session start", events[0].label)
        assertEquals(1_000L, events[0].atMs)
        assertEquals("TETRIS (GB)", events[0].detail)
        assertEquals("first frame", events[1].label)
        assertEquals(1_250L, events[1].atMs)
    }

    @Test
    fun disablingStopsRecording() {
        val trace = DisplayTrace(clock = { 0L })
        trace.enabled = true
        trace.record("session start")
        trace.enabled = false
        trace.record("first frame")
        assertEquals(1, trace.size)
        assertEquals("session start", trace.snapshot()[0].label)
    }

    @Test
    fun snapshotNewestFirstReversesInsertionOrder() {
        val trace = DisplayTrace(clock = { 0L })
        trace.enabled = true
        trace.record("session start")
        trace.record("first frame")
        trace.record("surface created")
        val newest = trace.snapshotNewestFirst()
        assertEquals(
            listOf("surface created", "first frame", "session start"),
            newest.map { it.label },
        )
    }

    /**
     * The whole point of reading the trace from the pause
     * menu: recording a pause, a resume or a surface
     * recreation must not clear the events before them —
     * the record survives the session it diagnoses.
     */
    @Test
    fun traceSurvivesPauseResumeAndSurfaceRecreation() {
        val trace = DisplayTrace(clock = { 0L })
        trace.enabled = true
        trace.record("session start", "TETRIS (GB)")
        trace.record("first frame", "160x144")
        trace.record("paused")
        trace.record("resumed")
        trace.record("surface created", "creation #2; pending frame: no; frameConsumed: true")
        trace.record(
            "surface ready",
            "requestRender issued; replay requested; latest frame: present",
        )
        assertEquals(6, trace.size)
        val newest = trace.snapshotNewestFirst()
        assertEquals("surface ready", newest[0].label)
        assertEquals("session start", newest.last().label)
        assertEquals(
            listOf(
                "session start",
                "first frame",
                "paused",
                "resumed",
                "surface created",
                "surface ready",
            ),
            trace.snapshot().map { it.label },
        )
    }

    @Test
    fun oldestEventsDropPastTheCap() {
        val trace = DisplayTrace(clock = { 0L }, maxEvents = 3)
        trace.enabled = true
        repeat(5) { trace.record("event $it") }
        assertEquals(3, trace.size)
        assertEquals(
            listOf("event 2", "event 3", "event 4"),
            trace.snapshot().map { it.label },
        )
    }

    @Test
    fun formatDisplayTraceTimeShowsMilliseconds() {
        // Timezone-proof: only the shape is pinned, not
        // the hour the test machine happens to be in.
        val text = formatDisplayTraceTime(1_700_000_000_123L)
        assertTrue(text.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
    }
}
