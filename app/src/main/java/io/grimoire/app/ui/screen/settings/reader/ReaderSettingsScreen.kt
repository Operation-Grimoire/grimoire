package io.grimoire.app.ui.screen.settings.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.grimoire.app.ui.screen.reader.ColorThemePicker
import io.grimoire.app.ui.screen.reader.FontPicker
import io.grimoire.app.ui.screen.reader.OrientationPicker
import io.grimoire.app.ui.screen.reader.StepperRow
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader
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
    val hideInlineImages by viewModel.readerHideInlineImages.collectAsState()
    val colorTheme by viewModel.readerColorTheme.collectAsState()
    val font by viewModel.readerFont.collectAsState()
    val fontSize by viewModel.readerFontSize.collectAsState()
    val lineHeightTimes10 by viewModel.readerLineHeightTimes10.collectAsState()
    val paragraphSpacing by viewModel.readerParagraphSpacing.collectAsState()

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
            item { SettingsSectionHeader("Appearance") }
            item {
                ListItem(
                    headlineContent = { Text("Color theme") },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            ColorThemePicker(
                                selected = colorTheme,
                                onSelect = viewModel::setReaderColorTheme,
                            )
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Font") },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            FontPicker(
                                selected = font,
                                onSelect = viewModel::setReaderFont,
                            )
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StepperRow(
                                label = "Font size",
                                value = "${fontSize}sp",
                                onDecrement = { viewModel.setReaderFontSize(fontSize - 1) },
                                onIncrement = { viewModel.setReaderFontSize(fontSize + 1) },
                                decrementEnabled = fontSize > 12,
                                incrementEnabled = fontSize < 32,
                            )
                            StepperRow(
                                label = "Line height",
                                value = "%.1f×".format(lineHeightTimes10 / 10f),
                                onDecrement = { viewModel.setReaderLineHeight(lineHeightTimes10 - 1) },
                                onIncrement = { viewModel.setReaderLineHeight(lineHeightTimes10 + 1) },
                                decrementEnabled = lineHeightTimes10 > 10,
                                incrementEnabled = lineHeightTimes10 < 30,
                            )
                            StepperRow(
                                label = "Paragraph spacing",
                                value = "${paragraphSpacing}dp",
                                onDecrement = { viewModel.setReaderParagraphSpacing(paragraphSpacing - 4) },
                                onIncrement = { viewModel.setReaderParagraphSpacing(paragraphSpacing + 4) },
                                decrementEnabled = paragraphSpacing > 0,
                                incrementEnabled = paragraphSpacing < 32,
                            )
                        }
                    },
                )
            }
            item { SettingsSectionHeader("Reading") }
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
            item { SettingsSectionHeader("Display") }
            item {
                ListItem(
                    headlineContent = { Text("Screen rotation") },
                    supportingContent = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            OrientationPicker(
                                selected = orientation,
                                onSelect = viewModel::setReaderOrientation,
                            )
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
            item { SettingsSectionHeader("Privacy") }
            item {
                ListItem(
                    headlineContent = { Text("Hide images") },
                    supportingContent = { Text("Hide inline images until you tap to reveal or hold to peek") },
                    trailingContent = {
                        Switch(
                            checked = hideInlineImages,
                            onCheckedChange = { viewModel.setReaderHideInlineImages(it) },
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setReaderHideInlineImages(!hideInlineImages)
                    },
                )
            }
        }
    }
}
