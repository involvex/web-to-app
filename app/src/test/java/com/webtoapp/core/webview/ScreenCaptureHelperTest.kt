package com.webtoapp.core.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the ScreenCaptureHelper injection script contract: the
 * `window.WtaScreenCapture` surface web content programs against.
 */
class ScreenCaptureHelperTest {

    private val script: String = ScreenCaptureHelper.getInjectionScript()

    @Test
    fun `script installs WtaScreenCapture exactly once`() {
        assertThat(script).contains("window.WtaScreenCapture")
        assertThat(script).contains("window.__webtoapp_screencapture_helper__")
    }

    @Test
    fun `script exposes the full promise-based surface`() {
        assertThat(script).contains("getDisplayMedia")
        assertThat(script).contains("requestDeviceAccess")
        assertThat(script).contains("capture:")
        assertThat(script).contains("stopStream")
        assertThat(script).contains("stopAll")
        assertThat(script).contains("isSupported")
        assertThat(script).contains("isDeviceGranted")
        assertThat(script).contains("toDataUrl")
    }

    @Test
    fun `getDisplayMedia yields a real MediaStream via canvas captureStream`() {
        assertThat(script).contains("canvas.captureStream()")
        assertThat(script).contains("decodeToCanvas")
    }

    @Test
    fun `script never patches the real getDisplayMedia`() {
        assertThat(script).doesNotContain("navigator.mediaDevices")
        assertThat(script).doesNotContain("mediaDevices.getDisplayMedia =")
    }

    @Test
    fun `script degrades through explicit error names`() {
        assertThat(script).contains("NotSupportedError")
        assertThat(script).contains("NotAllowedError")
        assertThat(script).contains("TimeoutError")
    }

    @Test
    fun `script is a self-contained IIFE`() {
        val trimmed = script.trim()
        assertThat(trimmed).startsWith("(function()")
        assertThat(trimmed).endsWith("})();")
    }
}
