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
import io.grimoire.app.ui.screen.settings.SettingsViewModel

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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Behavior") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    leadingContent = { Icon(AppIcons.Vibration, contentDescription = null) },
                    headlineContent = { Text("Haptic feedback") },
                    supportingContent = {
                        Text("Vibrate on long-press, tab switches, and selection actions.")
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
