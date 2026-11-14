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
     * threshold, colour-fold in item 4). Must be kept in step
     * with the shader; sim2.py (~/workspace/poing/verify/edge-rework/
     * sim2.py) mirrors the same shader in Python and
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
        val axisAligned = step(
            min(abs(gx), abs(gy)),
            params.textAlign * max(abs(gx), abs(gy)) + 0.001f,
        )
        val text = step(0.5f, lMax - lMin) * axisAligned
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
        var wgt = edge * wAlong * (1f - pattern) * (1f - isolated) *
            (1f - text) * (1f - corner)
        wgt = clampF(wgt, 0f, params.blendMax)
        var col = Rgb(
            clampF(mix(c0.r, along.r, wgt), lo.r, hi.r),
            clampF(mix(c0.g, along.g, wgt), lo.g, hi.g),
            clampF(mix(c0.b, along.b, wgt), lo.b, hi.b),
        )
        // Dither blend, gated by the 1px and text bypasses.
        val patternEff = pattern * (1f - isolated) * (1f - text)
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
        // colour exactly).
        if (params.snap15) {
            val snapped = Rgb(
                floor(col.r * 31f + 0.5f) / 31f,
                floor(col.g * 31f + 0.5f) / 31f,
                floor(col.b * 31f + 0.5f) / 31f,
            )
            val blended = max(if (wgt > 0.001f) 1f else 0f, patternEff)
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
        val blue = Rgb(0.1f, 0.1f, 0.8f)
        val frame = Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) red else blue
        }
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBC)
        val out = renderEdge(frame, w, h, w * 2, h * 2, params, 2)
        val eff = effectiveTexels(out, w, h, w * 2, h * 2)
        // The average of the CORRECTED colours (the fold runs
        // before the dither blend), then 15-bit snapped
        // (GBC snap15) — the tolerance allows the snap step.
        val redC = colourCorrect(red, 2)
        val blueC = colourCorrect(blue, 2)
        val avg = Rgb(
            floor(((redC.r + blueC.r) * 0.5f) * 31f + 0.5f) / 31f,
            floor(((redC.g + blueC.g) * 0.5f) * 31f + 0.5f) / 31f,
            floor(((redC.b + blueC.b) * 0.5f) * 31f + 0.5f) / 31f,
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
        // corrected red and blue).
        val base = nearestCorrected(frame, w, h, w * 2, h * 2, 2)
        val baseEff = effectiveTexels(base, w, h, w * 2, h * 2)
        assertTrue("the base checkerboard must hold two colours", baseEff.any { it.dist(redC) <= 0.001f } && baseEff.any { it.dist(blueC) <= 0.001f })
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
