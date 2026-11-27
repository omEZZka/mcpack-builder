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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
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
            else -> {}
        }
        viewModel.consumeMessage()
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.catalogTitle, style = MaterialTheme.typography.headlineMedium)
                Text(s.catalogSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(s.searchMods) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.load(true) }) {
                            Icon(Icons.Default.Search, contentDescription = s.search)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.load(true) }),
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
        
        var showAdvanced by remember { mutableStateOf(false) }
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                ModCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { viewModel.setCategory(cat) },
                        label = { Text(s.category(cat)) }
                    )
                }
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = { viewModel.smartPick() },
                    label = { Text(s.smartSuggest) },
                    leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { showAdvanced = !showAdvanced },
                    label = { Text(if (showAdvanced) "Скрыть" else "Ещё") },
                    leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
                )
            }
            if (showAdvanced) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    CatalogSource.entries.forEach { src ->
                        FilterChip(
                            selected = state.source == src,
                            onClick = { viewModel.setSource(src) },
                            label = { Text(if (src == CatalogSource.MCPEDL) "MCPEDL" else "mcpehub.org") }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    CatalogSort.entries.forEach { sort ->
                        FilterChip(
                            selected = state.sort == sort,
                            onClick = { viewModel.setSort(sort) },
                            label = { Text(s.sort(sort)) }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    CatalogTag.entries.forEach { tag ->
                        FilterChip(
                            selected = state.tag == tag,
                            onClick = { viewModel.setTag(tag) },
                            label = { Text(s.tag(tag)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (state.query.isNotBlank() || state.tag != CatalogTag.ALL) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { viewModel.resetFilters() },
                        label = { Text(s.resetFilters) },
                        leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }
        
        Divider()
        Spacer(Modifier.height(4.dp))

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
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (added) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp)) {
            CoverImage(
                url = mod.coverUrl, 
                modifier = Modifier.size(100.dp),
                contentDescription = mod.title
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    mod.title, 
                    style = MaterialTheme.typography.titleMedium, 
                    maxLines = 2, 
                    overflow = TextOverflow.Ellipsis,
                    color = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    mod.category?.let { SmallTag(it) }
                    mod.version?.let { SmallTag(it) }
                    mod.rating?.takeIf { it > 0 }?.let {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = String.format("%.1f", it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    mod.downloadCount?.let {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = formatCompact(it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                mod.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        s.author(it), 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (added) {
                        AssistChip(
                            onClick = {}, 
                            label = { Text(s.inBuild, style = MaterialTheme.typography.labelMedium) }, 
                            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    } else {
                        Button(
                            onClick = onAdd, 
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                if (mod.typeId == 2) Icons.Default.Download else Icons.Default.Add, 
                                null, 
                                Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (mod.typeId == 2) s.downloadMap else s.add,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
