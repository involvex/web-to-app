package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.scraper.WebsiteScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScrapeWebsiteTool : Tool {
    override val name = "ScrapeWebsite"
    override val description = """
        Scrape a website into local static assets for offline use. Downloads HTML,
        CSS, JS, images, fonts, media, and other static resources, rewriting URLs
        to point to local files. The result is stored in the app's internal
        'scraped_sites/<projectId>/' directory.

        The web page's JS can access the entry file via:
          NativeBridge.getScrapedSiteEntry(projectId)

        Use GetScrapedSites to list previously scraped sites or check disk usage.
        Progress is reported live as the scrape downloads files.

        Parameters allow limiting depth (how many link hops), max files, max
        file size, and whether to download CDN resources or follow links.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        string("url", "The URL to scrape (must be http/https).", required = true)
        integer("maxDepth", "Maximum link-following depth (1-10). Default 3.", default = 3)
        integer("maxFiles", "Maximum files to download (1-2000). Default 500.", default = 500)
        integer("maxFileSize", "Maximum individual file size in MB (1-100). Default 20.", default = 20)
        integer("maxTotalSize", "Maximum total download size in MB (1-500). Default 200.", default = 200)
        integer("concurrency", "Number of concurrent downloads (1-32). Default 16.", default = 16)
        boolean("followLinks", "Follow internal links to discover more pages. Default true.", default = true)
        boolean("downloadCdnResources", "Download resources from CDN domains. Default true.", default = true)
    }
    override fun isReadOnly(): Boolean = false
    override fun activityDescription(args: JsonObject): String? =
        args.get("url")?.asString?.let { "Scraping website $it" }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val url = args.get("url")?.asString?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("ScrapeWebsite: missing or empty `url`.")

        val scraper = WebsiteScraper(ctx.androidContext)

        val config = WebsiteScraper.ScrapeConfig(
            url = url,
            maxDepth = (args.get("maxDepth")?.asInt ?: 3).coerceIn(1, 10),
            maxFiles = (args.get("maxFiles")?.asInt ?: 500).coerceIn(1, 2000),
            maxFileSize = ((args.get("maxFileSize")?.asInt ?: 20) * 1024L * 1024L).coerceIn(1024L * 1024L, 100L * 1024L * 1024L),
            maxTotalSize = ((args.get("maxTotalSize")?.asInt ?: 200) * 1024L * 1024L).coerceIn(1024L * 1024L, 500L * 1024L * 1024L),
            concurrency = (args.get("concurrency")?.asInt ?: 16).coerceIn(1, 32),
            followLinks = args.get("followLinks")?.asBoolean ?: true,
            downloadCdnResources = args.get("downloadCdnResources")?.asBoolean ?: true,
            timeoutSeconds = 30
        )

        val progressScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        val result = scraper.scrape(config) { progress ->
            progressScope.launch {
                ctx.progress(
                    "Scraping: ${progress.phase.name} — " +
                        "${progress.downloadedFiles}/${progress.totalDiscovered} files, " +
                        "${formatBytes(progress.downloadedBytes)}, ${progress.currentFile}"
                )
            }
        }

        return when (result) {
            is WebsiteScraper.ScrapeResult.Success -> {
                val sizeMb = result.totalSize / (1024 * 1024)
                ToolResult.ok(
                    "Scraped ${result.totalFiles} files (${sizeMb}MB) from $url. " +
                        "Entry: ${result.entryFile}, elapsed=${result.elapsedMs}ms. " +
                        "Access via getScrapedSiteEntry() or GetScrapedSites."
                )
            }
            is WebsiteScraper.ScrapeResult.Error -> {
                ToolResult.error("ScrapeWebsite failed: ${result.message}")
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return "$mb MB"
    }
}

class GetScrapedSitesTool : Tool {
    override val name = "GetScrapedSites"
    override val description = """
        List all previously scraped websites with their file count, total size,
        last modified time, and directory path. Use this to check what's already
        been scraped before re-scraping, or to find a projectId for use with
        apps that reference scraped content.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {}
    override fun isReadOnly() = true
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val scraper = WebsiteScraper(ctx.androidContext)
        val sites = scraper.getScrapedSites()
        if (sites.isEmpty()) {
            return ToolResult.ok("No scraped sites found.")
        }
        val sb = StringBuilder()
        sb.appendLine("Scraped sites:")
        for (site in sites) {
            val sizeKb = site.totalSize / 1024
            sb.appendLine("- ${site.projectId}: ${site.fileCount} files, ${sizeKb}KB, dir=${site.dirPath}")
        }
        return ToolResult.ok(sb.toString().trimEnd())
    }
}

class DeleteScrapedSiteTool : Tool {
    override val name = "DeleteScrapedSite"
    override val description = """
        Delete a scraped website by project ID. This removes the scraped
        site's directory and all downloaded files from local storage.
        Use GetScrapedSites to find project IDs.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        string("projectId", "The project ID of the scraped site to delete (from GetScrapedSites).", required = true)
    }
    override fun isReadOnly() = false
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val projectId = args.get("projectId")?.asString?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("DeleteScrapedSite: missing `projectId`.")
        val scraper = WebsiteScraper(ctx.androidContext)
        val sites = scraper.getScrapedSites()
        if (sites.none { it.projectId == projectId }) {
            return ToolResult.error("DeleteScrapedSite: no scraped site with projectId=$projectId")
        }
        scraper.deleteScrapedSite(projectId)
        return ToolResult.ok("Deleted scraped site: $projectId")
    }
}
