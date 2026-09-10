package com.webtoapp.core.extension

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingPipModuleTest {

    @Test
    fun `built-in streaming pip module is registered`() {
        val module = BuiltInModules.getAll().single { it.id == "builtin-streaming-pip" }

        assertThat(module.builtIn).isTrue()
        assertThat(module.category).isEqualTo(ModuleCategory.VIDEO)
        assertThat(module.runAt).isEqualTo(ModuleRunTime.DOCUMENT_END)
        assertThat(module.permissions).containsAtLeast(
            ModulePermission.DOM_ACCESS,
            ModulePermission.PICTURE_IN_PICTURE,
        )
        assertThat(module.name).isNotEmpty()
        assertThat(module.description).isNotEmpty()
        assertThat(module.code).contains("__WTA_MODULE_UI__")
        assertThat(module.code).contains("requestPictureInPicture")
        assertThat(module.panelHtml).contains("wta-stream-pip-list")
    }

    @Test
    fun `streaming pip template is registered with stream filter config`() {
        val template = ModuleTemplates.getAll().single { it.id == "template-stream-detect-pip" }

        assertThat(template.category).isEqualTo(ModuleCategory.VIDEO)
        assertThat(template.configItems.map { it.key }).containsAtLeast("autoEntry", "streamingTypes")
        assertThat(template.code).contains("requestPictureInPicture")
    }

    @Test
    fun `built-in module ids stay unique`() {
        val ids = BuiltInModules.getAll().map { it.id }
        assertThat(ids).containsNoDuplicates()
    }
}
