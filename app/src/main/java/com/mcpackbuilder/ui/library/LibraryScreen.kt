package com.mcpackbuilder.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.SavedBuild
import com.mcpackbuilder.util.*
import java.io.File

@Composable
fun LibraryScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val builds by vm.builds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<SavedBuild?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Мои сборки", style = MaterialTheme.typography.headlineSmall) }
        if (builds.isEmpty()) item { Text("Здесь появятся готовые сборки.") }
        items(builds, key = { it.id }) { b -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(b.name, style = MaterialTheme.typography.titleMedium)
            Text("${formatDate(b.createdAt)} · ${formatBytes(b.fileSize)} · .${b.kind}")
            Text(b.modTitles.joinToString(", "))
            Button({ BuildExporter.openInMinecraft(context, File(b.filePath)) }) { Text("Открыть в Minecraft") }
            Row { TextButton({ BuildExporter.share(context, File(b.filePath)) }) { Text("Поделиться") }; TextButton({ deleting = b }) { Text("Удалить") } }
        } } }
    }
    deleting?.let { b -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить «${b.name}»?") }, confirmButton = { TextButton({ vm.delete(b.id); deleting = null; onMessage("Сборка удалена") }) { Text("Удалить") } }, dismissButton = { TextButton({ deleting = null }) { Text("Отмена") } }) }
}
