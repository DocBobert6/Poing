package app.poing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import androidx.core.content.ContextCompat

/**
 * Phase 6 (item 10c) — the GB Camera: the phone's camera feeding
 * the core's libretro camera interface. The Game Boy Camera game
 * asks the core for an image; the core calls the bridge's start,
 * the bridge upcalls here, and camera2 frames flow back as
 * XRGB8888 128x112 through nativePushCameraFrame. The core
 * centre-crops whatever arrives, so exactly 128x112 is pushed.
 *
 * The camera only ever opens while a game is playing (started
 * and stopped with the session, like the sensors and rumble).
 * It needs the CAMERA runtime permission: without it the start
 * asks once per game and returns false, and the game keeps its
 * white image (the core's camera buffer is 0xFF until the first
 * frame arrives). A denied permission is never re-asked in the
 * same game. No camera hardware, or an open failure, is the
 * same white image — never a crash.
 *
 * Frames are throttled to 10 a second: the GB Camera is a toy
 * that captures one still at a time, not video.
 */
class GbCamera(
    private val context: Context,
) {
    companion object {
        /** The GB Camera's frame: 128x112 (GBCAM_WIDTH/HEIGHT). */
        const val WIDTH = 128
        const val HEIGHT = 112

        /** Frames are pushed at most this often. */
        const val MIN_FRAME_INTERVAL_MS = 100L
    }

    /**
     * Called when the camera permission has to be asked for (once
     * per game); the answer comes back through [onPermissionResult].
     * Set by the owner (GameScreen wires the permission launcher
     * here). A no-op by default, so a camera without an owner
     * simply never asks.
     */
    var onRequestPermission: () -> Unit = {}

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var lastPushMillis = 0L
    private var permissionRequested = false

    /** True once the camera open is under way (or the camera is running). */
    var started = false
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Start the camera (called by the core when the game requests
     * an image). False when the camera cannot start yet — no
     * permission, no camera hardware, or the open failed. True
     * when the open is under way: the first frame arrives later,
     * and the game retries the capture until then.
     */
    fun start(): Boolean {
        if (started) return true
        if (!hasPermission()) {
            if (!permissionRequested) {
                permissionRequested = true
                onRequestPermission()
            }
            return false
        }
        if (thread != null) return true // already opening
        val id = pickCameraId() ?: return false
        val t = HandlerThread("poing-gb-camera").apply { start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    beginSession(camera, h)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    device = null
                    started = false
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    device = null
                    started = false
                }
            }, h)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the open.
            stopThread()
            return false
        } catch (_: Exception) {
            stopThread()
            return false
        }
        started = true
        return true
    }

    /** Stop the camera (a game quit, or the player leaving). */
    fun stop() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        try {
            device?.close()
        } catch (_: Exception) {
        }
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        session = null
        device = null
        reader = null
        stopThread()
        started = false
    }

    /** The permission answer: granted restarts the camera (the game retries the capture). */
    fun onPermissionResult(granted: Boolean) {
        if (granted) start()
    }

    /** The back camera, or the first camera when the phone has no back one. */
    private fun pickCameraId(): String? {
        val ids = try {
            cameraManager.cameraIdList
        } catch (_: Exception) {
            return null
        }
        for (id in ids) {
            val characteristics = try {
                cameraManager.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }
            if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
            ) {
                return id
            }
        }
        return ids.firstOrNull()
    }

    /** The smallest YUV_420_888 size the camera offers (the GB Camera needs 128x112). */
    private fun smallestYuvSize(camera: CameraDevice): Size? {
        val map = try {
            cameraManager.getCameraCharacteristics(camera.id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        } catch (_: Exception) {
            return null
        } ?: return null
        val sizes = map.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        return sizes.minByOrNull { it.width * it.height }
    }

    private fun beginSession(camera: CameraDevice, h: Handler) {
        val size = smallestYuvSize(camera) ?: run {
            stop()
            return
        }
        val imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.YUV_420_888, 2,
        )
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                pushFrame(image)
            } finally {
                image.close()
            }
        }, h)
        reader = imageReader
        try {
            camera.createCaptureSession(
                listOf(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        request.addTarget(imageReader.surface)
                        try {
                            s.setRepeatingRequest(request.build(), null, h)
                        } catch (_: Exception) {
                        }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                    }
                },
                h,
            )
        } catch (_: Exception) {
        }
    }

    private fun pushFrame(image: Image) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPushMillis < MIN_FRAME_INTERVAL_MS) return
        lastPushMillis = now
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val y = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.duplicate().get(it) }
        val u = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.duplicate().get(it) }
        val v = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.duplicate().get(it) }
        val pixels = GbCameraFrame.convertYuvToXrgb(
            y, yPlane.rowStride,
            u, v, uPlane.rowStride, uPlane.pixelStride,
            image.width, image.height,
        )
        if (!Bridge.ensureLoaded()) return
        try {
            Bridge.nativePushCameraFrame(pixels)
        } catch (_: UnsatisfiedLinkError) {
            // The native libraries are gone (the game quit while a
            // frame was in flight); the frame is dropped.
        }
    }

    private fun stopThread() {
        thread?.quitSafely()
        thread = null
        handler = null
    }
}

/**
 * The YUV_420_888 → XRGB8888 conversion, centre-cropped to the GB
 * Camera's 128x112. Pure Kotlin — no Android APIs — so it runs
 * under plain JVM unit tests (GbCameraFrameTest).
 */
object GbCameraFrame {
    const val WIDTH = 128
    const val HEIGHT = 112

    /**
     * Convert one YUV_420_888 frame to XRGB8888 [WIDTH]x[HEIGHT].
     * The source is scaled to cover the target and the centre
     * window is sampled (nearest neighbour); colour is BT.601.
     * Out-of-range indices are clamped, so an odd stride can
     * never crash the conversion. A zero-size source yields a
     * black frame.
     */
    fun convertYuvToXrgb(
        y: ByteArray, yRowStride: Int,
        u: ByteArray, v: ByteArray, uvRowStride: Int, uvPixelStride: Int,
        srcWidth: Int, srcHeight: Int,
    ): IntArray {
        val out = IntArray(WIDTH * HEIGHT)
        if (srcWidth <= 0 || srcHeight <= 0 || y.isEmpty() || u.isEmpty() || v.isEmpty()) {
            return out
        }
        // Scale to cover the target, then sample the centre window.
        val scale = maxOf(WIDTH.toFloat() / srcWidth, HEIGHT.toFloat() / srcHeight)
        val sampleW = WIDTH / scale
        val sampleH = HEIGHT / scale
        val x0 = (srcWidth - sampleW) / 2f
        val y0 = (srcHeight - sampleH) / 2f
        for (oy in 0 until HEIGHT) {
            val sy = (y0 + (oy + 0.5f) / scale).toInt().coerceIn(0, srcHeight - 1)
            val uvRow = (sy / 2) * uvRowStride
            val yRow = sy * yRowStride
            for (ox in 0 until WIDTH) {
                val sx = (x0 + (ox + 0.5f) / scale).toInt().coerceIn(0, srcWidth - 1)
                val yy = y[(yRow + sx).coerceIn(0, y.size - 1)].toInt() and 0xFF
                val uvIndex = uvRow + (sx / 2) * uvPixelStride
                val uu = u[uvIndex.coerceIn(0, u.size - 1)].toInt() and 0xFF
                val vv = v[uvIndex.coerceIn(0, v.size - 1)].toInt() and 0xFF
                // BT.601 in integer math (1.402, 0.344, 0.714,
                // 1.772 scaled by 256).
                var r = yy + ((359 * (vv - 128)) shr 8)
                var g = yy - ((88 * (uu - 128) + 183 * (vv - 128)) shr 8)
                var b = yy + ((454 * (uu - 128)) shr 8)
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                out[oy * WIDTH + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }
}
