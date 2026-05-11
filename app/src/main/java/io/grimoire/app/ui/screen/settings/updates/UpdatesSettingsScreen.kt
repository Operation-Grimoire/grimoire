package io.grimoire.app.ui.screen.settings.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.grimoire.app.data.preferences.UpdateChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpdatesSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val channel by viewModel.channel.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Updates") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(headlineContent = { Text("Update channel") })
            }
            items(UpdateChannel.entries.toList()) { entry ->
                val selected = channel == entry
                ListItem(
                    leadingContent = {
                        RadioButton(selected = selected, onClick = { viewModel.setChannel(entry) })
                    },
                    headlineContent = { Text(entry.displayName) },
                    supportingContent = { Text(entry.description) },
                    modifier = Modifier.clickable { viewModel.setChannel(entry) },
                )
            }
        }
    }
}

private val UpdateChannel.displayName: String
    get() = when (this) {
        UpdateChannel.STABLE -> "Stable"
        UpdateChannel.BETA -> "Beta"
    }

private val UpdateChannel.description: String
    get() = when (this) {
        UpdateChannel.STABLE -> "Tagged releases only. Recommended."
        UpdateChannel.BETA -> "Fresh builds from main on every commit. May be unstable."
    }
