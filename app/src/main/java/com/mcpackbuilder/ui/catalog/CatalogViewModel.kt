package com.mcpackbuilder.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.data.parser.McpedlRepository
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
    val query: String = "",
    val category: ModCategory = ModCategory.ADDONS,
    val items: List<ModEntry> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val selectedUrls: Set<String> = emptySet()
)

class CatalogViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = McpedlRepository(container.parser, container.fetcher)
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            container.selection.mods.collect { mods ->
                _state.update { it.copy(selectedUrls = mods.map { m -> m.pageUrl }.toSet()) }
            }
        }
        load(reset = true)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() = load(reset = true)

    fun refresh() = load(reset = true)

    fun setCategory(category: ModCategory) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category) }
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore) return
        if (current.page >= current.pageCount) return
        load(reset = false)
    }

    fun addToBuild(mod: ModEntry) {
        val added = container.selection.add(mod)
        _state.update {
            it.copy(
                message = if (added) "«${mod.title}» добавлен в сборку" else "«${mod.title}» уже в сборке"
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (reset && current.loading) {
            searchJob?.cancel()
        }
        if (!reset && (current.loading || current.loadingMore)) return
        val page = if (reset) 1 else current.page + 1
        _state.update {
            it.copy(loading = reset, loadingMore = !reset, error = null)
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val result = repository.catalog(
                    query = current.query.trim(),
                    category = current.category,
                    page = page
                )
                _state.update { s ->
                    val merged = if (reset) result.items else (s.items + result.items)
                    s.copy(
                        items = merged.distinctBy { it.pageUrl },
                        page = result.page,
                        pageCount = result.pageCount,
                        total = result.total,
                        loading = false,
                        loadingMore = false,
                        loaded = true,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        loaded = true,
                        error = e.message ?: "Не удалось загрузить каталог"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(container) }
        }
    }
}
