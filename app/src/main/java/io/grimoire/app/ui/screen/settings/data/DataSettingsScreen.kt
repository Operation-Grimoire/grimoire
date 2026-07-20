package io.grimoire.app.ui.screen.settings.data

import io.grimoire.app.ui.icon.*
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader
import io.grimoire.app.R

private enum class ConfirmTarget { COVER_CACHE, BROWSE_DATA, INSTALLERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<ConfirmTarget?>(null) }

    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value)

    state.message?.let { message ->
        val msg = when (message) {
            DataSettingsMessage.CoverCacheCleared -> stringResource(R.string.data_cover_cache_cleared)
            is DataSettingsMessage.BrowseDataCleared -> pluralStringResource(
                R.plurals.data_browse_cleared,
                message.count,
                message.count,
            )
            is DataSettingsMessage.InstallerFilesCleared -> pluralStringResource(
                R.plurals.data_installers_cleared,
                message.count,
                message.count,
            )
        }
        androidx.compose.runtime.LaunchedEffect(msg) {
            snackbarHost.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_data_title)) },
                actions = {
                    PlainTooltipIconButton(onClick = viewModel::refresh, enabled = !state.busy, tooltip = stringResource(R.string.action_refresh)) {
                        Icon(AppIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        val b = state.breakdown
        Box(Modifier.padding(padding)) {
            if (state.loading || state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn {
                item { SettingsSectionHeader(stringResource(R.string.data_storage)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (b == null) {
                            Text("…")
                        } else {
                            StorageUsageBar(b)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(
                                    R.string.data_storage_summary,
                                    bytes(b.appTotalBytes),
                                    bytes(b.deviceFreeBytes),
                                    bytes(b.deviceTotalBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item { SettingsSectionHeader(stringResource(R.string.data_your_data)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_library_title)) },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else pluralStringResource(
                                    R.plurals.data_library_novels,
                                    b.libraryNovels,
                                    b.libraryNovels,
                                    b.libraryChapters,
                                ),
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_browse)) },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else pluralStringResource(
                                    R.plurals.data_browse_count,
                                    b.browseNovels,
                                    b.browseNovels,
                                ),
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { confirm = ConfirmTarget.BROWSE_DATA },
                                enabled = b != null && b.browseNovels > 0 && !state.busy,
                            ) { Text(stringResource(R.string.action_clear)) }
                        },
                    )
                }

                item { SettingsSectionHeader(stringResource(R.string.data_downloads)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_chapter_text)) },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else pluralStringResource(
                                    R.plurals.data_chapter_text_summary,
                                    b.downloadedTextCount,
                                    bytes(b.downloadedTextBytes),
                                    b.downloadedTextCount,
                                ),
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_chapter_images)) },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else pluralStringResource(
                                    R.plurals.data_chapter_images_summary,
                                    b.downloadedImageCount,
                                    bytes(b.downloadedImageBytes),
                                    b.downloadedImageCount,
                                ),
                            )
                        },
                    )
                }

                item { SettingsSectionHeader(stringResource(R.string.data_caches)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_cover_cache)) },
                        supportingContent = {
                            Text(if (b == null) "…" else bytes(b.coverCacheBytes))
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { confirm = ConfirmTarget.COVER_CACHE },
                                enabled = b != null && b.coverCacheBytes > 0 && !state.busy,
                            ) { Text(stringResource(R.string.action_clear)) }
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_database)) },
                        supportingContent = {
                            Text(if (b == null) "…" else bytes(b.databaseBytes))
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.data_installer_files)) },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else stringResource(R.string.data_installer_summary, bytes(b.installerBytes)),
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { confirm = ConfirmTarget.INSTALLERS },
                                enabled = b != null && b.installerCount > 0 && !state.busy,
                            ) { Text(stringResource(R.string.action_clear)) }
                        },
                    )
                }
            }
        }
    }

    confirm?.let { target ->
        val (title, body, onConfirm) = when (target) {
            ConfirmTarget.COVER_CACHE -> Triple(
                stringResource(R.string.data_clear_cover_title),
                stringResource(R.string.data_clear_cover_message),
                viewModel::clearCoverCache,
            )
            ConfirmTarget.BROWSE_DATA -> Triple(
                stringResource(R.string.data_clear_browse_title),
                stringResource(R.string.data_clear_browse_message),
                viewModel::clearBrowseData,
            )
            ConfirmTarget.INSTALLERS -> Triple(
                stringResource(R.string.data_clear_installer_title),
                stringResource(R.string.data_clear_installer_message),
                viewModel::clearInstallerFiles,
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    confirm = null
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
