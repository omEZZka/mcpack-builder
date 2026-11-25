package com.mcpackbuilder.ui.builder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcpackbuilder.AppContainer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import com.mcpackbuilder.domain.model.ModEntry

@Composable
fun BuilderScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val vm: BuilderViewModel = viewModel(factory = BuilderViewModel.factory(container))
    val s by vm.state.collectAsStateWithLifecycle()
    var attachId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = attachId
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val dir = File(container.app.cacheDir, "downloads").apply { mkdirs() }
                val file = File.createTempFile("import-", ".zip", dir)
                try {
                    container.app.contentResolver.openInputStream(uri)!!.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
                    java.util.zip.ZipFile(file).use { require(it.size() > 0) { "Архив пуст" } }
                    file
                } catch (e: Exception) { file.delete(); throw e }
            } }.onSuccess { file ->
                if (id != null) container.selection.attachFile(id, file.path) else {
                    val uid = UUID.randomUUID().toString()
                    val title = container.app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Локальный мод"
                    container.selection.add(ModEntry(uid,title,pageUrl="local:$uid",filePath=file.path))
                }
            }.onFailure { onMessage("Ошибка импорта: ${it.message}") }
        }
    }
    LaunchedEffect(s.message) { s.message?.let { onMessage(it); vm.consumeMessage() } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Конструктор сборки", style = MaterialTheme.typography.headlineSmall) }
        item { TextButton({ attachId = null; open.launch(arrayOf("*/*")) }) { Text("Добавить локальный архив") } }
        item { OutlinedTextField(s.buildName, vm::setBuildName, label = { Text("Название сборки") }, singleLine = true) }
        item { Text(if (s.mods.isEmpty()) "Добавьте моды из каталога." else "Первый мод имеет наивысший приоритет.") }
        itemsIndexed(s.mods, key = { _, m -> m.id }) { i, m ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${i+1}. ${m.title}", style = MaterialTheme.typography.titleMedium)
                Text(if (m.isDownloaded) "Файл скачан" else "Требуется архив")
                Row {
                    TextButton({ vm.moveUp(m.id) }, enabled = i > 0 && !s.building) { Text("↑") }
                    TextButton({ vm.moveDown(m.id) }, enabled = i < s.mods.lastIndex && !s.building) { Text("↓") }
                    TextButton({ vm.remove(m.id) }, enabled = !s.building) { Text("Удалить") }
                }
                if (!m.isDownloaded) Button({ vm.download(m) }, enabled = s.downloadingModId == null) { Text("Скачать") }
                TextButton({ attachId = m.id; open.launch(arrayOf("*/*")) }, enabled = !s.building) { Text("Из файлов") }
            } }
        }
        item { s.downloadStatus?.let { Text(it) }; Button(vm::build, enabled = !s.building && s.mods.isNotEmpty()) { Text("Собрать в 1 файл") } }
        if (s.building) item { LinearProgressIndicator(progress = s.progress / 100f); Text("${s.progress}% · ${s.stage}") }
        s.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        s.result?.let { item { Text("Готово: ${it.output.name}. Файл доступен в разделе «Мои сборки».") } }
        if (s.conflicts.isNotEmpty()) item { Text("Конфликты", style = MaterialTheme.typography.titleMedium) }
        items(s.conflicts) { Text(it, color = MaterialTheme.colorScheme.error) }
        items(s.logs) { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
