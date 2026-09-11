package com.webtoapp.core.agent.tool.builtin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import com.webtoapp.core.agent.files.ProjectFileManager
import com.webtoapp.core.agent.permission.PermissionChecker
import com.webtoapp.core.agent.permission.PermissionPrompter
import com.webtoapp.core.agent.plan.PlanManager
import com.webtoapp.core.agent.tool.ToolRegistryFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebBrowseToolTest {

    private val tool = WebBrowseTool()

    @Test
    fun `tool advertises read-only schema with required action parameter`() {
        assertThat(tool.name).isEqualTo("WebBrowse")
        assertThat(tool.isReadOnly()).isTrue()

        val args = JsonObject().apply {
            addProperty("action", "navigate")
            addProperty("url", "https://example.com")
        }
        assertThat(tool.activityDescription(args)).isEqualTo("Web: navigate https://example.com")

        val schema = tool.parametersSchema.toString()
        assertThat(schema).contains("action")
        assertThat(schema).contains("required")
        assertThat(schema).contains("navigate")
        assertThat(schema).contains("click")
        assertThat(schema).contains("fill")
        assertThat(schema).contains("js")
    }

    @Test
    fun `description documents all actions and session reuse`() {
        assertThat(tool.description).contains("navigate")
        assertThat(tool.description).contains("click")
        assertThat(tool.description).contains("fill")
        assertThat(tool.description).contains("js")
        assertThat(tool.description).contains("sessionId")
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

        val matches = registry.all.filter { it.name == "WebBrowse" }
        assertThat(matches).hasSize(1)
        assertThat(matches.single().isReadOnly()).isTrue()

        val names = registry.all.map { it.name }
        assertThat(names.distinct()).isEqualTo(names)
    }

    @Test
    fun `timeoutSec parameter has correct type and default`() {
        val schemaObj = tool.parametersSchema.asJsonObject
        val props = schemaObj.getAsJsonObject("properties")
        val timeoutProp = props.getAsJsonObject("timeoutSec")
        assertThat(timeoutProp.get("type").asString).isEqualTo("integer")
        assertThat(timeoutProp.has("default")).isTrue()
        assertThat(timeoutProp.get("default").asInt).isEqualTo(15)
    }

    @Test
    fun `all action enum values are declared`() {
        val schemaObj = tool.parametersSchema.asJsonObject
        val actionProp = schemaObj.getAsJsonObject("properties").getAsJsonObject("action")
        val enumArray = actionProp.getAsJsonArray("enum")
        val values = enumArray.map { it.asString }
        assertThat(values).containsExactly("navigate", "click", "fill", "js")
    }

    @Test
    fun `click and fill actions document selector parameter`() {
        val schemaObj = tool.parametersSchema.asJsonObject
        val props = schemaObj.getAsJsonObject("properties")
        assertThat(props.has("selector")).isTrue()
        assertThat(props.getAsJsonObject("selector").get("type").asString).isEqualTo("string")
        assertThat(props.has("value")).isTrue()
        assertThat(props.has("submitSelector")).isTrue()
    }

    @Test
    fun `execute without action returns error`() {
        val args = JsonObject()
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("missing `action`")
    }

    @Test
    fun `file scheme URL is rejected via validation error`() {
        val args = JsonObject().apply {
            addProperty("action", "navigate")
            addProperty("url", "file:///data/local/tmp/secret.html")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("only http/https URLs are allowed")
    }

    @Test
    fun `data URI URL is rejected via validation error`() {
        val args = JsonObject().apply {
            addProperty("action", "navigate")
            addProperty("url", "data:text/html,<script>alert(1)</script>")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("only http/https URLs are allowed")
    }

    @Test
    fun `unknown action returns error`() {
        val args = JsonObject().apply {
            addProperty("action", "swipe")
            addProperty("url", "https://example.com")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("unknown action")
    }

    @Test
    fun `content scheme URL is rejected via validation error`() {
        val args = JsonObject().apply {
            addProperty("action", "navigate")
            addProperty("url", "content://com.example.app/secret")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("only http/https URLs are allowed")
    }

    @Test
    fun `navigate without url returns error`() {
        val args = JsonObject().apply { addProperty("action", "navigate") }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("missing `url`")
    }

    @Test
    fun `click without selector returns error`() {
        val args = JsonObject().apply {
            addProperty("action", "click")
            addProperty("url", "https://example.com")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("missing `selector`")
    }

    @Test
    fun `fill without value returns error`() {
        val args = JsonObject().apply {
            addProperty("action", "fill")
            addProperty("url", "https://example.com")
            addProperty("selector", "#input")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("missing `value`")
    }

    @Test
    fun `js without expression returns error`() {
        val args = JsonObject().apply {
            addProperty("action", "js")
            addProperty("url", "https://example.com")
        }
        val result = tool.validateArgs(args)
        assertThat(result).isNotNull()
        assertThat(result!!.isError).isTrue()
        assertThat(result.text).contains("missing `js`")
    }

    @Test
    fun `valid navigate args pass validation`() {
        val args = JsonObject().apply {
            addProperty("action", "navigate")
            addProperty("url", "https://example.com")
        }
        assertThat(tool.validateArgs(args)).isNull()
    }

    @Test
    fun `valid click args pass validation`() {
        val args = JsonObject().apply {
            addProperty("action", "click")
            addProperty("url", "https://example.com")
            addProperty("selector", "#button")
        }
        assertThat(tool.validateArgs(args)).isNull()
    }

    @Test
    fun `valid fill args pass validation`() {
        val args = JsonObject().apply {
            addProperty("action", "fill")
            addProperty("url", "https://example.com")
            addProperty("selector", "#input")
            addProperty("value", "hello")
        }
        assertThat(tool.validateArgs(args)).isNull()
    }
}
