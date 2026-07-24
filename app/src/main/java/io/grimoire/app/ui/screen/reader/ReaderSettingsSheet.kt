package io.grimoire.app.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    sheetState: SheetState,
    colors: ReaderColors,
    textStyle: TextStyle,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    orientation: ReaderOrientation,
    hideInlineImages: Boolean,
    showChapterProgressPercent: Boolean,
    showNovelProgressPercent: Boolean,
    grimoireEasterEggEnabled: Boolean,
    markAsReadStrategy: MarkAsReadStrategy,
    markAsReadThreshold: Int,
    markAsReadParagraphsFromEnd: Int,
    ttsEnabled: Boolean,
    ttsEngine: TtsEngineType,
    ttsSpeechRate: Int,
    ttsPitch: Int,
    ttsAutoAdvance: Boolean,
    onDismiss: () -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Int) -> Unit,
    onParagraphSpacing: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onColorTheme: (ReaderColorTheme) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onHideInlineImages: (Boolean) -> Unit,
    onShowChapterProgressPercent: (Boolean) -> Unit,
    onShowNovelProgressPercent: (Boolean) -> Unit,
    onGrimoireEasterEggEnabled: (Boolean) -> Unit,
    onMarkAsReadStrategy: (MarkAsReadStrategy) -> Unit,
    onMarkAsReadThreshold: (Int) -> Unit,
    onMarkAsReadParagraphsFromEnd: (Int) -> Unit,
    onTtsEnabled: (Boolean) -> Unit,
    onTtsEngine: (TtsEngineType) -> Unit,
    onTtsSpeechRate: (Int) -> Unit,
    onTtsPitch: (Int) -> Unit,
    onTtsAutoAdvance: (Boolean) -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        SwipeTabRow(
            tabs = listOf(
                stringResource(R.string.reader_display_tab),
                stringResource(R.string.reader_tts_tab),
            ),
            style = SwipeTabStyle.Primary,
            // Inside a sheet: wrap the page height instead of filling the screen.
            fillHeight = false,
        ) { page ->
        if (page == 0) {
            ReaderDisplaySettings(
                colors = colors,
                textStyle = textStyle,
                fontSize = fontSize,
                lineHeightTimes10 = lineHeightTimes10,
                paragraphSpacing = paragraphSpacing,
                readerFont = readerFont,
                colorTheme = colorTheme,
                orientation = orientation,
                hideInlineImages = hideInlineImages,
                showChapterProgressPercent = showChapterProgressPercent,
                showNovelProgressPercent = showNovelProgressPercent,
                grimoireEasterEggEnabled = grimoireEasterEggEnabled,
                markAsReadStrategy = markAsReadStrategy,
                markAsReadThreshold = markAsReadThreshold,
                markAsReadParagraphsFromEnd = markAsReadParagraphsFromEnd,
                onFontSize = onFontSize,
                onLineHeight = onLineHeight,
                onParagraphSpacing = onParagraphSpacing,
                onFont = onFont,
                onColorTheme = onColorTheme,
                onOrientation = onOrientation,
                onHideInlineImages = onHideInlineImages,
                onShowChapterProgressPercent = onShowChapterProgressPercent,
                onShowNovelProgressPercent = onShowNovelProgressPercent,
                onGrimoireEasterEggEnabled = onGrimoireEasterEggEnabled,
                onMarkAsReadStrategy = onMarkAsReadStrategy,
                onMarkAsReadThreshold = onMarkAsReadThreshold,
                onMarkAsReadParagraphsFromEnd = onMarkAsReadParagraphsFromEnd,
            )
        } else {
            ReaderTtsSettings(
                enabled = ttsEnabled,
                engine = ttsEngine,
                speechRate = ttsSpeechRate,
                pitch = ttsPitch,
                autoAdvance = ttsAutoAdvance,
                onEnabled = onTtsEnabled,
                onEngine = onTtsEngine,
                onSpeechRate = onTtsSpeechRate,
                onPitch = onTtsPitch,
                onAutoAdvance = onTtsAutoAdvance,
                onOpenFullSettings = onOpenTtsSettings,
            )
        }
        }
    }
}

@Composable
private fun ReaderDisplaySettings(
    colors: ReaderColors,
    textStyle: TextStyle,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    orientation: ReaderOrientation,
    hideInlineImages: Boolean,
    showChapterProgressPercent: Boolean,
    showNovelProgressPercent: Boolean,
    grimoireEasterEggEnabled: Boolean,
    markAsReadStrategy: MarkAsReadStrategy,
    markAsReadThreshold: Int,
    markAsReadParagraphsFromEnd: Int,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Int) -> Unit,
    onParagraphSpacing: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onColorTheme: (ReaderColorTheme) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onHideInlineImages: (Boolean) -> Unit,
    onShowChapterProgressPercent: (Boolean) -> Unit,
    onShowNovelProgressPercent: (Boolean) -> Unit,
    onGrimoireEasterEggEnabled: (Boolean) -> Unit,
    onMarkAsReadStrategy: (MarkAsReadStrategy) -> Unit,
    onMarkAsReadThreshold: (Int) -> Unit,
    onMarkAsReadParagraphsFromEnd: (Int) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    // Live preview
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_preview_text),
            style = textStyle,
        )
    }
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSectionLabel(stringResource(R.string.reader_color_theme))
        ColorThemePicker(selected = colorTheme, onSelect = onColorTheme)

        SettingsSectionLabel(stringResource(R.string.reader_font))
        FontPicker(selected = readerFont, onSelect = onFont)

        StepperRow(
            label = stringResource(R.string.reader_font_size),
            value = "${fontSize}sp",
            onDecrement = { onFontSize(fontSize - 1) },
            onIncrement = { onFontSize(fontSize + 1) },
            decrementEnabled = fontSize > 12,
            incrementEnabled = fontSize < 32,
        )

        StepperRow(
            label = stringResource(R.string.reader_line_height),
            value = "%.1f×".format(lineHeightTimes10 / 10f),
            onDecrement = { onLineHeight(lineHeightTimes10 - 1) },
            onIncrement = { onLineHeight(lineHeightTimes10 + 1) },
            decrementEnabled = lineHeightTimes10 > 10,
            incrementEnabled = lineHeightTimes10 < 30,
        )

        StepperRow(
            label = stringResource(R.string.reader_paragraph_spacing),
            value = "${paragraphSpacing}dp",
            onDecrement = { onParagraphSpacing(paragraphSpacing - 4) },
            onIncrement = { onParagraphSpacing(paragraphSpacing + 4) },
            decrementEnabled = paragraphSpacing > 0,
            incrementEnabled = paragraphSpacing < 32,
        )

        SettingsSectionLabel(stringResource(R.string.reader_screen_rotation))
        OrientationPicker(selected = orientation, onSelect = onOrientation)

        SettingsSectionLabel(stringResource(R.string.reader_privacy))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_hide_images_setting),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.reader_hide_images_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = hideInlineImages, onCheckedChange = onHideInlineImages)
        }

        SettingsSectionLabel(stringResource(R.string.reader_reading_progress))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_show_chapter_percent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showChapterProgressPercent,
                onCheckedChange = onShowChapterProgressPercent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_show_book_percent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showNovelProgressPercent,
                onCheckedChange = onShowNovelProgressPercent,
            )
        }

        SettingsSectionLabel(stringResource(R.string.reader_auto_mark_read))
        MarkAsReadStrategyPicker(
            selected = markAsReadStrategy,
            onSelect = onMarkAsReadStrategy,
        )
        when (markAsReadStrategy) {
            MarkAsReadStrategy.PERCENT -> StepperRow(
                label = stringResource(R.string.reader_mark_at),
                value = "$markAsReadThreshold%",
                onDecrement = { onMarkAsReadThreshold(markAsReadThreshold - 5) },
                onIncrement = { onMarkAsReadThreshold(markAsReadThreshold + 5) },
                decrementEnabled = markAsReadThreshold > 50,
                incrementEnabled = markAsReadThreshold < 100,
            )
            MarkAsReadStrategy.PARAGRAPHS_FROM_END -> StepperRow(
                label = stringResource(R.string.reader_within_last),
                value = pluralStringResource(
                    R.plurals.reader_paragraph_count,
                    markAsReadParagraphsFromEnd,
                    markAsReadParagraphsFromEnd,
                ),
                onDecrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd - 1) },
                onIncrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd + 1) },
                decrementEnabled = markAsReadParagraphsFromEnd > 0,
                incrementEnabled = markAsReadParagraphsFromEnd < 20,
            )
            MarkAsReadStrategy.AT_END -> Unit
        }

        SettingsSectionLabel(stringResource(R.string.reader_easter_eggs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_animate_grimoire),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.reader_animate_grimoire_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = grimoireEasterEggEnabled,
                onCheckedChange = onGrimoireEasterEggEnabled,
            )
        }
    }
    }
}

@Composable
private fun ReaderTtsSettings(
    enabled: Boolean,
    engine: TtsEngineType,
    speechRate: Int,
    pitch: Int,
    autoAdvance: Boolean,
    onEnabled: (Boolean) -> Unit,
    onEngine: (TtsEngineType) -> Unit,
    onSpeechRate: (Int) -> Unit,
    onPitch: (Int) -> Unit,
    onAutoAdvance: (Boolean) -> Unit,
    onOpenFullSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_enable_tts),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.reader_enable_tts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }

        SettingsSectionLabel(stringResource(R.string.reader_speech_engine))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = engine == TtsEngineType.DEVICE,
                onClick = { onEngine(TtsEngineType.DEVICE) },
                label = { Text(stringResource(R.string.reader_on_device)) },
            )
            FilterChip(
                selected = engine == TtsEngineType.ELEVENLABS,
                onClick = { onEngine(TtsEngineType.ELEVENLABS) },
                label = { Text("ElevenLabs") },
            )
        }

        StepperRow(
            label = stringResource(R.string.reader_speech_rate),
            value = "%.2f×".format(speechRate / 100f),
            onDecrement = { onSpeechRate(speechRate - 5) },
            onIncrement = { onSpeechRate(speechRate + 5) },
            decrementEnabled = speechRate > 25,
            incrementEnabled = speechRate < 300,
        )

        StepperRow(
            label = stringResource(R.string.reader_pitch),
            value = "%.2f×".format(pitch / 100f),
            onDecrement = { onPitch(pitch - 5) },
            onIncrement = { onPitch(pitch + 5) },
            decrementEnabled = pitch > 50,
            incrementEnabled = pitch < 200,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_auto_advance),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoAdvance, onCheckedChange = onAutoAdvance)
        }

        TextButton(onClick = onOpenFullSettings) {
            Text(stringResource(R.string.reader_manage_voices))
        }
    }
}
