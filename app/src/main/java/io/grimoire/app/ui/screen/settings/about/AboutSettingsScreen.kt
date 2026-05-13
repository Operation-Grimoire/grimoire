package io.grimoire.app.ui.screen.settings.about

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("–")
    }
    val updateState by viewModel.updateState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("About") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(version ?: "–") },
                    trailingContent = {
                        when (val state = updateState) {
                            is UpdateState.Idle -> TextButton(onClick = viewModel::checkForUpdates) {
                                Text("Check for updates")
                            }
                            is UpdateState.Checking -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            is UpdateState.UpToDate -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Up to date",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            is UpdateState.Error -> TextButton(onClick = viewModel::checkForUpdates) {
                                Text("Retry")
                            }
                            is UpdateState.Available -> {} // shown as a separate row below
                        }
                    },
                )
            }

            if (updateState is UpdateState.Available) {
                val avail = updateState as UpdateState.Available
                item {
                    ListItem(
                        headlineContent = { Text("Update available") },
                        supportingContent = { Text(avail.version) },
                        trailingContent = {
                            TextButton(onClick = { viewModel.downloadAndInstall(avail.apkUrl, avail.sha256) }) {
                                Text("Download")
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            if (updateState is UpdateState.Error) {
                item {
                    Text(
                        text = (updateState as UpdateState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                Text(
                    text = "Grimoire",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Open Source Licenses") },
                    supportingContent = { Text("Third-party libraries used in this app") },
                )
            }
        }
    }
}
