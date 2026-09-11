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
class CertificateInspectToolTest {

    private val tool = CertificateInspectTool()

    @Test
    fun `tool advertises read-only schema with required url parameter`() {
        assertThat(tool.name).isEqualTo("InspectCertificate")
        // Reading certs is non-mutating — no permission prompt needed.
        assertThat(tool.isReadOnly()).isTrue()

        val args = JsonObject().apply { addProperty("url", "https://example.com") }
        assertThat(tool.activityDescription(args)).isEqualTo("Inspecting certificate for https://example.com")

        val schema = tool.parametersSchema.toString()
        assertThat(schema).contains("url")
        assertThat(schema).contains("required")
    }

    @Test
    fun `description tells the LLM what info is returned`() {
        assertThat(tool.description).contains("certificate chain")
        assertThat(tool.description).contains("issuer")
        assertThat(tool.description).contains("SHA-256 fingerprint")
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

        val matches = registry.all.filter { it.name == "InspectCertificate" }
        assertThat(matches).hasSize(1)
        assertThat(matches.single().isReadOnly()).isTrue()

        val names = registry.all.map { it.name }
        assertThat(names.distinct()).isEqualTo(names)
    }
}
