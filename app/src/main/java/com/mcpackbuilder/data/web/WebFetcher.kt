package com.mcpackbuilder.data.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class WebPage(val html: String, val finalUrl: String)

class WebFetcher(private val context: Context) {
    suspend fun fetch(url: String): WebPage = withContext(Dispatchers.IO) {
        require(Uri.parse(url).host == "mcpedl.com")
        val doc = Jsoup.connect(url).timeout(15000).maxBodySize(2_000_000).get()
        WebPage(doc.outerHtml(), doc.location())
    }
    fun openBrowser(url: String) {
        val uri = Uri.parse(url)
        require(uri.scheme == "https")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
