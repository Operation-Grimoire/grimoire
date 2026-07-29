package io.grimoire.app.ui.screen.settings.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.R
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.ArrowBack
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_privacy_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SettingsSectionHeader(stringResource(R.string.privacy_section_diagnostics)) }
            item {
                Text(
                    stringResource(R.string.privacy_diagnostics_note),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                val crashReports by viewModel.crashReportsEnabled.collectAsState()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.privacy_crash_reports)) },
                    supportingContent = { Text(stringResource(R.string.privacy_crash_reports_summary)) },
                    trailingContent = {
                        Switch(checked = crashReports, onCheckedChange = { viewModel.setCrashReports(it) })
                    },
                    modifier = Modifier.clickable { viewModel.setCrashReports(!crashReports) },
                )
            }
            item {
                val usageAnalytics by viewModel.usageAnalyticsEnabled.collectAsState()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.privacy_usage_analytics)) },
                    supportingContent = { Text(stringResource(R.string.privacy_usage_analytics_summary)) },
                    trailingContent = {
                        Switch(checked = usageAnalytics, onCheckedChange = { viewModel.setUsageAnalytics(it) })
                    },
                    modifier = Modifier.clickable { viewModel.setUsageAnalytics(!usageAnalytics) },
                )
            }
        }
    }
}
