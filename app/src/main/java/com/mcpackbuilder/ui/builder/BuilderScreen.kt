package com.mcpackbuilder.ui.builder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.ModEntry
import com.mcpackbuilder.ui.components.EmptyState
import com.mcpackbuilder.ui.components.SectionHeader
import com.mcpackbuilder.ui.theme.LocalStrings
import java.io.File
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuilderScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val vm: BuilderViewModel = viewModel(factory = BuilderViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    var confirmLocal by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var confirmDevice by remember { mutableStateOf(false) }
    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importFromUri(uri)
    }
    val downloadsDir = remember { File("/storage/emulated/0/Download") }
    val localList = remember { mutableStateListOf<ModEntry>() }
    var selectedLocal by rememberSaveable { mutableStateOf(setOf<String>()) }

    val msg = state.message
    LaunchedEffect(msg) { if (msg != null) { onMessage(msg); vm.clearMessage() } }

    fun refreshLocal() {
        val entries = downloadsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in setOf("mcpack", "mcaddon", "mcworld", "zip") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        localList.clear()
        localList.addAll(entries.map { ModEntry(id = UUID.nameUUIDFromBytes(it.absolutePath.toByteArray()).toString(), title = it.nameWithoutExtension, pageUrl = "file:${it.absolutePath}", filePath = it.absolutePath) })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader(s.buildMods, s.addFromDevice) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.buildName, vm::setPackName, label = { Text(s.packName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(s.format, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("mcaddon", "mcpack", "zip").forEach { k ->
                            FilterChip(selected = state.format == k, onClick = { vm.setFormat(k) }, label = { Text(k) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refreshLocal(); confirmLocal = true }) { Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.fromFolder) }
                        OutlinedButton(onClick = { confirmDevice = true }) { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.installed) }
                    }
                }
            }
        }
        if (state.conflicts.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.conflicts, style = MaterialTheme.typography.titleSmall)
                    state.conflicts.forEach { c -> Text(c, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.modsCount(state.mods.size), style = MaterialTheme.typography.titleMedium)
                if (state.mods.isNotEmpty()) TextButton(onClick = { container.selection.clear() }) { Text(s.clearSelection) }
            }
        }
        if (state.mods.isEmpty()) item { EmptyState(s.myBuilds, s.addFromDevice) }
        itemsIndexed(state.mods, key = { _, m -> m.pageUrl }) { i, mod ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mod.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (mod.isDownloaded) "✓ Скачан" else "⚠ Требуется скачивание",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mod.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Row {
                            IconButton(onClick = { vm.moveUp(mod.id) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, null) }
                            IconButton(onClick = { vm.moveDown(mod.id) }, enabled = i < state.mods.lastIndex) { Icon(Icons.Default.ArrowDownward, null) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!mod.isDownloaded) {
                            Button(onClick = { vm.downloadMod(mod) }, enabled = state.downloadingModId == null) {
                                Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Скачать")
                            }
                        }
                        OutlinedButton(onClick = { vm.remove(mod.id) }) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            // Download All button
            if (state.mods.any { !it.isDownloaded }) {
                OutlinedButton(
                    onClick = { vm.downloadAllMods() },
                    enabled = !state.autoDownloading && state.downloadingModId == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.autoDownloading) "Скачивание..." else "Скачать все моды (${state.mods.count { !it.isDownloaded }})")
                }
                Spacer(Modifier.height(8.dp))
            }
            // Build button
            Button(
                onClick = { if (vm.requestExport()) confirmExport = true },
                enabled = state.mods.isNotEmpty() && !state.building && state.mods.all { it.isDownloaded },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Build, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.building) s.building else s.build)
            }
        }
        if (state.building) item { LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("${state.progress}% · ${state.stage}", style = MaterialTheme.typography.bodySmall) }
        state.error?.let { err -> item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        state.result?.let { res -> item { Text("${s.readyInLibrary(res.output.name)}", style = MaterialTheme.typography.bodyMedium) } }
    }

    if (confirmDevice) AlertDialog(onDismissRequest = { confirmDevice = false }, title = { Text(s.deviceAccessTitle) }, text = { Text(s.deviceAccessBody) }, confirmButton = { TextButton({ confirmDevice = false; devicePicker.launch(null) }) { Text(s.pickFolder) } }, dismissButton = { TextButton({ confirmDevice = false }) { Text(s.cancel) } })
    if (confirmExport) AlertDialog(onDismissRequest = { confirmExport = false }, title = { Text(s.buildConfirmTitle(state.mods.size)) }, text = { Text(s.buildConfirmBody) }, confirmButton = { TextButton({ confirmExport = false; vm.build() }) { Text(s.build) } }, dismissButton = { TextButton({ confirmExport = false }) { Text(s.cancel) } })
    if (confirmLocal) AlertDialog(onDismissRequest = { confirmLocal = false }, title = { Text(s.localFiles) }, text = { LazyColumn { items(localList, key = { it.pageUrl }) { mod -> val checked = mod.pageUrl in selectedLocal; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { selectedLocal = if (checked) selectedLocal - mod.pageUrl else selectedLocal + mod.pageUrl }); Text(mod.title, Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }, confirmButton = { TextButton({ container.selection.addAll(localList.filter { it.pageUrl in selectedLocal }); confirmLocal = false }) { Text(s.add) } }, dismissButton = { TextButton({ confirmLocal = false }) { Text(s.cancel) } })
}
