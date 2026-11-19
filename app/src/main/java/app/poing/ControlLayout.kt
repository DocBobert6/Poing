package app.poing

/**
 * Overlay rework (Robert, 2026-11-13): Mariner-parity control
 * editor. Every control is one spec — its centre as a FRACTION
 * of the screen (so a layout survives rotation and screen
 * sizes, Mariner's ControlSpec model) plus its size in dp —
 * and the controls can sit anywhere on screen, including over
 * the game. Clusters (the D-pad cross, the A/B pair, the L/R
 * pair, the Start/Select pair) move together by default, with
 * a grouped/separate switch in the editor; A and B stay a pair
 * by default, Robert's word, with the toggle to move them
 * separately.
 *
 * The fractions are of the FULL screen, not the player area:
 * the player area grows and shrinks as the top bar hides and
 * shows, so fractions of it would make the buttons jump every
 * time the bar toggles; fractions of the full screen never
 * move. The editor canvas is the full screen too, so what is
 * edited is what is played.
 *
 * Pure Kotlin, no Android APIs, so the model is unit-tested on
 * the JVM (ControlLayoutTest).
 */
enum class ControlId(val key: String) {
    DPAD("dpad"),
    A("a"),
    B("b"),
    L("l"),
    R("r"),
    START("start"),
    SELECT("select"),
    // Alpha.10 — the FF / 0.5x / RW buttons (Part D):
    // they live in the overlay now, with a spec each,
    // instead of a fixed Row at the bottom-right.
    FF("ff"),
    SLOW("slow"),
    RW("rw"),
    // Alpha.25 — the achievement unlock popup: a movable
    // overlay item, not a button. Its spec is the chip's
    // centre (fractions of the screen) + its width in dp;
    // the height is the chip's own (POPUP_HEIGHT_DP).
    POPUP("popup"),
    // Alpha.32 — the fps counter: a movable overlay chip,
    // not a button, following the popup pattern. Its spec
    // is the chip's centre (fractions of the screen) + its
    // width in dp; the height is the chip's own
    // (FPS_HEIGHT_DP). It shows only while dev stats are
    // on, and its chip is the system's theme colour.
    FPS("fps"),
}

/** One control's place on screen: centre fraction + size in dp. */
data class OverlayControl(
    val id: ControlId,
    val cx: Float,
    val cy: Float,
    val sizeDp: Int,
) {
    /**
     * Keep the control on screen. Mariner's clamp: a centre
     * never closer than 0.03 to any edge, so a control can sit
     * over the game centre but never slide off the screen; size
     * in Mariner's 36–220dp range. Alpha.25 — the popup is a
     * wide chip, not a square button, so its size (its width)
     * clamps to its own range instead. Alpha.32 — the fps
     * counter is a wide chip too, with its own range.
     */
    fun clamped(): OverlayControl = copy(
        cx = cx.coerceIn(ControlLayout.MIN_FRACTION, ControlLayout.MAX_FRACTION),
        cy = cy.coerceIn(ControlLayout.MIN_FRACTION, ControlLayout.MAX_FRACTION),
        sizeDp = sizeDp.coerceIn(
            when (id) {
                ControlId.POPUP -> ControlLayout.POPUP_MIN_WIDTH_DP
                ControlId.FPS -> ControlLayout.FPS_MIN_WIDTH_DP
                else -> ControlLayout.MIN_SIZE_DP
            },
            when (id) {
                ControlId.POPUP -> ControlLayout.POPUP_MAX_WIDTH_DP
                ControlId.FPS -> ControlLayout.FPS_MAX_WIDTH_DP
                else -> ControlLayout.MAX_SIZE_DP
            },
        ),
    )
}

data class ControlLayout(
    val controls: List<OverlayControl> = DEFAULT.controls,
    val opacity: Int = DEFAULT_OPACITY,
) {
    fun control(id: ControlId): OverlayControl =
        controls.firstOrNull { it.id == id }
            ?: DEFAULT.control(id)

    /** The controls that render for this system: GB/GBC have no L/R. */
    fun controlsFor(isGba: Boolean): List<OverlayControl> =
        if (isGba) controls else controls.filter { it.id != ControlId.L && it.id != ControlId.R }

    fun withControl(updated: OverlayControl): ControlLayout =
        copy(controls = controls.map { if (it.id == updated.id) updated else it })

    /**
     * Move one control to a new centre. Grouped (the default):
     * the whole cluster moves with it by the same delta — the
     * A/B pair, the L/R pair, the Start/Select pair, the D-pad
     * cross (a cluster of one). Separate: only that control
     * moves. Every moved control is clamped to the screen.
     */
    fun moved(id: ControlId, cx: Float, cy: Float, grouped: Boolean): ControlLayout {
        val old = control(id)
        val dx = cx - old.cx
        val dy = cy - old.cy
        val cluster = if (grouped) clusterOf(id) else null
        return copy(
            controls = controls.map { spec ->
                when {
                    spec.id == id -> spec.copy(cx = cx, cy = cy).clamped()
                    cluster != null && clusterOf(spec.id) == cluster ->
                        spec.copy(cx = spec.cx + dx, cy = spec.cy + dy).clamped()
                    else -> spec
                }
            },
        )
    }

    /**
     * Resize one control, 1dp at a time from the editor stepper.
     * Grouped: the whole cluster takes the new size, Mariner's
     * handleResize. Separate: only that control.
     */
    fun resized(id: ControlId, sizeDp: Int, grouped: Boolean): ControlLayout {
        val cluster = if (grouped) clusterOf(id) else null
        return copy(
            controls = controls.map { spec ->
                when {
                    spec.id == id -> spec.copy(sizeDp = sizeDp).clamped()
                    cluster != null && clusterOf(spec.id) == cluster ->
                        spec.copy(sizeDp = sizeDp).clamped()
                    else -> spec
                }
            },
        )
    }

    fun withOpacity(value: Int): ControlLayout =
        copy(opacity = value.coerceIn(MIN_OPACITY, MAX_OPACITY))

    companion object {
        const val MIN_SIZE_DP = 36
        const val MAX_SIZE_DP = 220

        /**
         * Alpha.25 — the unlock popup's width range (a wide
         * chip, not a square button) and its height: badge
         * 32dp + 6dp of padding top and bottom. The width is
         * also capped at POPUP_MAX_WIDTH_FRACTION of the game
         * width at render time (see popupWidthDp), so the chip
         * can never run edge to edge the way the old
         * full-width card did.
         */
        const val POPUP_MIN_WIDTH_DP = 120
        const val POPUP_MAX_WIDTH_DP = 480
        const val POPUP_HEIGHT_DP = 48f
        const val POPUP_MAX_WIDTH_FRACTION = 0.75f

        /**
         * Alpha.32 — the fps counter's width range (a wide
         * chip, like the popup) and its height: one line of
         * terminal text + 4dp of padding top and bottom. The
         * width is NOT capped at a fraction of the game
         * width, unlike the popup: the diagnostic text is
         * long, and the cap would cut the numbers the chip
         * exists to show. The size stepper still bounds it
         * to this range, and 480dp still fits the screen.
         */
        const val FPS_MIN_WIDTH_DP = 120
        const val FPS_MAX_WIDTH_DP = 480
        const val FPS_HEIGHT_DP = 32f

        /**
         * Alpha.25 — the popup's placed width: its spec's
         * width, capped at 75% of the game width. Pure, so
         * the cap is unit-tested (ControlLayoutTest).
         */
        fun popupWidthDp(sizeDp: Int, gameWidthDp: Float): Float =
            minOf(sizeDp.toFloat(), POPUP_MAX_WIDTH_FRACTION * gameWidthDp)

        /** Mariner's clamp: a centre never closer than 0.03 to an edge. */
        const val MIN_FRACTION = 0.03f
        const val MAX_FRACTION = 0.97f

        /** Mariner's snap grid. */
        const val SNAP_GRID_DP = 8

        const val DEFAULT_OPACITY = 100
        const val MIN_OPACITY = 0
        const val MAX_OPACITY = 100

        /**
         * The reference canvas the defaults are measured on:
         * the Pixel 10a in landscape, 923x411dp, top bar hidden.
         * The store migration uses the same canvas, so an old
         * dp-offset layout converts exactly on Robert's phone.
         */
        const val REFERENCE_WIDTH_DP = 923f
        const val REFERENCE_HEIGHT_DP = 411f

        /**
         * Which cluster a control belongs to: the D-pad cross,
         * the A/B pair, the L/R pair, the Start/Select pair,
         * and the FF / 0.5x / RW trio. Mariner's groupOf,
         * Poing's clusters.
         */
        fun clusterOf(id: ControlId): String = when (id) {
            ControlId.DPAD -> "dpad"
            ControlId.A, ControlId.B -> "ab"
            ControlId.L, ControlId.R -> "lr"
            ControlId.START, ControlId.SELECT -> "startselect"
            ControlId.FF, ControlId.SLOW, ControlId.RW -> "ffrw"
            // Alpha.25 — the popup is a cluster of one: a
            // grouped move drags it alone, never the buttons.
            ControlId.POPUP -> "popup"
            // Alpha.32 — the fps counter is a cluster of one
            // too, for the same reason.
            ControlId.FPS -> "fps"
        }

        /** Snap a centre point (in dp on the editor canvas) to the grid when snapping is on. */
        fun snap(dp: Float, on: Boolean): Float =
            if (on) {
                (dp / SNAP_GRID_DP).let { Math.round(it) * SNAP_GRID_DP.toFloat() }
            } else {
                dp
            }

        /**
         * The defaults, as fractions: the Phase 6 / layout-rework
         * spots on the reference canvas — D-pad centred in the
         * left flank (box 144dp, 16dp in), A/B centred in the
         * right flank (64dp circles, 12dp apart, 16dp in), L/R
         * shoulder pills at the top corners (96x48dp, 16dp in,
         * 60dp down — the bar-hidden spot, clear of the floating
         * power light), Start/Select pills side by side at the
         * bottom centre (64x32dp, 16dp up), and the FF / 0.5x /
         * RW trio as 48dp circles in the bottom-right corner
         * (16dp in, 16dp up, 12dp apart — the fixed Row's spot,
         * so an existing install keeps its buttons exactly
         * where they were). Alpha.25 — the unlock popup chip,
         * 320dp wide at the top centre (32dp down, the old
         * TopCenter spot with the bar hidden), so it starts
         * slim and movable. Alpha.32 — the fps counter chip,
         * 360dp wide at the top centre (100dp down, clear of
         * the ~80dp top bar and of the left-aligned RA chip
         * below it, clear of the L/R shoulder tabs at the top
         * corners and of the popup's 32dp spot), so the
         * diagnostic text fits on one line. Listed in
         * ControlId order — the store loads in that order, and
         * list equality is order-sensitive, so a save/load
         * round-trip depends on it.
         */
        val DEFAULT: ControlLayout = run {
            fun spec(id: ControlId, centreXDp: Float, centreYDp: Float, sizeDp: Int) =
                OverlayControl(
                    id = id,
                    cx = centreXDp / REFERENCE_WIDTH_DP,
                    cy = centreYDp / REFERENCE_HEIGHT_DP,
                    sizeDp = sizeDp,
                )
            ControlLayout(
                controls = listOf(
                    spec(ControlId.DPAD, 16f + 72f, 411f / 2f, 144),
                    spec(ControlId.A, 923f - 16f - 32f, 411f / 2f, 64),
                    spec(ControlId.B, 923f - 16f - 140f + 32f, 411f / 2f, 64),
                    spec(ControlId.L, 16f + 48f, 60f + 24f, 96),
                    spec(ControlId.R, 923f - 16f - 48f, 60f + 24f, 96),
                    spec(ControlId.START, (923f - 140f) / 2f + 32f, 411f - 16f - 16f, 64),
                    spec(ControlId.SELECT, (923f - 140f) / 2f + 32f + 76f, 411f - 16f - 16f, 64),
                    spec(ControlId.FF, 923f - 16f - 24f, 411f - 16f - 24f, 48),
                    spec(ControlId.SLOW, 923f - 16f - 24f - 60f, 411f - 16f - 24f, 48),
                    spec(ControlId.RW, 923f - 16f - 24f - 120f, 411f - 16f - 24f, 48),
                    spec(ControlId.POPUP, 923f / 2f, 32f, 320),
                    spec(ControlId.FPS, 923f / 2f, 100f, 360),
                ),
            )
        }
    }
}
