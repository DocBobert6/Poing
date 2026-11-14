package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Phase 9 fix (Robert, 2026-11-14) — the render-level
 * regression test for edge smoothing.
 *
 * The first Phase 9 build was green with the feature broken:
 * sim.py evaluated the edge formula at hand-picked sub-texel
 * positions (0.25 / 0.75) that the renderer never produced —
 * pass 1 ran at the frame's native size, so every fragment
 * sat at a texel centre (sub 0.5, 0.5), the boundary weight
 * was exactly 0, and the shader output its input unchanged
 * on any game (Robert's V-Rally 3 screenshots: hard 1px
 * luma cliffs, flat sky perfectly constant). The formula
 * tests passed; the geometry was never tested.
 *
 * This test closes that gap: it evaluates the edge shader
 * over the renderer's ACTUAL fragment positions — display
 * pixel (i, j) at vTex ((i+0.5)/viewW, (j+0.5)/viewH) —
 * and asserts a diagonal edge's pixels actually change at
 * display resolution, and change by nothing at native
 * resolution. The fragment evaluation below mirrors
 * DisplayShaders.EDGE_FRAGMENT exactly (same formulas,
 * same constants, same order) and must be kept in step
 * with it; sim.py (~/workspace/poing/verify/phase9/sim.py)
 * mirrors the same shader in Python and cross-checks these
 * numbers at full device resolution.
 *
 * Plain JVM test (no Robolectric): pure Kotlin math, the
 * same as EdgeSmoothingTest.
 */
class EdgeRenderGeometryTest {

    private data class Rgb(val r: Float, val g: Float, val b: Float) {
        fun dist(o: Rgb): Float {
            val dr = r - o.r
            val dg = g - o.g
            val db = b - o.b
            return sqrt(dr * dr + dg * dg + db * db)
        }

        fun luma(): Float = 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun step(edge: Float, x: Float): Float = if (x >= edge) 1f else 0f

    private fun clampF(x: Float, lo: Float, hi: Float): Float = max(lo, min(hi, x))

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clampF((x - e0) / (e1 - e0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun mixRgb(a: Rgb, b: Rgb, t: Float): Rgb =
        Rgb(mix(a.r, b.r, t), mix(a.g, b.g, t), mix(a.b, b.b, t))

    /**
     * The colour-fold, mirrored from EDGE_FRAGMENT's
     * colourCorrect (which is COLOUR_FRAGMENT's mode logic
     * as a function): mode 0 is Jade's FSTN ramp, mode 1
     * passes through, modes 2 / 3 are the SameBoy response
     * ramp (LUT row 0 CGB / row 1 AGB) then the mGBA matrix
     * in linear light (x0.94). The LUT values come from
     * DisplayShaders itself, so a LUT change moves both.
     */
    private fun colourCorrect(c: Rgb, mode: Int): Rgb {
        if (mode == 1) return c
        if (mode == 0) {
            val l = c.luma()
            val s0 = Rgb(0.094f, 0.161f, 0.129f)
            val s1 = Rgb(0.290f, 0.388f, 0.322f)
            val s2 = Rgb(0.518f, 0.612f, 0.518f)
            val s3 = Rgb(0.647f, 0.710f, 0.647f)
            val c01 = mixRgb(s0, s1, clampF(l / 0.322f, 0f, 1f))
            val c12 = mixRgb(s1, s2, clampF((l - 0.322f) / 0.356f, 0f, 1f))
            val c23 = mixRgb(s2, s3, clampF((l - 0.678f) / 0.322f, 0f, 1f))
            return mixRgb(mixRgb(c01, c12, step(0.322f, l)), c23, step(0.678f, l))
        }
        val lut = if (mode == 2) DisplayShaders.GAMMA_LUT_GBC else DisplayShaders.GAMMA_LUT_GBA
        fun ramp(ch: Float): Double {
            val idx = min(floor(ch * 32f), 31f).toInt()
            return (lut[idx] / 255.0).pow(2.2)
        }
        val lr = ramp(c.r)
        val lg = ramp(c.g)
        val lb = ramp(c.b)
        val mR = 0.94 * (0.82 * lr + 0.24 * lg - 0.06 * lb)
        val mG = 0.94 * (0.125 * lr + 0.665 * lg + 0.21 * lb)
        val mB = 0.94 * (0.195 * lr + 0.075 * lg + 0.73 * lb)
        return Rgb(
            clampF(max(mR, 0.0).pow(1.0 / 2.2).toFloat(), 0f, 1f),
            clampF(max(mG, 0.0).pow(1.0 / 2.2).toFloat(), 0f, 1f),
            clampF(max(mB, 0.0).pow(1.0 / 2.2).toFloat(), 0f, 1f),
        )
    }

    private fun close(a: Rgb, b: Rgb, eps: Float = 0.001f): Boolean = a.dist(b) <= eps

    /** GL_NEAREST sampling of the frame at UV (u, v). */
    private fun sample(frame: Array<Rgb>, w: Int, h: Int, u: Float, v: Float): Rgb {
        val x = floor(u * w).toInt().coerceIn(0, w - 1)
        val y = floor(v * h).toInt().coerceIn(0, h - 1)
        return frame[y * w + x]
    }

    /**
     * DisplayShaders.EDGE_FRAGMENT, mirrored in Kotlin
     * (reworked 2026-11-14: dither blends flat, no GB
     * snap, no motion, no taper, per-system text
     * threshold, colour-fold in item 4; Fix 3: the text
     * ratio is evaluated over the 5x5 outer ring, RAW
     * luma, so staircased diagonals no longer read as
     * text). Must be kept in step with the shader;
     * sim3.py (~/workspace/poing/verify/edge-rework/
     * sim3.py) mirrors the same shader in Python and
     * cross-checks these numbers at full device
     * resolution.
     */
    private fun edgeFragment(
        frame: Array<Rgb>,
        w: Int,
        h: Int,
        u: Float,
        v: Float,
        params: EdgeParams,
        mode: Int,
    ): Rgb {
        val tx = 1f / w
        val ty = 1f / h
        // The colour-fold runs first: all nine samples are
        // corrected before any edge work, so the dither
        // averages, the gradients and the blends all work
        // in the corrected colours.
        val c0 = colourCorrect(sample(frame, w, h, u, v), mode)
        val cN = colourCorrect(sample(frame, w, h, u, v + ty), mode)
        val cS = colourCorrect(sample(frame, w, h, u, v - ty), mode)
        val cE = colourCorrect(sample(frame, w, h, u + tx, v), mode)
        val cW = colourCorrect(sample(frame, w, h, u - tx, v), mode)
        val cNE = colourCorrect(sample(frame, w, h, u + tx, v + ty), mode)
        val cNW = colourCorrect(sample(frame, w, h, u - tx, v + ty), mode)
        val cSE = colourCorrect(sample(frame, w, h, u + tx, v - ty), mode)
        val cSW = colourCorrect(sample(frame, w, h, u - tx, v - ty), mode)
        val tol = params.ditherTol
        val dDiag = c0.dist(cSE)
        val dE = c0.dist(cE)
        val dS = c0.dist(cS)
        val checker = step(dDiag, tol) * step(tol, dE) * step(tol, dS)
        val stripeV = step(c0.dist(cN) + c0.dist(cS), tol) *
            step(tol, c0.dist(cE)) * step(tol, c0.dist(cW))
        val stripeH = step(c0.dist(cE) + c0.dist(cW), tol) *
            step(tol, c0.dist(cN)) * step(tol, c0.dist(cS))
        val bayer2 = step(cE.dist(cW), tol) * step(cN.dist(cS), tol) *
            step(cNE.dist(cSE), tol) * step(cNE.dist(cNW), tol) *
            step(tol, c0.dist(cE)) * step(tol, c0.dist(cN)) *
            step(tol, cE.dist(cN))
        val l0 = c0.luma()
        val lN = cN.luma()
        val lS = cS.luma()
        val lE = cE.luma()
        val lW = cW.luma()
        val lNE = cNE.luma()
        val lNW = cNW.luma()
        val lSE = cSE.luma()
        val lSW = cSW.luma()
        val bayer4 = step(0f, (lE - l0) * (lW - l0)) *
            step(0f, (lN - l0) * (lS - l0)) *
            step(0.03f, abs(lE - l0)) * step(0.03f, abs(lW - l0)) *
            step(0.03f, abs(lN - l0)) * step(0.03f, abs(lS - l0)) *
            step(0.05f, abs(lE - lW)) * step(0.05f, abs(lN - lS))
        val pattern = max(max(checker, max(stripeV, stripeH)), max(bayer2, bayer4))
        // The dither average: the intended level of the
        // pattern. Priority matches the shader (bayer4,
        // bayer2, stripeH, stripeV / checker).
        val ditherAvg: Rgb = when {
            bayer4 > 0.5f -> Rgb(
                (c0.r + cN.r + cS.r + cE.r + cW.r + cNE.r + cNW.r + cSE.r + cSW.r) / 9f,
                (c0.g + cN.g + cS.g + cE.g + cW.g + cNE.g + cNW.g + cSE.g + cSW.g) / 9f,
                (c0.b + cN.b + cS.b + cE.b + cW.b + cNE.b + cNW.b + cSE.b + cSW.b) / 9f,
            )
            bayer2 > 0.5f -> Rgb(
                (c0.r + cE.r + cN.r + cNE.r) * 0.25f,
                (c0.g + cE.g + cN.g + cNE.g) * 0.25f,
                (c0.b + cE.b + cN.b + cNE.b) * 0.25f,
            )
            stripeH > 0.5f -> Rgb(
                (c0.r + cN.r) * 0.5f,
                (c0.g + cN.g) * 0.5f,
                (c0.b + cN.b) * 0.5f,
            )
            checker > 0.5f || stripeV > 0.5f -> Rgb(
                (c0.r + cE.r) * 0.5f,
                (c0.g + cE.g) * 0.5f,
                (c0.b + cE.b) * 0.5f,
            )
            else -> c0
        }
        val nMean = Rgb(
            (cN.r + cS.r + cE.r + cW.r + cNE.r + cNW.r + cSE.r + cSW.r) / 8f,
            (cN.g + cS.g + cE.g + cW.g + cNE.g + cNW.g + cSE.g + cSW.g) / 8f,
            (cN.b + cS.b + cE.b + cW.b + cNE.b + cNW.b + cSE.b + cSW.b) / 8f,
        )
        var spread = 0f
        for (c in listOf(cN, cS, cE, cW, cNE, cNW, cSE, cSW)) {
            spread = max(spread, c.dist(nMean))
        }
        val isolated = step(spread, 0.05f) * step(0.15f, c0.dist(nMean))
        val gx = (lE + lNE + lSE) - (lW + lNW + lSW)
        val gy = (lN + lNE + lNW) - (lS + lSE + lSW)
        val edgeStrength = abs(gx) + abs(gy)
        val lMax = max(max(max(l0, lN), max(lS, lE)), max(max(lW, lNE), max(lNW, max(lSE, lSW))))
        val lMin = min(min(min(l0, lN), min(lS, lE)), min(min(lW, lNE), min(lNW, min(lSE, lSW))))
        // 5x5 text test (Fix 3, 2026-11-14): the 3x3 ratio
        // fired on staircase steps, so the ratio is now
        // evaluated over the outer ring, on RAW luma — see
        // the shader's comment and sim3.py
        // (~/workspace/poing/verify/edge-rework/sim3.py).
        val rE2 = sample(frame, w, h, u + 2f * tx, v).luma()
        val rNE2 = sample(frame, w, h, u + 2f * tx, v + ty).luma()
        val rSE2 = sample(frame, w, h, u + 2f * tx, v - ty).luma()
        val rW2 = sample(frame, w, h, u - 2f * tx, v).luma()
        val rNW2 = sample(frame, w, h, u - 2f * tx, v + ty).luma()
        val rSW2 = sample(frame, w, h, u - 2f * tx, v - ty).luma()
        val rN2 = sample(frame, w, h, u, v + 2f * ty).luma()
        val rN2E = sample(frame, w, h, u + tx, v + 2f * ty).luma()
        val rN2W = sample(frame, w, h, u - tx, v + 2f * ty).luma()
        val rS2 = sample(frame, w, h, u, v - 2f * ty).luma()
        val rS2E = sample(frame, w, h, u + tx, v - 2f * ty).luma()
        val rS2W = sample(frame, w, h, u - tx, v - 2f * ty).luma()
        val gx5 = (rE2 + rNE2 + rSE2) - (rW2 + rNW2 + rSW2)
        val gy5 = (rN2 + rN2E + rN2W) - (rS2 + rS2E + rS2W)
        val axisAligned = step(
            min(abs(gx5), abs(gy5)),
            params.textAlign * max(abs(gx5), abs(gy5)) + 0.001f,
        )
        val text = step(0.5f, lMax - lMin) * axisAligned
        // Stroke-length gate (Task 4 item 1): the bypass
        // survives only when the raw luma step along the
        // stroke terminates within 2 texels in at least one
        // direction — see the shader's comment and sim4c.py
        // (~/workspace/poing/verify/edge-rework/sim4c.py,
        // 93/93 in sim4c-run2.log). The gate feeds the edge
        // blend (wgt) only; patternEff below keeps the
        // ungated text bypass, because it replaces the texel.
        val rMax = max(max(max(rE2, rNE2), max(rSE2, rW2)), max(max(rNW2, rSW2), max(max(rN2, rN2E), max(rN2W, max(rS2, max(rS2E, rS2W))))))
        val rMin = min(min(min(rE2, rNE2), min(rSE2, rW2)), min(min(rNW2, rSW2), min(min(rN2, rN2E), min(rN2W, min(rS2, min(rS2E, rS2W))))))
        val gateScale = 0.5f * (rMax - rMin)
        val l0r = sample(frame, w, h, u, v).luma()
        val lF2: Float
        val lB2: Float
        if (abs(gx5) > abs(gy5)) {
            lF2 = sample(frame, w, h, u, v + 2f * ty).luma()
            lB2 = sample(frame, w, h, u, v - 2f * ty).luma()
        } else {
            lF2 = sample(frame, w, h, u + 2f * tx, v).luma()
            lB2 = sample(frame, w, h, u - 2f * tx, v).luma()
        }
        val gate = max(step(gateScale, abs(lF2 - l0r)), step(gateScale, abs(lB2 - l0r)))
        val textGated = text * gate
        val corner = step(cNW.dist(cSE), 0.1f) * step(cNE.dist(cSW), 0.1f) *
            step(0.05f, edgeStrength)
        val subX = (u * w) - floor(u * w)
        val subY = (v * h) - floor(v * h)
        val wAlong: Float
        val along: Rgb
        val lo: Rgb
        val hi: Rgb
        if (abs(gx) > abs(gy)) {
            along = Rgb(mix(cS.r, cN.r, subY), mix(cS.g, cN.g, subY), mix(cS.b, cN.b, subY))
            wAlong = abs(subY - 0.5f) * 2f
            lo = Rgb(min(min(c0.r, cS.r), cN.r), min(min(c0.g, cS.g), cN.g), min(min(c0.b, cS.b), cN.b))
            hi = Rgb(max(max(c0.r, cS.r), cN.r), max(max(c0.g, cS.g), cN.g), max(max(c0.b, cS.b), cN.b))
        } else {
            along = Rgb(mix(cW.r, cE.r, subX), mix(cW.g, cE.g, subX), mix(cW.b, cE.b, subX))
            wAlong = abs(subX - 0.5f) * 2f
            lo = Rgb(min(min(c0.r, cW.r), cE.r), min(min(c0.g, cW.g), cE.g), min(min(c0.b, cW.b), cE.b))
            hi = Rgb(max(max(c0.r, cW.r), cE.r), max(max(c0.g, cW.g), cE.g), max(max(c0.b, cW.b), cE.b))
        }
        val edge = smoothstep(params.edgeLo, params.edgeHi, edgeStrength)
        var wgt = edge * wAlong * (1f - isolated) *
            (1f - textGated) * (1f - corner)
        wgt = clampF(wgt, 0f, params.blendMax)
        var col = Rgb(
            clampF(mix(c0.r, along.r, wgt), lo.r, hi.r),
            clampF(mix(c0.g, along.g, wgt), lo.g, hi.g),
            clampF(mix(c0.b, along.b, wgt), lo.b, hi.b),
        )
        // Dither blend, gated by the 1px and text bypasses.
        // The text bypass here is the UNGATED one, not
        // textGated: patternEff replaces the texel (see the
        // gate above), so it keeps the old protection.
        // And the shade-distance guard (Task 4 item 4): the
        // flattening only runs when the shades it would
        // average are close enough to be one intended grey
        // — same priority as ditherAvg above, same 0.7 bound
        // for all systems (params.ditherGuardHi).
        val shadeDist: Float = when {
            bayer4 > 0.5f -> listOf(cN, cS, cE, cW, cNE, cNW, cSE, cSW).maxOf { c0.dist(it) }
            bayer2 > 0.5f -> listOf(
                c0.dist(cE), c0.dist(cN), c0.dist(cNE),
                cE.dist(cN), cE.dist(cNE), cN.dist(cNE),
            ).max()
            stripeH > 0.5f -> c0.dist(cN)
            else -> c0.dist(cE)
        }
        val guard = if (shadeDist <= params.ditherGuardHi) 1f else 0f
        val patternEff = pattern * (1f - isolated) * (1f - text) * guard
        col = Rgb(
            mix(col.r, ditherAvg.r, patternEff),
            mix(col.g, ditherAvg.g, patternEff),
            mix(col.b, ditherAvg.b, patternEff),
        )
        // 15-bit snap-back, GBC / GBA only. GB has no snap:
        // on 4 shades any grey is harmonious, and the old
        // binary snap turned a smooth ramp into a hard
        // one-shade band. Only a blend or a pattern is ever
        // snapped (w == 0 and patternEff == 0 keeps its own
        // colour exactly) — and the trigger is the colour
        // actually having moved, not w > 0: the stroke-length
        // gate lets w > 0 on straight glyph runs, where the
        // blend is a no-op, and the old w-based trigger
        // snapped those texels anyway (the text-bar test
        // went from 0 to 3840 changed pixels on it).
        if (params.snap15) {
            val snapped = Rgb(
                floor(col.r * 31f + 0.5f) / 31f,
                floor(col.g * 31f + 0.5f) / 31f,
                floor(col.b * 31f + 0.5f) / 31f,
            )
            val blended = max(if (col.dist(c0) > 0.001f) 1f else 0f, patternEff)
            col = Rgb(
                mix(c0.r, snapped.r, blended),
                mix(c0.g, snapped.g, blended),
                mix(c0.b, snapped.b, blended),
            )
        }
        return col
    }

    /**
     * The edge pass at the renderer's real fragment
     * geometry: display pixel (i, j) at vTex
     * ((i+0.5)/viewW, (j+0.5)/viewH) — the positions the
     * fullscreen quad actually produces. Returns the
     * display-size output, row-major, top first.
     */
    private fun renderEdge(
        frame: Array<Rgb>,
        w: Int,
        h: Int,
        viewW: Int,
        viewH: Int,
        params: EdgeParams,
        mode: Int,
    ): Array<Rgb> {
        val out = Array(viewW * viewH) { Rgb(0f, 0f, 0f) }
        for (j in 0 until viewH) {
            val v = (j + 0.5f) / viewH
            for (i in 0 until viewW) {
                val u = (i + 0.5f) / viewW
                out[j * viewW + i] = edgeFragment(frame, w, h, u, v, params, mode)
            }
        }
        return out
    }

    /** The unblended picture at display size (the plain blit). */
    private fun nearest(frame: Array<Rgb>, w: Int, h: Int, viewW: Int, viewH: Int): Array<Rgb> {
        val out = Array(viewW * viewH) { Rgb(0f, 0f, 0f) }
        for (j in 0 until viewH) {
            val y = min(h - 1, ((j + 0.5f) / viewH * h).toInt())
            for (i in 0 until viewW) {
                val x = min(w - 1, ((i + 0.5f) / viewW * w).toInt())
                out[j * viewW + i] = frame[y * w + x]
            }
        }
        return out
    }

    /**
     * The colour-corrected nearest base: the plain blit does
     * not colour-correct, so the edge output must be compared
     * against the corrected nearest, not the raw frame — the
     * colour-fold changes colours everywhere, and "changed"
     * means edge / dither work only. The sim's
     * nearest_corrected_np is the same base.
     */
    private fun nearestCorrected(
        frame: Array<Rgb>,
        w: Int,
        h: Int,
        viewW: Int,
        viewH: Int,
        mode: Int,
    ): Array<Rgb> {
        val base = nearest(frame, w, h, viewW, viewH)
        return Array(base.size) { colourCorrect(base[it], mode) }
    }

    /**
     * The display pixels nearest each native texel's centre
     * — the texels' effective values after the pass, as the
     * numeric replica measures them (sim2.py's
     * _effective_texels). The centre of a texel keeps its
     * own colour for the edge blend (wAlong 0 there), but a
     * patterned texel blends to its dither average there,
     * so the centres are where the dither blend is read.
     */
    private fun effectiveTexels(out: Array<Rgb>, w: Int, h: Int, viewW: Int, viewH: Int): Array<Rgb> {
        val eff = Array(w * h) { Rgb(0f, 0f, 0f) }
        for (y in 0 until h) {
            val jy = ((y + 0.5f) * viewH / h - 0.5f).toInt().coerceIn(0, viewH - 1)
            for (x in 0 until w) {
                val ix = ((x + 0.5f) * viewW / w - 0.5f).toInt().coerceIn(0, viewW - 1)
                eff[y * w + x] = out[jy * viewW + ix]
            }
        }
        return eff
    }

    private fun changedPixels(a: Array<Rgb>, b: Array<Rgb>): Int {
        var n = 0
        for (i in a.indices) {
            if (a[i].dist(b[i]) > 0.001f) n++
        }
        return n
    }

    /**
     * The 10–90% transition width of the step at x0 on one
     * display line, in display px, against levels taken from
     * the BASELINE line (the sim's _local_width /
     * _local_levels): a hard 1px step has no intermediate
     * values, so it measures 0; a blended step spreads over
     * ~3–6px. The levels must come from the baseline — the
     * blended line's own levels can sit on a dither-averaged
     * patch and move the band off the real step.
     */
    private fun transitionWidth(line: FloatArray, x0: Int, a: Float, b: Float): Int {
        val t10 = a + 0.1f * (b - a)
        val t90 = a + 0.9f * (b - a)
        val lo = max(0, x0 - 14)
        val hi = min(line.size, x0 + 14)
        var first = -1
        var last = -1
        for (i in lo until hi) {
            if ((line[i] - t10) * (line[i] - t90) <= 0f) {
                if (first < 0) first = i
                last = i
            }
        }
        return if (first < 0) 0 else last - first
    }

    private fun diagonalFrame(w: Int, h: Int, light: Rgb, dark: Rgb): Array<Rgb> {
        return Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x + y < 200) light else dark
        }
    }

    @Test
    fun diagonalBlendsAtDisplayResolution() {
        // GBA 240x160 at a 2x display (480x320): the
        // renderer's real geometry. The diagonal must
        // actually blend — this is the test the first
        // Phase 9 build would have failed.
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = diagonalFrame(w, h, light, dark)
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val out = renderEdge(frame, w, h, 480, 320, params, 3)
        val base = nearestCorrected(frame, w, h, 480, 320, 3)
        val changed = changedPixels(out, base)
        // The blended band runs the length of the diagonal
        // (~200 texels); at 2x that is hundreds of display
        // pixels. The threshold is far below the measured
        // count, so float noise can never trip it.
        assertTrue("a diagonal must blend at display resolution, changed=$changed", changed > 100)
        // And only near the edge: nothing more than 3 texels
        // away from the diagonal may change.
        var far = 0
        for (j in 0 until 320) {
            for (i in 0 until 480) {
                val tx = (i + 0.5f) / 480 * w
                val ty = (j + 0.5f) / 320 * h
                if (abs(tx + ty - 200) > 3 && out[j * 480 + i].dist(base[j * 480 + i]) > 0.001f) far++
            }
        }
        assertEquals("blending must stay at the edge", 0, far)
    }

    @Test
    fun diagonalDoesNotBlendAtNativeResolution() {
        // The control: the SAME shader at the OLD geometry —
        // the pass-1 viewport the frame's own size, so every
        // fragment sits at a texel centre. Nothing may
        // change. If this ever fails, the formula changed;
        // if the display-resolution test ever fails, the
        // renderer is running the pass at the wrong size.
        // Together they pin the 2026-11-14 no-op.
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = diagonalFrame(w, h, light, dark)
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val out = renderEdge(frame, w, h, w, h, params, 3)
        val base = nearestCorrected(frame, w, h, w, h, 3)
        assertEquals("at native size the shader is a no-op", 0, changedPixels(out, base))
    }

    @Test
    fun flatFrameIsUnchangedAtDisplayResolution() {
        // A flat field must come out pixel-identical: the
        // fix must not soften the picture anywhere.
        val w = 240
        val h = 160
        val flat = Rgb(0.9f, 0.9f, 0.9f)
        val frame = Array(w * h) { flat }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val out = renderEdge(frame, w, h, 480, 320, params, 3)
        val base = nearestCorrected(frame, w, h, 480, 320, 3)
        assertEquals(0, changedPixels(out, base))
    }

    @Test
    fun gbDiagonalBlendsWithIntermediateGreys() {
        // GB at 2x: the diagonal blends — and, since the
        // rework deleted the GB palette snap, the blended
        // band holds intermediate greys, not just the 4
        // shades. (The old test pinned the snap: every
        // output a palette shade. The sim's C15 measures 24
        // distinct greys in the real SML2 logo band.)
        val w = 160
        val h = 144
        val frame = diagonalFrame(w, h, GB_PAL[0], GB_PAL[3])
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GB)
        val out = renderEdge(frame, w, h, 320, 288, params, 0)
        val base = nearestCorrected(frame, w, h, 320, 288, 0)
        val changed = changedPixels(out, base)
        assertTrue("a GB diagonal must blend, changed=$changed", changed > 50)
        // Every output is FSTN green-grey (the colour-fold:
        // the input greys land on the Pocket ramp, whose
        // stops are all green-dominant) — no longer plain
        // grey, and never a third hue.
        for (px in out) {
            assertTrue("GB output must stay FSTN green-grey", px.g >= px.r - 0.001f && px.g >= px.b - 0.001f)
        }
        // And the band holds intermediates, not just the 4 shades.
        val distinct = out.map { (it.r * 1000).toInt() }.toSet().size
        assertTrue("GB band must hold intermediate greys, distinct=$distinct", distinct > 4)
    }

    @Test
    fun colourCorrectMatchesTheChainModel() {
        // The fold's pins — the same numbers the sim checks
        // (C1) and the rebuild measured: the FSTN ramp lands
        // on the Pocket shades, Prism grey8 -> 64, Dusk
        // grey8 -> 46, white -> 248 in both; and a pure red
        // pins the matrix itself, not just the greys.
        assertTrue(close(colourCorrect(Rgb(1f, 1f, 1f), 0), Rgb(0.647f, 0.710f, 0.647f)))
        assertTrue(close(colourCorrect(Rgb(0f, 0f, 0f), 0), Rgb(0.094f, 0.161f, 0.129f)))
        assertTrue(close(colourCorrect(Rgb(0.322f, 0.322f, 0.322f), 0), Rgb(0.290f, 0.388f, 0.322f)))
        assertTrue(close(colourCorrect(Rgb(0.678f, 0.678f, 0.678f), 0), Rgb(0.518f, 0.612f, 0.518f)))
        val g8 = Rgb(66f / 255f, 66f / 255f, 66f / 255f)
        val white = Rgb(1f, 1f, 1f)
        assertTrue(close(colourCorrect(g8, 2), Rgb(64f / 255f, 64f / 255f, 64f / 255f), 0.01f))
        assertTrue(close(colourCorrect(white, 2), Rgb(248f / 255f, 248f / 255f, 248f / 255f), 0.01f))
        assertTrue(close(colourCorrect(g8, 3), Rgb(46f / 255f, 46f / 255f, 46f / 255f), 0.01f))
        assertTrue(close(colourCorrect(white, 3), Rgb(248f / 255f, 248f / 255f, 248f / 255f), 0.01f))
        assertTrue(close(colourCorrect(Rgb(1f, 0f, 0f), 2), Rgb(0.8884f, 0.3778f, 0.4625f), 0.002f))
        // Mode 1 passes through exactly (DMG-green / SGB
        // palettes as the core rendered them).
        val passthrough = Rgb(0.3f, 0.5f, 0.7f)
        assertTrue(close(colourCorrect(passthrough, 1), passthrough, 0.0001f))
    }

    @Test
    fun gbcColourCheckerboardBlendsToTheAverage() {
        // GBC at 2x: a 2-colour checkerboard (dither in
        // colour) blends to the average of the CORRECTED
        // colours (the fold runs before the dither blend)
        // — the real-LCD look — not preserved as noise and
        // not blurred into a third colour. The average is
        // then 15-bit snapped (GBC snap15), so the
        // tolerance allows the snap step (<= 0.016 a
        // channel).
        val w = 16
        val h = 16
        val red = Rgb(0.8f, 0.1f, 0.1f)
        // Task 4 item 4: the old second colour was blue
        // (0.1, 0.1, 0.8) — but red/blue's CORRECTED
        // distance is 0.906, past the 0.7 guard, so the
        // guard keeps that checkerboard sharp by design
        // (see the extreme test below). The colour-blend
        // mechanism is pinned on red/purple instead:
        // corrected distance 0.393, inside the guard, and
        // the average is a distinct third colour.
        val purple = Rgb(0.5f, 0.1f, 0.6f)
        val frame = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) red else purple
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBC)
        val out = renderEdge(frame, w, h, w * 2, h * 2, params, 2)
        val eff = effectiveTexels(out, w, h, w * 2, h * 2)
        // The average of the CORRECTED colours (the fold runs
        // before the dither blend), then 15-bit snapped
        // (GBC snap15) — the tolerance allows the snap step.
        val redC = colourCorrect(red, 2)
        val purpleC = colourCorrect(purple, 2)
        val avg = Rgb(
            floor(((redC.r + purpleC.r) * 0.5f) * 31f + 0.5f) / 31f,
            floor(((redC.g + purpleC.g) * 0.5f) * 31f + 0.5f) / 31f,
            floor(((redC.b + purpleC.b) * 0.5f) * 31f + 0.5f) / 31f,
        )
        // Interior only: the 1-texel frame border is sampled
        // clamped (GL_CLAMP_TO_EDGE), so a border texel's
        // neighbours include itself and the checker does not
        // fire there — the same border the flat-area check
        // excludes. The interior is where the blend is read.
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val px = eff[y * w + x]
                assertTrue("a GBC dither texel must blend to the average colour, got $px", px.dist(avg) <= 0.03f)
            }
        }
        // And the base is NOT the average: the checkerboard
        // really is two colours before the blend (the
        // corrected red and purple).
        val base = nearestCorrected(frame, w, h, w * 2, h * 2, 2)
        val baseEff = effectiveTexels(base, w, h, w * 2, h * 2)
        assertTrue("the base checkerboard must hold two colours", baseEff.any { it.dist(redC) <= 0.001f } && baseEff.any { it.dist(purpleC) <= 0.001f })
    }

    @Test
    fun gbcBlendedOutputsQuantizeTo15Bit() {
        // GBC at 2x: the diagonal blends, and every
        // changed output pixel is 15-bit quantized (each
        // channel k/31) — the snap15 the rework kept for
        // GBC (GB has no snap; see
        // gbDiagonalBlendsWithIntermediateGreys).
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = diagonalFrame(w, h, light, dark)
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBC)
        val out = renderEdge(frame, w, h, 480, 320, params, 2)
        val base = nearestCorrected(frame, w, h, 480, 320, 2)
        val changed = changedPixels(out, base)
        assertTrue("a GBC diagonal must blend, changed=$changed", changed > 100)
        for (i in out.indices) {
            if (out[i].dist(base[i]) <= 0.001f) continue
            for (c in listOf(out[i].r, out[i].g, out[i].b)) {
                val q = floor(c * 31f + 0.5f) / 31f
                assertTrue("a changed GBC output must be 15-bit quantized, got $c", abs(c - q) <= 0.001f)
            }
        }
    }

    @Test
    fun textEdgeStaysCrispAtDisplayResolution() {
        // An axis-aligned high-contrast bar (a text stroke)
        // must come out pixel-identical at display size.
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = Array(w * h) { idx ->
            val y = idx / w
            if (y in 70..74) dark else light
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val out = renderEdge(frame, w, h, 480, 320, params, 3)
        val base = nearestCorrected(frame, w, h, 480, 320, 3)
        assertEquals(0, changedPixels(out, base))
    }

    @Test
    fun staircaseDoesNotTripTheTextBypass() {
        // Fix 3: the detector-level pin. On a 1:3 staircase
        // the old 3x3 ratio fell below the GBA threshold
        // (0.05) on a quarter of the boundary texels, so the
        // text bypass fired and the blend never ran there
        // (the sim's probe: old fire rate 0.25, new 0.00).
        // The 5x5 outer-ring ratio averages the steps into
        // a diagonal gradient: its minimum over the boundary
        // is 0.333, far above the threshold, so the bypass
        // never fires. The render-level test above pins the
        // softening; this one pins the detector — the width
        // alone does not discriminate, because a row that
        // crosses a blended segment still measures 4px with
        // the old detector.
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (3 * y < x + 140) light else dark
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val tx = 1f / w
        val ty = 1f / h
        var minRatio = Float.MAX_VALUE
        var fires = 0
        var n = 0
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                val c = frame[y * w + x]
                if (c.dist(frame[y * w + x + 1]) < 0.3f &&
                    c.dist(frame[(y + 1) * w + x]) < 0.3f
                ) continue
                val u = (x + 0.5f) / w
                val v = (y + 0.5f) / h
                val rE2 = sample(frame, w, h, u + 2f * tx, v).luma()
                val rNE2 = sample(frame, w, h, u + 2f * tx, v + ty).luma()
                val rSE2 = sample(frame, w, h, u + 2f * tx, v - ty).luma()
                val rW2 = sample(frame, w, h, u - 2f * tx, v).luma()
                val rNW2 = sample(frame, w, h, u - 2f * tx, v + ty).luma()
                val rSW2 = sample(frame, w, h, u - 2f * tx, v - ty).luma()
                val rN2 = sample(frame, w, h, u, v + 2f * ty).luma()
                val rN2E = sample(frame, w, h, u + tx, v + 2f * ty).luma()
                val rN2W = sample(frame, w, h, u - tx, v + 2f * ty).luma()
                val rS2 = sample(frame, w, h, u, v - 2f * ty).luma()
                val rS2E = sample(frame, w, h, u + tx, v - 2f * ty).luma()
                val rS2W = sample(frame, w, h, u - tx, v - 2f * ty).luma()
                val gx5 = (rE2 + rNE2 + rSE2) - (rW2 + rNW2 + rSW2)
                val gy5 = (rN2 + rN2E + rN2W) - (rS2 + rS2E + rS2W)
                val denom = max(abs(gx5), abs(gy5))
                val ratio = if (denom > 0f) min(abs(gx5), abs(gy5)) / denom else 1f
                minRatio = min(minRatio, ratio)
                if (ratio < params.textAlign) fires++
                n++
            }
        }
        assertTrue("staircase boundary texels must number in the hundreds, n=$n", n > 100)
        assertEquals("the text bypass must never fire on a staircase", 0, fires)
        assertTrue(
            "the 5x5 ratio must clear the threshold everywhere, min=$minRatio",
            minRatio > 0.15f,
        )
    }

    @Test
    fun circleDoesNotTripTheTextBypass() {
        // Task 4 item 1: the ungated text bypass ate round
        // sprites — a synthetic circle's boundary fired it on
        // 21-35% of texels (the sim's C19: R=6 0.35, R=10
        // 0.34, R=16 0.31, R=24 0.27), because a curve's
        // near-horizontal / near-vertical stretches are
        // locally axis-aligned. The stroke-length gate drops
        // the bypass where the step continues (a curve) and
        // keeps it where the step terminates (a glyph): at
        // distance 2 the gated fire is 0.00 on every tested
        // radius (sim4c-run2.log). This test is the
        // discriminator: the ungated fire must be high (the
        // old code fails it) and the gated fire zero. There
        // is deliberately no render-level circle test: on a
        // slowly-bending arc the newly-blended texels mix
        // toward same-colour along-neighbours, so the
        // render is gate-independent here (measured: 576
        // changed px and 0-1px widths with the gate on and
        // off alike). The render-level proof is the sim's
        // coverage metric on real frames (C20: 0.79-1.00) and
        // strongest edges (C18), where staircased curves
        // put the along-neighbours across the edge.
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val cx = 120
        val cy = 80
        val r = 10
        val frame = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy < r * r) dark else light
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val tx = 1f / w
        val ty = 1f / h
        var n = 0
        var ungatedFires = 0
        var gatedFires = 0
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                val c = frame[y * w + x]
                if (c.dist(frame[y * w + x + 1]) < 0.3f &&
                    c.dist(frame[(y + 1) * w + x]) < 0.3f
                ) continue
                val u = (x + 0.5f) / w
                val v = (y + 0.5f) / h
                val rE2 = sample(frame, w, h, u + 2f * tx, v).luma()
                val rNE2 = sample(frame, w, h, u + 2f * tx, v + ty).luma()
                val rSE2 = sample(frame, w, h, u + 2f * tx, v - ty).luma()
                val rW2 = sample(frame, w, h, u - 2f * tx, v).luma()
                val rNW2 = sample(frame, w, h, u - 2f * tx, v + ty).luma()
                val rSW2 = sample(frame, w, h, u - 2f * tx, v - ty).luma()
                val rN2 = sample(frame, w, h, u, v + 2f * ty).luma()
                val rN2E = sample(frame, w, h, u + tx, v + 2f * ty).luma()
                val rN2W = sample(frame, w, h, u - tx, v + 2f * ty).luma()
                val rS2 = sample(frame, w, h, u, v - 2f * ty).luma()
                val rS2E = sample(frame, w, h, u + tx, v - 2f * ty).luma()
                val rS2W = sample(frame, w, h, u - tx, v - 2f * ty).luma()
                val gx5 = (rE2 + rNE2 + rSE2) - (rW2 + rNW2 + rSW2)
                val gy5 = (rN2 + rN2E + rN2W) - (rS2 + rS2E + rS2W)
                val axisAligned = step(
                    min(abs(gx5), abs(gy5)),
                    params.textAlign * max(abs(gx5), abs(gy5)) + 0.001f,
                )
                // The 3x3 range, on raw luma: this frame is
                // two greys (0.9 / 0.1), so the raw range
                // (0.8) and the colour-corrected range the
                // mirror uses both clear the 0.5 step.
                val l0 = c.luma()
                val lN = frame[(y - 1) * w + x].luma()
                val lS = frame[(y + 1) * w + x].luma()
                val lE = frame[y * w + x + 1].luma()
                val lW = frame[y * w + x - 1].luma()
                val lNE = frame[(y - 1) * w + x + 1].luma()
                val lNW = frame[(y - 1) * w + x - 1].luma()
                val lSE = frame[(y + 1) * w + x + 1].luma()
                val lSW = frame[(y + 1) * w + x - 1].luma()
                val lMax = max(max(max(l0, lN), max(lS, lE)), max(max(lW, lNE), max(lNW, max(lSE, lSW))))
                val lMin = min(min(min(l0, lN), min(lS, lE)), min(min(lW, lNE), min(lNW, min(lSE, lSW))))
                val text = step(0.5f, lMax - lMin) * axisAligned
                // The stroke-length gate at distance 2.
                val rMax = max(max(max(rE2, rNE2), max(rSE2, rW2)), max(max(rNW2, rSW2), max(max(rN2, rN2E), max(rN2W, max(rS2, max(rS2E, rS2W))))))
                val rMin = min(min(min(rE2, rNE2), min(rSE2, rW2)), min(min(rNW2, rSW2), min(min(rN2, rN2E), min(rN2W, min(rS2, min(rS2E, rS2W))))))
                val gateScale = 0.5f * (rMax - rMin)
                val l0r = c.luma()
                val lF2: Float
                val lB2: Float
                if (abs(gx5) > abs(gy5)) {
                    lF2 = sample(frame, w, h, u, v + 2f * ty).luma()
                    lB2 = sample(frame, w, h, u, v - 2f * ty).luma()
                } else {
                    lF2 = sample(frame, w, h, u + 2f * tx, v).luma()
                    lB2 = sample(frame, w, h, u - 2f * tx, v).luma()
                }
                val gate = max(step(gateScale, abs(lF2 - l0r)), step(gateScale, abs(lB2 - l0r)))
                val textGated = text * gate
                n++
                if (text > 0.5f) ungatedFires++
                if (textGated > 0.5f) gatedFires++
            }
        }
        assertTrue("circle boundary texels must number in the dozens, n=$n", n > 40)
        assertTrue(
            "the ungated bypass must fire on the circle (old code), ungated=$ungatedFires/$n",
            ungatedFires * 5 > n,
        )
        assertEquals("the gate must drop the bypass on the whole circle", 0, gatedFires)
    }

    @Test
    fun staircasedDiagonalBlendsAtDisplayResolution() {
        // Fix 3 (Robert's device photos, 2026-11-14): the
        // old 3x3 text bypass fired on a third to a half of
        // a staircased diagonal's texels — each step texel's
        // gradient is one-directional — so the blend never
        // ran on real diagonals. The 5x5 outer-ring ratio
        // averages the steps into a diagonal gradient. A
        // 1:3 staircase (1 texel down per 3 across, the
        // pixel-art diagonal) must soften on EVERY row that
        // crosses it: the transition width is >= 3px on each
        // of five rows, and the band changes by hundreds of
        // pixels. (The width alone does not discriminate the
        // old detector — a row crossing a blended segment
        // still measures 4px with it; the detector test
        // above is the discriminator. This one pins the
        // render softening: the sim's C17 measures 4px on
        // 1:2 / 1:3 / 1:4 and 7px on a steep 3:1.)
        val w = 240
        val h = 160
        val light = Rgb(0.9f, 0.9f, 0.9f)
        val dark = Rgb(0.1f, 0.1f, 0.1f)
        val frame = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (3 * y < x + 140) light else dark
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA)
        val out = renderEdge(frame, w, h, 480, 320, params, 3)
        val base = nearestCorrected(frame, w, h, 480, 320, 3)
        val changed = changedPixels(out, base)
        assertTrue("a staircased diagonal must blend, changed=$changed", changed > 100)
        for (ty in listOf(60, 70, 80, 90, 100)) {
            // The boundary at this row is x = 3*ty - 140;
            // the display row and crossing are the 2x
            // positions of that texel row and column.
            val j = ((ty + 0.5f) * 2f).toInt()
            val xd = ((3 * ty - 140 + 0.5f) * 2f).toInt()
            val baseRow = FloatArray(480) { i -> base[j * 480 + i].luma() }
            val outRow = FloatArray(480) { i -> out[j * 480 + i].luma() }
            val a = baseRow.copyOfRange(max(0, xd - 12), max(1, xd - 6)).sorted()[2]
            val b = baseRow.copyOfRange(min(480, xd + 7), min(480, xd + 13)).sorted()[2]
            val wBase = transitionWidth(baseRow, xd, a, b)
            val wOut = transitionWidth(outRow, xd, a, b)
            assertTrue(
                "staircase row $ty must soften (base ${wBase}px -> blended ${wOut}px)",
                wOut >= 3 && wBase <= 1,
            )
        }
    }

    @Test
    fun shadeDistanceGuardKeepsHighContrastDitherSharp() {
        // Task 4 item 4: the dither flattening only runs
        // when the shades it would average are close enough
        // to be one intended grey (0.7, all systems). An
        // extreme checkerboard (0.9 / 0.1 greys, corrected
        // distance 0.761) stays its own colours; an
        // adjacent-shade checkerboard (0.62 / 0.30, corrected
        // distance 0.338) still flattens to its average.
        // Read on the interior effective texels, as the GBC
        // test does: the 1-texel frame border is sampled
        // clamped, so the Sobel no longer cancels there and
        // the edge blend fires on the border ring whatever
        // the guard does. The sim's F1 / F1b pin the same
        // split at the fragment level; this one pins it at
        // display size.
        val w = 160
        val h = 144
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GB)
        val extreme = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) Rgb(0.9f, 0.9f, 0.9f) else Rgb(0.1f, 0.1f, 0.1f)
        }
        val outE = renderEdge(extreme, w, h, 320, 288, params, 0)
        val baseE = nearestCorrected(extreme, w, h, 320, 288, 0)
        val effE = effectiveTexels(outE, w, h, 320, 288)
        val baseEffE = effectiveTexels(baseE, w, h, 320, 288)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                assertTrue(
                    "an extreme checkerboard texel must keep its own colour, got ${effE[y * w + x]}",
                    effE[y * w + x].dist(baseEffE[y * w + x]) <= 0.001f,
                )
            }
        }
        val adjacent = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) Rgb(0.62f, 0.62f, 0.62f) else Rgb(0.30f, 0.30f, 0.30f)
        }
        val outA = renderEdge(adjacent, w, h, 320, 288, params, 0)
        val effA = effectiveTexels(outA, w, h, 320, 288)
        val hiC = colourCorrect(Rgb(0.62f, 0.62f, 0.62f), 0)
        val loC = colourCorrect(Rgb(0.30f, 0.30f, 0.30f), 0)
        val avg = Rgb(
            (hiC.r + loC.r) * 0.5f,
            (hiC.g + loC.g) * 0.5f,
            (hiC.b + loC.b) * 0.5f,
        )
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                assertTrue(
                    "an adjacent-shade checkerboard texel must blend to the average, got ${effA[y * w + x]}",
                    effA[y * w + x].dist(avg) <= 0.01f,
                )
            }
        }
    }

    companion object {
        /** The GB test palette (the sim.py PAL4 greys). */
        private val GB_PAL = arrayOf(
            Rgb(0.92f, 0.92f, 0.92f),
            Rgb(0.62f, 0.62f, 0.62f),
            Rgb(0.30f, 0.30f, 0.30f),
            Rgb(0.08f, 0.08f, 0.08f),
        )
    }
}
