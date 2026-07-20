package io.grimoire.app.ui.screen.settings.connections

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsSettingsViewModel = hiltViewModel(),
) {
    val github by viewModel.github.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_connections_title)) },
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ConnectionRow(
                    icon = AppIcons.AccountCircle,
                    title = stringResource(R.string.connections_github),
                    statusLabel = when (github.status) {
                        ConnectionStatusType.DISCONNECTED -> stringResource(R.string.connections_not_connected)
                        ConnectionStatusType.SIGNING_IN -> stringResource(R.string.connections_signing_in)
                        ConnectionStatusType.CONNECTED -> "@${github.login.orEmpty()}"
                        ConnectionStatusType.ERROR -> stringResource(R.string.connections_error)
                    },
                    isConnected = github.isConnected,
                    onClick = onNavigateToGitHub,
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
                AppIcons.ArrowForwardIos,
                contentDescription = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
