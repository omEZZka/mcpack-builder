package com.mcpackbuilder.data.parser

import com.mcpackbuilder.data.web.WebFetcher
import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.CatalogSource
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class CatalogResult(val items: List<ModEntry>, val page: Int, val pageCount: Int, val total: Int)

class McpedlRepository(private val parser: McpedlParser, private val fetcher: WebFetcher) {

    private val detailCache = ConcurrentHashMap<String, ModEntry>()
    private val listingCache = ConcurrentHashMap<String, CatalogResult>()
    private val pageParser = object : McpedlParser() {} // reuse logic; we extend below via helpers

    suspend fun catalog(query: String, category: ModCategory, page: Int, sort: CatalogSort, source: CatalogSource = CatalogSource.MCPEDL): CatalogResult {
        val key = "${source.name}|${category.name}|${sort.name}|${page}|${query.trim().lowercase()}"
        listingCache[key]?.let { return it }
        val result = when (source) {
            CatalogSource.MCPEDL -> mcpedlCatalog(query, category, page, sort)
            CatalogSource.MCPEHUB -> mcpehubCatalog(query, category, page)
        }
        if (result.items.isNotEmpty()) listingCache[key] = result
        return result
    }

    private suspend fun mcpedlCatalog(query: String, category: ModCategory, page: Int, sort: CatalogSort): CatalogResult {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            val apiUrl = "https://mcpedl.com/api/list?type_id=${category.typeId ?: 1}&sort=${sort.api}&order=desc&limit=20&page=$page&platform_id=2"
            val doc = fetcher.getDocument(apiUrl)
            val json = org.jsoup.Jsoup.parse(doc.html).text()
            parser.parseApiList(json, category)
        } else {
            val pageUrl = buildString {
                append("https://mcpedl.com/?s=")
                append(java.net.URLEncoder.encode(trimmed, "UTF-8"))
                if (category.typeId != null) append("&type_id=${category.typeId}")
                append("&platform_id=2")
                if (page > 1) append("&page=$page")
            }
            val doc = fetcher.getDocument(pageUrl)
            parser.parseCatalog(doc.html, doc.finalUrl, category)
        }
    }

    private suspend fun mcpehubCatalog(query: String, category: ModCategory, page: Int): CatalogResult {
        val trimmed = query.trim()
        val base = category.mcpehubUrl
        val url = if (trimmed.isBlank()) {
            if (page <= 1) base else "$base/page/$page/"
        } else {
            val q = java.net.URLEncoder.encode(trimmed, "UTF-8")
            if (page <= 1) "https://mcpehub.org/?s=$q" else "https://mcpehub.org/page/$page/?s=$q"
        }
        val doc = fetcher.getDocument(url)
        return parseMcpehubListing(doc.html, doc.finalUrl, category)
    }

    fun parseMcpehubListing(html: String, baseUrl: String, category: ModCategory): CatalogResult {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val items = linkedSetOf<ModEntry>()
        val cards = doc.select("article, .post, .entry, .item, .mod-card, .card, li.post-item")
        val nodes = if (cards.isNotEmpty()) cards else doc.select("a[href]")
        for (node in nodes) {
            val link = if (node.tagName() == "a") node else node.selectFirst("a[href*='/mods/'], a[href*='/maps/'], a[href*='/textures/'], a[href*='/shaders/'], a[href]") ?: continue
            val href = normalizeMcpehubUrl(link.absUrl("href").ifBlank { link.attr("href") }, baseUrl) ?: continue
            if (!isMcpehubContentUrl(href)) continue
            val container = if (node.tagName() == "a") node.parent() ?: node else node
            val title = container.selectFirst("h1,h2,h3,h4,.title,.entry-title")?.text()?.trim()?.takeIf { it.length > 2 }
                ?: link.text().trim().takeIf { it.length > 4 } ?: continue
            if (title.equals("Download", true) || title.equals("Скачать", true)) continue
            val cover = container.selectFirst("img")?.let { img -> abs(img.attr("data-src").ifBlank { img.attr("data-lazy-src").ifBlank { img.attr("src") } }, baseUrl) }
            val id = href.trimEnd('/').substringAfterLast('/')
            items += ModEntry(
                id = id.ifBlank { href.hashCode().toString() },
                title = title,
                coverUrl = cover,
                pageUrl = href,
                category = category.name.lowercase(),
                typeId = category.typeId ?: 1,
                source = "mcpehub"
            )
        }
        val page = Regex("""/page/(\d+)""").find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val pageCount = doc.select("a[href*='/page/']").mapNotNull { Regex("""/page/(\d+)""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()?.coerceAtLeast(page) ?: page
        return CatalogResult(items.toList(), page, pageCount, items.size)
    }

    suspend fun enrich(entry: ModEntry, source: CatalogSource = if (entry.source == "mcpehub") CatalogSource.MCPEHUB else CatalogSource.MCPEDL): ModEntry {
        detailCache[entry.pageUrl]?.let { return it }
        val doc = fetcher.getDocument(entry.pageUrl)
        val detailed = if (entry.source == "mcpehub" || source == CatalogSource.MCPEHUB) parseMcpehubDetail(entry, doc.html, doc.finalUrl) else parser.parseDetail(entry, doc.html, doc.finalUrl)
        detailCache[entry.pageUrl] = detailed
        return detailed
    }

    private fun parseMcpehubDetail(entry: ModEntry, html: String, baseUrl: String): ModEntry {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val title = doc.selectFirst("h1.entry-title, h1.post-title, h1")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: entry.title
        val desc = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".entry-content p, .post-content p, article p")?.text()?.trim()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { abs(it, baseUrl) } ?: entry.coverUrl
        val links = linkedSetOf<String>()
        doc.select("a[href]").forEach { a ->
            val href = abs(a.attr("href"), baseUrl) ?: return@forEach
            val text = a.text().lowercase()
            val lower = href.lowercase()
            if (hasArchiveExtension(lower) || lower.contains("download") || lower.contains("file") || text.contains("download") || text.contains("скачать")) {
                if (lower.startsWith("http")) links += href
            }
        }
        return entry.copy(title = title, description = desc ?: entry.description, coverUrl = cover, downloadLinks = if (links.isNotEmpty()) links.toList() else entry.downloadLinks)
    }

    fun directDownload(urls: List<String>): String? = urls.firstOrNull { hasArchiveExtension(it) }

    private fun normalizeMcpehubUrl(raw: String, base: String): String? {
        val abs = abs(raw, base) ?: return null
        return abs.substringBefore('#').trimEnd('/')
    }

    private fun isMcpehubContentUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("mcpehub.org/") && (u.contains("/mods/") || u.contains("/maps/") || u.contains("/textures/") || u.contains("/shaders/") || u.contains("/mod/") || u.contains("/map/"))
            && !u.contains("/page/") && !u.contains("/category/") && !u.endsWith("/mods") && !u.endsWith("/maps") && !u.endsWith("/textures") && !u.endsWith("/shaders")
    }

    private fun abs(raw: String, base: String): String? {
        val v = raw.trim()
        if (v.isBlank()) return null
        return try {
            val resolved = java.net.URI(base).resolve(v).toString()
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
        } catch (_: Exception) { null }
    }

    private fun hasArchiveExtension(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return listOf(".mcpack", ".mcaddon", ".mcworld", ".zip", ".mctemplate").any { lower.endsWith(it) }
    }
}
