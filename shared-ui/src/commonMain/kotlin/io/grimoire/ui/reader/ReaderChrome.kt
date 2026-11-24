package io.grimoire.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.component.TooltipBottomBar
import io.grimoire.ui.component.TooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.ArrowBack
import io.grimoire.ui.icon.NavigateBefore
import io.grimoire.ui.icon.NavigateNext
import io.grimoire.ui.icon.Settings
import io.grimoire.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Reader top bar: back, chapter title, platform actions and an optional progress strip.
 * Slides in and out with [visible].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    visible: Boolean,
    title: String,
    colors: ReaderColors,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    progressText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background.copy(alpha = 0.95f),
                    titleContentColor = colors.foreground,
                    navigationIconContentColor = colors.foreground,
                    actionIconContentColor = colors.foreground,
                ),
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onBack, tooltip = stringResource(Res.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = actions,
            )
            // Thin progress strip directly under the top bar; hides with it.
            if (progressText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.foreground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** "Chapter 40% · Book 12%" for the enabled parts, or null when both are hidden. */
@Composable
fun readerProgressText(chapterPercent: Int?, bookPercent: Int?): String? {
    val parts = buildList {
        // Compose resources keep the Android "%%" escape literally.
        if (chapterPercent != null) add(stringResource(Res.string.reader_chapter_progress, chapterPercent.coerceIn(0, 100)).replace("%%", "%"))
        if (bookPercent != null) add(stringResource(Res.string.reader_book_progress, bookPercent.coerceIn(0, 100)).replace("%%", "%"))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Reader bottom bar: previous, platform read-aloud controls, settings and next.
 * Slides in and out with [visible].
 */
@Composable
fun ReaderBottomBar(
    visible: Boolean,
    colors: ReaderColors,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onSettings: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    settingsModifier: Modifier = Modifier,
    nextModifier: Modifier = Modifier,
    readAloud: @Composable RowScope.() -> Unit = {},
) {
    TooltipBottomBar(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        containerColor = colors.background.copy(alpha = 0.95f),
        contentColor = colors.foreground,
    ) {
        TooltipIconButton(
            icon = AppIcons.NavigateBefore,
            label = stringResource(Res.string.reader_previous),
            onClick = onPrevious,
            enabled = hasPrevious,
        )
        readAloud()
        TooltipIconButton(
            icon = AppIcons.Settings,
            label = stringResource(Res.string.reader_settings),
            onClick = onSettings,
            modifier = settingsModifier,
        )
        TooltipIconButton(
            icon = AppIcons.NavigateNext,
            label = stringResource(Res.string.reader_next),
            onClick = onNext,
            modifier = nextModifier,
            enabled = hasNext,
        )
    }
}

/** Chapter heading at the top of the reader list, styled like the body text. */
@Composable
fun ReaderChapterTitle(title: String, colors: ReaderColors, fontFamily: FontFamily, layout: ReaderTextLayout) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = fontFamily,
            color = colors.foreground,
            textAlign = layout.textAlign,
            textDirection = layout.textDirection,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    )
}

/** A scene break between paragraphs. */
@Composable
fun ReaderSeparator(colors: ReaderColors, paragraphSpacing: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = (paragraphSpacing * 2).dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.4f), color = colors.foreground.copy(alpha = 0.4f))
    }
}

/** End of the chapter: a next-chapter link, or an end-of-book note on the last chapter. */
@Composable
fun ReaderChapterEnd(hasNext: Boolean, colors: ReaderColors, onNext: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (hasNext) {
            TextButton(onClick = onNext) {
                Text(stringResource(Res.string.reader_next_chapter), color = colors.foreground.copy(alpha = 0.7f))
            }
        } else {
            Text(
                stringResource(Res.string.reader_end_of_book),
                style = MaterialTheme.typography.bodySmall,
                color = colors.foreground.copy(alpha = 0.4f),
            )
        }
    }
}

/** A position-restore notice above the text, with an optional action such as retry. */
@Composable
fun ReaderPositionNotice(
    text: String,
    colors: ReaderColors,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.foreground, modifier = Modifier.weight(1f))
        if (actionLabel != null) TextButton(enabled = actionEnabled, onClick = onAction) { Text(actionLabel) }
    }
}

/** Body text style for the reader's paragraphs. */
fun readerTextStyle(fontSize: Int, lineHeightTimes10: Int, font: ReaderFont, colors: ReaderColors, layout: ReaderTextLayout): TextStyle =
    TextStyle(
        fontSize = androidx.compose.ui.unit.TextUnit(fontSize.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(fontSize * lineHeightTimes10 / 10f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontFamily = font.fontFamily,
        color = colors.foreground,
        textAlign = layout.textAlign,
        textDirection = layout.textDirection,
    )
