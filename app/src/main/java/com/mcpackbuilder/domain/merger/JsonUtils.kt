package com.mcpackbuilder.domain.merger

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Утилиты для "грязного" JSON из модов: комментарии, висячие запятые,
 * глубокое слияние объектов и слияние языковых файлов.
 */
object JsonUtils {

    private val parser = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val printer = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inString = false
        var escaped = false
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when {
                c == '"' -> {
                    inString = true
                    out.append(c)
                    i++
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '/' -> {
                    i += 2
                    while (i < src.length && src[i] != '\n' && src[i] != '\r') i++
                }
                c == '/' && i + 1 < src.length && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < src.length && !(src[i] == '*' && src[i + 1] == '/')) i++
                    if (i + 1 < src.length) i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    fun stripTrailingCommas(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inString = false
        var escaped = false
        while (i < src.length) {
            val c = src[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            if (c == '"') {
                inString = true
                out.append(c)
                i++
                continue
            }
            if (c == ',') {
                var j = i + 1
                while (j < src.length && src[j].isWhitespace()) j++
                if (j < src.length && (src[j] == '}' || src[j] == ']')) {
                    i++
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    fun sanitize(src: String): String = stripTrailingCommas(stripComments(src))

    fun parseElement(src: String): JsonElement? = try {
        parser.parseToJsonElement(sanitize(src))
    } catch (_: Exception) {
        try {
            parser.parseToJsonElement(src)
        } catch (_: Exception) {
            null
        }
    }

    fun parseObject(src: String): JsonObject? = parseElement(src) as? JsonObject

    fun encode(element: JsonElement): String = printer.encodeToString(JsonElement.serializer(), element)

    fun deepMerge(base: JsonObject, overlay: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(base)
        for ((key, value) in overlay) {
            val old = out[key]
            out[key] = if (old is JsonObject && value is JsonObject) deepMerge(old, value) else value
        }
        return JsonObject(out)
    }

    /** Объединяет .lang: значения более приоритетного мода перекрывают прежние. */
    fun mergeLang(lowPriority: String, highPriority: String): String {
        val values = LinkedHashMap<String, String>()
        val comments = LinkedHashSet<String>()
        fun consume(text: String) {
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trimEnd('\r')
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    if (trimmed.isNotEmpty()) comments.add(line)
                    return@forEach
                }
                val eq = line.indexOf('=')
                if (eq <= 0) {
                    comments.add(line)
                    return@forEach
                }
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1)
                if (key.isNotEmpty()) values[key] = value
            }
        }
        consume(lowPriority)
        consume(highPriority)
        return buildString {
            comments.forEach { appendLine(it) }
            values.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
        }
    }

    fun maxVersion(a: List<Int>, b: List<Int>): List<Int> {
        val size = maxOf(a.size, b.size)
        return (0 until size).map { i -> maxOf(a.getOrElse(i) { 0 }, b.getOrElse(i) { 0 }) }
    }

    fun compareVersion(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun intList(element: JsonElement?): List<Int>? {
        val arr = element as? JsonArray ?: return null
        val list = arr.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        return list.ifEmpty { null }
    }

    fun stringOf(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    fun longOf(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.longOrNull

    fun objArray(obj: JsonObject, key: String): List<JsonObject> =
        (obj[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    fun primitiveInt(element: JsonElement?): Int? = (element as? JsonPrimitive)?.intOrNull
}
