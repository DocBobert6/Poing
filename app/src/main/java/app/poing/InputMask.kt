package app.poing

/**
 * Player-1 joypad bitmask for the libretro core (see libretro.h:
 * RETRO_DEVICE_ID_JOYPAD_*). The core reads the mask through
 * RETRO_DEVICE_ID_JOYPAD_MASK; bit i is (1 << id).
 *
 * mGBA's input descriptors (src/platform/libretro/libretro.c) map
 * the handheld buttons onto joypad buttons: A -> joypad A, B ->
 * joypad B, Start -> Start, Select -> Select, L -> L, R -> R.
 * (X/Y are Turbo A/B, R2/L2 Turbo R/L — wired in Phase 4 for
 * standard gamepads; R3/L3 the solar sensor — not wired.)
 *
 * Pure Kotlin, no Android dependencies, so it is unit-tested on the
 * JVM (InputMaskTest).
 */
object InputMask {
    // libretro joypad button ids (libretro.h)
    const val JOY_B = 0
    const val JOY_Y = 1
    const val JOY_SELECT = 2
    const val JOY_START = 3
    const val JOY_UP = 4
    const val JOY_DOWN = 5
    const val JOY_LEFT = 6
    const val JOY_RIGHT = 7
    const val JOY_A = 8
    const val JOY_X = 9
    const val JOY_L = 10
    const val JOY_R = 11
    const val JOY_L2 = 14
    const val JOY_R2 = 15

    /** Build the player-1 mask from the currently held inputs. */
    fun build(
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false,
        a: Boolean = false,
        b: Boolean = false,
        start: Boolean = false,
        select: Boolean = false,
        l: Boolean = false,
        r: Boolean = false,
        l2: Boolean = false,
        r2: Boolean = false,
    ): Int {
        var mask = 0
        if (up) mask = mask or (1 shl JOY_UP)
        if (down) mask = mask or (1 shl JOY_DOWN)
        if (left) mask = mask or (1 shl JOY_LEFT)
        if (right) mask = mask or (1 shl JOY_RIGHT)
        if (a) mask = mask or (1 shl JOY_A)
        if (b) mask = mask or (1 shl JOY_B)
        if (start) mask = mask or (1 shl JOY_START)
        if (select) mask = mask or (1 shl JOY_SELECT)
        if (l) mask = mask or (1 shl JOY_L)
        if (r) mask = mask or (1 shl JOY_R)
        if (l2) mask = mask or (1 shl JOY_L2)
        if (r2) mask = mask or (1 shl JOY_R2)
        return mask
    }
}
