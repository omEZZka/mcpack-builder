package com.mcpackbuilder.data.parser

import com.mcpackbuilder.data.web.WebFetcher
import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.CatalogSource
import com.mcpackbuilder.domain.model.CatalogTag
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class CatalogResult(val items: List<ModEntry>, val page: Int, val pageCount: Int, val total: Int)

class McpedlRepository(private val parser: McpedlParser, private val fetcher: WebFetcher) {

    private val detailCache = ConcurrentHashMap<String, ModEntry>()
    private val listingCache = ConcurrentHashMap<String, CatalogResult>()

    suspend fun catalog(query: String, category: ModCategory, page: Int, sort: CatalogSort, source: CatalogSource = CatalogSource.MCPEDL, tag: CatalogTag = CatalogTag.ALL): CatalogResult {
        val trimmedQuery = query.trim()
        val key = "${source.name}|${category.name}|${sort.name}|${tag.name}|${page}|$trimmedQuery"
        listingCache[key]?.let { return it }

        val result = when (source) {
            CatalogSource.MCPEDL -> mcpedlCatalog(trimmedQuery, category, page, sort, tag)
            CatalogSource.MCPEHUB -> mcpehubCatalog(trimmedQuery, category, page, tag)
        }

        if (result.items.isNotEmpty()) listingCache[key] = result
        return result
    }

    private suspend fun mcpedlCatalog(query: String, category: ModCategory, page: Int, sort: CatalogSort, tag: CatalogTag): CatalogResult {
        val tags = mutableListOf<String>()
        // category-specific tag (e.g. shaders)
        if (category == ModCategory.SHADERS) tags += "shaders"
        // user-selected tag
        if (tag != CatalogTag.ALL && tag.mcpedlSlug.isNotBlank()) tags += tag.mcpedlSlug

        val params = mutableListOf<String>()
        params += "page=$page"
        params += "page_size=20"
        if (query.isNotBlank()) params += "q=" + URLEncoder.encode(query, "UTF-8")
        params += "sort=" + URLEncoder.encode(sort.api, "UTF-8")
        params += "type_id=${category.typeId ?: 1}"
        if (tags.isNotEmpty()) params += "cf_tags=" + URLEncoder.encode(tags.joinToString(","), "UTF-8")

        val url = "https://api.mcpedl.com/api/search/advanced?" + params.joinToString("&")
        val jsonText = fetchJson(url)
        val root = Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(jsonText).jsonObject
        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
        val items = results.mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val pageUrl = "https://mcpedl.com/${slug.trim('/')}/"
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: slug
            val cover = obj["image"]?.jsonPrimitive?.contentOrNull
            val author = obj["user_nicename"]?.jsonPrimitive?.contentOrNull
                ?: obj["display_name"]?.jsonPrimitive?.contentOrNull
            val desc = obj["summary"]?.jsonPrimitive?.contentOrNull
                ?: obj["introduction"]?.jsonPrimitive?.contentOrNull
            val downloadCount = obj["downloadCount"]?.jsonPrimitive?.longOrNull
            val published = obj["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
            val typeId = obj["type_id"]?.jsonPrimitive?.intOrNull ?: (category.typeId ?: 1)
            ModEntry(
                id = id,
                title = title,
                author = author,
                category = category.name.lowercase(),
                version = null,
                coverUrl = cover,
                pageUrl = pageUrl,
                description = desc,
                downloadLinks = emptyList(),
                typeId = typeId,
                downloadCount = downloadCount,
                published = published,
                source = "mcpedl"
            )
        }
        val meta = root["meta"]?.jsonObject
        val currentPage = meta?.get("page")?.jsonPrimitive?.intOrNull ?: page
        val pageCount = meta?.get("page_count")?.jsonPrimitive?.intOrNull ?: 1
        val total = meta?.get("total")?.jsonPrimitive?.intOrNull ?: items.size
        return CatalogResult(items, currentPage, pageCount.coerceAtLeast(1), total)
    }

    private suspend fun mcpehubCatalog(query: String, category: ModCategory, page: Int, tag: CatalogTag): CatalogResult {
        // MCPEHUB is not easily filterable; keep previous HTML approach via fetcher
        val base = category.mcpehubUrl
        val url = if (query.isBlank()) {
            if (page <= 1) base else "${base.trimEnd('/')}/page/$page/"
        } else {
            val q = URLEncoder.encode(query, "UTF-8")
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
        if (entry.source == "mcpehub" || source == CatalogSource.MCPEHUB) {
            val doc = fetcher.getDocument(entry.pageUrl)
            val detailed = parseMcpehubDetail(entry, doc.html, doc.finalUrl)
            detailCache[entry.pageUrl] = detailed
            return detailed
        }
        // MCPEDL: use slug API
        val slug = entry.pageUrl.trimEnd('/').substringAfterLast('/')
        return try {
            val jsonText = fetchJson("https://api.mcpedl.com/api/route/slug/$slug")
            val data = Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(jsonText).jsonObject["data"]?.jsonObject
            val title = data?.get("title")?.jsonPrimitive?.contentOrNull ?: entry.title
            val desc = data?.get("introduction")?.jsonPrimitive?.contentOrNull
                ?: data?.get("summary")?.jsonPrimitive?.contentOrNull
                ?: entry.description
            val cover = entry.coverUrl
            val links = linkedSetOf<String>()
            data?.get("downloads")?.jsonArray?.forEach { d ->
                val url = d.jsonObject["file"]?.jsonPrimitive?.contentOrNull
                if (url != null && hasArchiveExtension(url)) links += url
            }
            // dedupe by url
            val unique = links.distinct()
            val detailed = entry.copy(
                title = title,
                description = desc ?: entry.description,
                coverUrl = cover,
                downloadLinks = if (unique.isNotEmpty()) unique else entry.downloadLinks
            )
            detailCache[entry.pageUrl] = detailed
            detailed
        } catch (e: Exception) {
            detailCache[entry.pageUrl] = entry
            entry
        }
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

    private fun fetchJson(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.bufferedReader().readText() } ?: ""
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(120)}")
        return text
    }

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
        val l = url.substringBefore('?').lowercase()
        return listOf(".mcpack", ".mcaddon", ".mcworld", ".zip", ".mctemplate").any { l.endsWith(it) }
    }

}
