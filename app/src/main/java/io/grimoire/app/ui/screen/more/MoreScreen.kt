package io.grimoire.app.ui.screen.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    onNavigateToWarnings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val activeCount by viewModel.activeDownloadCount.collectAsState()
    val updateCount by viewModel.updateCount.collectAsState()
    val subscribedUpdateCount by viewModel.subscribedUpdateCount.collectAsState()
    val issueCount by viewModel.updateIssueCount.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Tasks") },
                    supportingContent = { Text("Running downloads and syncs, plus their history") },
                    leadingContent = {
                        Icon(Icons.Default.PendingActions, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToTasks),
                )
                HorizontalDivider()
            }
            item {
                val hasUpdates = updateCount > 0
                // The subscribed count is the subset that actually fired a
                // notification, so we surface it apart from the full total: a
                // badge on the icon plus a breakdown in the supporting line.
                val subscribed = subscribedUpdateCount.coerceAtMost(updateCount)
                ListItem(
                    headlineContent = { Text("Updates") },
                    supportingContent = {
                        Text(
                            when {
                                !hasUpdates -> "No new chapters"
                                subscribed == 0 ->
                                    "$updateCount new chapter${if (updateCount == 1) "" else "s"}"
                                subscribed == updateCount ->
                                    "$updateCount new, all from subscribed"
                                else ->
                                    "$updateCount new · $subscribed from subscribed"
                            }
                        )
                    },
                    leadingContent = {
                        when {
                            hasUpdates && subscribed > 0 -> BadgedBox(
                                badge = { Badge { Text("$subscribed") } },
                            ) {
                                Icon(Icons.Default.NewReleases, contentDescription = null)
                            }
                            hasUpdates -> Icon(Icons.Default.NewReleases, contentDescription = null)
                            else -> Icon(
                                Icons.Default.Inbox,
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
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToWarnings),
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
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToDownloads),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Statistics") },
                    supportingContent = { Text("Your reading activity") },
                    leadingContent = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToStatistics),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Settings") },
                    supportingContent = { Text("Appearance, library, reader, backups") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToSettings),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("About") },
                    supportingContent = { Text("Version, update channel, licenses") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout),
                )
            }
        }
    }
}
