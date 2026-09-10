package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.data.model.WebApp
import com.webtoapp.data.model.WebViewConfig
import com.webtoapp.data.model.NativeBridgeCapabilities

class SetPictureInPictureTool : Tool {
    override val name = "SetPictureInPicture"
    override val description = """
         Enable or disable Picture-in-Picture (PiP) support for a WebView-based app.
         This configures the pictureInPictureEnabled flag and the nativeBridge pip
         capability so that the web page's JavaScript can call NativeBridge.enterPiP()
         to enter Android's PiP mode. Requires API 26+ and a device with
         FEATURE_PICTURE_IN_PICTURE. The web page's JS must invoke enterPiP() to
         actually trigger PiP — this tool only configures the capability.
         Changes take effect on the next host preview or exported APK build.
         Use GetApp to find the appId and check current config.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        integer("appId", "The app id (from ListApps).", required = true)
        boolean("enabled", "Set to true to enable PiP, false to disable.", required = true)
    }
    override fun isReadOnly() = false
    override fun activityDescription(args: JsonObject): String? =
        args.get("appId")?.asString?.let { "Setting PiP for app $it" }
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val appId = args.get("appId")?.asLong ?: return ToolResult.error("SetPictureInPicture: missing appId.")
        val enabled = args.get("enabled")?.asBoolean ?: return ToolResult.error("SetPictureInPicture: missing enabled.")
        val app = ctx.appRepository.getWebApp(appId) ?: return ToolResult.error("SetPictureInPicture: app $appId not found.")
        val currentWv = app.webViewConfig
        val updatedWv = currentWv.copy(
            pictureInPictureEnabled = enabled,
            nativeBridgeCapabilities = currentWv.nativeBridgeCapabilities.copy(pip = enabled)
        )
        val updatedApp = app.copy(webViewConfig = updatedWv)
        ctx.appRepository.updateWebApp(updatedApp)
        return ToolResult.ok(
            "PiP ${if (enabled) "enabled" else "disabled"} for \"${app.name}\" " +
                "(pictureInPictureEnabled=$enabled, nativeBridgeCapabilities.pip=$enabled). " +
                "Changes take effect on the next app launch."
        )
    }
}
