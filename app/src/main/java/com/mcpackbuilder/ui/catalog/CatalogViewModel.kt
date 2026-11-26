package com.mcpackbuilder.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.data.download.MapImporter
import com.mcpackbuilder.data.parser.McpedlRepository
import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.CatalogSource
import com.mcpackbuilder.domain.model.CatalogTag
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val sort: CatalogSort = CatalogSort.NEW,
    val source: CatalogSource = CatalogSource.MCPEDL,
    val query: String = "",
    val category: ModCategory = ModCategory.MODS,
    val tag: CatalogTag = CatalogTag.ALL,
    val items: List<ModEntry> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val selectedUrls: Set<String> = emptySet(),
    val message: String? = null
)

class CatalogViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = McpedlRepository(container.parser, container.fetcher)
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.selection.mods.collect { mods ->
                _state.update { it.copy(selectedUrls = mods.map { m -> m.pageUrl }.toSet()) }
            }
        }
        load(true)
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q) } }
    fun setSort(s: CatalogSort) { _state.update { it.copy(sort = s) }; load(true) }
    fun setSource(src: CatalogSource) { _state.update { it.copy(source = src) }; load(true) }
    fun setCategory(c: ModCategory) { _state.update { it.copy(category = c) }; load(true) }
    fun setTag(t: CatalogTag) { _state.update { it.copy(tag = t) }; load(true) }
    fun consumeMessage() { _state.update { it.copy(message = null) } }

    fun addToBuild(mod: ModEntry) {
        val added = container.selection.add(mod)
        _state.update { it.copy(message = if (added) "add:${mod.title}" else "exists:${mod.title}") }
    }

    fun downloadMap(mod: ModEntry) {
        viewModelScope.launch {
            _state.update { it.copy(message = "downloading:${mod.title}") }
            try {
                val enriched = repo.enrich(mod, _state.value.source)
                val link = repo.directDownload(enriched.downloadLinks) ?: enriched.downloadLinks.firstOrNull()
                    ?: throw IllegalStateException("no-link")
                MapImporter.download(container, mod.title, link)
                _state.update { it.copy(message = "map-saved:${mod.title}") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "map-error:${e.message ?: ""}") }
            }
        }
    }

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        val current = _state.value
        val nextPage = if (refresh) 1 else current.page
        loadJob = viewModelScope.launch {
            _state.update { 
                it.copy(
                    loading = refresh || !it.loaded, 
                    loadingMore = !refresh && it.loaded, 
                    error = null, 
                    page = nextPage,
                    items = if (refresh) emptyList() else it.items
                ) 
            }
            try {
                // Only pass tag if it's not ALL - API doesn't support ALL tag
                val tagToUse = if (current.tag != CatalogTag.ALL && current.query.trim().isEmpty()) current.tag else CatalogTag.ALL
                val result = repo.catalog(current.query, current.category, nextPage, current.sort, current.source, tagToUse)
                _state.update {
                    val merged = if (refresh) result.items else (it.items + result.items).distinctBy { m -> m.pageUrl }
                    it.copy(
                        items = merged, 
                        page = result.page, 
                        pageCount = result.pageCount, 
                        total = result.total, 
                        loading = false, 
                        loadingMore = false, 
                        loaded = true, 
                        error = if (merged.isEmpty() && current.tag != CatalogTag.ALL) "По тегу '${current.tag.name}' ничего не найдено" else null
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.update { 
                    it.copy(
                        loading = false, 
                        loadingMore = false, 
                        loaded = true, 
                        error = "Ошибка: ${e.message ?: "неизвестная"}"
                    ) 
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.page >= s.pageCount) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true, error = null) }
            try {
                val result = repo.catalog(s.query, s.category, s.page + 1, s.sort, s.source, s.tag)
                _state.update {
                    val merged = (it.items + result.items).distinctBy { m -> m.pageUrl }
                    it.copy(items = merged, page = result.page, pageCount = result.pageCount, total = result.total, loadingMore = false, loaded = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "error") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(container) }
        }
    }
}
