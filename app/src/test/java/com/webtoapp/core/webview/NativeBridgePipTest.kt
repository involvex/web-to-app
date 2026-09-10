package com.webtoapp.core.webview

import android.content.Context
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

/**
 * Regression tests for the NativeBridge Picture-in-Picture capability gating.
 *
 * `enterPiP` / `exitPiP` / `isPiPActive` must:
 * - Return false without throwing when the capability is disabled.
 * - Return false when the context is not an Activity (host preview context).
 * - Not crash on any API level below O (no PictureInPictureParams available).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class NativeBridgePipTest {

    private fun bridgeWith(
        pip: Boolean,
        context: Context = ApplicationProvider.getApplicationContext()
    ): NativeBridge {
        return NativeBridge(
            context = context,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            webViewProvider = { null },
            capabilities = NativeBridgeCapabilities(pip = pip)
        )
    }

    @Test
    fun `pip methods no-op when capability is disabled`() {
        val bridge = bridgeWith(pip = false)

        assertThat(bridge.enterPiP()).isFalse()
        assertThat(bridge.exitPiP()).isFalse()
        assertThat(bridge.isPiPActive()).isFalse()
    }

    @Test
    fun `pip methods return false when context is not Activity`() {
        // Application context is not an Activity — must no-op, not crash.
        val bridge = bridgeWith(pip = true)

        assertThat(bridge.enterPiP()).isFalse()
        assertThat(bridge.isPiPActive()).isFalse()
    }
}
