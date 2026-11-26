package com.mcpackbuilder.data.parser

import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class CatalogResultLegacy(val items: List<ModEntry>, val page: Int, val pageCount: Int, val total: Int)

open class McpedlParser {

    fun parseApiList(jsonText: String, category: ModCategory): CatalogResult {
        val root = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrElse { return CatalogResult(emptyList(), 1, 1, 0) }
        val itemsArray = root["data"]?.jsonArray ?: root["items"]?.jsonArray ?: return CatalogResult(emptyList(), 1, 1, 0)
        val items = itemsArray.mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj.string("title") ?: return@mapNotNull null
            val slug = obj.string("slug") ?: obj.string("id") ?: title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-")
            val pageUrl = obj.string("url") ?: "https://mcpedl.com/${slug.trim('/')}/"
            val id = obj.string("id") ?: slug
            val cover = obj.string("cover_image")?.let { normalizeUrl(it, "https://mcpedl.com/") }
            ModEntry(
                id = id,
                title = Jsoup.parse(title).text(),
                author = obj.string("username").takeUnless { it.isNullOrBlank() },
                category = category.name.lowercase(),
                coverUrl = cover,
                pageUrl = pageUrl,
                typeId = obj.int("type_id") ?: category.typeId ?: 1,
                downloadCount = obj.long("download_count"),
                published = obj.string("published") ?: "",
                downloadsToday = obj.long("downloads_today"),
                source = "mcpedl"
            )
        }
        val meta = root["meta"]?.jsonObject
        val page = meta?.int("page") ?: 1
        val pageCount = meta?.int("last_page") ?: meta?.int("page_count") ?: 1
        val total = meta?.int("total") ?: items.size
        return CatalogResult(items, page, pageCount, total)
    }

    fun parseCatalog(html: String, baseUrl: String, category: ModCategory): CatalogResult {
        val doc = Jsoup.parse(html, baseUrl)
        val items = linkedSetOf<ModEntry>()
        // MCPEDL new structure: .fancybox.post with .fancybox__title-top > a
        val fancyBoxes = doc.select("div.fancybox.post")
        for (fb in fancyBoxes) {
            val titleDiv = fb.selectFirst("div.fancybox__title-top") ?: continue
            val a = titleDiv.selectFirst("a[href]") ?: continue
            val href = normalizeUrl(a.absUrl("href").ifBlank { a.attr("href") }, baseUrl) ?: continue
            if (!isContentUrl(href)) continue
            val title = a.text().trim().takeIf { it.length > 2 && !it.equals("Download", true) } ?: continue
            val img = fb.selectFirst("img[src]") ?: fb.selectFirst("div.post__img__background")
            var cover: String? = null
            if (img != null) {
                val src = img.attr("src").ifBlank { img.attr("data-src") }.ifBlank { img.attr("data-lazy-src") }
                    .ifBlank { img.attr("style").let { s -> Regex("url\\(['"]?([^'")]*)['"]?\\)").find(s)?.groupValues?.getOrNull(1) ?: "" } }
                cover = normalizeUrl(src, baseUrl)
            }
            val id = href.trimEnd('/').substringAfterLast('/').ifBlank { href.hashCode().toString() }
            items += ModEntry(id = id, title = title, coverUrl = cover, pageUrl = href, category = category.name.lowercase(), typeId = category.typeId ?: 1, source = "mcpedl")
        }
        if (items.isNotEmpty()) {
            val page = Regex("""/page/(\d+)""").find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""[?&]page=(\d+)""").find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val pageCount = doc.select("a[href*='/page/']").mapNotNull { Regex("""/page/(\d+)""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()
                ?: doc.select("a[href*='page=']").mapNotNull { Regex("""[?&]page=(\d+)""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()
                ?: page
            return CatalogResult(items.toList(), page, pageCount.coerceAtLeast(page), items.size)
        }
        // Fallback to old selectors
        val selectors = listOf("article.g-block", "article", ".post", ".entry", ".g-block", ".listing-item")
        for (sel in selectors) {
            val nodes = doc.select(sel)
            if (nodes.isEmpty()) continue
            for (node in nodes) {
                val a = node.selectFirst("a[href]") ?: continue
                val href = normalizeUrl(a.absUrl("href").ifBlank { a.attr("href") }, baseUrl) ?: continue
                if (!isContentUrl(href)) continue
                val title = node.selectFirst("h1,h2,h3,h4,.title,.post-title,.g-title")?.text()?.trim()?.takeIf { it.length > 2 }
                    ?: a.attr("title").trim().takeIf { it.length > 2 }
                    ?: a.text().trim().takeIf { it.length > 4 } ?: continue
                if (title.equals("Download", true) || title.equals("Скачать", true)) continue
                val cover = node.selectFirst("img")?.let { img -> normalizeUrl(img.attr("data-src").ifBlank { img.attr("data-lazy-src").ifBlank { img.attr("src") } }, baseUrl) }
                val id = href.trimEnd('/').substringAfterLast('/').ifBlank { href.hashCode().toString() }
                items += ModEntry(id = id, title = title, coverUrl = cover, pageUrl = href, category = category.name.lowercase(), typeId = category.typeId ?: 1, source = "mcpedl")
            }
            if (items.isNotEmpty()) break
        }
        val page = Regex("""[?&]page=(\d+)""").find(baseUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val pageCount = doc.select("a[href*='page=']").mapNotNull { Regex("""[?&]page=(\d+)""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()?.coerceAtLeast(page) ?: page
        return CatalogResult(items.toList(), page, pageCount, items.size)
    }

    fun parseDetail(entry: ModEntry, html: String, baseUrl: String): ModEntry {
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: entry.title
        val description = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".entry-content p, .post-content p, article p, .content p")?.text()?.trim()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { normalizeUrl(it, baseUrl) } ?: entry.coverUrl
        val downloadLinks = linkedSetOf<String>()
        doc.select("a[href]").forEach { a ->
            val href = normalizeUrl(a.absUrl("href").ifBlank { a.attr("href") }, baseUrl) ?: return@forEach
            val text = a.text().lowercase(Locale.ROOT)
            val lower = href.lowercase(Locale.ROOT)
            if (isDownloadLink(lower, text)) downloadLinks += href
        }
        val author = doc.selectFirst(".author a, .entry-author, [rel=author], .byline a")?.text()?.trim()
        return entry.copy(title = title, author = author ?: entry.author, description = description ?: entry.description, coverUrl = cover, downloadLinks = if (downloadLinks.isNotEmpty()) downloadLinks.toList() else entry.downloadLinks)
    }

    protected fun normalizeUrl(raw: String, base: String): String? {
        val v = raw.trim()
        if (v.isBlank() || v.startsWith("data:") || v.startsWith("javascript:")) return null
        return try {
            val resolved = URI(base).resolve(v).toString()
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
        } catch (_: Exception) { null }
    }

    protected fun isContentUrl(url: String): Boolean {
        val u = url.lowercase(Locale.ROOT)
        if (!u.contains("mcpedl.com/")) return false
        if (u.contains("/api/") || u.contains("/download/") || u.contains("?s=") || u.contains("/tag/") || u.contains("/category/") || u.contains("/author/")) return false
        val path = u.substringAfter("mcpedl.com").substringBefore('?').trim('/')
        if (path.isBlank()) return false
        val reserved = setOf("mods", "maps", "texture-packs", "shaders", "addons", "search", "login", "register")
        if (path in reserved) return false
        return path.count { it == '/' } <= 3
    }

    protected fun isDownloadLink(lower: String, text: String): Boolean {
        if (hasArchiveExtension(lower)) return true
        return (text.contains("download") || text.contains("скачать")) && (lower.contains("download") || lower.contains("file") || lower.contains("mediafire") || lower.contains("drive.google"))
    }

    protected fun hasArchiveExtension(url: String): Boolean {
        val l = url.substringBefore('?').lowercase(Locale.ROOT)
        return listOf(".mcpack", ".mcaddon", ".mcworld", ".zip", ".mctemplate").any { l.endsWith(it) }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}
