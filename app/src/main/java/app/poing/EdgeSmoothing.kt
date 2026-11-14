package app.poing

/**
 * Edge smoothing, REWORKED 2026-11-14 (Robert's order):
 * HowlingDog's dither-aware GLSL edge shader, rebuilt
 * per-system for these handhelds — not a copy of it, and
 * not a copy of the Phase 9 version either. The goal is
 * the best edge smoothing of any Game Boy emulator, proven
 * by a side-by-side against xBR / ScaleFX on real frames
 * before shipping — and if the side-by-side on Robert's
 * device does not show a win, this file does not claim
 * one. The ship gate is his device comparison, not this
 * code.
 *
 * The HowlingDog goals, confirmed by Robert for these
 * handhelds: dither-aware as the differentiator;
 * directional interpolation (blend along the edge, not
 * across); corner preservation; temporal stability by a
 * pure function of the frame (no history, no wobble);
 * colour-aware blending (no muddy intermediates, the
 * palette stays clean); clean sharp pixel art, not vector
 * art; off by default; an alternative to Jade / Prism /
 * Dusk, never stacked — edge on means the display chain
 * is off ([resolve]). One curated combined mode: edge
 * smoothing + light grid only. No Vulkan, no ES 3.0:
 * GLSL in the existing ES 2.0 renderer. No AI / ML.
 *
 * What the rework changed from Phase 9 (each verified in
 * the numeric replica, ~/workspace/poing/verify/
 * edge-rework/sim2.py, on real frames reconstructed from
 * Robert's screenshots):
 * 1. Dither BLENDS FLAT. Phase 9 preserved patterned
 *    texels (the blend weight was 0 there), so the GB
 *    dither stayed as coarse noise — the map ground in
 *    Super Mario Land 2 read as static, not shading.
 *    Now a patterned texel blends to the average of its
 *    shades (checkerboard / stripes: the two shades;
 *    Bayer 2x2: the 2x2 block; Bayer 4x4 proxy: the 3x3
 *    mean) — the real-LCD look, where the hardware
 *    blurred 1-texel dither into smooth grey. The
 *    pattern gate APPLIES the blend in pattern regions,
 *    gated by the 1px and text bypasses (a real dither
 *    texel is never isolated and never text, so the gate
 *    does not block real dither).
 * 2. NO palette snap for GB — the Phase 9 binary snap
 *    (`mix(c0, snapped, step(0.001, w))`) turned a
 *    smooth ramp into a hard one-shade band: a 224->0
 *    blend at w=0.5 gives 112, which snaps to 160. On
 *    4 shades any grey is harmonious; there is nothing
 *    muddy to protect. GBC / GBA keep the 15-bit snap
 *    (a fine grid, prevents out-of-gamut values).
 * 3. NO motion-adaptive — deleted, not floored. It used
 *    frame history (a 20x18 luma grid differenced
 *    against the last frame), which breaks the
 *    pure-function temporal-stability goal, and it eased
 *    smoothing to zero while scrolling — V-Rally driving
 *    got nothing. HowlingDog proved scrolling needs
 *    none of it: smoothing identical on every frame is
 *    no wobble by construction.
 * 4. NO blend-cap taper — deleted. The sim measured it:
 *    the synthetic diagonal's transition width is 7px
 *    tapered or not, and the real edges gain only 1-3%
 *    more changed pixels untapered. The cap is not the
 *    binding constraint at phone scale; the geometry is.
 * 5. Per-system text bypass thresholds, set by the sim
 *    from the text/diagonal axis-ratio margins: GB 0.15
 *    (chunky 8x8; the tails overlap, the medians
 *    separate cleanly), GBC 0.08, GBA 0.05 (the hardest
 *    balance — thin outlines and shallow diagonals must
 *    blend, small HUD text must stay crisp). Small text
 *    keeps 84-93% of its ink/paper contrast (measured
 *    on the V-Rally HUD and the Golden Sun menu), so it
 *    stays legible; it softens slightly, it is not
 *    erased.
 * 6. The checker rule is strict (the diagonal must
 *    actually match the centre) — the loose
 *    `dDiag*3.0` rule false-fired on Bayer 2x2 and
 *    blended it to the wrong level. The Bayer 4x4 proxy
 *    now requires opposite sides to differ from each
 *    other (a real 4x4 has different levels on E vs W
 *    and N vs S); without it, it erased isolated 1px
 *    dots (all four neighbours are the background
 *    level). All five detectors are validated against
 *    real diagonal edges in the sim: 0 false-fires on
 *    the TD6 car, the V-Rally drive and the SML2 title.
 * 7. Grid-aligned smoothing — the edge pass runs at the
 *    DISPLAY size into the offscreen framebuffer (pass
 *    1), so each screen pixel lands at a real sub-texel
 *    position: the blend weight grows toward the texel
 *    boundary and the centre of a texel stays its own
 *    colour. At the frame's native size every fragment
 *    sat at a texel centre and the weight was exactly 0
 *    everywhere — the shader was a no-op (Robert's
 *    V-Rally 3 screenshots, 2026-11-14). The grid pass
 *    (pass 2) stays separate and still reads the native
 *    texel grid, as in the chains.
 * 8. Subpixel-aware for GBA — REMOVED in item 3 of the
 *    rework (the offset was 0.3333 of a texel, about 1.9
 *    screen px at 5.7x; true subpixel would be about
 *    0.058 texel; and the target phone is pOLED, not an
 *    RGB-stripe panel — the shader comment even cited
 *    the AGB panel, the wrong display). A vertical edge
 *    blends the same way as any other edge now, and the
 *    sim proves it does not fringe (max |R-G| 0.0000 in
 *    the band on a greyscale vertical edge).
 *
 * NOT YET (later items in the same rework): the
 * colour-fold (item 4 — each system's colour model
 * folded into the edge pass, so edge mode does not lose
 * the chains' researched colour) and the dev-stats
 * readout showing whether the edge program is actually
 * active vs silently fallen back (item 5). Until item 4
 * lands, edge mode shows the core's generic colour
 * correction, not the chains' model — the same gap the
 * rework was ordered to close, closed in item 4.
 *
 * Honest limits (not built, same as the plan):
 * layer-aware smoothing is not feasible from the
 * composited frame alone — it would need the mGBA core
 * forked to output layers. No AI / ML upscaling. The
 * Bayer 4x4 test is a local proxy: a 3x3 window cannot
 * see a 4-texel period, so it detects the
 * non-monotonic steps a 4x4 ordered pattern makes
 * locally (the shader comment says exactly this).
 *
 * Pure Kotlin — no Android APIs — so the model runs
 * under plain JVM unit tests. The renderer only
 * applies it.
 */
enum class EdgeMode {
    OFF,
    EDGE,
    EDGE_GRID,
}

/** The per-system edge parameters, applied as shader uniforms. */
data class EdgeParams(
    /** Colour distance that still counts as "the same" in the dither tests. */
    val ditherTol: Float,
    /** Edge strength where smoothing starts. */
    val edgeLo: Float,
    /** Edge strength where smoothing is full. */
    val edgeHi: Float,
    /** The max blend weight. No taper: the sim measured the
     *  taper's effect at under 3% on real edges, so it was
     *  dropped (see the class comment, item 4). */
    val blendMax: Float,
    /** True for GBC / GBA: quantize blended outputs to the
     *  15-bit grid. False for GB: no snap at all — on 4
     *  shades any grey is harmonious (see the class
     *  comment, item 2). */
    val snap15: Boolean,
    /** The text bypass's axis-aligned threshold: the
     *  gradient ratio below which a high-contrast edge
     *  reads as straight text, not a diagonal. Set by the
     *  sim from the text/diagonal margins (GB 0.15,
     *  GBC 0.08, GBA 0.05). */
    val textAlign: Float,
)

/** The display actually in effect, after the exclusivity rule. */
data class DisplayResolution(
    val chain: DisplayChain,
    val edge: EdgeMode,
)

object EdgeSmoothing {

    /**
     * Resolve the display from the chain the system wants,
     * whether its toggle is on, and the edge mode. The edge
     * mode wins: when it is on, the chain is off — they fight
     * visually, and the chain's colour + grid would hide the
     * crispness the edge pass creates. Otherwise the chain is
     * on when its toggle is on, and clean when it is off.
     */
    fun resolve(chain: DisplayChain, chainOn: Boolean, edge: EdgeMode): DisplayResolution {
        val effectiveChain = if (edge != EdgeMode.OFF || !chainOn) DisplayChain.CLEAN else chain
        return DisplayResolution(effectiveChain, edge)
    }

    /**
     * The edge parameters for a system. GB's 4 shades differ
     * subtly, so its thresholds sit lower and its blend cap
     * is the gentlest — a hard blend on 4 shades is a visible
     * step, not a smooth edge — and it gets no snap. GBC
     * keeps the same tolerance in colour with a slightly
     * stronger blend and the 15-bit snap. GBA has the most
     * colours and the thinnest outlines, so it gets the
     * strongest blend, plus the narrowest text bypass. The blend cap
     * does NOT taper with display scale: the sim measured
     * the taper's effect at under 3% on real edges, so it
     * was dropped.
     */
    fun edgeParamsFor(system: GameSystem): EdgeParams {
        return when (system) {
            GameSystem.GB -> EdgeParams(0.10f, 0.05f, 0.30f, 0.60f, false, 0.15f)
            GameSystem.GBC -> EdgeParams(0.10f, 0.05f, 0.35f, 0.65f, true, 0.08f)
            GameSystem.GBA -> EdgeParams(0.08f, 0.06f, 0.35f, 0.70f, true, 0.05f)
        }
    }
}
