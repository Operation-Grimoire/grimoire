package io.grimoire.app.ui.screen.more

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: MoreViewModel = hiltViewModel(),
    onNavigateToTasks: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToWarnings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTours: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val activeCount by viewModel.activeDownloadCount.collectAsState()
    val updateCount by viewModel.updateCount.collectAsState()
    val subscribedUpdateCount by viewModel.subscribedUpdateCount.collectAsState()
    val issueCount by viewModel.updateIssueCount.collectAsState()
    val incognito by viewModel.incognito.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Incognito") },
                    supportingContent = {
                        Text(
                            if (incognito) "Not recording history this session"
                            else "Pause reading & browsing history"
                        )
                    },
                    leadingContent = {
                        Icon(
                            AppIcons.VisibilityOff,
                            contentDescription = null,
                            tint = if (incognito) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = incognito, onCheckedChange = { viewModel.toggleIncognito() })
                    },
                    modifier = Modifier.clickable(onClick = viewModel::toggleIncognito),
                )
                HorizontalDivider()
            }
            item {
                val hasUpdates = updateCount > 0
                val hasSubscribedUpdates = subscribedUpdateCount > 0
                ListItem(
                    headlineContent = { Text("Updates") },
                    supportingContent = {
                        Text(
                            when {
                                hasSubscribedUpdates -> {
                                    val chapters =
                                        "$subscribedUpdateCount new chapter${if (subscribedUpdateCount == 1) "" else "s"}"
                                    "$chapters from subscribed novels"
                                }
                                hasUpdates -> {
                                    "$updateCount new chapter${if (updateCount == 1) "" else "s"} across your library"
                                }
                                else -> "No new chapters"
                            }
                        )
                    },
                    leadingContent = {
                        if (hasSubscribedUpdates) {
                            Icon(AppIcons.NewReleases, contentDescription = null)
                        } else {
                            Icon(
                                AppIcons.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToUpdates),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("History") },
                    supportingContent = { Text("Recently read chapters and browsed novels") },
                    leadingContent = { Icon(AppIcons.History, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToHistory),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Downloads") },
                    supportingContent = {
                        Text(
                            if (activeCount > 0) "$activeCount in progress"
                            else "Downloaded chapters and queue"
                        )
                    },
                    leadingContent = { Icon(AppIcons.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToDownloads),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Tasks") },
                    supportingContent = { Text("Running downloads and syncs, plus their history") },
                    leadingContent = {
                        Icon(AppIcons.PendingActions, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToTasks),
                )
                HorizontalDivider()
            }
            item {
                val hasIssues = issueCount > 0
                ListItem(
                    headlineContent = { Text("Update warnings") },
                    supportingContent = {
                        Text(
                            if (hasIssues) {
                                val noun = if (issueCount == 1) "novel needs" else "novels need"
                                "$issueCount $noun attention"
                            } else {
                                "No refresh problems"
                            }
                        )
                    },
                    leadingContent = {
                        if (hasIssues) {
                            Icon(
                                AppIcons.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Icon(AppIcons.CheckCircle, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToWarnings),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Statistics") },
                    supportingContent = { Text("Your reading activity") },
                    leadingContent = { Icon(AppIcons.BarChart, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToStatistics),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Settings") },
                    supportingContent = { Text("Appearance, library, reader, backups") },
                    leadingContent = { Icon(AppIcons.Settings, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToSettings),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Tours") },
                    supportingContent = { Text("Replay the in-app guided tours") },
                    leadingContent = { Icon(AppIcons.Explore, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToTours),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("About") },
                    supportingContent = { Text("Version, update channel, licenses") },
                    leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout),
                )
            }
        }
    }
}
