package io.grimoire.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val installedStatuses = setOf(ExtensionStatus.INSTALLED, ExtensionStatus.UPDATE, ExtensionStatus.UNAVAILABLE)

@Composable
internal fun ExtensionsPane(state: ReaderState, enabled: Boolean, model: ReaderModel, openLocal: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val online = !state.offline
    val installed = state.extensionRows.filter { it.status in installedStatuses }
    val available = state.extensionRows.filter { it.status !in installedStatuses }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.lang.equals(query.trim(), ignoreCase = true) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extensions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (state.repositories.isNotEmpty()) {
                    OutlinedButton(onClick = model::refreshRepositories, enabled = enabled && online) { Text("Refresh") }
                }
            }
        }
        state.extensionNotice?.let { notice -> item { Text(notice, style = MaterialTheme.typography.bodyMedium) } }
        if (state.offline && state.repositories.isNotEmpty()) item {
            Text("Go online to refresh repositories and install extensions. Installed extensions still work.")
        }
        if (state.repositories.isNotEmpty()) {
            item { SectionHeader("Installed") }
            if (installed.isEmpty()) item { Text("No extensions installed yet. Install one from the list below.") }
            items(installed, key = { "installed:" + it.pkg }) { row ->
                ExtensionItem(row, signIn = when (row.pkg) {
                    in state.signedIn -> "Signed in"
                    in state.loginPackages -> "Not signed in"
                    else -> null
                }) {
                    if (row.pkg in state.signedIn) {
                        TextButton(onClick = { model.signOut(row.pkg) }, enabled = enabled) { Text("Sign out") }
                    } else if (row.pkg in state.loginPackages) {
                        OutlinedButton(onClick = { model.signIn(row.pkg) }, enabled = enabled && online) { Text("Sign in") }
                    }
                    if (row.status == ExtensionStatus.UPDATE) {
                        Button(onClick = { model.installExtension(row) }, enabled = enabled && online) { Text("Update") }
                    }
                    TextButton(onClick = { model.removeExtension(row) }, enabled = enabled) { Text("Remove") }
                }
            }
            item {
                SectionHeader("Available")
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search by name or language") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            if (available.isEmpty()) item {
                Text(if (state.repositories.any { it.refreshed }) "No other extensions match." else "Refresh to load the extension list.")
            }
            items(available, key = { "available:" + it.repository + ":" + it.pkg }) { row ->
                ExtensionItem(row) {
                    if (row.status == ExtensionStatus.AVAILABLE) {
                        Button(onClick = { model.installExtension(row) }, enabled = enabled && online) { Text("Install") }
                    }
                }
            }
            item { SectionHeader("Repositories") }
            items(state.repositories, key = { "repository:" + it.id }) { repository -> RepositoryItem(repository, enabled, model) }
            item { AddRepository(state, enabled && online, model) }
        }
        item {
            SectionHeader("Local extension")
            Text("For development: open a trusted local manifest for this session only.",
                style = MaterialTheme.typography.bodySmall)
        }
        item { Text(state.localExtension ?: "No extension open") }
        item { OutlinedButton(onClick = openLocal, enabled = enabled) { Text("Open local extension") } }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ExtensionItem(row: ExtensionRow, signIn: String? = null, actions: @Composable RowScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                val version = when (row.status) {
                    ExtensionStatus.UPDATE -> "${row.installedVersion} → ${row.availableVersion}"
                    ExtensionStatus.INSTALLED, ExtensionStatus.UNAVAILABLE -> row.installedVersion
                    else -> row.availableVersion
                }
                Text(listOfNotNull(row.lang.uppercase(), version, row.repository).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                signIn?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                row.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
private fun RepositoryItem(repository: RepositoryView, enabled: Boolean, model: ReaderModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (repository.builtin) "${repository.id} (official)" else repository.id, style = MaterialTheme.typography.titleSmall)
                Text(repository.indexUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Key ${repository.fingerprint}", style = MaterialTheme.typography.bodySmall)
                Text(if (repository.refreshed) extensionCount(repository.extensionCount) else "Not refreshed yet",
                    style = MaterialTheme.typography.bodySmall)
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
            Text("Enter the index URL and the public key from the repository's publisher.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Index URL (https://…/jar-index.json)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, singleLine = true, label = { Text("Public key (base64)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { model.checkRepository(url, key) }, enabled = enabled && url.isNotBlank() && key.isNotBlank()) {
                Text("Check repository")
            }
        } else {
            OutlinedCard(Modifier.fillMaxWidth()) {
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
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled && state.sources.size > 1) {
            Text(state.sourceName ?: "Choose a source")
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
