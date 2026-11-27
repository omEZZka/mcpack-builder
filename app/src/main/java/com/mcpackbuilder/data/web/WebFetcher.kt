package com.mcpackbuilder.data.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class WebPage(val html: String, val finalUrl: String)

class WebFetcher(private val context: Context) {

    suspend fun getDocument(url: String): WebPage = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http")
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val doc: Document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(5_000_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .get()
                return@withContext WebPage(doc.outerHtml(), doc.location())
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) kotlinx.coroutines.delay(600L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Не удалось загрузить $url")
    }

    suspend fun fetch(url: String): WebPage = getDocument(url)

    fun openBrowser(url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }
}
