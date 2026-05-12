package io.grimoire.app.ui.screen.settings.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val threshold by viewModel.readerMarkAsReadThreshold.collectAsState()
    val orientation by viewModel.readerOrientation.collectAsState()
    val hideNotificationBar by viewModel.readerHideNotificationBar.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Reader") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Mark as read at $threshold%") },
                    supportingContent = {
                        Slider(
                            value = threshold.toFloat(),
                            onValueChange = { viewModel.setReaderMarkAsReadThreshold(it.roundToInt()) },
                            valueRange = 50f..100f,
                            steps = 9,
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Screen rotation") },
                    supportingContent = {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderOrientation.entries.forEach { value ->
                                val label = when (value) {
                                    ReaderOrientation.FREE -> "Free"
                                    ReaderOrientation.PORTRAIT -> "Vertical"
                                    ReaderOrientation.LANDSCAPE -> "Horizontal"
                                }
                                FilterChip(
                                    selected = orientation == value,
                                    onClick = { viewModel.setReaderOrientation(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Hide notification bar") },
                    supportingContent = { Text("Hide the status bar at the top of the screen while reading") },
                    trailingContent = {
                        Switch(
                            checked = hideNotificationBar,
                            onCheckedChange = { viewModel.setReaderHideNotificationBar(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setReaderHideNotificationBar(!hideNotificationBar)
                    },
                )
            }
        }
    }
}
