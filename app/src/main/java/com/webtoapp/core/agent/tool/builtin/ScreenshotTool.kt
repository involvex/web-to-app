package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.stats.WebsiteScreenshotService
import org.koin.java.KoinJavaComponent

class ScreenshotTool : Tool {
    override val name = "ScreenshotApp"
    override val description = """
        Capture a screenshot of an app's web content and save it as a WebP file.
        The screenshot is stored in app-private storage and the path is returned.
        Use this to generate thumbnails for app previews or to visually verify
        a site's current state. Only captures the main viewport; for full-page
        scrolling screenshots not yet supported.

        Use ListApps to find available app ids. Returns the file path on success.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        integer("appId", "The app id (from ListApps).", required = true)
        boolean("forceRefresh", "Force a fresh capture instead of returning existing screenshot.", default = false)
    }
    override fun isReadOnly() = true
    override fun activityDescription(args: JsonObject): String? =
        args.get("appId")?.asString?.let { "Capturing screenshot for app $it" }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val appId = args.get("appId")?.asLong ?: return ToolResult.error("ScreenshotApp: missing appId.")
        val forceRefresh = args.get("forceRefresh")?.asBoolean ?: false

        val app = ctx.appRepository.getWebApp(appId)
                   ?: return ToolResult.error("ScreenshotApp: app $appId not found.")

        val service = KoinJavaComponent.get<WebsiteScreenshotService>(
            WebsiteScreenshotService::class.java, null, null
        )

        if (!forceRefresh && service.hasScreenshot(appId)) {
            val existing = service.getScreenshotPath(appId)
            return ToolResult.ok("Screenshot already exists: $existing")
        }

        val url = app.url
        if (url == null) {
            return ToolResult.error("ScreenshotApp: app has no start URL to capture.")
        }

        val path = service.captureScreenshot(appId, url)
        return if (path != null) {
            ToolResult.ok("Screenshot captured: $path")
        } else {
            ToolResult.error("ScreenshotApp: capture failed for app $appId at $url.")
        }
    }
}
