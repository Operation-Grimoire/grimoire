package io.grimoire.ui.reader

import io.grimoire.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReaderColorThemePicker(
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
                            ReaderColorTheme.LIGHT -> Res.string.reader_theme_light
                            ReaderColorTheme.SEPIA -> Res.string.reader_theme_sepia
                            ReaderColorTheme.DARK -> Res.string.reader_theme_dark
                            ReaderColorTheme.BLACK -> Res.string.reader_theme_black
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
fun FontPicker(
    selected: ReaderFont,
    onSelect: (ReaderFont) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderFont.entries.forEach { font ->
            val isSelected = font == selected
            val label = when (font) {
                ReaderFont.DEFAULT -> stringResource(Res.string.reader_font_sans)
                ReaderFont.SERIF -> stringResource(Res.string.reader_font_serif)
                ReaderFont.MONOSPACE -> stringResource(Res.string.reader_font_mono)
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
