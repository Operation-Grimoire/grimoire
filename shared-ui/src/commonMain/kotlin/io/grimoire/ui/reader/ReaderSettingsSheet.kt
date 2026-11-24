package io.grimoire.ui.reader

import io.grimoire.ui.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.SwipeTabRow
import io.grimoire.ui.component.SwipeTabStyle
import io.grimoire.ui.component.sheet.SheetSectionLabel
import io.grimoire.ui.component.sheet.SheetSwitchRow
import io.grimoire.ui.component.sheet.SingleChoiceSegmented
import io.grimoire.ui.component.sheet.StepperRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    sheetState: SheetState,
    colors: ReaderColors,
    textStyle: TextStyle,
    previewText: String,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    /** Null hides screen rotation, which only Android offers. */
    orientation: ReaderOrientation?,
    textAlign: ReaderTextAlign,
    /** Null hides the image privacy switch. */
    hideInlineImages: Boolean?,
    showChapterProgressPercent: Boolean,
    showNovelProgressPercent: Boolean,
    grimoireEasterEggEnabled: Boolean,
    /** Null hides automatic mark-as-read. */
    markAsReadStrategy: MarkAsReadStrategy?,
    markAsReadThreshold: Int,
    markAsReadParagraphsFromEnd: Int,
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
    /** Read-aloud settings tab; null shows display settings only. */
    ttsTab: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        val display: @Composable () -> Unit = {
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
        }
        if (ttsTab == null) display() else SwipeTabRow(
            tabs = listOf(
                stringResource(Res.string.reader_display_tab),
                stringResource(Res.string.reader_tts_tab),
            ),
            style = SwipeTabStyle.Secondary,
            // Inside a sheet: wrap the page height instead of filling the screen.
            fillHeight = false,
        ) { page -> if (page == 0) display() else ttsTab() }
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
    orientation: ReaderOrientation?,
    textAlign: ReaderTextAlign,
    /** Null hides the image privacy switch. */
    hideInlineImages: Boolean?,
    showChapterProgressPercent: Boolean,
    showNovelProgressPercent: Boolean,
    grimoireEasterEggEnabled: Boolean,
    /** Null hides automatic mark-as-read. */
    markAsReadStrategy: MarkAsReadStrategy?,
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
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pinPreview = !constraints.hasBoundedHeight || maxHeight >= 420.dp
        val previewMaxHeight = if (constraints.hasBoundedHeight) {
            (maxHeight * 0.30f).coerceAtMost(156.dp)
        } else {
            156.dp
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (pinPreview) {
                // Keep the preview pinned above the scroll container when vertical space allows
                // so alignment, spacing, and typography changes stay visible, capped relative to sheet height.
                ReaderPreviewCard(
                    colors = colors,
                    textStyle = textStyle,
                    previewText = previewText,
                    dividerColor = dividerColor,
                    modifier = Modifier.heightIn(max = previewMaxHeight),
                )
                HorizontalDivider(color = dividerColor)
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(top = 4.dp, bottom = 16.dp),
            ) {
                if (!pinPreview) {
                    ReaderPreviewCard(
                        colors = colors,
                        textStyle = textStyle,
                        previewText = previewText,
                        dividerColor = dividerColor,
                        modifier = Modifier.heightIn(max = previewMaxHeight),
                    )
                    HorizontalDivider(color = dividerColor)
                }
                SheetSectionLabel(stringResource(Res.string.reader_color_theme))
            Spacer(Modifier.height(6.dp))
            ReaderColorThemePicker(
                selected = colorTheme,
                onSelect = onColorTheme,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            SheetSectionLabel(stringResource(Res.string.reader_font))
            Spacer(Modifier.height(6.dp))
            FontPicker(
                selected = readerFont,
                onSelect = onFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StepperRow(
                    label = stringResource(Res.string.reader_font_size),
                    value = "${fontSize}sp",
                    onDecrement = { onFontSize(fontSize - 1) },
                    onIncrement = { onFontSize(fontSize + 1) },
                    decrementEnabled = fontSize > 12,
                    incrementEnabled = fontSize < 32,
                )

                StepperRow(
                    label = stringResource(Res.string.reader_line_height),
                    value = "${lineHeightTimes10 / 10}.${lineHeightTimes10 % 10}×",
                    onDecrement = { onLineHeight(lineHeightTimes10 - 1) },
                    onIncrement = { onLineHeight(lineHeightTimes10 + 1) },
                    decrementEnabled = lineHeightTimes10 > 10,
                    incrementEnabled = lineHeightTimes10 < 30,
                )

                StepperRow(
                    label = stringResource(Res.string.reader_paragraph_spacing),
                    value = "${paragraphSpacing}dp",
                    onDecrement = { onParagraphSpacing(paragraphSpacing - 4) },
                    onIncrement = { onParagraphSpacing(paragraphSpacing + 4) },
                    decrementEnabled = paragraphSpacing > 0,
                    incrementEnabled = paragraphSpacing < 32,
                )
            }

            SheetSectionLabel(stringResource(Res.string.reader_text_alignment))
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmented(
                options = ReaderTextAlign.entries,
                selected = textAlign,
                onSelect = onTextAlign,
                label = { value ->
                    when (value) {
                        ReaderTextAlign.AUTO -> stringResource(Res.string.reader_align_auto)
                        ReaderTextAlign.LEFT -> stringResource(Res.string.reader_align_left)
                        ReaderTextAlign.RIGHT -> stringResource(Res.string.reader_align_right)
                        ReaderTextAlign.CENTER -> stringResource(Res.string.reader_align_center)
                        ReaderTextAlign.JUSTIFY -> stringResource(Res.string.reader_align_justify)
                    }
                },
            )
            Text(
                text = stringResource(Res.string.reader_text_alignment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (orientation != null) {
            SheetSectionLabel(stringResource(Res.string.reader_screen_rotation))
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmented(
                options = ReaderOrientation.entries,
                selected = orientation,
                onSelect = onOrientation,
                label = { value ->
                    when (value) {
                        ReaderOrientation.FREE -> stringResource(Res.string.reader_orientation_free)
                        ReaderOrientation.PORTRAIT -> stringResource(Res.string.reader_orientation_vertical)
                        ReaderOrientation.LANDSCAPE -> stringResource(Res.string.reader_orientation_horizontal)
                    }
                },
            )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                color = dividerColor,
            )

            if (hideInlineImages != null) {
            SheetSectionLabel(stringResource(Res.string.reader_privacy))
            SheetSwitchRow(
                title = stringResource(Res.string.reader_hide_images_setting),
                hint = stringResource(Res.string.reader_hide_images_hint),
                checked = hideInlineImages,
                onCheckedChange = onHideInlineImages,
            )
            }

            SheetSectionLabel(stringResource(Res.string.reader_reading_progress))
            SheetSwitchRow(
                title = stringResource(Res.string.reader_show_chapter_percent),
                checked = showChapterProgressPercent,
                onCheckedChange = onShowChapterProgressPercent,
            )
            SheetSwitchRow(
                title = stringResource(Res.string.reader_show_book_percent),
                checked = showNovelProgressPercent,
                onCheckedChange = onShowNovelProgressPercent,
            )

            if (markAsReadStrategy != null) {
            SheetSectionLabel(stringResource(Res.string.reader_auto_mark_read))
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmented(
                options = MarkAsReadStrategy.entries,
                selected = markAsReadStrategy,
                onSelect = onMarkAsReadStrategy,
                label = { value ->
                    when (value) {
                        MarkAsReadStrategy.PERCENT -> stringResource(Res.string.reader_mark_percent)
                        MarkAsReadStrategy.PARAGRAPHS_FROM_END -> stringResource(Res.string.reader_mark_near_end)
                        MarkAsReadStrategy.AT_END -> stringResource(Res.string.reader_mark_at_end)
                    }
                },
            )
            when (markAsReadStrategy) {
                MarkAsReadStrategy.PERCENT -> StepperRow(
                    label = stringResource(Res.string.reader_mark_at),
                    value = "$markAsReadThreshold%",
                    onDecrement = { onMarkAsReadThreshold(markAsReadThreshold - 5) },
                    onIncrement = { onMarkAsReadThreshold(markAsReadThreshold + 5) },
                    decrementEnabled = markAsReadThreshold > 50,
                    incrementEnabled = markAsReadThreshold < 100,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp),
                )
                MarkAsReadStrategy.PARAGRAPHS_FROM_END -> StepperRow(
                    label = stringResource(Res.string.reader_within_last),
                    value = pluralStringResource(
                        Res.plurals.reader_paragraph_count,
                        markAsReadParagraphsFromEnd,
                        markAsReadParagraphsFromEnd,
                    ),
                    onDecrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd - 1) },
                    onIncrement = { onMarkAsReadParagraphsFromEnd(markAsReadParagraphsFromEnd + 1) },
                    decrementEnabled = markAsReadParagraphsFromEnd > 0,
                    incrementEnabled = markAsReadParagraphsFromEnd < 20,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp),
                )
                MarkAsReadStrategy.AT_END -> Unit
            }
            }

            SheetSectionLabel(stringResource(Res.string.reader_easter_eggs))
            SheetSwitchRow(
                title = stringResource(Res.string.reader_animate_grimoire),
                hint = stringResource(Res.string.reader_animate_grimoire_hint),
                checked = grimoireEasterEggEnabled,
                onCheckedChange = onGrimoireEasterEggEnabled,
            )
        }
        }
    }
}

@Composable
private fun ReaderPreviewCard(
    colors: ReaderColors,
    textStyle: TextStyle,
    previewText: String,
    dividerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.background,
        border = BorderStroke(1.dp, dividerColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = previewText,
            style = textStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
