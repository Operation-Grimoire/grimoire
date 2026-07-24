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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.R

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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.more_incognito)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (incognito) R.string.more_incognito_enabled
                                else R.string.more_incognito_disabled,
                            )
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
                    headlineContent = { Text(stringResource(R.string.more_updates)) },
                    supportingContent = {
                        Text(
                            when {
                                hasSubscribedUpdates -> pluralStringResource(
                                    R.plurals.more_subscribed_updates_summary,
                                    subscribedUpdateCount,
                                    subscribedUpdateCount,
                                )
                                hasUpdates -> pluralStringResource(
                                    R.plurals.more_updates_summary,
                                    updateCount,
                                    updateCount,
                                )
                                else -> stringResource(R.string.more_no_new_chapters)
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
                    headlineContent = { Text(stringResource(R.string.more_history)) },
                    supportingContent = { Text(stringResource(R.string.more_history_summary)) },
                    leadingContent = { Icon(AppIcons.History, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToHistory),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.more_downloads)) },
                    supportingContent = {
                        Text(
                            if (activeCount > 0) pluralStringResource(
                                R.plurals.more_downloads_in_progress,
                                activeCount,
                                activeCount,
                            ) else stringResource(R.string.more_downloads_summary)
                        )
                    },
                    leadingContent = { Icon(AppIcons.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToDownloads),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.more_tasks)) },
                    supportingContent = { Text(stringResource(R.string.more_tasks_summary)) },
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
                    headlineContent = { Text(stringResource(R.string.more_update_warnings)) },
                    supportingContent = {
                        Text(
                            if (hasIssues) pluralStringResource(
                                R.plurals.more_update_warnings_summary,
                                issueCount,
                                issueCount,
                            ) else stringResource(R.string.more_no_refresh_problems)
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
                    headlineContent = { Text(stringResource(R.string.more_statistics)) },
                    supportingContent = { Text(stringResource(R.string.more_statistics_summary)) },
                    leadingContent = { Icon(AppIcons.BarChart, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToStatistics),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_title)) },
                    supportingContent = { Text(stringResource(R.string.more_settings_summary)) },
                    leadingContent = { Icon(AppIcons.Settings, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToSettings),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.more_tours)) },
                    supportingContent = { Text(stringResource(R.string.more_tours_summary)) },
                    leadingContent = { Icon(AppIcons.Explore, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToTours),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.more_about)) },
                    supportingContent = { Text(stringResource(R.string.more_about_summary)) },
                    leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout),
                )
            }
        }
    }
}
