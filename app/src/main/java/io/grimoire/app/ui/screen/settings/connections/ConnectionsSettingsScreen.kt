package io.grimoire.app.ui.screen.settings.connections

import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onNavigateToAthenaeum: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsSettingsViewModel = hiltViewModel(),
) {
    val github by viewModel.github.collectAsState()
    val athenaeum by viewModel.athenaeum.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ConnectionRow(
                    icon = Icons.Default.AccountCircle,
                    title = "GitHub",
                    statusLabel = github.statusLabel,
                    isConnected = github.isConnected,
                    onClick = onNavigateToGitHub,
                )
            }
            item {
                ConnectionRow(
                    icon = Icons.Default.CloudUpload,
                    title = "Athenaeum",
                    statusLabel = athenaeum.statusLabel,
                    isConnected = athenaeum.isConnected,
                    onClick = onNavigateToAthenaeum,
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    icon: ImageVector,
    title: String,
    statusLabel: String,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                statusLabel,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
