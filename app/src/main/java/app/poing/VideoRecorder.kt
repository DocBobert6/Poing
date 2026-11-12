package app.poing

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import java.io.FileDescriptor

/**
 * Phase 6 (item 11) — video recording: the game's frames into an
 * H.264 MP4 (MediaCodec + MediaMuxer). Video only, no audio —
 * the audio thread drains the bridge's ring buffer into
 * AudioTrack, and there is no second drain to feed an encoder
 * without changing the bridge's consume-on-read ring; audio
 * capture is a follow-up, not this item.
 *
 * The encoder takes a Surface: each displayed frame's Bitmap is
 * drawn onto it with a Canvas (the draw runs on the emu thread,
 * right after the frame is copied — a 240x160 draw is well under
 * a millisecond, inside the frame budget). Presentation times
 * come from the frame capture clock (monotonic), so the MP4
 * plays at the game's own pace, including fast-forward and slow
 * motion. The muxer writes to the FileDescriptor the caller
 * supplies (a MediaStore entry — see CaptureStore).
 *
 * The encoder is configured at the game's native size (160x144
 * or 240x160 — both even, as H.264 requires). Bitrate 2 Mbps,
 * 60 fps declared (the real pace comes from the presentation
 * times), I-frame every 2 seconds.
 */
class VideoRecorder {
    companion object {
        const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        const val BITRATE = 2_000_000
        const val FRAME_RATE = 60
        const val I_FRAME_INTERVAL_SECONDS = 2
    }

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var startNanos = 0L

    /**
     * Frames actually drawn onto the encoder surface in this
     * recording (0 when the recording started while paused and
     * never saw a frame). stop() reports false for a 0-frame
     * recording so the caller can drop the empty file instead of
     * presenting it as saved. Read after stop() — releaseAll()
     * deliberately does not reset it; the next start() does.
     */
    var framesRecorded = 0
        private set

    /**
     * The first recorded frame's clock: presentation times are
     * relative to it, not to start() — a recording can start
     * while the game is paused (from the pause menu), and the
     * paused time must not become a gap at the head of the file.
     */
    private var firstFrameNanos = 0L

    /** True while a recording is running. */
    val isRecording: Boolean get() = codec != null

    /** Seconds since the recording started (0 when not recording). */
    fun elapsedSeconds(): Long {
        val start = startNanos
        if (start == 0L) return 0
        return (System.nanoTime() - start) / 1_000_000_000L
    }

    /**
     * Start recording into [outputFd] at [width]x[height]. False
     * (and nothing started) when already recording, the encoder
     * cannot be created, or the device is below API 26 — the
     * MediaMuxer(FileDescriptor) constructor needs API 26, and
     * on API 24–25 it would throw NoSuchMethodError (an Error,
     * not an Exception) instead of failing cleanly. The caller
     * owns the FileDescriptor.
     */
    fun start(outputFd: FileDescriptor, width: Int, height: Int): Boolean {
        if (codec != null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) return false
        return try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
            }
            val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            val mux = MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            encoder.start()
            codec = encoder
            muxer = mux
            inputSurface = surface
            trackIndex = -1
            muxerStarted = false
            startNanos = System.nanoTime()
            firstFrameNanos = 0L
            framesRecorded = 0
            true
        } catch (_: Exception) {
            releaseAll()
            false
        }
    }

    /**
     * Record one frame. The [bitmap] is drawn onto the encoder's
     * input surface; the presentation time is measured from the
     * first recorded frame, so the MP4 plays at the game's own
     * pace, including fast-forward and slow motion. A no-op when
     * not recording. Drawing happens on the caller's thread (the
     * emu thread); the encode itself is asynchronous.
     */
    fun recordFrame(bitmap: Bitmap) {
        val surface = inputSurface ?: return
        val now = System.nanoTime()
        if (firstFrameNanos == 0L) firstFrameNanos = now
        val presentationTimeUs = (now - firstFrameNanos) / 1000
        val canvas = try {
            surface.lockCanvas(null)
        } catch (_: Exception) {
            return
        }
        try {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
        framesRecorded++
        drainOutput(presentationTimeUs)
    }

    /**
     * Stop recording and finalize the MP4. False when nothing was
     * recording, and false when no frame was ever recorded — a
     * recording started while paused and stopped before resume
     * would otherwise land as an empty MP4 presented as saved.
     * The muxer is stopped (which finalizes the file) and
     * everything is released; the caller closes the
     * FileDescriptor (and deletes the entry when this returns
     * false for a 0-frame recording).
     */
    fun stop(): Boolean {
        val encoder = codec ?: return false
        if (framesRecorded == 0) {
            releaseAll()
            return false
        }
        return try {
            try {
                encoder.signalEndOfInputStream()
            } catch (_: Exception) {
            }
            drainOutput(Long.MAX_VALUE)
            true
        } finally {
            releaseAll()
        }
    }

    /** Drain the encoder's output into the muxer (non-blocking, except the final drain). */
    private fun drainOutput(untilPtsUs: Long) {
        val encoder = codec ?: return
        val mux = muxer ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = try {
                encoder.dequeueOutputBuffer(info, 0)
            } catch (_: Exception) {
                break
            }
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) break
                    trackIndex = mux.addTrack(encoder.outputFormat)
                    mux.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    if (muxerStarted && info.size > 0) {
                        val buffer = encoder.getOutputBuffer(index)
                        if (buffer != null) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            try {
                                mux.writeSampleData(trackIndex, buffer, info)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    if (untilPtsUs != Long.MAX_VALUE && info.presentationTimeUs >= untilPtsUs) return
                }
                else -> return
            }
        }
    }

    private fun releaseAll() {
        try {
            inputSurface?.release()
        } catch (_: Exception) {
        }
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) {
        }
        try {
            muxer?.release()
        } catch (_: Exception) {
        }
        inputSurface = null
        codec = null
        muxer = null
        trackIndex = -1
        muxerStarted = false
        startNanos = 0L
        firstFrameNanos = 0L
    }
}
