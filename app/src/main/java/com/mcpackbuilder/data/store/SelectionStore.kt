package com.mcpackbuilder.data.store

import android.content.Context
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class SelectionStore(context: Context) {

    private val prefs = context.getSharedPreferences("mcpack_selection", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ModEntry.serializer())

    private val _mods = MutableStateFlow(load())
    val mods: StateFlow<List<ModEntry>> = _mods.asStateFlow()

    @Synchronized
    fun add(mod: ModEntry): Boolean {
        val current = _mods.value
        if (current.any { it.pageUrl == mod.pageUrl || it.id == mod.id }) return false
        update(current + mod)
        return true
    }

    @Synchronized
    fun addAll(mods: List<ModEntry>): Int {
        val current = _mods.value.toMutableList()
        var added = 0
        mods.forEach { mod ->
            if (current.none { it.pageUrl == mod.pageUrl || it.id == mod.id }) {
                current.add(mod)
                added++
            }
        }
        if (added > 0) update(current)
        return added
    }

    @Synchronized
    fun remove(id: String) {
        update(_mods.value.filterNot { it.id == id })
    }

    @Synchronized
    fun clear() {
        update(emptyList())
    }

    @Synchronized
    fun move(id: String, delta: Int) {
        val list = _mods.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        val item = list.removeAt(index)
        list.add(target, item)
        update(list)
    }

    @Synchronized
    fun attachFile(id: String, path: String) {
        update(_mods.value.map { if (it.id == id) it.copy(filePath = path) else it })
    }

    @Synchronized
    fun updateMod(id: String, transform: (ModEntry) -> ModEntry) {
        update(_mods.value.map { if (it.id == id) transform(it) else it })
    }

    @Synchronized
    private fun update(list: List<ModEntry>) {
        _mods.value = list
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
    }

    private fun load(): List<ModEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "mods"
    }
}
