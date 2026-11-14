package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Task 6 (2026-11-14) — DLSS-in-spirit reconstruction,
 * GBC colour reconstruction: the render-level regression
 * test, in the shape of EdgeRenderGeometryTest.
 *
 * The numeric replica (sim8.py,
 * ~/workspace/poing/verify/edge-rework/sim8.py, 42/42)
 * proves the design on synthetic GBC frames + the real
 * TD6 GBC frames (no Gold / Silver device frames exist,
 * so the sim proof is synthetic + TD6, stated plainly).
 * This test pins the Kotlin mirror of the sim's Pass B
 * (the line reconstruction) to the sim's numbers on the
 * one geometry case that matters most: the synthetic
 * 45-degree diagonal (sim R1 / R2). The mirror below is
 * the scalar Pass B (recon_fragment_from_passA in the
 * sim), evaluated over the renderer's ACTUAL fragment
 * positions — display pixel (i, j) at vTex
 * ((i+0.5)/viewW, (j+0.5)/viewH) — at 5x, the scale the
 * sim proves at.
 *
 * The Pass A input is built analytically, not by a full
 * second replica of both passes (the sim is the numeric
 * proof; the Kotlin tests pin the wiring + this one
 * geometry case): on a clean two-colour diagonal the
 * classification is exactly what the sim's R12 measures
 * — 94 edge texels, 0 dither / text / 1px — so Pass A
 * here is the corrected colours plus the edge class from
 * the 3x3 Sobel (edgeStrength > 0.05), with the 2-texel
 * border zeroed as the sim's Pass A is. The class count
 * itself is asserted (94), so a drift in the analytic
 * Pass A fails here first.
 *
 * Plain JVM test (no Robolectric): pure Kotlin math, the
 * same as EdgeRenderGeometryTest.
 */
class ReconGeometryTest {

    private data class Rgb(val r: Float, val g: Float, val b: Float) {
        fun dist(o: Rgb): Float {
            val dr = r - o.r
            val dg = g - o.g
            val db = b - o.b
            return sqrt(dr * dr + dg * dg + db * db)
        }

        fun luma(): Float = 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** One Pass A texel: the (collapsed / corrected) colour and the class (0 flat, 1 edge, 2 dither, 3 text, 4 1px). */
    private data class Texel(val rgb: Rgb, val cls: Int)

    private fun step(edge: Float, x: Float): Float = if (x >= edge) 1f else 0f

    private fun clampF(x: Float, lo: Float, hi: Float): Float = max(lo, min(hi, x))

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clampF((x - e0) / (e1 - e0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun median(values: FloatArray): Float {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) * 0.5f
    }

    /** The 5x5 Sobel kernel weights (the sim's w / k). */
    private val w5 = floatArrayOf(1f, 4f, 6f, 4f, 1f)
    private val k5 = floatArrayOf(-2f, -1f, 0f, 1f, 2f)

    /**
     * The colour-fold, mode 2 only (GBC / Prism): the
     * SameBoy response ramp (LUT row 0) then the mGBA
     * matrix in linear light (x0.94). Mirrors
     * RECON_A1_FRAGMENT's colourCorrect for mode 2 (and
     * EdgeRenderGeometryTest's mirror of it); the LUT
     * values come from DisplayShaders itself, so a LUT
     * change moves both.
     */
    private fun correctGbc(c: Rgb): Rgb {
        val lut = DisplayShaders.GAMMA_LUT_GBC
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

    /** The sim's synthetic 45-degree diagonal, 32x32: dark where y > x + 4, light otherwise. */
    private fun diag45Frame(w: Int = 32, h: Int = 32): Array<Rgb> {
        val light = Rgb(0.85f, 0.85f, 0.80f)
        val dark = Rgb(0.15f, 0.15f, 0.35f)
        return Array(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (y > x + 4) dark else light
        }
    }

    /**
     * The analytic Pass A for a clean diagonal: the
     * corrected colours, and the edge class from the 3x3
     * Sobel on the corrected luma (edgeStrength > 0.05,
     * the GBC edgeLo), with the 2-texel border zeroed as
     * the sim's Pass A is. No dither / text / 1px exists
     * on this frame (the sim's R12: 94 edge, 0 of each
     * other class), and no collapse runs (no pattern).
     */
    private fun passA(frame: Array<Rgb>, w: Int, h: Int): Array<Texel> {
        val corrected = Array(w * h) { correctGbc(frame[it]) }
        val out = Array(w * h) { Texel(corrected[it], 0) }
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                fun l(dx: Int, dy: Int): Float = corrected[(y + dy) * w + (x + dx)].luma()
                val gx = (l(1, 0) + l(1, -1) + l(1, 1)) - (l(-1, 0) + l(-1, -1) + l(-1, 1))
                val gy = (l(0, 1) + l(1, 1) + l(-1, 1)) - (l(0, -1) + l(1, -1) + l(-1, -1))
                if (abs(gx) + abs(gy) > 0.05f) {
                    out[y * w + x] = Texel(corrected[y * w + x], 1)
                }
            }
        }
        return out
    }

    /**
     * The 5x5 Sobel's edge angle (degrees, 0..180) at
     * texel (x, y) of a luma array — the sim's
     * _sobel5_angle, same convention (gx = E - W,
     * gy = S - N, image coords, y down; the edge
     * direction is atan2(-gx, gy) mod 180).
     */
    private fun sobel5Angle(luma: FloatArray, w: Int, h: Int, x: Int, y: Int): Float {
        fun l(dx: Int, dy: Int): Float =
            luma[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
        var gx = 0f
        var gy = 0f
        for (dy in -2..2) {
            for (dx in -2..2) {
                gx += w5[dy + 2] * k5[dx + 2] * l(dx, dy)
                gy += k5[dy + 2] * w5[dx + 2] * l(dx, dy)
            }
        }
        val ang = Math.toDegrees(atan2(-gx.toDouble(), gy.toDouble())) % 180.0
        return if (ang < 0) (ang + 180.0).toFloat() else ang.toFloat()
    }

    /**
     * The scalar Pass B (v3), mirroring the sim's
     * recon_fragment_from_passA exactly: the 5x5 Sobel
     * gradient gives the edge angle; the side colours come
     * from the profile ends (t = +-2 along the normal);
     * the offset comes from a 2D interval fit over the
     * 5x5 window; the output is a 1-display-px transition
     * centred on the line. Only edge-class texels are
     * reconstructed; the 15-bit snap follows the edge
     * pass's rule (snap only when the colour actually
     * moved, including a dither texel).
     */
    private fun reconFragment(
        passA: Array<Texel>,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
        sx: Float,
        sy: Float,
        displayScale: Float,
    ): Rgb {
        fun a(dx: Int, dy: Int): Texel {
            val xx = (x + dx).coerceIn(0, w - 1)
            val yy = (y + dy).coerceIn(0, h - 1)
            return passA[yy * w + xx]
        }
        val c0 = a(0, 0)
        val cls0 = c0.cls
        var gx = 0f
        var gy = 0f
        var cNW = Rgb(0f, 0f, 0f)
        var cNE = Rgb(0f, 0f, 0f)
        var cSE = Rgb(0f, 0f, 0f)
        var cSW = Rgb(0f, 0f, 0f)
        for (dy in -2..2) {
            for (dx in -2..2) {
                val s = a(dx, dy)
                val l = s.rgb.luma()
                gx += w5[dy + 2] * k5[dx + 2] * l
                gy += k5[dy + 2] * w5[dx + 2] * l
                if (dx == -1 && dy == -1) cNW = s.rgb
                if (dx == 1 && dy == -1) cNE = s.rgb
                if (dx == 1 && dy == 1) cSE = s.rgb
                if (dx == -1 && dy == 1) cSW = s.rgb
            }
        }
        val gn = sqrt(gx * gx + gy * gy) + 1e-9f
        val nx = gx / gn
        val ny = gy / gn
        val corner = step(cNW.dist(cSE), 0.1f) * step(cNE.dist(cSW), 0.1f) *
            step(0.05f, abs(gx) + abs(gy))
        fun profileEnd(t: Float): Rgb {
            val dx = floor(t * nx + 0.5f).toInt()
            val dy = floor(t * ny + 0.5f).toInt()
            return a(dx, dy).rgb
        }
        val cA = profileEnd(-2f)
        val cB = profileEnd(2f)
        val lA = cA.luma()
        val lB = cB.luma()
        var dLow = -100f
        var dHigh = 100f
        for (dy in -2..2) {
            for (dx in -2..2) {
                val s = a(dx, dy)
                val sv = nx * dx + ny * dy
                val dA = s.rgb.dist(cA)
                val dB = s.rgb.dist(cB)
                val okCls = s.cls != 2 && s.cls != 3 && s.cls != 4
                if (dA < 0.15f && dA < dB && okCls) dLow = max(dLow, sv)
                if (dB < 0.15f && dB < dA && okCls) dHigh = min(dHigh, sv)
            }
        }
        val okInterval = dLow > -50f && dHigh < 50f && dLow < dHigh
        val d = if (okInterval) 0.5f * (dLow + dHigh) else 0f
        var out = c0.rgb
        if (cls0 == 1 && okInterval && abs(lB - lA) >= 0.1f &&
            (abs(gx) + abs(gy)) >= 0.5f && corner < 0.5f
        ) {
            val sTex = (sx - 0.5f) * nx + (sy - 0.5f) * ny - d
            val sPx = sTex * displayScale
            val mixw = smoothstep(-0.5f, 0.5f, sPx)
            out = Rgb(
                cA.r + (cB.r - cA.r) * mixw,
                cA.g + (cB.g - cA.g) * mixw,
                cA.b + (cB.b - cA.b) * mixw,
            )
        }
        val snapped = Rgb(
            floor(out.r * 31f + 0.5f) / 31f,
            floor(out.g * 31f + 0.5f) / 31f,
            floor(out.b * 31f + 0.5f) / 31f,
        )
        val moved = max(
            if (out.dist(c0.rgb) > 0.001f) 1f else 0f,
            if (cls0 == 2) 1f else 0f,
        )
        return Rgb(
            mix(c0.rgb.r, snapped.r, moved),
            mix(c0.rgb.g, snapped.g, moved),
            mix(c0.rgb.b, snapped.b, moved),
        )
    }

    /** Pass B at the renderer's real fragment geometry: display pixel (i, j) at vTex ((i+0.5)/viewW, (j+0.5)/viewH). */
    private fun renderRecon(
        passA: Array<Texel>,
        w: Int,
        h: Int,
        viewW: Int,
        viewH: Int,
    ): Array<Rgb> {
        val out = Array(viewW * viewH) { Rgb(0f, 0f, 0f) }
        val scale = min(viewW.toFloat() / w, viewH.toFloat() / h)
        for (j in 0 until viewH) {
            val v = (j + 0.5f) / viewH
            for (i in 0 until viewW) {
                val u = (i + 0.5f) / viewW
                val fx = u * w
                val fy = v * h
                val x = floor(fx).toInt().coerceIn(0, w - 1)
                val y = floor(fy).toInt().coerceIn(0, h - 1)
                out[j * viewW + i] = reconFragment(
                    passA, w, h, x, y, fx - floor(fx), fy - floor(fy), scale,
                )
            }
        }
        return out
    }

    /** The colour-corrected nearest base (the sim's nearest_corrected_np). */
    private fun nearestCorrected(
        frame: Array<Rgb>,
        w: Int,
        h: Int,
        viewW: Int,
        viewH: Int,
    ): Array<Rgb> {
        val out = Array(viewW * viewH) { Rgb(0f, 0f, 0f) }
        for (j in 0 until viewH) {
            val y = min(h - 1, ((j + 0.5f) / viewH * h).toInt())
            for (i in 0 until viewW) {
                val x = min(w - 1, ((i + 0.5f) / viewW * w).toInt())
                out[j * viewW + i] = correctGbc(frame[y * w + x])
            }
        }
        return out
    }

    /**
     * The display-x where the row's luma crosses the
     * midpoint between its left and right levels (linear
     * interpolation) — the sim's _step_pos. Null when the
     * row does not step here.
     */
    private fun stepPos(row: FloatArray, lo: Int, hi: Int): Float? {
        val seg = row.copyOfRange(lo, hi)
        if (seg.size < 4) return null
        val a = median(seg.copyOfRange(0, 4))
        val b = median(seg.copyOfRange(seg.size - 4, seg.size))
        if (abs(b - a) < 0.2f) return null
        val mid = 0.5f * (a + b)
        for (i in 0 until seg.size - 1) {
            if ((seg[i] - mid) * (seg[i + 1] - mid) <= 0f && seg[i + 1] != seg[i]) {
                return lo + i + (mid - seg[i]) / (seg[i + 1] - seg[i])
            }
        }
        return null
    }

    private data class Straight(val maxResid: Float, val maxOffset: Float, val n: Int)

    /**
     * Step-position straightness vs the ideal line
     * x_texel = y_texel - 4 — the sim's _straightness, for
     * one render. The bar is the RESIDUAL from the
     * best-fit straight line through the measured step
     * positions (a straightened line has ~0 residual even
     * when it sits a constant offset from the ideal line;
     * that offset is an information limit, not a failure —
     * a staircase determines its intercept only to +-0.5
     * texels — so the offset is reported separately).
     */
    private fun straightness(
        arr: Array<Rgb>,
        viewW: Int,
        viewH: Int,
        w: Int,
        h: Int,
        rows: IntProgression,
    ): Straight? {
        val scale = viewW.toFloat() / w
        val pts = mutableListOf<Pair<Float, Float>>()
        val ideals = mutableListOf<Float>()
        for (r in rows) {
            val yc = (r + 0.5f) * h / viewH
            val xIdeal = (yc - 4f) * scale
            if (xIdeal < 12f || xIdeal > viewW - 12) continue
            val lo = max(0, xIdeal.toInt() - 25)
            val hi = min(viewW, xIdeal.toInt() + 26)
            val rowLuma = FloatArray(viewW) { i -> arr[r * viewW + i].luma() }
            val pos = stepPos(rowLuma, lo, hi) ?: continue
            pts.add(r.toFloat() to pos)
            ideals.add(xIdeal)
        }
        if (pts.size < 4) return null
        val n = pts.size.toFloat()
        var sr = 0f
        var sp = 0f
        var srp = 0f
        var srr = 0f
        for ((r, p) in pts) {
            sr += r
            sp += p
            srp += r * p
            srr += r * r
        }
        val a = (n * srp - sr * sp) / (n * srr - sr * sr)
        val b = (sp - a * sr) / n
        var maxResid = 0f
        var maxOff = 0f
        for (k in pts.indices) {
            val (r, p) = pts[k]
            maxResid = max(maxResid, abs(p - (a * r + b)))
            maxOff = max(maxOff, abs((a * r + b) - ideals[k]))
        }
        return Straight(maxResid, maxOff, pts.size)
    }

    /**
     * The step's two levels on a display line: medians
     * 6–12px either side of the crossing — the sim's
     * _local_levels, on the BASELINE line (the nearest
     * render is unblended, so its levels are the texel
     * colours).
     */
    private fun localLevels(row: FloatArray, x0: Int): Pair<Float, Float>? {
        val n = row.size
        val a = median(row.copyOfRange(max(0, x0 - 12), max(1, x0 - 6)))
        val b = median(row.copyOfRange(min(n, x0 + 7), min(n, x0 + 13)))
        if (abs(b - a) < 0.2f) return null
        return a to b
    }

    /** The 10–90% transition width of the step at x0, in display px — the sim's _local_width. */
    private fun localWidth(row: FloatArray, x0: Int, levels: Pair<Float, Float>): Int {
        val (a, b) = levels
        val t10 = a + 0.1f * (b - a)
        val t90 = a + 0.9f * (b - a)
        val lo = max(0, x0 - 14)
        val hi = min(row.size, x0 + 14)
        var first = -1
        var last = -1
        for (i in lo until hi) {
            if ((row[i] - t10) * (row[i] - t90) <= 0f) {
                if (first < 0) first = i
                last = i
            }
        }
        return if (first < 0) 0 else last - first
    }

    @Test
    fun diag45PassAHas94EdgeTexelsAndNoOtherClass() {
        // Sim R12: the analytic Pass A must match the
        // sim's classification exactly — 94 edge texels,
        // 0 dither / text / 1px — or the geometry tests
        // below are measuring a different input.
        val frame = diag45Frame()
        val pa = passA(frame, 32, 32)
        assertEquals(94, pa.count { it.cls == 1 })
        assertEquals(0, pa.count { it.cls == 2 })
        assertEquals(0, pa.count { it.cls == 3 })
        assertEquals(0, pa.count { it.cls == 4 })
    }

    @Test
    fun diag45SobelAngleReads45Degrees() {
        // Sim R-grad: the 5x5 Sobel reads the 45-degree
        // line at 0.0 degrees of error (the 3x3 Sobel is
        // too local for shallow staircases — it reads
        // 18.4 degrees for a 26.6 degree line — which is
        // why Pass B uses the 5x5).
        val frame = diag45Frame()
        val pa = passA(frame, 32, 32)
        val luma = FloatArray(32 * 32) { pa[it].rgb.luma() }
        for ((x, y) in listOf(12 to 16, 16 to 20, 8 to 12, 20 to 24)) {
            val ang = sobel5Angle(luma, 32, 32, x, y)
            assertTrue("Sobel angle at ($x,$y) must read 45deg, got $ang", abs(ang - 45f) <= 5f)
        }
    }

    @Test
    fun diag45ReconIsStraight() {
        // Sim R1, at 5x (160x160), rows 35..133 step 2:
        // recon max residual 0.00px (bar <= 1.0), base
        // 2.11px (bar >= 2.0), recon offset 3.01px vs base
        // 3.12px (bar: no further than nearest + 1px).
        val frame = diag45Frame()
        val pa = passA(frame, 32, 32)
        val recon = renderRecon(pa, 32, 32, 160, 160)
        val base = nearestCorrected(frame, 32, 32, 160, 160)
        val rows = 35 until 135 step 2
        val sRecon = straightness(recon, 160, 160, 32, 32, rows)
        val sBase = straightness(base, 160, 160, 32, 32, rows)
        assertTrue("recon must straighten the diagonal", sRecon != null)
        assertTrue("base must zigzag", sBase != null)
        assertTrue(
            "recon max residual must be <= 1.0px, got ${sRecon!!.maxResid} (n=${sRecon.n})",
            sRecon.maxResid <= 1.0f,
        )
        assertTrue(
            "base max residual must be >= 2.0px, got ${sBase!!.maxResid} (n=${sBase.n})",
            sBase.maxResid >= 2.0f,
        )
        assertTrue(
            "recon must not shift the line further than nearest + 1px, recon off=${sRecon.maxOffset} base off=${sBase.maxOffset}",
            sRecon.maxOffset <= sBase.maxOffset + 1.0f,
        )
    }

    @Test
    fun diag45ReconStepIsSharp() {
        // Sim R2, rows 60 / 80 / 100: recon widths [0, 0,
        // 0] (bar <= 2px on every row) — the line is
        // reconstructed, not smeared (the edge pass smears
        // the same step to 7px). The base is a hard step
        // (width 0), the control that the measurement
        // itself is sound.
        val frame = diag45Frame()
        val pa = passA(frame, 32, 32)
        val recon = renderRecon(pa, 32, 32, 160, 160)
        val base = nearestCorrected(frame, 32, 32, 160, 160)
        for (r in listOf(60, 80, 100)) {
            val x0 = (((r + 0.5f) / 5f - 4f) * 5f).toInt()
            val baseRow = FloatArray(160) { i -> base[r * 160 + i].luma() }
            val reconRow = FloatArray(160) { i -> recon[r * 160 + i].luma() }
            val levels = localLevels(baseRow, x0)
            assertTrue("row $r must step", levels != null)
            assertEquals("base row $r must be a hard step", 0, localWidth(baseRow, x0, levels!!))
            val wRecon = localWidth(reconRow, x0, levels)
            assertTrue("recon row $r must stay sharp (width <= 2px), got $wRecon", wRecon <= 2)
        }
    }

    @Test
    fun flatFrameIsUnchanged() {
        // Sim R9: a flat frame comes out pixel-identical —
        // the reconstruction must not soften the picture
        // anywhere.
        val w = 32
        val h = 32
        val flat = Rgb(0.85f, 0.85f, 0.80f)
        val frame = Array(w * h) { flat }
        val pa = passA(frame, w, h)
        assertTrue("a flat frame has no edge texels", pa.none { it.cls != 0 })
        val recon = renderRecon(pa, w, h, 160, 160)
        val base = nearestCorrected(frame, w, h, 160, 160)
        var changed = 0
        for (i in recon.indices) {
            if (recon[i].dist(base[i]) > 0.001f) changed++
        }
        assertEquals("a flat frame must come out pixel-identical", 0, changed)
    }
}
