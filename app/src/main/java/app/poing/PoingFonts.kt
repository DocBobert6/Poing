package app.poing

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Phase 5 — the approved font system (Robert, 2026-11-12).
 *
 * One pixel display face only, so the look stays intentional:
 *
 * - [DotGothic16] — all display type: the "Poing!" wordmark,
 *   section headers, system badges, boot splash, settings section
 *   titles. Single weight 400, so hierarchy comes from size and
 *   colour, never weight. Sizes are the approved set only
 *   ([DisplaySizeSmall]/[DisplaySizeMedium]/[DisplaySizeLarge]) —
 *   the 16-dot grid's own steps — enforced by [displayTextStyle],
 *   never for body copy.
 * - [Dseg7] — numeric readouts only: FPS / diagnostic numbers,
 *   playtime, clock. Numbers are always segment, never
 *   DotGothic16. [Dseg14] only where a readout needs letters
 *   (a save-state time carries a month name).
 * - [Vt323] — the diagnostic overlay and terminal-style text.
 * - Body copy stays the neutral system sans — pixel faces at body
 *   size are unreadable, and the clean body is what makes the
 *   display face feel deliberate instead of gimmicky.
 *
 * Press Start 2P and Silkscreen are alternates, NOT in this system
 * (a second pixel display face next to DotGothic16 would clash).
 * All four faces are SIL OFL 1.1; the full licence text for each
 * travels with the font in assets/fonts/, and each is listed in
 * NOTICE (shown on the Licences screen). DSEG's Reserved Font Name
 * is "DSEG" — the family names are kept exactly as the authors
 * gave them; do not rename these fonts.
 *
 * Sources (verified at fetch time, 2026-11-12):
 * - DotGothic16 1.100, Fontworks, google/fonts ofl/dotgothic16,
 *   SHA-256 3ad9af88…0b8f1. Copyright 2020 The DotGothic16 Project
 *   Authors. Kept whole (2.0 MB) — the Japanese glyphs stay, so
 *   a Japanese game title still renders in the display face.
 * - VT323 2.000, Peter Hull, google/fonts ofl/vt323,
 *   SHA-256 cf4de751…8594d.
 * - DSEG v0.46 (stable), keshikan, github.com/keshikan/DSEG
 *   release asset fonts-DSEG_v046.zip; DSEG7Classic-Regular
 *   SHA-256 e3270928…afaf, DSEG14Classic-Regular SHA-256
 *   0106818d…4e6. Copyright (c) 2020, keshikan.
 */
val DotGothic16 = FontFamily(Font(R.font.dotgothic16))
val Vt323 = FontFamily(Font(R.font.vt323))
val Dseg7 = FontFamily(Font(R.font.dseg7))
val Dseg14 = FontFamily(Font(R.font.dseg14))

/** The approved DotGothic16 sizes (sp): the 16-dot grid's steps. */
const val DisplaySizeSmall = 16
const val DisplaySizeMedium = 24
const val DisplaySizeLarge = 32

/**
 * Display type in DotGothic16. [sizeSp] must be one of the
 * approved sizes — a pixel face off its grid goes soft, and the
 * sizes are the whole point of the system.
 */
fun displayTextStyle(sizeSp: Int): TextStyle {
    require(sizeSp in setOf(DisplaySizeSmall, DisplaySizeMedium, DisplaySizeLarge)) {
        "DotGothic16 is only used at 16/24/32sp (the approved grid steps), not ${sizeSp}sp"
    }
    return TextStyle(fontFamily = DotGothic16, fontSize = sizeSp.sp)
}

/** Segment digits (DSEG7) for a numeric readout. */
fun segmentTextStyle(sizeSp: Int = DisplaySizeSmall): TextStyle =
    TextStyle(fontFamily = Dseg7, fontSize = sizeSp.sp)

/** Segment text with letters (DSEG14) for a readout that needs them. */
fun segmentTextStyle14(sizeSp: Int = DisplaySizeSmall): TextStyle =
    TextStyle(fontFamily = Dseg14, fontSize = sizeSp.sp)

/** Terminal text (VT323) for the diagnostic overlay and debug lines. */
fun terminalTextStyle(sizeSp: Int = DisplaySizeSmall): TextStyle =
    TextStyle(fontFamily = Vt323, fontSize = sizeSp.sp)
