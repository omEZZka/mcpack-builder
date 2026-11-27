package com.mcpackbuilder.data.parser

import com.mcpackbuilder.data.web.WebFetcher
import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.CatalogSource
import com.mcpackbuilder.domain.model.CatalogTag
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val result = withContext(Dispatchers.IO) {
            when (source) {
                CatalogSource.MCPEDL -> mcpedlCatalog(trimmedQuery, category, page, sort, tag)
                CatalogSource.MCPEHUB -> mcpehubCatalog(trimmedQuery, category, page, tag)
            }
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
        // API requires полноценный запрос (multi_match + operator AND);
        // 1-2 символа дают 0 результатов — показываем общий список
        val effectiveQuery = query.trim().takeIf { it.length >= 3 } ?: ""
        if (effectiveQuery.isNotBlank()) params += "q=" + URLEncoder.encode(effectiveQuery, "UTF-8")
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
            val rating = obj["average_rating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
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
                rating = rating,
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
        val base = category.mcpehubUrl
        val effectiveQuery = query.trim().takeIf { it.length >= 3 } ?: ""
        val url = if (effectiveQuery.isBlank()) {
            if (page <= 1) base else "${base.trimEnd('/')}/page/$page/"
        } else {
            val q = URLEncoder.encode(effectiveQuery, "UTF-8")
            if (page <= 1) "https://mcpehub.org/index.php?do=search&subaction=search&story=$q"
            else "https://mcpehub.org/page/$page/?do=search&subaction=search&story=$q"
        }
        val doc = fetcher.getDocument(url)
        return parseMcpehubListing(doc.html, doc.finalUrl, category)
    }

    fun parseMcpehubListing(html: String, baseUrl: String, category: ModCategory): CatalogResult {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val items = linkedSetOf<ModEntry>()
        // MCPEHUB: <article><a class="news-item" href="..."><h2>title</h2><img>...</a></article>
        val cards = doc.select("article")
        val anchors = if (cards.isNotEmpty()) cards.mapNotNull { it.selectFirst("a.news-item[href], a[href*=news-item], a[href]") }
                      else doc.select("a[href]")
        for (link in anchors) {
            val href = normalizeMcpehubUrl(link.absUrl("href").ifBlank { link.attr("href") }, baseUrl) ?: continue
            if (!isMcpehubContentUrl(href)) continue
            val container = link.parent() ?: link
            val title = container.selectFirst("h2, h1, h3, .title")?.text()?.trim()?.takeIf { it.length > 2 }
                ?: link.attr("title").trim().takeIf { it.length > 2 }
                ?: link.text().trim().takeIf { it.length > 4 } ?: continue
            if (title.equals("Download", true) || title.equals("Скачать", true)) continue
            val img = container.selectFirst("img")
            val cover = img?.let { i ->
                val src = i.attr("data-src").ifBlank { i.attr("data-lazy-src").ifBlank { i.attr("src") } }
                abs(src, baseUrl)
            }
            val dateText = container.selectFirst(".flex.between span, .news-item-info span")?.text()?.trim()
            val id = href.trimEnd('/').substringAfterLast('/').substringBefore('-').ifBlank { href.hashCode().toString() }
            items += ModEntry(
                id = id,
                title = title,
                coverUrl = cover,
                pageUrl = href,
                category = category.name.lowercase(),
                typeId = category.typeId ?: 1,
                published = dateText ?: "",
                source = "mcpehub"
            )
        }
        val page = Regex("""/page/(\d+)""").find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val pageCount = doc.select("a[href*='/page/']").mapNotNull { Regex("""/page/(\d+)""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()?.coerceAtLeast(page) ?: page
        return CatalogResult(items.toList(), page, pageCount, items.size)
    }

    suspend fun enrich(entry: ModEntry, source: CatalogSource = if (entry.source == "mcpehub") CatalogSource.MCPEHUB else CatalogSource.MCPEDL): ModEntry = withContext(Dispatchers.IO) {
        detailCache[entry.pageUrl]?.let { return@withContext it }
        if (entry.source == "mcpehub" || source == CatalogSource.MCPEHUB) {
            val doc = fetcher.getDocument(entry.pageUrl)
            val smart = SmartParser.parseDetail(doc.html, doc.finalUrl, entry.title, entry.coverUrl)
            val detailed = entry.copy(
                title = smart.title ?: entry.title,
                description = smart.description ?: entry.description,
                coverUrl = smart.coverUrl ?: entry.coverUrl,
                version = smart.version ?: entry.version,
                downloadLinks = smart.downloadLinks.ifEmpty { entry.downloadLinks }
            )
            detailCache[entry.pageUrl] = detailed
            return@withContext detailed
        }
        val slug = entry.pageUrl.trimEnd('/').substringAfterLast('/')
        var links = linkedSetOf<String>()
        var title = entry.title
        var desc = entry.description
        // 1) API slug JSON
        try {
            val jsonText = fetchJson("https://api.mcpedl.com/api/route/slug/$slug")
            val data = Json { ignoreUnknownKeys = true; isLenient = true }
                .parseToJsonElement(jsonText).jsonObject["data"]?.jsonObject
            title = data?.get("title")?.jsonPrimitive?.contentOrNull ?: title
            desc = data?.get("introduction")?.jsonPrimitive?.contentOrNull
                ?: data?.get("summary")?.jsonPrimitive?.contentOrNull ?: desc
            data?.get("downloads")?.jsonArray?.forEach { d ->
                val url = d.jsonObject["file"]?.jsonPrimitive?.contentOrNull
                if (url != null && hasArchiveExtension(url)) links += url
            }
            // страховка: сканируем весь JSON на архивные ссылки
            if (links.isEmpty()) links += SmartParser.extractArchiveLinks(jsonText)
        } catch (_: Exception) {
        }
        // 2) HTML fallback через умный парсер
        if (links.isEmpty()) {
            try {
                val doc = fetcher.getDocument(entry.pageUrl)
                val smart = SmartParser.parseDetail(doc.html, doc.finalUrl, entry.title, entry.coverUrl)
                if (smart.title != null) title = smart.title
                if (smart.description != null) desc = smart.description
                links += smart.downloadLinks
            } catch (_: Exception) {
            }
        }
        val detailed = entry.copy(
            title = title,
            description = desc ?: entry.description,
            downloadLinks = links.distinct().ifEmpty { entry.downloadLinks }
        )
        detailCache[entry.pageUrl] = detailed
        detailed
    }

    fun directDownload(urls: List<String>): String? = urls.firstOrNull { hasArchiveExtension(it) }

    private fun fetchJson(url: String): String {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 20000
                conn.readTimeout = 25000
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Accept-Encoding", "gzip")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36")
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                var text = stream?.use { raw ->
                    val encoding = conn.contentEncoding?.lowercase()
                    val input = if (encoding == "gzip") java.util.zip.GZIPInputStream(raw) else raw
                    input.bufferedReader().readText()
                } ?: ""
                conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(120)}")
                return text
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) try { Thread.sleep(700L * (attempt + 1)) } catch (_: InterruptedException) {}
            }
        }
        throw lastError ?: IllegalStateException("Network error")
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
