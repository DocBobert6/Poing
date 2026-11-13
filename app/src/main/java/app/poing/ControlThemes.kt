package app.poing

/**
 * Per-system control styling — the look of the touch controls for
 * each of the three Game Boy systems. Appearance ONLY: all three
 * systems take the exact same input (D-pad + A/B + Start/Select,
 * plus L/R on GBA), so nothing here changes how a control works,
 * only what it looks like. The look is matched to the real
 * hardware, verified against the hardware itself (photos and
 * descriptions, 2026-11-12):
 * - GB (DMG-01, 1989): the brick's black D-pad ("the D-Pad
 *   molded from black"), maroon A/B ("dark red or maroon",
 *   Wikipedia's "magenta accents"), grey silicone Start/Select
 *   pills, light-grey shell. The D-pad well is the shell's
 *   light grey — the cross sits on the shell on the hardware,
 *   and a light well is the only way a black cross reads on
 *   the player's black background.
 * - GBC (CGB-001, 1998): dark grey D-pad and A/B ("dark grey
 *   directional pad… and dark grey A and B action buttons",
 *   Kiwi manual; "the black D-pad" on the Atomic Purple unit).
 *   The well evokes the iconic Atomic Purple translucent shell
 *   (a judgment call — the shell is not a button, but it is
 *   what a Color looks like); the buttons stay the hardware's
 *   dark grey.
 * - GBA (AGB-001, 2001, indigo): light grey buttons — the
 *   indigo unit's D-pad, A/B, Start/Select and L/R are all
 *   pale grey, verified against Nintendo's own front photo of
 *   the indigo unit (Wikipedia, Nintendo-Game-Boy-Advance-
 *   Purple-FL). The well is the shell's deep indigo; the unit
 *   is the first Game Boy with L/R shoulders.
 *
 * The FF/RW/0.5x action buttons are NOT themed — they are
 * utility controls, not hardware, and stay silver.
 *
 * Pure Kotlin, no Android — unit-testable on the plain JVM
 * (ControlThemesTest). Colours are ARGB ints, like
 * HowlingDog's OverlayTheme; the UI converts with Color(int).
 * The pressed state is one colour per system (all buttons
 * share it); its label is always white.
 */
object ControlThemes {

    /** One button's colours: its background and its label. */
    data class ButtonStyle(val background: Int, val label: Int)

    /** The whole control look for one system. Colours are ARGB ints. */
    data class Theme(
        /** The D-pad cross itself. */
        val dpadCross: Int,
        /** The well the cross sits in. */
        val dpadWell: Int,
        val buttonA: ButtonStyle,
        val buttonB: ButtonStyle,
        val startSelect: ButtonStyle,
        /** L/R shoulder tabs (GBA only — defined for every system so the value is sane if ever rendered). */
        val shoulder: ButtonStyle,
        /** Pressed background, shared by every button; label is always white. */
        val pressed: Int,
    )

    private val WHITE: Int = 0xFFFFFFFF.toInt()
    private val BLACK: Int = 0xFF000000.toInt()

    /** DMG-01 (1989): black cross on the light shell, maroon A/B, grey pills. */
    private val DMG_THEME = Theme(
        dpadCross = 0xFF1A1A1A.toInt(),
        dpadWell = 0xFFA8A8AD.toInt(),
        buttonA = ButtonStyle(background = 0xFF8E2438.toInt(), label = WHITE),
        buttonB = ButtonStyle(background = 0xFF8E2438.toInt(), label = WHITE),
        startSelect = ButtonStyle(background = 0xFF8A8A90.toInt(), label = BLACK),
        shoulder = ButtonStyle(background = 0xFF8A8A90.toInt(), label = BLACK),
        pressed = 0xFFB03040.toInt(),
    )

    /** CGB-001 (1998): dark grey buttons; the well evokes the Atomic Purple shell. */
    private val COLOR_THEME = Theme(
        dpadCross = 0xFF2E2E33.toInt(),
        dpadWell = 0xFF472C6E.toInt(),
        buttonA = ButtonStyle(background = 0xFF3A3A40.toInt(), label = WHITE),
        buttonB = ButtonStyle(background = 0xFF3A3A40.toInt(), label = WHITE),
        startSelect = ButtonStyle(background = 0xFF3A3A40.toInt(), label = WHITE),
        shoulder = ButtonStyle(background = 0xFF3A3A40.toInt(), label = WHITE),
        pressed = 0xFF7E57C2.toInt(),
    )

    /** AGB-001 (2001, indigo): pale grey buttons on a deep indigo well. */
    private val ADVANCE_THEME = Theme(
        dpadCross = 0xFFD5D5DA.toInt(),
        dpadWell = 0xFF3A3A6E.toInt(),
        buttonA = ButtonStyle(background = 0xFFD5D5DA.toInt(), label = BLACK),
        buttonB = ButtonStyle(background = 0xFFD5D5DA.toInt(), label = BLACK),
        startSelect = ButtonStyle(background = 0xFFD5D5DA.toInt(), label = BLACK),
        shoulder = ButtonStyle(background = 0xFFD5D5DA.toInt(), label = BLACK),
        pressed = 0xFF5B5BA6.toInt(),
    )

    /** The control look for a system, by the header-detected [GameSystem]. */
    fun themeFor(system: GameSystem): Theme = when (system) {
        GameSystem.GB -> DMG_THEME
        GameSystem.GBC -> COLOR_THEME
        GameSystem.GBA -> ADVANCE_THEME
    }
}
