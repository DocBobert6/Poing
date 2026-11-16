package app.poing

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Alpha.28 — the black-screen trace (Robert, 2026-11-16):
 * the same shape as the alpha.3 fetch trace (removed in
 * alpha.9 part B), for the display path instead of covers.
 * Alpha.20 fixed one black-screen cause (a surface
 * recreation after the pending frame was consumed replayed
 * nothing); Robert reported on 2026-11-16 that the screen
 * is still black before a button press sometimes, and
 * proposed a trace on/off option again. This is the
 * recorder: a timestamped list of display-path events —
 * session start, first frame, surface created / changed,
 * surface ready (with the dirty-check state at that
 * moment), first draw after a surface creation, pause /
 * resume, orientation — newest first when read, surviving
 * the game session so Robert can screenshot it from the
 * pause menu.
 *
 * RECORDING ONLY: nothing here changes the display path.
 * The renderer and the screen call [record] at the points
 * they already pass through; when [enabled] is false the
 * calls are no-ops. The record is bounded (oldest drops
 * past [DEFAULT_MAX_EVENTS]) so a long session cannot
 * grow it without limit, and it is synchronized: the
 * renderer records on the GL thread, the screen on the
 * main thread.
 *
 * Pure (no Android framework calls except the wall clock
 * in the default [clock]), so it is unit-testable without
 * Robolectric. The clock is injectable so tests can pin
 * ordering and formatting.
 */
data class DisplayTraceEvent(
    val atMs: Long,
    val label: String,
    val detail: String = "",
)

class DisplayTrace(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    @Volatile
    var enabled: Boolean = false

    private val lock = Any()
    private val events = ArrayDeque<DisplayTraceEvent>()

    fun record(label: String, detail: String = "") {
        if (!enabled) return
        val event = DisplayTraceEvent(clock(), label, detail)
        synchronized(lock) {
            if (events.size >= maxEvents) events.removeFirst()
            events.addLast(event)
        }
    }

    /** Oldest first — the order the events happened in. */
    fun snapshot(): List<DisplayTraceEvent> = synchronized(lock) { events.toList() }

    /** Newest first — the order the pause-menu viewer shows. */
    fun snapshotNewestFirst(): List<DisplayTraceEvent> = synchronized(lock) { events.reversed() }

    val size: Int get() = synchronized(lock) { events.size }

    companion object {
        const val DEFAULT_MAX_EVENTS = 200
    }
}

/**
 * "HH:mm:ss.SSS" for a trace timestamp — millisecond
 * precision, so the surface-created → first-draw ordering
 * is visible inside one second. Plain text, no DSEG7:
 * the viewer shows it as body copy.
 */
fun formatDisplayTraceTime(atMs: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(java.util.Date(atMs))
