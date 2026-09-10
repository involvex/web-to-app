package com.webtoapp.core.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.webtoapp.data.model.NativeBridgeCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the NativeBridge screen-capture JPEG pipeline
 * (`captureScreen` / `startScreenCapture`).
 *
 * The capture path renders the WebView into a Bitmap, compresses it to JPEG
 * and ships base64 to JS. These tests pin the encode/decode round-trip:
 * valid JPEG bytes, NO_WRAP output, and quality clamping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeBridgeScreenCaptureTest {

    private fun noisyBitmap(size: Int = 64): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val r = (x * 4) % 256
                val g = (y * 4) % 256
                val b = ((x + y) * 2) % 256
                bitmap.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return bitmap
    }

    @Test
    fun `encode produces valid base64 JPEG without line wraps`() {
        val encoded = NativeBridge.encodeBitmapToBase64Jpeg(noisyBitmap(), 80)

        assertThat(encoded).isNotNull()
        assertThat(encoded!!).isNotEmpty()
        assertThat(encoded).doesNotContain("\n")

        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        // JPEG SOI marker.
        assertThat(bytes[0]).isEqualTo(0xFF.toByte())
        assertThat(bytes[1]).isEqualTo(0xD8.toByte())

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isEqualTo(64)
        assertThat(decoded.height).isEqualTo(64)
    }

    @Test
    fun `out of range quality is coerced instead of crashing`() {
        val bitmap = noisyBitmap()

        assertThat(NativeBridge.encodeBitmapToBase64Jpeg(bitmap, 150)).isNotNull()
        assertThat(NativeBridge.encodeBitmapToBase64Jpeg(bitmap, -10)).isNotNull()
    }

    @Test
    fun `higher quality yields larger output for non-trivial content`() {
        val bitmap = noisyBitmap()

        val low = NativeBridge.encodeBitmapToBase64Jpeg(bitmap, 10)!!
        val high = NativeBridge.encodeBitmapToBase64Jpeg(bitmap, 95)!!

        assertThat(high.length).isAtLeast(low.length)
    }

    // --- Device capture gating (MediaProjection) ---

    private fun bridgeWith(
        screenCapture: Boolean,
        context: Context = ApplicationProvider.getApplicationContext()
    ): NativeBridge {
        return NativeBridge(
            context = context,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            webViewProvider = { null },
            capabilities = NativeBridgeCapabilities(screenCapture = screenCapture)
        )
    }

    @Test
    fun `device capture is not granted before consent`() {
        assertThat(bridgeWith(screenCapture = true).isDeviceCaptureGranted()).isFalse()
    }

    @Test
    fun `device capture stays disabled when capability is off`() {
        val bridge = bridgeWith(screenCapture = false)

        assertThat(bridge.isDeviceCaptureGranted()).isFalse()
        // None of these may throw; with the capability off they are strict no-ops.
        bridge.requestDeviceCapture("cb")
        bridge.startDeviceCapture(70, "cb", 500)
        bridge.stopDeviceCapture()
        bridge.stopScreenCapture()
        bridge.release()
    }

    @Test
    fun `device capture without consent host fails gracefully`() {
        // Application context is not a ScreenCaptureConsentHost (same as the
        // floating-window service path): requests must no-op instead of crashing.
        val bridge = bridgeWith(screenCapture = true)

        bridge.requestDeviceCapture("cb")
        bridge.startDeviceCapture(70, "cb", 500)

        assertThat(bridge.isDeviceCaptureGranted()).isFalse()

        bridge.stopDeviceCapture()
        bridge.stopScreenCapture()
        bridge.release()
    }
}
