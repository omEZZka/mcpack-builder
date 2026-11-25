package com.mcpackbuilder.data.store

import android.content.Context
import com.mcpackbuilder.domain.model.SavedBuild
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class LibraryStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("mcpack_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedBuild.serializer())

    private val _builds = MutableStateFlow(load())
    val builds: StateFlow<List<SavedBuild>> = _builds.asStateFlow()

    fun save(build: SavedBuild) {
        val updated = (listOf(build) + _builds.value.filterNot { it.id == build.id })
            .sortedByDescending { it.createdAt }
        _builds.value = updated
        persist(updated)
    }

    fun delete(id: String) {
        val build = _builds.value.firstOrNull { it.id == id }
        if (build != null) {
            val file = File(build.filePath)
            file.delete()
            val parent = file.parentFile
            if (parent != null && parent.name == id) parent.deleteRecursively()
        }
        val updated = _builds.value.filterNot { it.id == id }
        _builds.value = updated
        persist(updated)
    }

    fun refresh() {
        val existing = _builds.value.filter { File(it.filePath).isFile }
        if (existing.size != _builds.value.size) {
            _builds.value = existing
            persist(existing)
        }
    }

    private fun load(): List<SavedBuild> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }

    private fun persist(list: List<SavedBuild>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
    }

    private companion object {
        const val KEY = "builds"
    }
}
