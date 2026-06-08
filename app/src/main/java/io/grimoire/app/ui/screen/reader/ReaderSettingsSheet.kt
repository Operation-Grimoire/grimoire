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
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.ui.component.SwipeTabRow
import io.grimoire.app.ui.component.SwipeTabStyle

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
            tabs = listOf("Display", "Read aloud"),
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
            text = "The quick brown fox jumps over the lazy dog. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.",
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
        SettingsSectionLabel("Color theme")
        ColorThemePicker(selected = colorTheme, onSelect = onColorTheme)

        SettingsSectionLabel("Font")
        FontPicker(selected = readerFont, onSelect = onFont)

        StepperRow(
            label = "Font size",
            value = "${fontSize}sp",
            onDecrement = { onFontSize(fontSize - 1) },
            onIncrement = { onFontSize(fontSize + 1) },
            decrementEnabled = fontSize > 12,
            incrementEnabled = fontSize < 32,
        )

        StepperRow(
            label = "Line height",
            value = "%.1f×".format(lineHeightTimes10 / 10f),
            onDecrement = { onLineHeight(lineHeightTimes10 - 1) },
            onIncrement = { onLineHeight(lineHeightTimes10 + 1) },
            decrementEnabled = lineHeightTimes10 > 10,
            incrementEnabled = lineHeightTimes10 < 30,
        )

        StepperRow(
            label = "Paragraph spacing",
            value = "${paragraphSpacing}dp",
            onDecrement = { onParagraphSpacing(paragraphSpacing - 4) },
            onIncrement = { onParagraphSpacing(paragraphSpacing + 4) },
            decrementEnabled = paragraphSpacing > 0,
            incrementEnabled = paragraphSpacing < 32,
        )

        SettingsSectionLabel("Screen rotation")
        OrientationPicker(selected = orientation, onSelect = onOrientation)

        SettingsSectionLabel("Privacy")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hide images",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Tap to reveal · hold to peek",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = hideInlineImages, onCheckedChange = onHideInlineImages)
        }

        SettingsSectionLabel("Reading progress")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show chapter %",
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
                text = "Show book %",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showNovelProgressPercent,
                onCheckedChange = onShowNovelProgressPercent,
            )
        }

        SettingsSectionLabel("Auto-mark as read")
        MarkAsReadStrategyPicker(
            selected = markAsReadStrategy,
            onSelect = onMarkAsReadStrategy,
        )
        when (markAsReadStrategy) {
            MarkAsReadStrategy.PERCENT -> StepperRow(
                label = "Mark at",
                value = "$markAsReadThreshold%",
                onDecrement = { onMarkAsReadThreshold(markAsReadThreshold - 5) },
                onIncrement = { onMarkAsReadThreshold(markAsReadThreshold + 5) },
                decrementEnabled = markAsReadThreshold > 50,
                incrementEnabled = markAsReadThreshold < 100,
            )
            MarkAsReadStrategy.PARAGRAPHS_FROM_END -> StepperRow(
                label = "Within last",
                value = if (markAsReadParagraphsFromEnd == 1) "1 paragraph"
                        else "$markAsReadParagraphsFromEnd paragraphs",
                onDecrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd - 1) },
                onIncrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd + 1) },
                decrementEnabled = markAsReadParagraphsFromEnd > 0,
                incrementEnabled = markAsReadParagraphsFromEnd < 20,
            )
            MarkAsReadStrategy.AT_END -> Unit
        }

        SettingsSectionLabel("Easter eggs")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Animate the word \"grimoire\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Tap a styled word in any chapter for details",
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
                    text = "Enable text-to-speech",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Show playback controls in the reader",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }

        SettingsSectionLabel("Speech engine")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = engine == TtsEngineType.DEVICE,
                onClick = { onEngine(TtsEngineType.DEVICE) },
                label = { Text("On-device") },
            )
            FilterChip(
                selected = engine == TtsEngineType.ELEVENLABS,
                onClick = { onEngine(TtsEngineType.ELEVENLABS) },
                label = { Text("ElevenLabs") },
            )
        }

        StepperRow(
            label = "Speech rate",
            value = "%.2f×".format(speechRate / 100f),
            onDecrement = { onSpeechRate(speechRate - 5) },
            onIncrement = { onSpeechRate(speechRate + 5) },
            decrementEnabled = speechRate > 25,
            incrementEnabled = speechRate < 300,
        )

        StepperRow(
            label = "Pitch (on-device only)",
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
                text = "Auto-advance to next chapter",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoAdvance, onCheckedChange = onAutoAdvance)
        }

        TextButton(onClick = onOpenFullSettings) {
            Text("Manage voices & more settings")
        }
    }
}
