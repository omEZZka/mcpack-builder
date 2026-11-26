package com.mcpackbuilder.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.SavedBuild
import com.mcpackbuilder.ui.components.EmptyState
import com.mcpackbuilder.ui.components.SectionHeader
import com.mcpackbuilder.ui.components.SmallTag
import com.mcpackbuilder.ui.theme.LocalStrings
import com.mcpackbuilder.util.*
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val builds by vm.builds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = LocalStrings.current
    var deleting by remember { mutableStateOf<SavedBuild?>(null) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = builds.filter { filter == 0 || (filter == 1 && it.kind != "mcworld") || (filter == 2 && it.kind == "mcworld") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader(s.myBuilds, if (filter == 2) s.maps else s.builds) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(s.all, s.builds, s.maps).forEachIndexed { i, label ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyState(if (filter == 2) s.maps else s.myBuilds, if (filter == 2) "Здесь появятся сохранённые карты." else "Здесь появятся готовые сборки.") }
        }
        items(visible, key = { it.id }) { b ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatDate(b.createdAt)} · ${formatBytes(b.fileSize)} · .${b.kind}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SmallTag(".${b.kind}")
                    }
                    if (b.modTitles.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            b.modTitles.take(4).forEach { SmallTag(it) }
                            if (b.modTitles.size > 4) SmallTag("+${b.modTitles.size - 4}")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { BuildExporter.openInMinecraft(context, File(b.filePath)) }, modifier = Modifier.weight(1f)) { Text(s.openInMinecraft, maxLines = 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { BuildExporter.share(context, File(b.filePath)) }) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.share) }
                        TextButton(onClick = { deleting = b }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(s.delete) }
                    }
                }
            }
        }
    }
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(s.deleteBuildTitle(b.name)) },
            confirmButton = { TextButton({ vm.delete(b.id); deleting = null; onMessage(s.buildDeleted) }) { Text(s.delete) } },
            dismissButton = { TextButton({ deleting = null }) { Text(s.cancel) } }
        )
    }
}
