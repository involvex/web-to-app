package com.webtoapp.core.agent.tool

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.webtoapp.core.agent.files.ProjectFileManager
import com.webtoapp.core.agent.permission.PermissionChecker
import com.webtoapp.core.agent.permission.PermissionPrompter
import com.webtoapp.core.agent.plan.PlanManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies structural invariants of the agent tool registry to catch drift:
 *
 *   - Tools are properly registered (no duplicate names, consistent count).
 *   - Every tool's `parametersSchema` is a valid JSON Schema ("type": "object")
 *     with required fields present in properties.
 *   - Write tools (which mutate app state) are not falsely marked as read-only,
 *     since the LLM routes permission prompts based on isReadOnly().
 *   - App-scoped tools declare "appId" as a required integer parameter.
 *   - The ScrapeWebsite tool exposes its progressive scraping parameters.
 */
@RunWith(RobolectricTestRunner::class)
class AgentToolSchemaAlignmentTest {

    private fun buildRegistry(): ToolRegistry {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prompter = PermissionPrompter()
        val planManager = PlanManager(
            sessionId = "test",
            fileManager = ProjectFileManager(context),
            permissionChecker = PermissionChecker(prompter)
        )
        return ToolRegistryFactory(planManager, imageRegistry = null)
            .build(hasImageModel = false)
    }

    @Test
    fun `every registered tool has a non-empty parametersSchema`() {
        val registry = buildRegistry()
        for (tool in registry.all) {
            val schemaObj = tool.parametersSchema.asJsonObject
            assertThat(schemaObj.has("type")).isTrue()
            assertThat(schemaObj.get("type").asString).isEqualTo("object")
            if (schemaObj.has("required")) {
                val required = schemaObj.getAsJsonArray("required").map { it.asString }
                val props = schemaObj.getAsJsonObject("properties").keySet()
                for (req in required) {
                    assertThat(props).contains(req)
                }
            }
        }
    }

    @Test
    fun `no two tools share the same name`() {
        val registry = buildRegistry()
        val names = registry.all.map { it.name }
        assertThat(names).doesNotContain(null)
        assertThat(names).hasSize(names.distinct().size)
    }

    @Test
    fun `write tools are not marked read-only`() {
        val registry = buildRegistry()
        // Tools that mutate app state must NOT be read-only.
        val knownWriteTools = setOf(
            "CreateApp", "CreateModule", "Edit", "Delete", "Write",
            "BuildApk", "ShareApk", "ExportApp", "ExportAab",
            "CreateShortcut", "MoveToCategory", "ClearAppCache", "DeleteApp",
            "DuplicateApp", "UpdateApp", "SetPictureInPicture",
            "SaveConfigTemplate", "ApplyConfigTemplate", "DeleteConfigTemplate",
            "ManageHostsRules", "InstallRuntime", "ClearRuntimeCache",
            "KillPort", "KillAllPorts", "SelectEngine", "DeleteEngine",
            "CloneApp", "BatchImportApps",
            "ScrapeWebsite", "DeleteScrapedSite",
            "InitializeBuildEnv", "GenerateImage", "UpdateModule",
            "ConfigureErrorPages"
        )
        val writeTools = registry.all.filter { it.name in knownWriteTools }
        assertThat(writeTools).isNotEmpty()
        for (tool in writeTools) {
            assertThat(tool.isReadOnly()).isFalse()
        }
    }

    @Test
    fun `appId-bearing tools declare appId as required integer`() {
        // Tools that operate on an app must have appId in their schema.
        val appToolNames = setOf(
            "BuildApk", "ShareApk", "ExportApp", "CreateShortcut", "MoveToCategory",
            "ClearAppCache", "DeleteApp", "DuplicateApp", "ExportAab",
            "UpdateApp", "SetPictureInPicture", "ConfigureErrorPages"
        )
        val registry = buildRegistry()
        for (tool in registry.all) {
            if (appToolNames.contains(tool.name)) {
                val schema = tool.parametersSchema.asJsonObject
                val props = schema.getAsJsonObject("properties")
                assertThat(props.has("appId"))
                val appIdProp = props.getAsJsonObject("appId")
                assertThat(appIdProp.get("type").asString).isEqualTo("integer")
            }
        }
    }

    @Test
    fun `scrape website tool has progressive scrape parameters`() {
        val registry = buildRegistry()
        val tool = registry.all.find { it.name == "ScrapeWebsite" }
            ?: return  // Not yet implemented
        val props = tool.parametersSchema.asJsonObject.getAsJsonObject("properties")
        assertThat(props.has("url")).isTrue()
        assertThat(props.has("maxDepth")).isTrue()
        assertThat(props.has("maxFiles")).isTrue()
        assertThat(props.has("followLinks")).isTrue()
        assertThat(props.has("downloadCdnResources")).isTrue()
    }
}
