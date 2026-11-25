package com.mcpackbuilder.data.parser

import com.mcpackbuilder.domain.merger.JsonUtils
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

data class CatalogPage(
    val items: List<ModEntry>,
    val page: Int,
    val pageCount: Int,
    val total: Int
)

/**
 * Парсер MCPEDL: быстрый JSON API + HTML-фолбэк через Jsoup.
 * HTML-путь используется, когда API недоступен или Cloudflare отдаёт страницу.
 */
class McpedlParser {

    companion object {
        private const val API_BASE = "https://api.mcpedl.com/api/"
        private const val SITE = "https://mcpedl.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        private val SECTION_BY_TYPE = mapOf(
            1 to "addons",
            2 to "maps",
            3 to "texture-packs",
            6 to "skins",
            9 to "scripts"
        )

        private val CATEGORY_SLUGS = setOf(
            "addons", "maps", "texture-packs", "skins", "scripts", "shaders", "seeds",
            "servers", "mods", "data-packs", "cosmetics", "fantasy", "food", "horror",
            "magic", "modjam-2025", "multiplayer", "performance", "roleplay", "survival",
            "technology", "utility", "vanilla", "armor-tools-and-weapons", "pvp", "realistic",
            "simplistic", "themed", "miscellaneous", "x16", "x32", "x64", "x128", "players",
            "mobs", "search", "login", "register", "user", "tag", "tags", "page"
        )

        private val VERSION_REGEX = Regex(
            """(?i)(?:minecraft|mcpe|bedrock|version|версия|v)\s*[:\-]?\s*(\d+\.\d+(?:\.\d+)?)"""
        )
        private val PLAIN_VERSION = Regex("""^\d+\.\d+(?:\.\d+)?$""")
        private val DOWNLOAD_EXT = Regex("""(?i)\.(mcpack|mcaddon|mcworld|zip)(?:$|\?)""")
        private val FILE_HOSTS = listOf(
            "mediafire.com", "lootlabs", "linkvertise", "forgecdn.net", "curseforge.com",
            "dropbox.com", "drive.google.com", "mega.nz", "mcpedl.com/download"
        )
    }

    // ---------- API ----------

    suspend fun searchApi(query: String, category: ModCategory, page: Int): CatalogPage? {
        val params = LinkedHashMap<String, String>()
        params["page"] = page.toString()
        if (query.isNotBlank()) params["q"] = query.trim()
        category.typeId?.let { params["type_id"] = it.toString() }
        val body = apiGet("search/advanced", params) ?: return null
        val results = (body["results"] as? JsonArray) ?: return null
        val meta = body["meta"] as? JsonObject
        val items = results.mapNotNull { (it as? JsonObject)?.let { obj -> mapSearchItem(obj) } }
        return CatalogPage(
            items = items,
            page = JsonUtils.primitiveInt(meta?.get("page")) ?: page,
            pageCount = JsonUtils.primitiveInt(meta?.get("page_count")) ?: 1,
            total = JsonUtils.primitiveInt(meta?.get("total")) ?: items.size
        )
    }

    suspend fun detailsApi(mod: ModEntry): ModEntry? {
        val slug = slugOf(mod.pageUrl)
        val queries = listOfNotNull(slug, mod.title).distinct()
        for (query in queries) {
            val body = apiGet(
                "load-submission-by-type-and-slug",
                mapOf("s" to query, "per_page" to "20")
            ) ?: continue
            val data = (body["data"] as? JsonArray) ?: continue
            val objects = data.mapNotNull { it as? JsonObject }
            val match = objects.firstOrNull {
                (it["slug"] as? JsonPrimitive)?.contentOrNull.equals(slug, ignoreCase = true)
            } ?: objects.firstOrNull {
                (it["slug"] as? JsonPrimitive)?.contentOrNull == mod.id
            } ?: continue
            return mapDetail(mod, match)
        }
        return null
    }

    private suspend fun apiGet(path: String, params: Map<String, String>): JsonObject? = withContext(Dispatchers.IO) {
        val query = params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        val url = API_BASE + path + if (query.isEmpty()) "" else "?$query"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Origin", SITE)
                setRequestProperty("Referer", "$SITE/")
            }
            val code = connection.responseCode
            if (code !in 200..299) return@withContext null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JsonUtils.parseObject(text)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapSearchItem(obj: JsonObject): ModEntry? {
        val slug = (obj["slug"] as? JsonPrimitive)?.contentOrNull ?: return null
        val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val typeId = (obj["type_id"] as? JsonPrimitive)?.intOrNull ?: 1
        val section = SECTION_BY_TYPE[typeId] ?: "addons"
        val tags = (obj["cf_tags"] as? JsonArray)?.mapNotNull { tag ->
            (tag as? JsonObject)?.let { (it["name"] as? JsonPrimitive)?.contentOrNull }
        }.orEmpty()
        val summary = (obj["summary"] as? JsonPrimitive)?.contentOrNull
        val version = tags.firstOrNull { PLAIN_VERSION.matches(it.trim()) }
            ?: extractVersion("$title ${summary.orEmpty()}")
        return ModEntry(
            id = slug,
            title = title,
            author = (obj["display_name"] as? JsonPrimitive)?.contentOrNull,
            category = tags.firstOrNull() ?: section,
            version = version,
            coverUrl = (obj["image"] as? JsonPrimitive)?.contentOrNull,
            pageUrl = "$SITE/$section/$slug/",
            description = summary,
            downloadCount = (obj["downloadCount"] as? JsonPrimitive)?.longOrNull,
            typeId = typeId
        )
    }

    private fun mapDetail(base: ModEntry, obj: JsonObject): ModEntry {
        val downloads = (obj["downloads"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { d -> (d["downloadUrl"] as? JsonPrimitive)?.contentOrNull } }
            .orEmpty()
        val images = (obj["submission_images"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val thumbnails = obj["thumbnails"] as? JsonObject
        val largeThumb = (thumbnails?.get("large") as? JsonPrimitive)?.contentOrNull
        val tags = (obj["tags"] as? JsonArray)?.mapNotNull { tag ->
            (tag as? JsonObject)?.let { (it["name"] as? JsonPrimitive)?.contentOrNull }
        }.orEmpty()
        val version = tags.firstOrNull { PLAIN_VERSION.matches(it.trim()) }
            ?: extractVersion(tags.joinToString(" "))
            ?: base.version
        val short = (obj["short_description"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["summary"] as? JsonPrimitive)?.contentOrNull
        val slug = (obj["slug"] as? JsonPrimitive)?.contentOrNull ?: base.id
        return base.copy(
            id = slug,
            title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim().takeUnless { it.isNullOrBlank() } ?: base.title,
            author = (obj["display_name"] as? JsonPrimitive)?.contentOrNull ?: base.author,
            version = version,
            coverUrl = base.coverUrl ?: largeThumb,
            description = short,
            screenshots = (listOfNotNull(largeThumb) + images).distinct().take(12),
            downloadLinks = downloads.distinct(),
            downloadCount = (obj["downloadCount"] as? JsonPrimitive)?.longOrNull ?: base.downloadCount
        )
    }

    // ---------- HTML (Jsoup) ----------

    fun parseSearchHtml(html: String, baseUrl: String): List<ModEntry> {
        val doc = Jsoup.parse(html, baseUrl)
        val result = LinkedHashMap<String, ModEntry>()
        for (anchor in doc.select("a[href]")) {
            val href = anchor.absUrl("href").ifBlank { resolve(baseUrl, anchor.attr("href")) }
            if (href.isBlank()) continue
            val uri = runCatching { URI(href) }.getOrNull() ?: continue
            if (!uri.host.isNullOrBlank() && uri.host !in setOf("mcpedl.com", "www.mcpedl.com")) continue
            val path = uri.path ?: continue
            if (!path.endsWith("/")) continue
            val segments = path.trim('/').split('/')
            if (segments.size != 2) continue
            if (segments[0].lowercase() !in SECTION_BY_TYPE.values) continue
            if (segments[1].lowercase() in CATEGORY_SLUGS) continue
            val pageUrl = "$SITE/${segments[0]}/${segments[1]}/"
            if (result.containsKey(pageUrl)) continue

            val card = anchor.closest(
                "article, .article-card, .c-card, .search-result, .results-grid > div, .shelf__item, .media-card, li"
            ) ?: anchor.parent() ?: anchor
            val title = card.selectFirst("h1, h2, h3, h4, .title, .submission-title")
                ?.text()?.trim()
                ?.takeUnless { it.isBlank() }
                ?: anchor.text().trim().takeUnless { it.isBlank() }
                ?: anchor.selectFirst("img")?.attr("alt")?.trim().orEmpty()
            if (title.length < 2) continue
            val img = card.selectFirst("img")
            val cover = img?.let { firstImageUrl(it, baseUrl) }
            val author = card.selectFirst("a[href*=/user/], a[href*=/author/], .author, .username")
                ?.text()?.trim()?.takeUnless { it.isBlank() }
            val text = card.text()
            val typeId = SECTION_BY_TYPE.entries.firstOrNull { it.value == segments[0] }?.key ?: 1
            result[pageUrl] = ModEntry(
                id = segments[1],
                title = title,
                author = author,
                category = segments[0],
                version = extractVersion(text),
                coverUrl = cover,
                pageUrl = pageUrl,
                description = card.selectFirst("p, .description, .summary")?.text()?.trim(),
                typeId = typeId
            )
            if (result.size >= 40) break
        }
        return result.values.toList()
    }

    fun parseModHtml(html: String, mod: ModEntry): ModEntry {
        val doc = Jsoup.parse(html, mod.pageUrl)
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?.takeUnless { it.isBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: mod.title
        val description = doc.selectFirst(
            ".submission-content, .article-content, .entry-content, .description, .content__wrap, main"
        )?.text()?.trim()?.take(4000)
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        val images = doc.select("img").mapNotNull { firstImageUrl(it, mod.pageUrl) }
        val downloads = doc.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { resolve(mod.pageUrl, a.attr("href")) }
            if (href.isBlank()) null else if (isDownloadUrl(href, a.text())) href else null
        }.distinct()
        val author = doc.selectFirst("a[href*=/user/], a[href*=/author/], .author")
            ?.text()?.trim()?.takeUnless { it.isBlank() }
        return mod.copy(
            title = title,
            author = author ?: mod.author,
            description = description ?: mod.description,
            coverUrl = mod.coverUrl ?: ogImage,
            screenshots = (listOfNotNull(ogImage) + images).filter { it.isNotBlank() }.distinct().take(12),
            downloadLinks = downloads,
            version = extractVersion(doc.text()) ?: mod.version
        )
    }

    // ---------- helpers ----------

    private fun firstImageUrl(img: Element, baseUrl: String): String? {
        val candidates = listOf(
            img.attr("src"),
            img.attr("data-src"),
            img.attr("data-lazy-src"),
            img.attr("data-original"),
            img.attr("srcset").substringBefore(' ').substringBefore(',')
        )
        for (candidate in candidates) {
            val value = candidate.trim()
            if (value.isBlank() || value.startsWith("data:")) continue
            val absolute = resolve(baseUrl, value)
            if (absolute.startsWith("http")) return absolute
        }
        return null
    }

    private fun resolve(baseUrl: String, href: String): String {
        if (href.isBlank()) return ""
        return try {
            URI(baseUrl).resolve(href).toString()
        } catch (_: Exception) {
            href
        }
    }

    private fun slugOf(pageUrl: String): String =
        pageUrl.trimEnd('/').substringAfterLast('/')

    fun extractVersion(text: String): String? {
        VERSION_REGEX.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        val parts = text.split(Regex("[^0-9.]+"))
        return parts.firstOrNull { PLAIN_VERSION.matches(it) && it.length in 3..12 }
    }

    private fun isDownloadUrl(url: String, anchorText: String): Boolean {
        val lower = url.lowercase()
        if (DOWNLOAD_EXT.containsMatchIn(lower)) return true
        if (FILE_HOSTS.any { lower.contains(it) }) return true
        val text = anchorText.lowercase()
        return (text.contains("download") || text.contains("скачать")) &&
            (lower.contains("/download") || lower.contains("file"))
    }
}
