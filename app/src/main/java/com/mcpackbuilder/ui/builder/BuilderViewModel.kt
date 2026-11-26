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
    val icon: File? = null,
    val mods: List<ModEntry> = emptyList(),
    val buildName: String = "Моя сборка",
    val format: String = "mcaddon",
    val building: Boolean = false,
    val progress: Int = 0,
    val stage: String = "",
    val logs: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val result: MergeResult? = null,
    val error: String? = null,
    val message: String? = null,
    val downloadingModId: String? = null,
    val downloadStatus: String? = null,
    val exportRequested: Boolean = false,
    val autoDownloading: Boolean = false,
    val aiSuggestions: List<String> = emptyList()
)

class BuilderViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = McpedlRepository(container.parser, container.fetcher)
    private val aiClient = com.mcpackbuilder.data.agent.AiAgentClient(container.app)
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

    fun setBuildName(name: String) { _state.update { it.copy(buildName = name) } }
    fun setPackName(name: String) = setBuildName(name)
    fun setFormat(fmt: String) { _state.update { it.copy(format = fmt) } }
    fun requestExport(): Boolean {
        val s = _state.value
        if (s.mods.isEmpty() || s.building) return false
        _state.update { it.copy(exportRequested = true) }
        return true
    }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    fun setIcon(file: File?) {
        val previous = _state.value.icon
        _state.update { it.copy(icon = file) }
        if (!_state.value.building && previous != file) previous?.delete()
    }

    fun remove(id: String) = container.selection.remove(id)
    fun move(from: Int, to: Int) {
        val mods = container.selection.mods.value
        if (from !in mods.indices || to !in mods.indices) return
        val id = mods[from].id
        container.selection.move(id, to - from)
    }
    fun moveUp(id: String) = container.selection.move(id, -1)
    fun moveDown(id: String) = container.selection.move(id, 1)
    fun clearAll() = container.selection.clear()

    fun importFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(message = null, error = null) }
            runCatching { com.mcpackbuilder.data.store.DevicePacks.read(container.app, uri) }
                .onSuccess { mods ->
                    val added = container.selection.addAll(mods)
                    _state.update { it.copy(message = "Добавлено паков: $added") }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Ошибка импорта") } }
        }
    }

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
                    enriched.copy(id = current.id, filePath = current.filePath)
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
        // Auto-download mods if AI agent is enabled
        val config = aiClient.loadConfig()
        if (config.enabled && mods.any { !it.isDownloaded }) {
            downloadAllMods()
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
                    },
                    icon = snapshot.icon
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

    private fun downloadAllMods() {
        if (_state.value.autoDownloading) return
        _state.update { it.copy(autoDownloading = true, downloadStatus = "Авто-скачивание модов...") }
        
        viewModelScope.launch {
            val modsToDownload = _state.value.mods.filter { !it.isDownloaded }
            var successCount = 0
            var failCount = 0
            
            modsToDownload.forEach { mod ->
                try {
                    _state.update { it.copy(downloadingModId = mod.id) }
                    val enriched = repository.enrich(mod)
                    container.selection.updateMod(mod.id) { current ->
                        enriched.copy(id = current.id, filePath = current.filePath)
                    }
                    val direct = repository.directDownload(enriched.downloadLinks)
                    if (direct != null) {
                        val file = FileDownloader.download(container.app, direct, null, null, null)
                        container.downloads.add(file.absolutePath, direct, mod.id)
                        container.selection.attachFile(mod.id, file.absolutePath)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }
            
            _state.update { 
                it.copy(
                    autoDownloading = false,
                    downloadingModId = null,
                    downloadStatus = null,
                    message = "Скачано: $successCount, Ошибок: $failCount"
                )
            }
            
            // Retry build if all downloads succeeded
            if (failCount == 0) {
                build()
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
