package com.mcpackbuilder

import android.app.Application
import com.mcpackbuilder.data.parser.McpedlParser
import com.mcpackbuilder.data.store.DownloadRepository
import com.mcpackbuilder.data.store.LibraryStore
import com.mcpackbuilder.data.store.SelectionStore
import com.mcpackbuilder.data.web.WebFetcher
import com.mcpackbuilder.domain.merger.AddonMerger
import com.mcpackbuilder.ui.theme.ThemeSettings
import java.io.File

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(val app: Application) {
    val theme = ThemeSettings(app)
    val fetcher = WebFetcher(app)
    val parser = McpedlParser()
    val selection = SelectionStore(app)
    val downloads = DownloadRepository(app)
    val library = LibraryStore(app)
    val merger = AddonMerger()

    val buildsDir: File get() = File(app.filesDir, "builds").apply { mkdirs() }
    val workDir: File get() = File(app.cacheDir, "merge_work").apply { mkdirs() }
}
