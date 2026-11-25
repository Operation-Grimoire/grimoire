package io.grimoire.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.ArrowDropDown
import io.grimoire.ui.icon.Explore
import io.grimoire.ui.icon.Extension
import io.grimoire.ui.icon.FileDownloadOff
import io.grimoire.ui.icon.Info
import io.grimoire.ui.icon.Refresh
import io.grimoire.ui.icon.Storage

private val installedStatuses = setOf(ExtensionStatus.INSTALLED, ExtensionStatus.UPDATE, ExtensionStatus.UNAVAILABLE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtensionsPane(state: ReaderState, enabled: Boolean, model: ReaderModel, openLocal: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var showRepositories by remember { mutableStateOf(false) }
    val online = !state.offline
    val scroll = rememberLazyListState()
    val installed = state.extensionRows.filter { it.status in installedStatuses }
    val available = state.extensionRows.filter { it.status !in installedStatuses }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.lang.equals(query.trim(), ignoreCase = true) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                actions = {
                    if (state.repositories.isNotEmpty()) {
                        DesktopSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search extensions",
                            enabled = enabled,
                            modifier = Modifier.width(280.dp).padding(end = 8.dp),
                        )
                        PlainTooltipIconButton(
                            onClick = model::refreshRepositories,
                            tooltip = "Refresh",
                            enabled = enabled && online,
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = "Refresh")
                        }
                        PlainTooltipIconButton(
                            onClick = { showRepositories = true },
                            tooltip = "Repositories",
                            enabled = enabled,
                        ) {
                            Icon(AppIcons.Storage, contentDescription = "Repositories")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = scroll,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.widthIn(max = 840.dp).fillMaxHeight().align(Alignment.TopCenter),
            ) {
                state.extensionNotice?.let { notice ->
                    item(key = "notice") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Icon(AppIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(notice, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (state.offline && state.repositories.isNotEmpty()) {
                    item(key = "offline-note") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Icon(AppIcons.FileDownloadOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    "Go online to refresh repositories and install extensions. Installed extensions still work.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (state.repositories.isNotEmpty()) {
                    item(key = "header-installed") { SectionHeader("Installed") }
                    if (installed.isEmpty()) {
                        item(key = "empty-installed") {
                            Text(
                                "No extensions installed yet. Install one from the list below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(installed, key = { "installed:" + it.pkg }) { row ->
                        ExtensionItem(row) {
                            if (row.status == ExtensionStatus.UPDATE) {
                                Button(onClick = { model.installExtension(row) }, enabled = enabled && online) { Text("Update") }
                            }
                            TextButton(onClick = { model.removeExtension(row) }, enabled = enabled) { Text("Remove") }
                        }
                    }
                    item(key = "header-available") {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        SectionHeader("Available")
                    }
                    if (available.isEmpty()) {
                        item(key = "empty-available") {
                            Text(
                                if (state.repositories.any { it.refreshed }) "No other extensions match." else "Refresh to load the extension list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(available, key = { "available:" + it.repository + ":" + it.pkg }) { row ->
                        ExtensionItem(row) {
                            if (row.status == ExtensionStatus.AVAILABLE) {
                                FilledTonalButton(onClick = { model.installExtension(row) }, enabled = enabled && online) { Text("Install") }
                            }
                        }
                    }
                    item(key = "dev-divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
                }
                item(key = "local-extension") {
                    SectionHeader("Local extension")
                    ListItem(
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Extension, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        headlineContent = { Text(state.localExtension ?: "No extension open") },
                        supportingContent = { Text("For development: open a trusted local manifest for this session only.") },
                        trailingContent = {
                            OutlinedButton(onClick = openLocal, enabled = enabled) { Text("Open local extension") }
                        },
                    )
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.fillMaxHeight().align(Alignment.CenterEnd))
        }
    }

    if (showRepositories) {
        RepositoriesDialog(
            state = state,
            enabled = enabled,
            online = online,
            model = model,
            onDismiss = { showRepositories = false },
        )
    }
}

@Composable
private fun RepositoriesDialog(
    state: ReaderState,
    enabled: Boolean,
    online: Boolean,
    model: ReaderModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extension repositories") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            ) {
                state.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(state.repositories, key = { "repository:" + it.id }) { repository ->
                        RepositoryItem(repository, enabled, model)
                    }
                    item(key = "add-repo") {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        AddRepository(state, enabled && online, model)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ExtensionItem(row: ExtensionRow, actions: @Composable RowScope.() -> Unit) {
    val version = when (row.status) {
        ExtensionStatus.UPDATE -> "${row.installedVersion} → ${row.availableVersion}"
        ExtensionStatus.INSTALLED, ExtensionStatus.UNAVAILABLE -> row.installedVersion
        else -> row.availableVersion
    }
    ListItem(
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(row.lang.uppercase().take(3), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(row.lang.uppercase(), version, row.repository).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        },
    )
}

@Composable
private fun RepositoryItem(repository: RepositoryView, enabled: Boolean, model: ReaderModel) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (repository.builtin) "${repository.id} (official)" else repository.id, style = MaterialTheme.typography.titleSmall)
                Text(repository.indexUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Key ${repository.fingerprint}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (repository.refreshed) extensionCount(repository.extensionCount) else "Not refreshed yet",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repository.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            Switch(repository.enabled, { model.setRepositoryEnabled(repository.id, it) }, enabled = enabled)
            if (!repository.builtin) {
                TextButton(onClick = { model.removeRepository(repository.id) }, enabled = enabled) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun AddRepository(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val pending = state.pendingRepository
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add a repository", style = MaterialTheme.typography.titleSmall)
        if (pending == null) {
            Text(
                "Enter the index URL and the public key from the repository's publisher.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Index URL (https://…/jar-index.json)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, singleLine = true, label = { Text("Public key (base64)") },
                modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = { model.checkRepository(url, key) }, enabled = enabled && url.isNotBlank() && key.isNotBlank()) {
                Text("Check repository")
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pending.trust.repository, style = MaterialTheme.typography.titleSmall)
                    Text("Key ${pending.fingerprint}", style = MaterialTheme.typography.bodySmall)
                    Text(extensionCount(pending.catalog.extensions.size), style = MaterialTheme.typography.bodySmall)
                    Text("Extensions run with Grimoire's permissions. Trust only publishers you know.",
                        style = MaterialTheme.typography.bodySmall)
                    pending.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { model.confirmRepository(); url = ""; key = "" }, enabled = enabled) { Text("Trust repository") }
                        TextButton(onClick = model::cancelRepository) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

/** Source picker for Browse. */
@Composable
internal fun SourcePicker(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    var open by remember { mutableStateOf(false) }
    val label = state.sourceName ?: "Choose a source"
    if (state.sources.size <= 1) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(AppIcons.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }
    Box {
        FilledTonalButton(
            onClick = { open = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(AppIcons.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text("${source.name} (${source.lang.uppercase()})") },
                    onClick = { open = false; model.selectSource(source.sourceId) },
                )
            }
        }
    }
}

private fun extensionCount(count: Int) = if (count == 1) "1 extension" else "$count extensions"
