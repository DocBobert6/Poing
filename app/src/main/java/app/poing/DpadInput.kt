package app.poing

/**
 * The 8-way D-pad's direction math (pure Kotlin, no Android
 * dependencies, unit-tested on the plain JVM).
 *
 * A press in a corner of the pad reports both directions (UP+RIGHT),
 * a press on an edge reports the one direction, and a press near
 * the centre (inside the dead zone) reports nothing. The diagonal
 * band is 0.35: beyond that on both axes, the press counts as a
 * diagonal.
 *
 * Coordinates: (x, y) is the press position inside the pad's square
 * touch area, in px; [size] is that square's side, also in px. +x
 * is right, +y is down (Android touch convention). The direction is
 * returned as a direction mask using the InputMask joypad bits.
 */
object DpadInput {

    /** Inside this normalised radius from the centre, no direction. */
    const val DEAD_ZONE = 0.22f

    /** Beyond this normalised offset on an axis, that axis counts. */
    const val AXIS_THRESHOLD = 0.35f

    /** Direction bits from a press at (x, y) inside a pad of [size] px. */
    fun directionFromPress(x: Float, y: Float, size: Float): Int {
        if (size <= 0f) return 0
        val half = size / 2f
        if (half <= 0f) return 0
        val dx = ((x - half) / half).coerceIn(-1f, 1f)
        val dy = ((y - half) / half).coerceIn(-1f, 1f)
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return 0
        var mask = 0
        if (dx > AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_RIGHT)
        if (dx < -AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_LEFT)
        if (dy > AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_DOWN)
        if (dy < -AXIS_THRESHOLD) mask = mask or (1 shl InputMask.JOY_UP)
        return mask
    }
}
