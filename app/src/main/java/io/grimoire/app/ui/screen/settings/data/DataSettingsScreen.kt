package io.grimoire.app.ui.screen.settings.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader

private enum class ConfirmTarget { COVER_CACHE, BROWSE_DATA }

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

    state.message?.let { msg ->
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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Data management") },
                actions = {
                    PlainTooltipIconButton(onClick = viewModel::refresh, enabled = !state.busy, tooltip = "Refresh") {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                item { SettingsSectionHeader("Your data") }
                item {
                    ListItem(
                        headlineContent = { Text("Library") },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else "${b.libraryNovels} novels · ${b.libraryChapters} chapters",
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Browse data") },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else "${b.browseNovels} cached novels you haven't saved or read",
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { confirm = ConfirmTarget.BROWSE_DATA },
                                enabled = b != null && b.browseNovels > 0 && !state.busy,
                            ) { Text("Clear") }
                        },
                    )
                }

                item { SettingsSectionHeader("Downloads") }
                item {
                    ListItem(
                        headlineContent = { Text("Chapter text") },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else "${bytes(b.downloadedTextBytes)} · ${b.downloadedTextCount} chapters",
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Chapter images") },
                        supportingContent = {
                            Text(
                                if (b == null) "…"
                                else "${bytes(b.downloadedImageBytes)} · ${b.downloadedImageCount} images",
                            )
                        },
                    )
                }

                item { SettingsSectionHeader("Caches") }
                item {
                    ListItem(
                        headlineContent = { Text("Cover cache") },
                        supportingContent = {
                            Text(if (b == null) "…" else bytes(b.coverCacheBytes))
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { confirm = ConfirmTarget.COVER_CACHE },
                                enabled = b != null && b.coverCacheBytes > 0 && !state.busy,
                            ) { Text("Clear") }
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Database") },
                        supportingContent = {
                            Text(if (b == null) "…" else bytes(b.databaseBytes))
                        },
                    )
                }
            }
        }
    }

    confirm?.let { target ->
        val (title, body, onConfirm) = when (target) {
            ConfirmTarget.COVER_CACHE -> Triple(
                "Clear cover cache?",
                "Cover images will be re-downloaded the next time they're shown.",
                viewModel::clearCoverCache,
            )
            ConfirmTarget.BROWSE_DATA -> Triple(
                "Clear browse data?",
                "Removes cached novels you haven't saved to your library or read. " +
                    "They're re-fetched from the source when you open them again. " +
                    "Read history and downloads are kept.",
                viewModel::clearBrowseData,
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
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel") }
            },
        )
    }
}
