package com.webtoapp.core.agent.tool.builtin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.webtoapp.core.agent.files.ProjectFileManager
import com.webtoapp.core.agent.permission.PermissionChecker
import com.webtoapp.core.agent.permission.PermissionPrompter
import com.webtoapp.core.agent.plan.PlanManager
import com.webtoapp.core.agent.tool.ToolRegistryFactory
import com.google.gson.JsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenshotToolTest {

    private val tool = ScreenshotTool()

    @Test
    fun `tool advertises read-only schema with required appId parameter`() {
        assertThat(tool.name).isEqualTo("ScreenshotApp")
        // Screenshot is non-mutating — no permission prompt needed.
        assertThat(tool.isReadOnly()).isTrue()

        val args = JsonObject().apply { addProperty("appId", 4) }
        assertThat(tool.activityDescription(args)).isEqualTo("Capturing screenshot for app 4")

        val schema = tool.parametersSchema.toString()
        assertThat(schema).contains("appId")
        assertThat(schema).contains("required")
        assertThat(schema).contains("forceRefresh")
    }

    @Test
    fun `description tells the LLM what is returned`() {
        assertThat(tool.description).contains("file path")
        assertThat(tool.description).contains("app-private storage")
    }

    @Test
    fun `tool is registered exactly once and is read-only in the registry`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prompter = PermissionPrompter()
        val planManager = PlanManager(
            sessionId = "test",
            fileManager = ProjectFileManager(context),
            permissionChecker = PermissionChecker(prompter)
        )
        val registry = ToolRegistryFactory(planManager, imageRegistry = null)
            .build(hasImageModel = false)

        val matches = registry.all.filter { it.name == "ScreenshotApp" }
        assertThat(matches).hasSize(1)
        assertThat(matches.single().isReadOnly()).isTrue()

        val names = registry.all.map { it.name }
        assertThat(names.distinct()).isEqualTo(names)
    }

    @Test
    fun `forceRefresh parameter has correct type and default`() {
        val schemaObj = tool.parametersSchema.asJsonObject
        val props = schemaObj.getAsJsonObject("properties")
        val forceProp = props.getAsJsonObject("forceRefresh")
        assertThat(forceProp.get("type").asString).isEqualTo("boolean")
        assertThat(forceProp.has("default")).isTrue()
        assertThat(forceProp.get("default").asBoolean).isFalse()
    }
}
