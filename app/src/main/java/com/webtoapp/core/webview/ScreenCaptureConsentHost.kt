package com.webtoapp.core.webview

import android.content.Intent

/**
 * Host surface capable of showing the MediaProjection consent dialog.
 *
 * Device screen capture requires a one-time user grant via
 * `MediaProjectionManager.createScreenCaptureIntent()`, which must be launched
 * from an Activity. ShellActivity and WebViewActivity implement this (via their
 * ActivityResult launchers); Service contexts (FloatingWindowService) and the
 * GeckoView engine path (application context) cannot, so device capture
 * reports unsupported there and NativeBridge falls back gracefully.
 */
interface ScreenCaptureConsentHost {
    fun requestScreenCaptureConsent(onResult: (resultCode: Int, data: Intent?) -> Unit)
}
