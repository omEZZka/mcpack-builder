package com.mcpackbuilder.data.store

import android.content.Context
import com.mcpackbuilder.domain.model.DownloadedFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class DownloadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("mcpack_downloads", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DownloadedFile.serializer())

    private val _files = MutableStateFlow(load())
    val files: StateFlow<List<DownloadedFile>> = _files.asStateFlow()

    fun add(path: String, url: String, modId: String?): DownloadedFile {
        val record = DownloadedFile(url = url, path = path, modId = modId)
        val updated = (listOf(record) + _files.value.filterNot { it.path == path }).take(60)
        _files.value = updated
        persist(updated)
        return record
    }

    fun latestFor(modId: String): DownloadedFile? =
        _files.value.firstOrNull { it.modId == modId && File(it.path).isFile }

    fun refresh() {
        val valid = _files.value.filter { File(it.path).isFile }
        _files.value = valid
        persist(valid)
    }

    private fun load(): List<DownloadedFile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .filter { File(it.path).isFile }
    }

    private fun persist(list: List<DownloadedFile>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
