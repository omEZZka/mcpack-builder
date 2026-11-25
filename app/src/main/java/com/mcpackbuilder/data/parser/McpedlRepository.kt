package com.mcpackbuilder.data.parser

import com.mcpackbuilder.data.web.WebFetcher
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import java.io.IOException
import java.net.URLEncoder

class McpedlRepository(
    private val parser: McpedlParser,
    private val fetcher: WebFetcher
) {

    suspend fun catalog(query: String, category: ModCategory, page: Int): CatalogPage {
        val api = runCatching { parser.searchApi(query, category, page) }.getOrNull()
        if (api != null) return api

        val url = if (query.isNotBlank()) {
            "https://mcpedl.com/search/?q=${URLEncoder.encode(query, "UTF-8")}&page=$page"
        } else {
            "${category.pageUrl}?page=$page"
        }
        val web = fetcher.fetch(url)
        val items = parser.parseSearchHtml(web.html, web.finalUrl)
        if (items.isEmpty()) {
            throw IOException("Не удалось распознать результаты MCPEDL. Возможно, изменилась вёрстка.")
        }
        return CatalogPage(items, page, page, items.size)
    }

    suspend fun enrich(mod: ModEntry): ModEntry {
        val fromApi = runCatching { parser.detailsApi(mod) }.getOrNull()
        if (fromApi != null && (fromApi.downloadLinks.isNotEmpty() || !fromApi.description.isNullOrBlank())) {
            return fromApi
        }
        return try {
            val web = fetcher.fetch(mod.pageUrl)
            parser.parseModHtml(web.html, mod)
        } catch (_: Exception) {
            mod
        }
    }

    fun directDownload(links: List<String>): String? {
        return links.firstOrNull { hasArchiveExtension(it) }
    }

    fun hasArchiveExtension(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return lower.endsWith(".mcpack") || lower.endsWith(".mcaddon") ||
            lower.endsWith(".zip") || lower.endsWith(".mcworld")
    }
}
