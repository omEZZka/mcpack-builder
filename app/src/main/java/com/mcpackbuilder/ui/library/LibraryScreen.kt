package com.mcpackbuilder.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = builds.filter { filter == 0 || (it.kind == "mcworld") == (filter == 2) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Мои сборки", style = MaterialTheme.typography.headlineSmall) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Всё", "Сборки", "Карты").forEachIndexed { i, label -> FilterChip(filter == i, { filter = i }, { Text(label) }) }
        } }
        if (visible.isEmpty()) item { Text(if (filter == 2) "Здесь появятся сохранённые карты." else "Здесь появятся готовые сборки.") }
        items(visible, key = { it.id }) { b -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(b.name, style = MaterialTheme.typography.titleMedium)
            Text("${formatDate(b.createdAt)} · ${formatBytes(b.fileSize)} · .${b.kind}")
            if (b.modTitles.isNotEmpty()) Text(b.modTitles.joinToString(", "))
            Button({ BuildExporter.openInMinecraft(context, File(b.filePath)) }) { Text("Открыть в Minecraft") }
            Row { TextButton({ BuildExporter.share(context, File(b.filePath)) }) { Text("Поделиться") }; TextButton({ deleting = b }) { Text("Удалить") } }
        } } }
    }
    deleting?.let { b -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить «${b.name}»?") }, confirmButton = { TextButton({ vm.delete(b.id); deleting = null; onMessage("Сборка удалена") }) { Text("Удалить") } }, dismissButton = { TextButton({ deleting = null }) { Text("Отмена") } }) }
}
