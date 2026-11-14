package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

    /** GL_NEAREST sampling of the frame at UV (u, v). */
    private fun sample(frame: Array<Rgb>, w: Int, h: Int, u: Float, v: Float): Rgb {
        val x = floor(u * w).toInt().coerceIn(0, w - 1)
        val y = floor(v * h).toInt().coerceIn(0, h - 1)
        return frame[y * w + x]
    }

    /**
     * DisplayShaders.EDGE_FRAGMENT, mirrored in Kotlin.
     * [pal] is the GB 4-shade palette (uPal0..3); ignored
     * unless params.snapGb.
     */
    private fun edgeFragment(
        frame: Array<Rgb>,
        w: Int,
        h: Int,
        u: Float,
        v: Float,
        params: EdgeParams,
        motion: Float,
        pal: Array<Rgb>,
    ): Rgb {
        val tx = 1f / w
        val ty = 1f / h
        val c0 = sample(frame, w, h, u, v)
        val cN = sample(frame, w, h, u, v + ty)
        val cS = sample(frame, w, h, u, v - ty)
        val cE = sample(frame, w, h, u + tx, v)
        val cW = sample(frame, w, h, u - tx, v)
        val cNE = sample(frame, w, h, u + tx, v + ty)
        val cNW = sample(frame, w, h, u - tx, v + ty)
        val cSE = sample(frame, w, h, u + tx, v - ty)
        val cSW = sample(frame, w, h, u - tx, v - ty)
        val tol = params.ditherTol
        val dDiag = c0.dist(cSE)
        val dE = c0.dist(cE)
        val dS = c0.dist(cS)
        val checker = step(dDiag * 3f + tol * 0.2f, dE + dS) * step(tol, dE) * step(tol, dS)
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
            step(0.03f, abs(lN - l0)) * step(0.03f, abs(lS - l0))
        val pattern = max(max(checker, max(stripeV, stripeH)), max(bayer2, bayer4))
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
            0.15f * max(abs(gx), abs(gy)) + 0.001f,
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
            var ar = mix(cW.r, cE.r, subX)
            var ag = mix(cW.g, cE.g, subX)
            var ab = mix(cW.b, cE.b, subX)
            if (params.subpixel) {
                ar = mix(cW.r, cE.r, clampF(subX + 0.3333f, 0f, 1f))
                ag = mix(cW.g, cE.g, subX)
                ab = mix(cW.b, cE.b, clampF(subX - 0.3333f, 0f, 1f))
            }
            along = Rgb(ar, ag, ab)
            wAlong = abs(subX - 0.5f) * 2f
            lo = Rgb(min(min(c0.r, cW.r), cE.r), min(min(c0.g, cW.g), cE.g), min(min(c0.b, cW.b), cE.b))
            hi = Rgb(max(max(c0.r, cW.r), cE.r), max(max(c0.g, cW.g), cE.g), max(max(c0.b, cW.b), cE.b))
        }
        val edge = smoothstep(params.edgeLo, params.edgeHi, edgeStrength)
        var wgt = edge * wAlong * (1f - pattern) * (1f - isolated) *
            (1f - text) * (1f - corner) * (1f - motion)
        wgt = clampF(wgt, 0f, params.blendMax)
        var col = Rgb(
            clampF(mix(c0.r, along.r, wgt), lo.r, hi.r),
            clampF(mix(c0.g, along.g, wgt), lo.g, hi.g),
            clampF(mix(c0.b, along.b, wgt), lo.b, hi.b),
        )
        val snapped: Rgb = if (params.snapGb) {
            var best = pal[0]
            var bestD = col.dist(pal[0])
            for (i in 1..3) {
                val d = col.dist(pal[i])
                if (d < bestD) {
                    best = pal[i]
                    bestD = d
                }
            }
            best
        } else {
            Rgb(
                floor(col.r * 31f + 0.5f) / 31f,
                floor(col.g * 31f + 0.5f) / 31f,
                floor(col.b * 31f + 0.5f) / 31f,
            )
        }
        if (wgt > 0.001f) col = snapped
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
        motion: Float = 0f,
        pal: Array<Rgb> = GB_PAL,
    ): Array<Rgb> {
        val out = Array(viewW * viewH) { Rgb(0f, 0f, 0f) }
        for (j in 0 until viewH) {
            val v = (j + 0.5f) / viewH
            for (i in 0 until viewW) {
                val u = (i + 0.5f) / viewW
                out[j * viewW + i] = edgeFragment(frame, w, h, u, v, params, motion, pal)
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
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA, 2.0f)
        val out = renderEdge(frame, w, h, 480, 320, params)
        val base = nearest(frame, w, h, 480, 320)
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
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA, 2.0f)
        val out = renderEdge(frame, w, h, w, h, params)
        val base = nearest(frame, w, h, w, h)
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
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA, 2.0f)
        val out = renderEdge(frame, w, h, 480, 320, params)
        val base = nearest(frame, w, h, 480, 320)
        assertEquals(0, changedPixels(out, base))
    }

    @Test
    fun gbDiagonalBlendsAndStaysInPalette() {
        // GB at 2x: the diagonal blends, and every output
        // pixel is one of the game's 4 shades.
        val w = 160
        val h = 144
        val frame = diagonalFrame(w, h, GB_PAL[0], GB_PAL[3])
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GB, 2.0f)
        val out = renderEdge(frame, w, h, 320, 288, params)
        val base = nearest(frame, w, h, 320, 288)
        val changed = changedPixels(out, base)
        assertTrue("a GB diagonal must blend, changed=$changed", changed > 50)
        for (px in out) {
            assertTrue("GB output must be a palette shade", GB_PAL.any { px.dist(it) <= 0.001f })
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
        val params = EdgeSmoothing.edgeParamsFor(GameSystem.GBA, 2.0f)
        val out = renderEdge(frame, w, h, 480, 320, params)
        val base = nearest(frame, w, h, 480, 320)
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
