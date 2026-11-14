package app.poing

/**
 * Phase 9 — edge smoothing: HowlingDog's dither-aware GLSL
 * edge shader, retuned for handhelds, not a copy of it. The
 * goal is to beat generic xBR / ScaleFX on handheld pixel
 * art — and if the side-by-side on Robert's device does not
 * show a win, this file does not claim one. The ship gate is
 * his device comparison, not this code.
 *
 * Why handhelds differ from Neo Geo: GB is 160x144, 4 shades
 * — checkerboard dither IS the shading (Pokémon water, Zelda
 * shadows, Mario gradients). GBC is the same resolution in
 * colour, still dither-heavy. GBA is 240x160, less dither,
 * more thin outlines and small text. So the retune is
 * per-system, not one set of numbers for all three.
 *
 * The eight improvements over HowlingDog, all in
 * [DisplayShaders.EDGE_FRAGMENT]:
 * 1. Multi-pattern dither — checkerboard + vertical /
 *    horizontal stripes + Bayer 2x2 / 4x4, not just 2x2
 *    checkerboard. A patterned texel is preserved, never
 *    blurred into mud.
 * 2. 1-pixel detail preservation — an isolated texel whose
 *    neighbours all agree with each other (a 1px eye, an
 *    outline dot) is output untouched.
 * 3. Text / UI bypass — a high-contrast, axis-aligned edge
 *    (an 8x8 glyph stroke, a text-box border, HUD numbers)
 *    stays pixel-crisp, never anti-aliased into blur. A
 *    straight sprite edge is axis-aligned too, and it needs
 *    no smoothing either — a staircase only exists on a
 *    diagonal — so the bypass costs nothing there.
 * 4. Palette snap-back — after blending, the result snaps
 *    back to the nearest colour in the game's actual palette:
 *    GB snaps to its 4 shades (the four most frequent frame
 *    colours, scanned from the frame itself, so any palette
 *    works — DMG green, SGB, all 48); GBC / GBA quantize to
 *    the 15-bit grid (5 bits a channel), which IS their
 *    palette space. No muddy intermediate ever survives.
 * 5. Motion-adaptive — the renderer differences each frame
 *    against the last (a small luma grid, [lumaGrid] +
 *    [meanAbsDiff]); while scrolling, the smoothing eases
 *    off, and it restores when still. The easing rises fast
 *    and decays slow, so the blend weight never wobbles.
 * 6. Grid-aligned smoothing — the edge pass runs at the
 *    DISPLAY size into the offscreen framebuffer (pass 1),
 *    so each screen pixel lands at a real sub-texel
 *    position: the blend weight grows toward the texel
 *    boundary and the centre of a texel stays its own
 *    colour. At the frame's native size every fragment sat
 *    at a texel centre and the weight was exactly 0
 *    everywhere — the shader was a no-op (Robert's V-Rally
 *    3 screenshots, 2026-11-14). The grid pass (pass 2)
 *    stays separate and still reads the native texel grid,
 *    as in the chains.
 * 7. Subpixel-aware for GBA — on a vertical edge the blend
 *    runs horizontally, and each channel blends at its own
 *    subpixel offset (the AGB panel is BGR: blue first from
 *    the left), so a vertical edge does not fringe.
 * 8. Per-system kernel scaling — GB, GBC and GBA get
 *    different thresholds and blend caps ([edgeParamsFor]),
 *    and the blend cap tapers above 3x display scale (the
 *    same reasoning as the grid taper: a wide soft band at
 *    5x reads as blur, not as a clean edge).
 *
 * How it ships (locked, Robert's decision): off by default;
 * an alternative to Jade / Prism / Dusk, never stacked —
 * edge on means the display chain is off ([resolve]). One
 * curated combined mode: edge smoothing + light grid only,
 * one tap, the same idea as HowlingDog's sharp-CRT mode but
 * with the LCD grid, never scanlines — every handheld LCD
 * here is flat. No Vulkan, no ES 3.0: GLSL in the existing
 * ES 2.0 renderer.
 *
 * Honest limits (not built, same as the plan): layer-aware
 * smoothing is not feasible from the composited frame alone
 * — it would need the mGBA core forked to output layers.
 * No AI / ML upscaling. The Bayer 4x4 test is a local proxy:
 * a 3x3 window cannot see a 4-texel period, so it detects
 * the non-monotonic steps a 4x4 ordered pattern makes
 * locally (the shader comment says exactly this).
 *
 * Pure Kotlin — no Android APIs — so the model, the motion
 * math and the palette scan run under plain JVM unit tests.
 * The renderer only applies them.
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
    /** The max blend weight (before the display-scale taper). */
    val blendMax: Float,
    /** True for GBA only: the BGR subpixel blend on vertical edges. */
    val subpixel: Boolean,
    /** True for GB only: snap to the game's 4 shades (GBC / GBA use the 15-bit grid). */
    val snapGb: Boolean,
)

/** The display actually in effect, after the exclusivity rule. */
data class DisplayResolution(
    val chain: DisplayChain,
    val edge: EdgeMode,
)

object EdgeSmoothing {

    /** The motion grid: 20x18 luma samples per frame. */
    const val MOTION_GRID_COLS = 20
    const val MOTION_GRID_ROWS = 18

    /** The motion factor decays this much per frame when the picture stops moving. */
    const val MOTION_DECAY = 0.85f

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
     * The edge parameters for a system at a display scale
     * (display pixels per native texel). GB's 4 shades differ
     * subtly, so its thresholds sit lower and its blend cap
     * is the gentlest — a hard blend on 4 shades is a visible
     * step, not a smooth edge. GBC keeps the same tolerance
     * in colour with a slightly stronger blend. GBA has the
     * most colours and the thinnest outlines, so it gets the
     * strongest blend and the only subpixel pass. The blend
     * cap tapers above 3x display scale: full at 3x and
     * below, down to 0.8x at 5x and above.
     */
    fun edgeParamsFor(system: GameSystem, displayScale: Float): EdgeParams {
        val base = when (system) {
            GameSystem.GB -> EdgeParams(0.10f, 0.05f, 0.30f, 0.60f, false, true)
            GameSystem.GBC -> EdgeParams(0.10f, 0.05f, 0.35f, 0.65f, false, false)
            GameSystem.GBA -> EdgeParams(0.08f, 0.06f, 0.35f, 0.70f, true, false)
        }
        val taper = kotlin.math.sqrt(3.0f / displayScale).coerceIn(0.8f, 1.0f)
        return base.copy(blendMax = base.blendMax * taper)
    }

    /**
     * The motion factor from a mean absolute luma difference
     * between two frames' grids: 0 below 0.02 (a still scene
     * with a moving sprite), 1 at 0.25 and above (a full
     * scroll). Between them it ramps — a slow scroll eases
     * the smoothing off part-way, it does not switch it.
     */
    fun motionFromMeanDiff(meanDiff: Float): Float =
        ((meanDiff - 0.02f) / (0.25f - 0.02f)).coerceIn(0.0f, 1.0f)

    /** The mean absolute difference between two luma grids of the same size. */
    fun meanAbsDiff(prev: FloatArray, cur: FloatArray): Float {
        require(prev.size == cur.size) { "motion grids must be the same size" }
        if (prev.isEmpty()) return 0f
        var sum = 0f
        for (i in prev.indices) sum += kotlin.math.abs(cur[i] - prev[i])
        return sum / prev.size
    }

    /**
     * Downsample an ARGB pixel array to a cols x rows luma
     * grid (row-major, top first): one sample per cell, at
     * the cell's centre. Pure math on the array, so it runs
     * under plain JVM unit tests; the renderer hands it the
     * frame's pixels.
     */
    fun lumaGrid(pixels: IntArray, width: Int, height: Int, cols: Int, rows: Int): FloatArray {
        val out = FloatArray(cols * rows)
        for (r in 0 until rows) {
            val y = ((r + 0.5f) * height / rows).toInt().coerceIn(0, height - 1)
            for (c in 0 until cols) {
                val x = ((c + 0.5f) * width / cols).toInt().coerceIn(0, width - 1)
                val p = pixels[y * width + x]
                val red = (p shr 16) and 0xFF
                val green = (p shr 8) and 0xFF
                val blue = p and 0xFF
                out[r * cols + c] = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
            }
        }
        return out
    }

    /**
     * The game's actual palette for GB snap-back: the most
     * frequent colours in the frame, most frequent first.
     * A GB frame holds its 4 shades (whatever the palette —
     * the scan reads the frame, not the palette's name), so
     * the top 4 are the palette. Fewer than [maxColors]
     * distinct colours pads by repeating the last, so the
     * shader always gets a full set; an empty frame pads
     * with black. Ties break by colour value, so the order
     * is deterministic.
     */
    fun topPaletteColors(pixels: IntArray, maxColors: Int = 4): IntArray {
        val counts = HashMap<Int, Int>()
        for (p in pixels) {
            val rgb = p and 0x00FFFFFF
            counts[rgb] = (counts[rgb] ?: 0) + 1
        }
        val ranked = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        val out = IntArray(maxColors)
        for (i in 0 until maxColors) {
            val rgb = when {
                ranked.isEmpty() -> 0
                i < ranked.size -> ranked[i]
                else -> ranked.last()
            }
            out[i] = 0xFF000000.toInt() or rgb
        }
        return out
    }
}
