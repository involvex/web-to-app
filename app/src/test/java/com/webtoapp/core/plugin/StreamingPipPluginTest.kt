package com.webtoapp.core.plugin

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The fork's streaming-PiP detector survived the v2.6.9 merge as a native
 * HCJ builtin (assets/plugins/builtin-streaming-pip) after the retired
 * module.json DSL (BuiltInModules/ModuleTemplates) was deleted upstream.
 *
 * These contract assertions pin the port: auto-discovery id, idle injection
 * (video detection needs the DOM), the hcj.* bridge surface, PiP entry
 * paths incl. the NativeBridge fallback, and re-injection idempotency.
 */
class StreamingPipPluginTest {

    private val manifest = File(assetRoot(), "builtin-streaming-pip/plugin.json").readText()
    private val doc = File(assetRoot(), "builtin-streaming-pip/plugin.html").readText()
    private val pageJs = doc
        .let {
            Regex(
                """<script type="hcj/page">(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(it)?.groupValues?.get(1) ?: error("hcj/page block missing")
        }

    @Test
    fun `manifest keeps the stable builtin id and idle injection`() {
        assertThat(manifest).contains("\"id\": \"builtin-streaming-pip\"")
        assertThat(manifest).contains("\"runAt\": \"document_idle\"")
        assertThat(manifest).contains("\"toolbar\": true")
    }

    @Test
    fun `manifest parses through the production parser`() {
        val parsed = PluginManifest.fromJson(manifest)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.id).isEqualTo("builtin-streaming-pip")
        assertThat(parsed.matches).isNotEmpty()
    }

    @Test
    fun `page script uses the hcj bridge surface only`() {
        assertThat(pageJs).contains("hcj.panel.onMessage")
        assertThat(pageJs).contains("hcj.panel.send")
        assertThat(pageJs).contains("hcj.on('action'")
        assertThat(pageJs).contains("hcj.on('navigate'")
        assertThat(pageJs).contains("hcj.lang")
        assertThat(pageJs).contains("hcj.toast")
        assertThat(pageJs).doesNotContain("__WTA_MODULE_UI__")
        assertThat(pageJs).doesNotContain("__MODULE_INFO__")
    }

    @Test
    fun `page script covers all PiP entry paths`() {
        assertThat(pageJs).contains("requestPictureInPicture")
        assertThat(pageJs).contains("NativeBridge.enterPiP")
        assertThat(pageJs).contains("detectStreamingSrc")
        assertThat(pageJs).contains("m3u8")
        assertThat(pageJs).contains("youtube")
        assertThat(pageJs).contains("MutationObserver")
    }

    @Test
    fun `panel exposes per-video toggle plus start-stop and auto actions`() {
        assertThat(pageJs).contains("data-wta-action=\"togglePip\"")
        assertThat(pageJs).contains("data-wta-action=\"startAll\"")
        assertThat(pageJs).contains("data-wta-action=\"stopAll\"")
        assertThat(pageJs).contains("data-wta-action=\"toggleAuto\"")
        assertThat(pageJs).contains("getPanelHtml")
        assertThat(doc).contains("wta-stream-pip-list")
    }

    @Test
    fun `plugin script is idempotent across re-injection`() {
        assertThat(pageJs).contains("window.__wtaSpipInit")
    }

    @Test
    fun `full document satisfies the package format helpers`() {
        assertThat(extractPageJs(doc)).contains("getPanelHtml")
        assertThat(hasPanelMarkup(doc)).isTrue()
    }

    private fun assetRoot(): File =
        listOf("app/src/main/assets/plugins", "src/main/assets/plugins")
            .asSequence().map(::File).firstOrNull { File(it, "builtin-streaming-pip").isDirectory }
            ?: error("Cannot locate builtin plugins asset dir")
}
