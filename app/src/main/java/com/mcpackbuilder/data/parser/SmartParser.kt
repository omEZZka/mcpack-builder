package com.mcpackbuilder.data.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale

data class ParsedDetail(
    val title: String?,
    val description: String?,
    val coverUrl: String?,
    val downloadLinks: List<String>,
    val author: String? = null,
    val version: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * «Умный парсер»: извлекает данные о моде несколькими стратегиями
 * (JSON-LD → meta-теги → эвристики по DOM → сырой поиск ссылок в HTML),
 * объединяет результат и нормализует его.
 */
object SmartParser {

    private val ARCHIVE_EXT = listOf(".mcpack", ".mcaddon", ".mcworld", ".mctemplate", ".zip")
    private val DOWNLOAD_HOSTS = listOf(
        "edge.forgecdn.net", "edge.mcpedl.com", "mediafire.com", "drive.google.com",
        "mega.nz", "dropbox.com", "cdn.mcpedl.com", "files.minecraftforge.net"
    )
    private val ARCHIVE_URL_REGEX = Regex(
        """https?:\\?/\\?/(?:edge\.forgecdn\.net|edge\.mcpedl\.com|cdn\.mcpedl\.com)/files\\?/[^\s"'<>\\]+""",
        RegexOption.IGNORE_CASE
    )

    fun parseDetail(html: String, baseUrl: String, fallbackTitle: String? = null, fallbackCover: String? = null): ParsedDetail {
        if (html.isBlank()) return ParsedDetail(fallbackTitle, null, fallbackCover, emptyList())
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull()
        if (doc == null) return ParsedDetail(fallbackTitle, null, fallbackCover, emptyList())

        val title = firstNonBlank(
            doc.selectFirst("meta[property=og:title]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content"),
            doc.selectFirst("h1.entry-title, h1.post-title, h1")?.text(),
            fallbackTitle
        )

        val cover = firstNonBlank(
            doc.selectFirst("meta[property=og:image]")?.attr("content")?.let { abs(it, baseUrl) },
            doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.let { abs(it, baseUrl) },
            fallbackCover
        )

        val description = firstNonBlank(
            doc.selectFirst("meta[name=description]")?.attr("content"),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst(".description-field, .introduction-field, .entry-content p, .post-content p, article p")?.text(),
            doc.selectFirst("p")?.text()
        )?.trim()?.take(1200)

        val author = firstNonBlank(
            doc.selectFirst("meta[name=author]")?.attr("content"),
            doc.selectFirst("[rel=author], .author a, .entry-author, .byline a, .submission-header__author")?.text()
        )

        val version = doc.selectFirst(".version, .submission-version, [class*=version]")?.text()
            ?.let { Regex("""\d+(?:\.\d+){1,3}""").find(it)?.value }

        val links = linkedSetOf<String>()

        // 1) JSON-LD структурированные данные
        doc.select("script[type=application/ld+json]").forEach { script ->
            runCatching { Json.parseToJsonElement(script.data()) }.getOrNull()?.let { sweepJson(it, links, baseUrl) }
        }

        // 2) Явные ссылки в разметке
        doc.select("a[href]").forEach { a ->
            val href = abs(a.absUrl("href").ifBlank { a.attr("href") }, baseUrl) ?: return@forEach
            if (isDownloadLink(href, a.text())) links += href
        }

        // 3) Сырой поиск CDN-ссылок в HTML (обход динамической вёрстки)
        ARCHIVE_URL_REGEX.findAll(html).forEach { m ->
            val cleaned = normalizeEscaped(m.value)
            if (isDownloadLink(cleaned, "")) links += cleaned
        }

        val tags = inferTags(html)
        return ParsedDetail(
            title = title?.trim()?.takeIf { it.isNotBlank() },
            description = description,
            coverUrl = cover,
            downloadLinks = links.toList(),
            author = author?.trim()?.takeIf { it.isNotBlank() },
            version = version,
            tags = tags
        )
    }

    /**
     * Умное извлечение архивных ссылок из произвольного текста/JSON (ответ API, HTML).
     */
    fun extractArchiveLinks(text: String, baseUrl: String? = null): List<String> {
        if (text.isBlank()) return emptyList()
        val out = linkedSetOf<String>()
        ARCHIVE_URL_REGEX.findAll(text).forEach { out += normalizeEscaped(it.value) }
        // любые http-ссылки с архивным расширением
        Regex("""https?://[^\s"'<>\\]+""").findAll(text).forEach { m ->
            val url = normalizeEscaped(m.value)
            if (hasArchiveExtension(url)) out += url
        }
        return out.toList()
    }

    private fun sweepJson(element: JsonElement, into: MutableSet<String>, baseUrl: String) {
        when (element) {
            is JsonObject -> element.values.forEach { sweepJson(it, into, baseUrl) }
            is JsonArray -> element.forEach { sweepJson(it, into, baseUrl) }
            is JsonPrimitive -> {
                if (!element.isString) return
                val value = element.content
                if (!value.startsWith("http")) return
                val url = abs(normalizeEscaped(value), baseUrl) ?: return
                if (isDownloadLink(url, "")) into += url
            }
            else -> Unit
        }
    }

    private fun isDownloadLink(url: String, text: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("http")) return false
        if (hasArchiveExtension(lower)) {
            if (text.contains("download", true) || text.contains("скачать", true)) return true
            // архивная ссылка на CDN/файлохостинге — почти всегда скачивание
            return DOWNLOAD_HOSTS.any { lower.contains(it) } || lower.contains("/files/") || lower.endsWith(".zip")
        }
        if (text.contains("download", true) || text.contains("скачать", true)) {
            return DOWNLOAD_HOSTS.any { lower.contains(it) } || lower.contains("/download") || lower.contains("/file")
        }
        return false
    }

    fun hasArchiveExtension(url: String): Boolean {
        val l = url.substringBefore('?').lowercase(Locale.ROOT)
        return ARCHIVE_EXT.any { l.endsWith(it) }
    }

    /** Подсказка тегов по ключевым словам в заголовке/описании. */
    fun inferTags(text: String): List<String> {
        val t = Jsoup.parse(text).text().lowercase(Locale.ROOT)
        val map = linkedMapOf(
            "horror" to listOf("horror", "хоррор", "scary", "terror", "жутк"),
            "magic" to listOf("magic", "магия", "spell", "rune", "enchant"),
            "survival" to listOf("survival", "выжив", "hardcore"),
            "pvp" to listOf("pvp", "combat", "battle", "бой", "сражен"),
            "realistic" to listOf("realistic", "реализм", "realism", "shader", "шейдер"),
            "parkour" to listOf("parkour", "паркур", "jump"),
            "adventure" to listOf("adventure", "приключ", "quest", "rpg"),
            "fantasy" to listOf("fantasy", "фэнтези", "dragon", "elf", "myth"),
            "technology" to listOf("technology", "техно", "machine", "tech", "factory"),
            "multiplayer" to listOf("multiplayer", "мультиплеер", "server", "realm"),
            "food" to listOf("food", "еда", "cooking", "recipe"),
            "utility" to listOf("utility", "утилит", "tool", "helper", "manager"),
            "cosmetics" to listOf("cosmetic", "космет", "cape", "skin", "hat"),
            "roleplay" to listOf("roleplay", "ролев", "city", "job")
        )
        return map.filter { (_, kws) -> kws.any { t.contains(it) } }.keys.toList()
    }

    private fun normalizeEscaped(raw: String): String =
        raw.replace("\\/", "/").replace("\\u002F", "/", ignoreCase = true).trim().trimEnd('\\', '"')

    private fun abs(raw: String?, base: String): String? {
        val v = raw?.trim() ?: return null
        if (v.isBlank() || v.startsWith("data:") || v.startsWith("javascript:")) return null
        return try {
            val resolved = URI(base).resolve(v).toString()
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else null
        } catch (_: Exception) { null }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}
