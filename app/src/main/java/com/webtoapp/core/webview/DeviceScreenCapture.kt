package com.webtoapp.core.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.webtoapp.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * True device screen capture via MediaProjection (Option A).
 *
 * Owns the projection session, a mirroring VirtualDisplay backed by an
 * ImageReader, and a polling frame loop that hands base64 JPEG frames to
 * [onFrame]. The one-time user consent dialog is launched by the host
 * Activity (see [ScreenCaptureConsentHost]); this engine only consumes the
 * consent result via [onConsentResult].
 *
 * The grant survives [stop], so capture can be restarted without asking the
 * user again. [release] drops the grant and must be called when the host
 * Activity is destroyed — a MediaProjection is a Binder-held system resource
 * and is NOT freed by garbage collection.
 *
 * No manifest permission is required: capture runs from a foreground Activity,
 * not a service (API 34's FOREGROUND_SERVICE_MEDIA_PROJECTION only applies to
 * service-based capture).
 */
class DeviceScreenCapture(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null

    /** Invoked on the main thread when the system revokes the projection. */
    var onRevoked: (() -> Unit)? = null

    val isGranted: Boolean get() = mediaProjection != null
    val isCapturing: Boolean get() = captureJob?.isActive == true

    fun consentIntent(): Intent? {
        return try {
            val mgr = context.getSystemService(MediaProjectionManager::class.java)
                ?: return null
            mgr.createScreenCaptureIntent()
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to create screen capture intent", e)
            null
        }
    }

    /**
     * Consumes the consent Activity result. Returns true when a projection
     * session is now held.
     */
    fun onConsentResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            AppLogger.d("DeviceScreenCapture", "Screen capture consent denied/cancelled")
            return false
        }
        return try {
            val mgr = context.getSystemService(MediaProjectionManager::class.java)
                ?: return false
            stopPipeline()
            val projection = mgr.getMediaProjection(resultCode, data) ?: return false
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    scope.launch(Dispatchers.Main) {
                        stopPipeline()
                        try {
                            onRevoked?.invoke()
                        } catch (e: Exception) {
                            AppLogger.e("DeviceScreenCapture", "onRevoked failed", e)
                        }
                    }
                }
            }
            projection.registerCallback(callback, null)
            projectionCallback = callback
            mediaProjection = projection
            AppLogger.d("DeviceScreenCapture", "MediaProjection session acquired")
            true
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to obtain MediaProjection", e)
            false
        }
    }

    /**
     * Starts streaming base64 JPEG frames to [onFrame] every [intervalMs].
     * Requires a grant ([onConsentResult] == true). Returns false when there
     * is no grant or the pipeline failed to start.
     */
    fun start(quality: Int, intervalMs: Long, onFrame: (String) -> Unit): Boolean {
        val projection = mediaProjection ?: return false
        if (captureJob?.isActive == true) return true
        val q = quality.coerceIn(0, 100)
        val interval = intervalMs.coerceAtLeast(100)
        return try {
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            if (width <= 0 || height <= 0) return false
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay = projection.createVirtualDisplay(
                "wta-device-capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )
            AppLogger.d(
                "DeviceScreenCapture",
                "Capture started: ${width}x$height quality=$q interval=${interval}ms"
            )
            captureJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val bitmap = acquireFrame()
                    val encoded = bitmap?.let {
                        NativeBridge.encodeBitmapToBase64Jpeg(it, q)
                    }
                    bitmap?.recycle()
                    if (encoded != null) {
                        try {
                            onFrame(encoded)
                        } catch (e: Exception) {
                            AppLogger.e("DeviceScreenCapture", "onFrame failed", e)
                        }
                    }
                    delay(interval)
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to start capture", e)
            stopPipeline()
            false
        }
    }

    /** Stops streaming but keeps the grant so capture can restart silently. */
    fun stop() {
        stopPipeline()
    }

    /** Stops streaming and drops the projection grant. */
    fun release() {
        stopPipeline()
        try {
            projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to unregister projection callback", e)
        }
        projectionCallback = null
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to stop MediaProjection", e)
        }
        mediaProjection = null
        onRevoked = null
    }

    private fun stopPipeline() {
        captureJob?.cancel()
        captureJob = null
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to release VirtualDisplay", e)
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Failed to close ImageReader", e)
        }
        imageReader = null
    }

    private fun acquireFrame(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "acquireLatestImage failed", e)
            null
        } ?: return null
        try {
            if (image.width <= 0 || image.height <= 0) return null
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            if (pixelStride <= 0) return null
            // Row stride is padded to hardware alignment; copy the padded rows
            // then crop back to the content size.
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(
                paddedWidth, image.height, Bitmap.Config.ARGB_8888
            )
            padded.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
            padded.recycle()
            return cropped
        } catch (e: Exception) {
            AppLogger.e("DeviceScreenCapture", "Frame conversion failed", e)
            return null
        } finally {
            try {
                image.close()
            } catch (e: Exception) {
                AppLogger.e("DeviceScreenCapture", "Failed to close Image", e)
            }
        }
    }
}
