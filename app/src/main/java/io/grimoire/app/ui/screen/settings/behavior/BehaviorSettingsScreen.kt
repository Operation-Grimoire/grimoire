package io.grimoire.app.ui.screen.settings.behavior

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
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
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_behavior_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    leadingContent = { Icon(AppIcons.Vibration, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.behavior_haptics)) },
                    supportingContent = {
                        Text(stringResource(R.string.behavior_haptics_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = viewModel::setHapticsEnabled,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setHapticsEnabled(!hapticsEnabled) },
                )
            }
        }
    }
}
