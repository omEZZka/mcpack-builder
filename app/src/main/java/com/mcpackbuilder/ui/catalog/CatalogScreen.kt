package com.mcpackbuilder.ui.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.CatalogSort
import com.mcpackbuilder.domain.model.CatalogSource
import com.mcpackbuilder.domain.model.CatalogTag
import com.mcpackbuilder.domain.model.ModCategory
import com.mcpackbuilder.domain.model.ModEntry
import com.mcpackbuilder.ui.components.CoverImage
import com.mcpackbuilder.ui.components.EmptyState
import com.mcpackbuilder.ui.components.SectionHeader
import com.mcpackbuilder.ui.components.SmallTag
import com.mcpackbuilder.ui.theme.LocalStrings
import com.mcpackbuilder.util.formatCompact

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(container: AppContainer, onMessage: (String) -> Unit, onOpenBuilder: () -> Unit = {}) {
    val viewModel: CatalogViewModel = viewModel(factory = CatalogViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        when {
            msg.startsWith("map-saved:") -> onMessage(s.mapSavedToBuilds)
            msg.startsWith("map-error:") -> onMessage(s.mapDownloadFailed(msg.removePrefix("map-error:")))
            msg.startsWith("downloading:") -> onMessage(s.downloadingArchive)
            else -> {} // Silent for add:/exists: to avoid spam
        }
        viewModel.consumeMessage()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        SectionHeader(s.catalogTitle, s.catalogSubtitle)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(s.searchMods) },
            trailingIcon = { IconButton({ viewModel.load(true) }) { Icon(Icons.Default.Search, s.search) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.load(true) })
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModCategory.entries.forEach { cat ->
                FilterChip(selected = state.category == cat, onClick = { viewModel.setCategory(cat) }, label = { Text(s.category(cat)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogSort.entries.forEach { sort ->
                FilterChip(selected = state.sort == sort, onClick = { viewModel.setSort(sort) }, label = { Text(s.sort(sort)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogTag.entries.forEach { tag ->
                FilterChip(selected = state.tag == tag, onClick = { viewModel.setTag(tag) }, label = { Text(s.tag(tag)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.source, style = MaterialTheme.typography.labelLarge)
            CatalogSource.entries.forEach { src ->
                FilterChip(selected = state.source == src, onClick = { viewModel.setSource(src) }, label = { Text(if (src == CatalogSource.MCPEDL) "MCPEDL" else "mcpehub.org") })
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() -> EmptyState(s.catalogLoadError, state.error ?: "", action = { Button(onClick = { viewModel.load(true) }) { Text(s.retry) } })
            state.loaded && state.items.isEmpty() -> EmptyState(s.nothingFound, s.nothingFoundHint)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Text(s.catalogFound(state.total.takeIf { it > 0 } ?: state.items.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(state.items, key = { _, item -> "${state.source}:${item.pageUrl}" }) { _, mod ->
                        ModCard(mod = mod, added = state.selectedUrls.contains(mod.pageUrl), onAdd = { if (mod.typeId == 2) viewModel.downloadMap(mod) else viewModel.addToBuild(mod) })
                    }
                    if (state.page < state.pageCount) {
                        item {
                            if (state.loadingMore) Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                            else Button(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text(s.loadMore) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModCard(mod: ModEntry, added: Boolean, onAdd: () -> Unit) {
    val s = LocalStrings.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            CoverImage(url = mod.coverUrl, modifier = Modifier.size(92.dp), contentDescription = mod.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mod.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mod.category?.let { SmallTag(it) }
                    mod.version?.let { SmallTag(it) }
                    mod.downloadCount?.let { SmallTag(formatCompact(it)) }
                }
                mod.author?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(s.author(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                if (added) {
                    AssistChip(onClick = {}, label = { Text(s.inBuild) }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                } else {
                    Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) {
                        Icon(if (mod.typeId == 2) Icons.Default.Download else Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (mod.typeId == 2) s.downloadMap else s.add)
                    }
                }
            }
        }
    }
}
