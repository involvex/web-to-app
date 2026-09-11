package com.webtoapp.core.agent.tool.builtin

import android.net.Uri
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.logging.AppLogger
import com.webtoapp.core.engine.EngineManager
import com.webtoapp.core.adblock.AdBlocker
import com.webtoapp.core.engine.BrowserEngine
import com.webtoapp.core.engine.BrowserEngineCallback
import com.webtoapp.data.model.WebViewConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit

class WebBrowseTool : Tool {
    override val name = "WebBrowse"
    override val description = """
        Load a URL in an off-screen browser engine, wait for the page to finish
        loading, then return the page title, current URL, and (optionally) the
        result of evaluating a JavaScript expression against the DOM.

        Actions:
        - navigate: load the given `url`, wait for the load to complete, return
          the page title and final URL. Use `js` (optional) to run JavaScript
          after load and capture its return value as JSON.
        - js: run a JavaScript `expression` (required) and return the JSON-encoded
          result. If `url` is provided, the engine navigates to it first and waits
          for load; otherwise the expression is evaluated against the page already
          loaded in the session identified by `sessionId`.
        - click: click an element matched by `selector` (CSS selector) on the
          page identified by `sessionId` (or `url` to load fresh). Optionally
          evaluate `js` after the click.
        - fill: set the value of an element matched by `selector` (CSS selector)
          to `value`, then optionally submit via `submitSelector` or Enter key.

        Sessions persist for 10 minutes of inactivity. Use `sessionId` to chain
        multiple actions against the same page context. For static scraping without
        JS, prefer ScrapeWebsite. Progress is reported live.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        enum("action", listOf("navigate", "click", "fill", "js"), "The interaction to perform.", required = true)
        string("url", "The URL to load (navigate/js). For click/fill: optional URL to load fresh before acting; otherwise operates on the session.")
        string("selector", "CSS selector for click/fill actions (e.g. '#submit-button').")
        string("value", "Value to fill into the input matched by `selector` (fill action only).")
        string("submitSelector", "For fill: a CSS selector for a submit button to click after filling. If omitted, Enter is pressed.")
        string("js", "JavaScript expression to evaluate after the action completes. Result is JSON-encoded and returned.")
        string("sessionId", "A session ID to reuse a previously created engine. If omitted, a new ephemeral engine is created.")
        integer("timeoutSec", "Maximum seconds to wait for page load / navigation. Default 15.", default = 15)
    }
    override fun isReadOnly(): Boolean = true

    override fun activityDescription(args: JsonObject): String? =
        args.get("url")?.asString?.let { url ->
            val a = args.get("action")?.asString ?: return null
            "Web: $a $url"
        }

    companion object {
        private const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L
    }

    private val sessions = HashMap<String, WebBrowseSession>()

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = withContext(Dispatchers.IO) {
        val action = args.get("action")?.asString?.takeIf { it.isNotEmpty() }
            ?: return@withContext ToolResult.error("WebBrowse: missing `action`.")

        val url = args.get("url")?.asString?.takeIf { it.isNotBlank() }?.let {
            val scheme = Uri.parse(it)?.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return@withContext ToolResult.error("WebBrowse: only http/https URLs are allowed.")
            }
            it
        }
        val sessionId = args.get("sessionId")?.asString
        val selector = args.get("selector")?.asString
        val value = args.get("value")?.asString
        val submitSelector = args.get("submitSelector")?.asString
        val js = args.get("js")?.asString?.takeIf { it.isNotBlank() }
        val timeoutSec = (args.get("timeoutSec")?.asInt ?: 15).coerceIn(1, 60)

        when (action) {
            "navigate" -> handleNavigate(url, js, timeoutSec, ctx)
            "js" -> handleJs(url, js, sessionId, timeoutSec, ctx)
            "click" -> handleClick(selector, js, url, sessionId, timeoutSec, ctx)
            "fill" -> handleFill(selector, value, submitSelector, js, url, sessionId, timeoutSec, ctx)
            else -> ToolResult.error("WebBrowse: unknown action `$action`. Supported: navigate, click, fill, js.")
        }
    }

    private suspend fun handleNavigate(
        url: String?, js: String?, timeoutSec: Int, ctx: ToolContext
    ): ToolResult {
        val resolvedUrl = url?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("WebBrowse navigate: missing `url`.")

        val session = getOrCreateSession(null, ctx)
        return withSessionLoaded(session, resolvedUrl, timeoutSec, ctx) { s ->
            val title = s.evaluateSync("document.title")
            val finalUrl = s.evaluateSync("window.location.href")
            val jsResult = js?.let { s.evaluateSync(it) }
            val sb = StringBuilder()
            sb.appendLine("Loaded: $finalUrl")
            sb.appendLine("Title: $title")
            if (jsResult != null) sb.appendLine("JS result: $jsResult")
            sb.toString().trimEnd()
        }
    }

    private suspend fun handleJs(
        url: String?, js: String?, sessionId: String?, timeoutSec: Int, ctx: ToolContext
    ): ToolResult {
        val script = js?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("WebBrowse js: missing `js` expression.")

        val session = getOrCreateSession(sessionId, ctx)

        if (url != null) {
            return withSessionLoaded(session, url, timeoutSec, ctx) { s ->
                val result = s.evaluateSync(script)
                "JS result: $result"
            }
        }

        if (session.currentUrl == null) {
            return ToolResult.error("WebBrowse js: no URL provided and no active session. Provide `url` or `sessionId`.")
        }
        val result = session.evaluateSync(script)
        return ToolResult.ok("JS result: $result")
    }

    private suspend fun handleClick(
        selector: String?, js: String?, url: String?, sessionId: String?,
        timeoutSec: Int, ctx: ToolContext
    ): ToolResult {
        val sel = selector?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("WebBrowse click: missing `selector`.")

        val session = getOrCreateSession(sessionId, ctx)

        val clickScript = "document.querySelector(${session.escapeJs(sel)}).click(); 'clicked'"

        if (url != null) {
            return withSessionLoaded(session, url, timeoutSec, ctx) { s ->
                val found = s.evaluateSync("!!document.querySelector(${s.escapeJs(sel)})")
                val clickResult = if (found == "true") s.evaluateSync(clickScript) else null
                val postResult = js?.let { s.evaluateSync(it) }
                val sb = StringBuilder()
                sb.appendLine("Clicked: $sel")
                sb.appendLine("Element found: $found")
                if (postResult != null) sb.appendLine("JS result: $postResult")
                sb.toString().trimEnd()
            }
        }

        if (session.currentUrl == null) {
            return ToolResult.error("WebBrowse click: no URL provided and no active session.")
        }
        val found = session.evaluateSync("!!document.querySelector(${session.escapeJs(sel)})")
        if (found != "true") return ToolResult.error("WebBrowse click: element not found: $sel")
        session.evaluateSync(clickScript)
        val postResult = js?.let { session.evaluateSync(it) }
        val sb = StringBuilder()
        sb.appendLine("Clicked: $sel")
        sb.appendLine("Element found: true")
        if (postResult != null) sb.appendLine("JS result: $postResult")
        return ToolResult.ok(sb.toString().trimEnd())
    }

    private suspend fun handleFill(
        selector: String?, value: String?, submitSelector: String?,
        js: String?, url: String?, sessionId: String?, timeoutSec: Int, ctx: ToolContext
    ): ToolResult {
        val sel = selector?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("WebBrowse fill: missing `selector`.")
        val fillVal = value
            ?: return ToolResult.error("WebBrowse fill: missing `value`.")

        val session = getOrCreateSession(sessionId, ctx)

        val fillScript = """
            (function() {
                var el = document.querySelector(${session.escapeJs(sel)});
                if (!el) return false;
                el.value = ${session.escapeJs(fillVal)};
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            })()
        """.trimIndent()

        val submitScript = if (submitSelector != null) {
            "document.querySelector(${session.escapeJs(submitSelector)}).click(); 'submitted'"
        } else {
            """
            var el = document.querySelector(${session.escapeJs(sel)});
            var evt = new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', bubbles: true});
            el.dispatchEvent(evt); 'submitted'
            """.trimIndent()
        }

        if (url != null) {
            return withSessionLoaded(session, url, timeoutSec, ctx) { s ->
                val filled = s.evaluateSync(fillScript)
                if (filled != "true") return@withSessionLoaded "Fill: element not found: $sel"
                s.evaluateSync(submitScript)
                val postResult = js?.let { s.evaluateSync(it) }
                val sb = StringBuilder()
                sb.appendLine("Filled: $sel = $fillVal")
                sb.appendLine("Submitted: true")
                if (postResult != null) sb.appendLine("JS result: $postResult")
                sb.toString().trimEnd()
            }
        }

        if (session.currentUrl == null) {
            return ToolResult.error("WebBrowse fill: no URL provided and no active session.")
        }
        val filled = session.evaluateSync(fillScript)
        if (filled != "true") return ToolResult.error("WebBrowse fill: element not found: $sel")
        session.evaluateSync(submitScript)
        val postResult = js?.let { session.evaluateSync(it) }
        val sb = StringBuilder()
        sb.appendLine("Filled: $sel = $fillVal")
        sb.appendLine("Submitted: true")
        if (postResult != null) sb.appendLine("JS result: $postResult")
        return ToolResult.ok(sb.toString().trimEnd())
    }

    private suspend fun getOrCreateSession(sessionId: String?, ctx: ToolContext): WebBrowseSession {
        cleanupExpiredSessions()
        if (sessionId != null) {
            val existing = sessions[sessionId]
            if (existing != null && !existing.isDestroyed) {
                return existing
            }
        }
        val session = WebBrowseSession(ctx)
        sessions[session.id] = session
        return session
    }

    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (_, session) ->
            val expired = !session.isDestroyed && (now - session.lastUsed) > SESSION_TIMEOUT_MS
            if (expired) {
                session.destroy()
                true
            } else {
                false
            }
        }
    }

    private suspend fun withSessionLoaded(
        session: WebBrowseSession, url: String, timeoutSec: Int, ctx: ToolContext,
        block: suspend (WebBrowseSession) -> String
    ): ToolResult {
        return try {
            val ok = session.loadUrlAndWait(url, timeoutSec)
            if (!ok) {
                ToolResult.error("WebBrowse: page load failed or timed out after ${timeoutSec}s for $url")
            } else {
                val result = block(session)
                if (result.isBlank()) ToolResult.ok("Done.")
                else ToolResult.ok(result)
            }
        } catch (e: Exception) {
            ToolResult.error("WebBrowse: ${e.message}")
        }
    }
}

private class WebBrowseSession(
    private val ctx: ToolContext
) {
    val id: String = System.currentTimeMillis().toString() + "_" + (0..9999).random()
    var currentUrl: String? = null
    var isDestroyed = false
        private set
    var lastUsed: Long = System.currentTimeMillis()

    private val engineManager: EngineManager by lazy { EngineManager.getInstance(ctx.androidContext) }
    private var engine: BrowserEngine? = null

    fun escapeJs(str: String): String {
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
    }

    suspend fun loadUrlAndWait(url: String, timeoutSec: Int): Boolean = withContext(Dispatchers.Main) {
        try {
            val adBlocker = KoinJavaComponent.get<AdBlocker>(AdBlocker::class.java, null, null)
            val engineType = engineManager.selectedEngine.value
            engine = engineManager.createEngine(engineType, adBlocker)

            val freshCallback = CompletableDeferred<Boolean>()
            engine?.createView(ctx.androidContext, WebViewConfig(), object : BrowserEngineCallback {
                override fun onPageStarted(url: String?) {}
                override fun onPageFinished(url: String?) {
                    if (freshCallback.isActive) freshCallback.complete(true)
                }
                override fun onProgressChanged(progress: Int) {}
                override fun onTitleChanged(title: String?) {}
                override fun onIconReceived(icon: android.graphics.Bitmap?) {}
                override fun onError(errorCode: Int, description: String) {
                    if (freshCallback.isActive) freshCallback.complete(false)
                }
                override fun onSslError(error: String) {}
                override fun onExternalLink(url: String) {}
                override fun onShowCustomView(view: android.view.View?, callback: Any?) {}
                override fun onHideCustomView() {}
                override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) {}
            })
            currentUrl = url
            engine?.loadUrl(url)

            val result = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSec.toLong())) {
                freshCallback.await()
            }
            lastUsed = System.currentTimeMillis()
            result == true
        } catch (e: Exception) {
            AppLogger.e("WebBrowse", "loadUrlAndWait failed", e)
            currentUrl = null
            false
        }
    }

    suspend fun evaluateSync(script: String): String? = withContext(Dispatchers.Main) {
        try {
            val deferred = CompletableDeferred<String?>()
            engine?.evaluateJavascript(script) { result ->
                deferred.complete(result)
            } ?: run { deferred.complete(null) }
            deferred.await()
        } catch (e: Exception) {
            AppLogger.e("WebBrowse", "evaluateSync failed", e)
            null
        }
    }

    fun destroy() {
        isDestroyed = true
        engine?.destroy()
        engine = null
        currentUrl = null
    }
}
