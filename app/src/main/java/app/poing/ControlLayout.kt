package app.poing

/**
 * Phase 6 (item 4) — per-game saved control positions. Each of
 * the four control groups (D-pad, Start/Select, A/B, L/R) has an
 * offset from its default spot, in dp, saved per game and restored
 * the next time that game starts. A game with no saved layout
 * plays at the defaults.
 *
 * Pure Kotlin, no Android APIs, so the model is unit-tested on
 * the JVM (ControlLayoutTest).
 */
enum class ControlGroup { DPAD, START_SELECT, AB, LR }

/** One group's offset from its default spot, in dp. Positive x moves right, positive y moves down. */
data class ControlPosition(val xDp: Float, val yDp: Float) {
    fun movedBy(dxDp: Float, dyDp: Float): ControlPosition =
        copy(xDp = xDp + dxDp, yDp = yDp + dyDp).clamped()

    /**
     * Keep the group on screen. Sideways a generous 160dp either
     * way; down only 12dp (the Start/Select pills sit 16dp above
     * the bottom of the player area — further down and they leave
     * the screen); up 140dp (about the height of a flank — further
     * and a group would sit over the game view instead of beside
     * it). The defaults are the overlay spots (D-pad left flank,
     * A/B right flank, L/R top corners, Start/Select bottom
     * centre), so a saved offset is a delta from the overlay
     * default, not from the old stacked rows.
     *
     * Honest limit: the clamp is one shared box for all four
     * groups, so it cannot be exact for every spot — a saved
     * extreme offset can still push a corner group (an L/R pill
     * dragged hard outward, the D-pad dragged hard up) partly
     * off-screen. A group is never lost: dragging it back always
     * works, and Reset in the layout editor returns every group
     * to its default instantly.
     */
    fun clamped(): ControlPosition = copy(
        xDp = xDp.coerceIn(-160f, 160f),
        yDp = yDp.coerceIn(-140f, 12f),
    )

    companion object {
        val ZERO = ControlPosition(0f, 0f)
    }
}

data class ControlLayout(
    val dpad: ControlPosition = ControlPosition.ZERO,
    val startSelect: ControlPosition = ControlPosition.ZERO,
    val ab: ControlPosition = ControlPosition.ZERO,
    val lr: ControlPosition = ControlPosition.ZERO,
) {
    fun with(group: ControlGroup, position: ControlPosition): ControlLayout = when (group) {
        ControlGroup.DPAD -> copy(dpad = position)
        ControlGroup.START_SELECT -> copy(startSelect = position)
        ControlGroup.AB -> copy(ab = position)
        ControlGroup.LR -> copy(lr = position)
    }

    fun position(group: ControlGroup): ControlPosition = when (group) {
        ControlGroup.DPAD -> dpad
        ControlGroup.START_SELECT -> startSelect
        ControlGroup.AB -> ab
        ControlGroup.LR -> lr
    }

    companion object {
        val DEFAULT = ControlLayout()
    }
}
