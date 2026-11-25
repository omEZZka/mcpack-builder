package com.mcpackbuilder.data.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class WebPage(val html: String, val finalUrl: String)

class WebFetcher(private val context: Context) {
    suspend fun getDocument(url: String): WebPage = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http")
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36")
            .timeout(15000)
            .maxBodySize(2_000_000)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .get()
        WebPage(doc.outerHtml(), doc.location())
    }

    suspend fun fetch(url: String): WebPage = getDocument(url)

    fun openBrowser(url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https" || uri.scheme == "http")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
