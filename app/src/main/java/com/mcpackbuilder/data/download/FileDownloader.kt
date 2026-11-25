package com.mcpackbuilder.data.download

import android.content.Context
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileDownloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    suspend fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): File = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IOException("Неподдерживаемая ссылка: $url")
        }
        val directory = File(context.cacheDir, "downloads").apply { mkdirs() }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 90_000
                setRequestProperty("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code при скачивании")
            val contentType = connection.contentType
            if (contentType != null && contentType.contains("text/html", ignoreCase = true)) {
                throw IOException("Ссылка ведёт на HTML-страницу, а не на файл")
            }
            val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
            var name = sanitizeFileName(guessed)
            if (name.isBlank()) {
                name = "mod_${System.currentTimeMillis()}.mcpack"
            }
            if (!name.contains('.')) {
                name += extensionFor(mimeType)
            }
            val target = uniqueFile(directory, name)
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() == 0L) {
                target.delete()
                throw IOException("Сервер вернул пустой файл")
            }
            if (!looksLikeZip(target)) {
                target.delete()
                throw IOException("Файл не похож на .mcpack/.mcaddon/.zip (ожидался ZIP-архив)")
            }
            target
        } finally {
            connection?.disconnect()
        }
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().take(120)

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${base}_$index$ext")
            index++
        }
        return candidate
    }

    private fun extensionFor(mimeType: String?): String {
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext.isNullOrBlank()) ".mcpack" else ".$ext"
    }

    private fun looksLikeZip(file: File): Boolean {
        if (file.length() < 4) return false
        return file.inputStream().use { input ->
            val signature = ByteArray(4)
            if (input.read(signature) != 4) return@use false
            signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte() &&
                (signature[2] == 0x03.toByte() || signature[2] == 0x05.toByte() ||
                    signature[2] == 0x07.toByte())
        }
    }
}
