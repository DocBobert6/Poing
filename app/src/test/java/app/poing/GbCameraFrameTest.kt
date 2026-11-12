package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 (item 10c) — the GB Camera frame conversion (pure
 * Kotlin): YUV_420_888 to XRGB8888 128x112, centre-cropped.
 * The camera2 plumbing itself (open, session, permission) needs
 * real hardware, so it is a device test for Robert — but the
 * conversion is the part that can be wrong silently, so it is
 * pinned here with synthetic frames.
 */
class GbCameraFrameTest {

    /** A flat YUV frame: every pixel the same Y, U, V. */
    private fun flatFrame(
        yValue: Int, uValue: Int, vValue: Int,
        width: Int = 176, height: Int = 144,
    ): Triple<ByteArray, ByteArray, ByteArray> {
        val y = ByteArray(width * height) { yValue.toByte() }
        val uvSize = (width / 2) * (height / 2)
        val u = ByteArray(uvSize) { uValue.toByte() }
        val v = ByteArray(uvSize) { vValue.toByte() }
        return Triple(y, u, v)
    }

    private fun convert(
        y: ByteArray, u: ByteArray, v: ByteArray,
        width: Int, height: Int,
        uvPixelStride: Int = 1,
    ): IntArray {
        val uvRowStride = (width / 2) * uvPixelStride
        return GbCameraFrame.convertYuvToXrgb(
            y, width, u, v, uvRowStride, uvPixelStride, width, height,
        )
    }

    @Test
    fun whiteFrameConvertsToWhite() {
        val (y, u, v) = flatFrame(255, 128, 128)
        val out = convert(y, u, v, 176, 144)
        assertEquals(128 * 112, out.size)
        assertTrue(out.all { it == 0xFFFFFFFF.toInt() })
    }

    @Test
    fun blackFrameConvertsToBlack() {
        val (y, u, v) = flatFrame(0, 128, 128)
        val out = convert(y, u, v, 176, 144)
        assertTrue(out.all { it == 0xFF000000.toInt() })
    }

    @Test
    fun greyFrameConvertsToGrey() {
        val (y, u, v) = flatFrame(128, 128, 128)
        val out = convert(y, u, v, 176, 144)
        assertTrue(out.all { it == 0xFF808080.toInt() })
    }

    @Test
    fun redYuvConvertsToMostlyRed() {
        // BT.601 red: Y=76, U=85, V=255.
        val (y, u, v) = flatFrame(76, 85, 255)
        val out = convert(y, u, v, 176, 144)
        val pixel = out[0]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        assertTrue("red channel $r", r > 200)
        assertTrue("green channel $g", g < 50)
        assertTrue("blue channel $b", b < 50)
    }

    @Test
    fun centreCropKeepsTheCentre() {
        // 176 wide: the left half black, the right half white.
        // The output is centre-cropped, so its left edge stays
        // black and its centre is white.
        val width = 176
        val height = 144
        val y = ByteArray(width * height) { i -> if ((i % width) < 88) 0.toByte() else 255.toByte() }
        val uvSize = (width / 2) * (height / 2)
        val u = ByteArray(uvSize) { 128 }
        val v = ByteArray(uvSize) { 128 }
        val out = convert(y, u, v, width, height)
        assertEquals(0xFF000000.toInt(), out[0])
        assertEquals(0xFFFFFFFF.toInt(), out[64])
        assertEquals(0xFFFFFFFF.toInt(), out[127])
    }

    @Test
    fun interleavedUvStridesStillConvert() {
        // U/V planes with pixelStride 2 (interleaved UV): the
        // colour must read the same as with stride 1.
        val width = 176
        val height = 144
        val (y, _, _) = flatFrame(76, 128, 128, width, height)
        val uvSize = (width / 2) * (height / 2) * 2
        val u = ByteArray(uvSize) { i -> if (i % 2 == 0) 85.toByte() else 0 }
        val v = ByteArray(uvSize) { i -> if (i % 2 == 0) 255.toByte() else 0 }
        val out = convert(y, u, v, width, height, uvPixelStride = 2)
        val pixel = out[0]
        val r = (pixel shr 16) and 0xFF
        assertTrue("red channel $r", r > 200)
    }

    @Test
    fun zeroSizeSourceYieldsABlackFrame() {
        val out = GbCameraFrame.convertYuvToXrgb(
            ByteArray(0), 0, ByteArray(0), ByteArray(0), 0, 1, 0, 0,
        )
        assertEquals(128 * 112, out.size)
        assertTrue(out.all { it == 0 })
    }

    @Test
    fun bridgeCameraHandlersForwardStartAndStop() {
        var started = false
        var stopped = false
        Bridge.onCameraStartHandler = { started = true; true }
        Bridge.onCameraStopHandler = { stopped = true }
        try {
            assertTrue(Bridge.onCameraStart())
            assertTrue(started)
            Bridge.onCameraStop()
            assertTrue(stopped)
        } finally {
            Bridge.onCameraStartHandler = null
            Bridge.onCameraStopHandler = null
        }
        assertEquals(false, Bridge.onCameraStart())
    }
}
