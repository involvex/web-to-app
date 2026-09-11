package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.errorpage.ErrorPageConfig
import com.webtoapp.core.errorpage.ErrorPageMode
import com.webtoapp.core.errorpage.ErrorPageStyle
import com.webtoapp.core.errorpage.MiniGameType
import com.webtoapp.data.model.WebApp

class ConfigureErrorPagesTool : Tool {
    override val name = "ConfigureErrorPages"
    override val description = """
        Configure the custom error page settings for a WebView app. This controls
        how HTTP errors (4xx/5xx), network failures, SSL errors, and render
        crashes are presented to the user inside the WebView.

        Parameters:
        - mode: "DEFAULT" (system), "BUILTIN_STYLE" (themed), "CUSTOM_HTML"
          (user-provided HTML), "CUSTOM_MEDIA" (image/GIF/video), or "SUPPRESSED"
          (show nothing).
        - builtInStyle: one of MATERIAL, SATELLITE, OCEAN, FOREST, MINIMAL, NEON
          (used when mode="BUILTIN_STYLE").
        - showMiniGame: attach a browser mini-game to error pages.
        - miniGameType: RANDOM, BREAKOUT, MAZE, INK_ZEN, STAR_CATCH.
        - autoRetrySeconds: seconds before auto-retrying navigation (0 to disable).
        - customHtml: custom HTML/CSS/JS for the error page (used when
          mode="CUSTOM_HTML").
        - customMediaPath: project-relative path to an image/GIF/video for
          CUSTOM_MEDIA mode. Must have been imported into the project.
        - retryButtonText: custom label for the retry button (blank = default).
        - showHttp4xxErrorUi, showHttp5xxErrorUi, showNetworkErrorUi,
          showSslErrorUi, showRenderCrashErrorUi: per-error-type toggles.

        Only the fields you provide are changed; everything else is preserved.
        Use GetApp to inspect the current error page config before patching.
    """.trimIndent()

    override val parametersSchema: JsonElement = jsonSchema {
        integer("appId", "The app id (from ListApps).", required = true)
        enum("mode", ErrorPageMode.values().map { it.name }, "Error page display mode.", required = true)
        enum("builtInStyle", ErrorPageStyle.values().map { it.name }, "Built-in theme style (when mode=BUILTIN_STYLE).")
        enum("miniGameType", MiniGameType.values().map { it.name }, "Mini-game type (when showMiniGame=true).")
        boolean("showMiniGame", "Show a mini-game on error pages.", default = false)
        integer("autoRetrySeconds", "Auto-retry after N seconds (0 to disable). Default 15.", default = 15)
        string("customHtml", "Custom HTML for CUSTOM_HTML mode. Use absolute URLs for external assets; local files must be in the project.")
        string("customMediaPath", "Project-relative path to an image/GIF/video for CUSTOM_MEDIA mode.")
        string("retryButtonText", "Custom retry button label (blank = built-in default).")
        boolean("showHttp4xxErrorUi", "Show error UI for HTTP 4xx responses.", default = true)
        boolean("showHttp5xxErrorUi", "Show error UI for HTTP 5xx responses.", default = true)
        boolean("showNetworkErrorUi", "Show error UI for network failures.", default = true)
        boolean("showSslErrorUi", "Show error UI for SSL/TLS errors.", default = true)
        boolean("showRenderCrashErrorUi", "Show error UI when the WebView process crashes.", default = true)
    }

    override fun isReadOnly(): Boolean = false
    override fun activityDescription(args: JsonObject): String? =
        args.get("appId")?.asString?.let { "Configuring error pages for app $it" }

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val appId = args.get("appId")?.asLong
            ?: return ToolResult.error("ConfigureErrorPages: missing `appId`.")
        val app = ctx.appRepository.getWebApp(appId)
            ?: return ToolResult.error("ConfigureErrorPages: no app with id $appId.")

        val existing = app.webViewConfig.errorPageConfig
        val updated = existing.copy(
            mode = safeEnumValue(args.get("mode")?.asString, ErrorPageMode.values(), existing.mode),
            builtInStyle = safeEnumValue(args.get("builtInStyle")?.asString, ErrorPageStyle.values(), existing.builtInStyle),
            miniGameType = safeEnumValue(args.get("miniGameType")?.asString, MiniGameType.values(), existing.miniGameType),
            showMiniGame = args.has("showMiniGame").let { if (it) args.get("showMiniGame").asBoolean else existing.showMiniGame },
            autoRetrySeconds = args.has("autoRetrySeconds").let { if (it) args.get("autoRetrySeconds").asInt else existing.autoRetrySeconds },
            customHtml = args.has("customHtml").let { if (it) args.get("customHtml").asString else existing.customHtml },
            customMediaPath = args.has("customMediaPath").let { if (it) args.get("customMediaPath").asString else existing.customMediaPath },
            retryButtonText = args.has("retryButtonText").let { if (it) args.get("retryButtonText").asString else existing.retryButtonText },
            showHttp4xxErrorUi = args.has("showHttp4xxErrorUi").let { if (it) args.get("showHttp4xxErrorUi").asBoolean else existing.showHttp4xxErrorUi },
            showHttp5xxErrorUi = args.has("showHttp5xxErrorUi").let { if (it) args.get("showHttp5xxErrorUi").asBoolean else existing.showHttp5xxErrorUi },
            showNetworkErrorUi = args.has("showNetworkErrorUi").let { if (it) args.get("showNetworkErrorUi").asBoolean else existing.showNetworkErrorUi },
            showSslErrorUi = args.has("showSslErrorUi").let { if (it) args.get("showSslErrorUi").asBoolean else existing.showSslErrorUi },
            showRenderCrashErrorUi = args.has("showRenderCrashErrorUi").let { if (it) args.get("showRenderCrashErrorUi").asBoolean else existing.showRenderCrashErrorUi }
        )

        val updatedWv = app.webViewConfig.copy(errorPageConfig = updated)
        ctx.appRepository.updateWebApp(app.copy(webViewConfig = updatedWv))

        return ToolResult.ok(
            "Error page config updated for \"${app.name}\": mode=${updated.mode.name}, " +
                "builtInStyle=${updated.builtInStyle.name}, showMiniGame=${updated.showMiniGame}, " +
                "autoRetry=${updated.autoRetrySeconds}s"
        )
    }

    private fun <T : Enum<T>> safeEnumValue(name: String?, values: Array<T>, default: T): T {
        if (name == null) return default
        return values.find { it.name == name } ?: default
    }
}
