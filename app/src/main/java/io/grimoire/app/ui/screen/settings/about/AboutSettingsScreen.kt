package io.grimoire.app.ui.screen.settings.about

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import io.grimoire.app.data.preferences.UpdateChannel
import io.grimoire.app.ui.update.AppUpdateViewModel
import io.grimoire.app.ui.update.CheckState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity() as? ComponentActivity
            ?: error("AboutSettingsScreen must be hosted in a ComponentActivity")
    }
    val updateViewModel: AppUpdateViewModel = hiltViewModel(viewModelStoreOwner = activity)

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("–")
    }
    val checkState by updateViewModel.checkState.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val autoPopupEnabled by viewModel.autoPopupEnabled.collectAsState()
    val autoChangelogEnabled by viewModel.autoChangelogEnabled.collectAsState()
    val isLoadingChangelog by updateViewModel.isLoadingChangelog.collectAsState()

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
                        when (checkState) {
                            is CheckState.Idle -> TextButton(onClick = updateViewModel::checkForUpdates) {
                                Text("Check for updates")
                            }
                            is CheckState.Checking -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            is CheckState.UpToDate -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Up to date",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            is CheckState.Error -> TextButton(onClick = updateViewModel::checkForUpdates) {
                                Text("Retry")
                            }
                        }
                    },
                )
            }

            if (checkState is CheckState.Error) {
                item {
                    Text(
                        text = (checkState as CheckState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Show changelog") },
                    supportingContent = { Text("Open the release notes for ${version ?: "–"}") },
                    trailingContent = {
                        if (isLoadingChangelog) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isLoadingChangelog) {
                        updateViewModel.showCurrentChangelog()
                    },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                Text(
                    text = "Updates",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(UpdateChannel.entries.toList()) { entry ->
                val selected = channel == entry
                ListItem(
                    leadingContent = {
                        RadioButton(selected = selected, onClick = {
                            viewModel.setChannel(entry)
                            updateViewModel.resetCheckState()
                        })
                    },
                    headlineContent = { Text(entry.displayName) },
                    supportingContent = { Text(entry.description) },
                    modifier = Modifier.clickable {
                        viewModel.setChannel(entry)
                        updateViewModel.resetCheckState()
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Automatic update popups") },
                    supportingContent = {
                        Text("Show a dialog on launch when an update is available")
                    },
                    trailingContent = {
                        Switch(
                            checked = autoPopupEnabled,
                            onCheckedChange = { viewModel.setAutoPopupEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAutoPopupEnabled(!autoPopupEnabled)
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Changelog after updates") },
                    supportingContent = {
                        Text("Show release notes the first time you launch a new version")
                    },
                    trailingContent = {
                        Switch(
                            checked = autoChangelogEnabled,
                            onCheckedChange = { viewModel.setAutoChangelogEnabled(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAutoChangelogEnabled(!autoChangelogEnabled)
                    },
                )
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

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val UpdateChannel.displayName: String
    get() = when (this) {
        UpdateChannel.STABLE -> "Stable"
        UpdateChannel.BETA -> "Beta"
    }

private val UpdateChannel.description: String
    get() = when (this) {
        UpdateChannel.STABLE -> "Tagged releases only. Recommended."
        UpdateChannel.BETA -> "Fresh builds from main on every commit. May be unstable."
    }
