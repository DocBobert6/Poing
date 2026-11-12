package app.poing

/**
 * Phase 6 (item 9) — the rewind ring buffer: the last N serialized
 * states, oldest first, newest last. The PlayerSession captures
 * one snapshot a second while the game runs (on the emu thread,
 * between frames — no retro_run in flight there, so the serialize
 * is safe); holding the rewind control pops snapshots newest
 * first, so the game steps back through the time the ring covers.
 *
 * Pure Kotlin — no Android APIs — so the whole buffer runs under
 * plain JVM unit tests. The snapshots are plain byte arrays; the
 * buffer never touches the bridge itself (the session serializes
 * and unserializes).
 */
class RewindBuffer(private val capacity: Int = MAX_SNAPSHOTS) {
    companion object {
        /** 30 snapshots at one a second: 30 seconds of rewind. */
        const val MAX_SNAPSHOTS = 30

        /** One snapshot a second: the emu loop captures every 60th frame. */
        const val CAPTURE_EVERY_FRAMES = 60
    }

    /** One snapshot: the serialized core state, plus the RA progress that belongs to it (null when RA is not tracking the game). */
    data class Snapshot(val state: ByteArray, val raProgress: ByteArray?)

    private val snapshots = ArrayDeque<Snapshot>()

    val size: Int get() = snapshots.size
    val isEmpty: Boolean get() = snapshots.isEmpty()

    /** Push a snapshot; the oldest goes when the ring is full. */
    fun push(state: ByteArray, raProgress: ByteArray?) {
        snapshots.addLast(Snapshot(state, raProgress))
        while (snapshots.size > capacity) snapshots.removeFirst()
    }

    /** The newest snapshot without removing it, or null when the ring is empty. */
    fun peek(): Snapshot? = snapshots.lastOrNull()

    /** Remove and return the newest snapshot, or null when the ring is empty. */
    fun pop(): Snapshot? = if (snapshots.isEmpty()) null else snapshots.removeLast()

    /** Empty the ring: a new timeline starts (a state load, a new game). */
    fun clear() {
        snapshots.clear()
    }
}
