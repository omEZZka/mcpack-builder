package com.mcpackbuilder.ui.builder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.data.download.FileDownloader
import com.mcpackbuilder.data.parser.McpedlRepository
import com.mcpackbuilder.domain.model.MergeResult
import com.mcpackbuilder.domain.model.ModEntry
import com.mcpackbuilder.domain.model.SavedBuild
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuilderUiState(
    val mods: List<ModEntry> = emptyList(),
    val buildName: String = "Моя сборка",
    val building: Boolean = false,
    val progress: Int = 0,
    val stage: String = "",
    val logs: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val result: MergeResult? = null,
    val error: String? = null,
    val message: String? = null,
    val downloadingModId: String? = null,
    val downloadStatus: String? = null
)

class BuilderViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = McpedlRepository(container.parser, container.fetcher)
    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.selection.mods.collect { mods ->
                _state.update { it.copy(mods = mods) }
            }
        }
        viewModelScope.launch {
            container.downloads.files.collect { files ->
                val byPath = files.associateBy { it.path }
                _state.update { state ->
                    state.copy(
                        mods = state.mods.map { mod ->
                            if (mod.filePath != null && byPath.containsKey(mod.filePath)) mod else mod
                        }
                    )
                }
            }
        }
    }

    fun setBuildName(name: String) {
        _state.update { it.copy(buildName = name) }
    }

    fun remove(id: String) = container.selection.remove(id)

    fun moveUp(id: String) = container.selection.move(id, -1)

    fun moveDown(id: String) = container.selection.move(id, 1)

    fun clearAll() = container.selection.clear()

    fun download(mod: ModEntry) {
        if (_state.value.downloadingModId != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadingModId = mod.id,
                    downloadStatus = "Получение данных мода…",
                    message = null
                )
            }
            try {
                val enriched = repository.enrich(mod)
                container.selection.updateMod(mod.id) { current ->
                    enriched.copy(filePath = current.filePath)
                }
                val direct = repository.directDownload(enriched.downloadLinks)
                if (direct != null) {
                    _state.update { it.copy(downloadStatus = "Скачивание архива…") }
                    val file = FileDownloader.download(container.app, direct, null, null, null)
                    container.downloads.add(file.absolutePath, direct, mod.id)
                    container.selection.attachFile(mod.id, file.absolutePath)
                    _state.update {
                        it.copy(
                            downloadStatus = null,
                            message = "Файл скачан: ${file.name}"
                        )
                    }
                } else {
                    container.fetcher.openBrowser(enriched.downloadLinks.firstOrNull { it.startsWith("https://") } ?: enriched.pageUrl)
                    _state.update {
                        it.copy(
                            downloadStatus = null,
                            message = "Скачайте архив в браузере и выберите его кнопкой «Из файлов»."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        downloadStatus = null,
                        message = "Не удалось скачать: ${e.message}"
                    )
                }
            } finally {
                _state.update { it.copy(downloadingModId = null) }
            }
        }
    }

    fun build() {
        val snapshot = _state.value
        if (snapshot.building) return
        val mods = snapshot.mods
        if (mods.isEmpty()) {
            _state.update { it.copy(message = "Добавьте хотя бы один мод") }
            return
        }
        if (mods.any { !it.isDownloaded }) {
            _state.update { it.copy(message = "Сначала скачайте все моды (кнопка «Скачать»)") }
            return
        }
        val name = snapshot.buildName.trim().ifBlank { "Моя сборка" }
        val buildId = UUID.randomUUID().toString()
        val outputDir = File(container.buildsDir, buildId).apply { mkdirs() }
        _state.update {
            it.copy(
                building = true,
                progress = 0,
                stage = "Старт",
                logs = emptyList(),
                conflicts = emptyList(),
                result = null,
                error = null,
                message = null
            )
        }
        viewModelScope.launch {
            try {
                val result = container.merger.merge(
                    mods = mods,
                    buildName = name,
                    outputDir = outputDir,
                    workRoot = container.workDir,
                    onProgress = { percent, stage ->
                        _state.update { it.copy(progress = percent, stage = stage) }
                    },
                    onLog = { line ->
                        _state.update { it.copy(logs = (it.logs + line).takeLast(300)) }
                    }
                )
                container.library.save(
                    SavedBuild(
                        id = buildId,
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        filePath = result.output.absolutePath,
                        fileSize = result.output.length(),
                        modTitles = mods.map { it.title },
                        kind = result.output.extension.lowercase()
                    )
                )
                _state.update {
                    it.copy(
                        building = false,
                        progress = 100,
                        stage = "Готово",
                        conflicts = result.conflicts,
                        result = result,
                        message = "Сборка «$name» готова"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        building = false,
                        stage = "Ошибка",
                        error = e.message ?: "Не удалось собрать пак",
                        message = null
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BuilderViewModel(container) }
        }
    }
}
