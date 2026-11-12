package app.poing

/**
 * The currently held inputs, as one mutable mask. Held in
 * `remember` (not Compose state — the mask itself never needs a
 * recomposition) and shared by the touch controls and the session.
 * The pointer-input callbacks read the mask at call time, so they
 * never go stale.
 *
 * Phase 5 — moved verbatim out of GameScreen.kt into its own file
 * when the player skin split the controls out (the old GameScreen
 * tail held this class alongside HoldButton/DpadControl, and the
 * split dropped it by accident; commit 4's CI caught it).
 */
class HeldInput {
    @Volatile
    var mask: Int = 0
        private set

    fun setBit(bit: Int, held: Boolean): Int {
        mask = if (held) mask or (1 shl bit) else mask and (1 shl bit).inv()
        return mask
    }

    fun setDirections(dirMask: Int): Int {
        val dirBits = (1 shl InputMask.JOY_UP) or (1 shl InputMask.JOY_DOWN) or
            (1 shl InputMask.JOY_LEFT) or (1 shl InputMask.JOY_RIGHT)
        mask = (mask and dirBits.inv()) or dirMask
        return mask
    }

    /** Clear every held input (pause / quit). Returns the new mask (0). */
    fun clear(): Int {
        mask = 0
        return mask
    }
}
