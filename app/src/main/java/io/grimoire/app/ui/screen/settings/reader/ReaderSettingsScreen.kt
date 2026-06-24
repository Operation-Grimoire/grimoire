package io.grimoire.app.ui.screen.settings.reader

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.ui.screen.reader.ColorThemePicker
import io.grimoire.app.ui.screen.reader.FontPicker
import io.grimoire.app.ui.screen.reader.MarkAsReadStrategyPicker
import io.grimoire.app.ui.screen.reader.OrientationPicker
import io.grimoire.app.ui.screen.reader.StepperRow
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val markAsReadStrategy by viewModel.readerMarkAsReadStrategy.collectAsState()
    val threshold by viewModel.readerMarkAsReadThreshold.collectAsState()
    val paragraphsFromEnd by viewModel.readerMarkAsReadParagraphsFromEnd.collectAsState()
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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
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
                    headlineContent = { Text("Auto-mark as read") },
                    supportingContent = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            MarkAsReadStrategyPicker(
                                selected = markAsReadStrategy,
                                onSelect = viewModel::setReaderMarkAsReadStrategy,
                            )
                            when (markAsReadStrategy) {
                                MarkAsReadStrategy.PERCENT -> StepperRow(
                                    label = "Mark at",
                                    value = "$threshold%",
                                    onDecrement = { viewModel.setReaderMarkAsReadThreshold(threshold - 5) },
                                    onIncrement = { viewModel.setReaderMarkAsReadThreshold(threshold + 5) },
                                    decrementEnabled = threshold > 50,
                                    incrementEnabled = threshold < 100,
                                )
                                MarkAsReadStrategy.PARAGRAPHS_FROM_END -> StepperRow(
                                    label = "Within last",
                                    value = if (paragraphsFromEnd == 1) "1 paragraph"
                                            else "$paragraphsFromEnd paragraphs",
                                    onDecrement = { viewModel.setReaderMarkAsReadParagraphsFromEnd(paragraphsFromEnd - 1) },
                                    onIncrement = { viewModel.setReaderMarkAsReadParagraphsFromEnd(paragraphsFromEnd + 1) },
                                    decrementEnabled = paragraphsFromEnd > 0,
                                    incrementEnabled = paragraphsFromEnd < 20,
                                )
                                MarkAsReadStrategy.AT_END -> Unit
                            }
                        }
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
