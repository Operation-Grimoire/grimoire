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
import io.grimoire.app.data.local.entity.ReaderTextAlign
import io.grimoire.app.ui.component.sheet.SheetSectionLabel
import io.grimoire.app.ui.component.sheet.SheetSwitchRow
import io.grimoire.app.ui.component.sheet.SingleChoiceSegmented
import io.grimoire.app.ui.component.sheet.StepperRow
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
    previewText: String,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    orientation: ReaderOrientation,
    textAlign: ReaderTextAlign,
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
    onTextAlign: (ReaderTextAlign) -> Unit,
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
                previewText = previewText,
                fontSize = fontSize,
                lineHeightTimes10 = lineHeightTimes10,
                paragraphSpacing = paragraphSpacing,
                readerFont = readerFont,
                colorTheme = colorTheme,
                orientation = orientation,
                textAlign = textAlign,
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
                onTextAlign = onTextAlign,
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
    previewText: String,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    orientation: ReaderOrientation,
    textAlign: ReaderTextAlign,
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
    onTextAlign: (ReaderTextAlign) -> Unit,
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
            text = previewText,
            style = textStyle,
            maxLines = 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
        SheetSectionLabel(stringResource(R.string.reader_color_theme))
        ColorThemePicker(selected = colorTheme, onSelect = onColorTheme)

        SheetSectionLabel(stringResource(R.string.reader_font))
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

        SheetSectionLabel(stringResource(R.string.reader_text_alignment))
        SingleChoiceSegmented(
            options = ReaderTextAlign.entries,
            selected = textAlign,
            onSelect = onTextAlign,
            label = { value ->
                when (value) {
                    ReaderTextAlign.AUTO -> stringResource(R.string.reader_align_auto)
                    ReaderTextAlign.LEFT -> stringResource(R.string.reader_align_left)
                    ReaderTextAlign.RIGHT -> stringResource(R.string.reader_align_right)
                    ReaderTextAlign.CENTER -> stringResource(R.string.reader_align_center)
                    ReaderTextAlign.JUSTIFY -> stringResource(R.string.reader_align_justify)
                }
            },
        )
        Text(
            text = stringResource(R.string.reader_text_alignment_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetSectionLabel(stringResource(R.string.reader_screen_rotation))
        SingleChoiceSegmented(
            options = ReaderOrientation.entries,
            selected = orientation,
            onSelect = onOrientation,
            label = { value ->
                when (value) {
                    ReaderOrientation.FREE -> stringResource(R.string.reader_orientation_free)
                    ReaderOrientation.PORTRAIT -> stringResource(R.string.reader_orientation_vertical)
                    ReaderOrientation.LANDSCAPE -> stringResource(R.string.reader_orientation_horizontal)
                }
            },
        )

        SheetSectionLabel(stringResource(R.string.reader_privacy))
        SheetSwitchRow(
            title = stringResource(R.string.reader_hide_images_setting),
            hint = stringResource(R.string.reader_hide_images_hint),
            checked = hideInlineImages,
            onCheckedChange = onHideInlineImages,
        )

        SheetSectionLabel(stringResource(R.string.reader_reading_progress))
        SheetSwitchRow(
            title = stringResource(R.string.reader_show_chapter_percent),
            checked = showChapterProgressPercent,
            onCheckedChange = onShowChapterProgressPercent,
        )
        SheetSwitchRow(
            title = stringResource(R.string.reader_show_book_percent),
            checked = showNovelProgressPercent,
            onCheckedChange = onShowNovelProgressPercent,
        )

        SheetSectionLabel(stringResource(R.string.reader_auto_mark_read))
        SingleChoiceSegmented(
            options = MarkAsReadStrategy.entries,
            selected = markAsReadStrategy,
            onSelect = onMarkAsReadStrategy,
            label = { value ->
                when (value) {
                    MarkAsReadStrategy.PERCENT -> stringResource(R.string.reader_mark_percent)
                    MarkAsReadStrategy.PARAGRAPHS_FROM_END -> stringResource(R.string.reader_mark_near_end)
                    MarkAsReadStrategy.AT_END -> stringResource(R.string.reader_mark_at_end)
                }
            },
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

        SheetSectionLabel(stringResource(R.string.reader_easter_eggs))
        SheetSwitchRow(
            title = stringResource(R.string.reader_animate_grimoire),
            hint = stringResource(R.string.reader_animate_grimoire_hint),
            checked = grimoireEasterEggEnabled,
            onCheckedChange = onGrimoireEasterEggEnabled,
        )
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
        SheetSwitchRow(
            title = stringResource(R.string.reader_enable_tts),
            hint = stringResource(R.string.reader_enable_tts_hint),
            checked = enabled,
            onCheckedChange = onEnabled,
        )

        SheetSectionLabel(stringResource(R.string.reader_speech_engine))
        SingleChoiceSegmented(
            options = TtsEngineType.entries,
            selected = engine,
            onSelect = onEngine,
            label = { value ->
                when (value) {
                    TtsEngineType.DEVICE -> stringResource(R.string.reader_on_device)
                    TtsEngineType.ELEVENLABS -> "ElevenLabs"
                }
            },
        )

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

        SheetSwitchRow(
            title = stringResource(R.string.reader_auto_advance),
            checked = autoAdvance,
            onCheckedChange = onAutoAdvance,
        )

        TextButton(onClick = onOpenFullSettings) {
            Text(stringResource(R.string.reader_manage_voices))
        }
    }
}
