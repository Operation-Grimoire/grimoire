package io.grimoire.app.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.grimoire.app.data.preferences.MarkAsReadStrategy
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.R

internal data class ReaderColors(val background: Color, val foreground: Color)

internal val ReaderColorTheme.readerColors: ReaderColors
    get() = when (this) {
        ReaderColorTheme.LIGHT -> ReaderColors(Color.White, Color(0xFF1A1A1A))
        ReaderColorTheme.SEPIA -> ReaderColors(Color(0xFFFBF0D9), Color(0xFF4A3728))
        ReaderColorTheme.DARK -> ReaderColors(Color(0xFF1E1E2E), Color(0xFFCDD6F4))
        ReaderColorTheme.BLACK -> ReaderColors(Color.Black, Color(0xFFCCCCCC))
    }

internal val ReaderFont.fontFamily: FontFamily
    get() = when (this) {
        ReaderFont.DEFAULT -> FontFamily.Default
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.MONOSPACE -> FontFamily.Monospace
    }

@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ColorThemePicker(
    selected: ReaderColorTheme,
    onSelect: (ReaderColorTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderColorTheme.entries.forEach { theme ->
            val tc = theme.readerColors
            val isSelected = theme == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tc.background)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    )
                    .clickable { onSelect(theme) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        when (theme) {
                            ReaderColorTheme.LIGHT -> R.string.reader_theme_light
                            ReaderColorTheme.SEPIA -> R.string.reader_theme_sepia
                            ReaderColorTheme.DARK -> R.string.reader_theme_dark
                            ReaderColorTheme.BLACK -> R.string.reader_theme_black
                        },
                    ),
                    color = tc.foreground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun FontPicker(
    selected: ReaderFont,
    onSelect: (ReaderFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderFont.entries.forEach { font ->
            val isSelected = font == selected
            val label = when (font) {
                ReaderFont.DEFAULT -> stringResource(R.string.reader_font_sans)
                ReaderFont.SERIF -> stringResource(R.string.reader_font_serif)
                ReaderFont.MONOSPACE -> stringResource(R.string.reader_font_mono)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    .clickable { onSelect(font) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Aa",
                        fontFamily = font.fontFamily,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = label,
                        fontFamily = font.fontFamily,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MarkAsReadStrategyPicker(
    selected: MarkAsReadStrategy,
    onSelect: (MarkAsReadStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarkAsReadStrategy.entries.forEach { value ->
            val label = when (value) {
                MarkAsReadStrategy.PERCENT -> stringResource(R.string.reader_mark_percent)
                MarkAsReadStrategy.PARAGRAPHS_FROM_END -> stringResource(R.string.reader_mark_near_end)
                MarkAsReadStrategy.AT_END -> stringResource(R.string.reader_mark_at_end)
            }
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun OrientationPicker(
    selected: ReaderOrientation,
    onSelect: (ReaderOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderOrientation.entries.forEach { value ->
            val label = when (value) {
                ReaderOrientation.FREE -> stringResource(R.string.reader_orientation_free)
                ReaderOrientation.PORTRAIT -> stringResource(R.string.reader_orientation_vertical)
                ReaderOrientation.LANDSCAPE -> stringResource(R.string.reader_orientation_horizontal)
            }
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlainTooltipIconButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
                tooltip = stringResource(R.string.action_decrease),
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (decrementEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.Center,
            )
            PlainTooltipIconButton(
                onClick = onIncrement,
                enabled = incrementEnabled,
                tooltip = stringResource(R.string.action_increase),
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (incrementEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
