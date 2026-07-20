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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.ui.screen.reader.ColorThemePicker
import io.grimoire.app.ui.screen.reader.FontPicker
import io.grimoire.app.ui.screen.reader.MarkAsReadStrategyPicker
import io.grimoire.app.ui.screen.reader.OrientationPicker
import io.grimoire.app.ui.screen.reader.StepperRow
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.screen.settings.common.SettingsSectionHeader
import io.grimoire.app.R

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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_reader_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { SettingsSectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_settings_color_theme)) },
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
                    headlineContent = { Text(stringResource(R.string.reader_font)) },
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
                                label = stringResource(R.string.reader_settings_font_size),
                                value = "${fontSize}sp",
                                onDecrement = { viewModel.setReaderFontSize(fontSize - 1) },
                                onIncrement = { viewModel.setReaderFontSize(fontSize + 1) },
                                decrementEnabled = fontSize > 12,
                                incrementEnabled = fontSize < 32,
                            )
                            StepperRow(
                                label = stringResource(R.string.reader_settings_line_height),
                                value = "%.1f×".format(lineHeightTimes10 / 10f),
                                onDecrement = { viewModel.setReaderLineHeight(lineHeightTimes10 - 1) },
                                onIncrement = { viewModel.setReaderLineHeight(lineHeightTimes10 + 1) },
                                decrementEnabled = lineHeightTimes10 > 10,
                                incrementEnabled = lineHeightTimes10 < 30,
                            )
                            StepperRow(
                                label = stringResource(R.string.reader_settings_paragraph_spacing),
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
            item { SettingsSectionHeader(stringResource(R.string.settings_section_reading)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_settings_auto_mark)) },
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
                                    label = stringResource(R.string.reader_mark_at),
                                    value = "$threshold%",
                                    onDecrement = { viewModel.setReaderMarkAsReadThreshold(threshold - 5) },
                                    onIncrement = { viewModel.setReaderMarkAsReadThreshold(threshold + 5) },
                                    decrementEnabled = threshold > 50,
                                    incrementEnabled = threshold < 100,
                                )
                                MarkAsReadStrategy.PARAGRAPHS_FROM_END -> StepperRow(
                                    label = stringResource(R.string.reader_settings_within_last),
                                    value = pluralStringResource(
                                        R.plurals.reader_settings_paragraph_count,
                                        paragraphsFromEnd,
                                        paragraphsFromEnd,
                                    ),
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
            item { SettingsSectionHeader(stringResource(R.string.settings_section_display)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_settings_rotation)) },
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
                    headlineContent = { Text(stringResource(R.string.reader_settings_hide_status_bar)) },
                    supportingContent = { Text(stringResource(R.string.reader_settings_hide_status_bar_summary)) },
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
            item { SettingsSectionHeader(stringResource(R.string.settings_section_privacy)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reader_settings_hide_images)) },
                    supportingContent = { Text(stringResource(R.string.reader_settings_hide_images_summary)) },
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
