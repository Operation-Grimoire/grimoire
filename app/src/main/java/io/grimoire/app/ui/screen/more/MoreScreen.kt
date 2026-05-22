package io.grimoire.app.ui.screen.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
    onNavigateToUpdates: () -> Unit,
    onNavigateToWarnings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val activeCount by viewModel.activeDownloadCount.collectAsState()
    val updateCount by viewModel.updateCount.collectAsState()
    val issueCount by viewModel.updateIssueCount.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Updates") },
                    supportingContent = {
                        Text(
                            if (updateCount > 0) {
                                "$updateCount new chapter${if (updateCount == 1) "" else "s"}"
                            } else {
                                "New chapters found in your library"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.NewReleases, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToUpdates),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Update warnings") },
                    supportingContent = if (issueCount > 0) {
                        { Text("$issueCount novel${if (issueCount == 1) "" else "s"} need attention") }
                    } else {
                        { Text("Novels that failed to refresh") }
                    },
                    leadingContent = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToWarnings),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Downloads") },
                    supportingContent = if (activeCount > 0) {
                        { Text("$activeCount in progress") }
                    } else null,
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToDownloads),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Statistics") },
                    leadingContent = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToStatistics),
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Settings") },
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
