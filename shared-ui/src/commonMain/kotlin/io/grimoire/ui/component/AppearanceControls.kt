package io.grimoire.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.grimoire.ui.resources.*
import io.grimoire.ui.theme.ColorTheme
import io.grimoire.ui.theme.scheme
import org.jetbrains.compose.resources.stringResource
import io.grimoire.ui.theme.ThemeMode
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Check

/** Segmented button row for choosing between System, Light, and Dark theme modes. */
@Composable
fun ThemeModeSegmentedRow(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                label = { Text(mode.localizedDisplayName()) },
            )
        }
    }
}

/** Horizontal swatch row for picking one of the built-in [ColorTheme] palettes. */
@Composable
fun ColorThemePicker(
    selected: ColorTheme,
    enabled: Boolean,
    darkPreview: Boolean,
    onSelected: (ColorTheme) -> Unit,
    modifier: Modifier = Modifier,
    headerPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    rowContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Column(Modifier.padding(headerPadding)) {
            Text(
                text = stringResource(Res.string.appearance_color_palette),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val supporting = if (enabled) {
                stringResource(Res.string.appearance_color_palette_summary)
            } else {
                stringResource(Res.string.appearance_color_palette_disabled)
            }
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .selectableGroup(),
            contentPadding = rowContentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ColorTheme.entries) { theme ->
                val (primary, secondary, tertiary) = previewSwatches(theme, darkPreview)
                ColorThemeChip(
                    name = theme.localizedDisplayName(),
                    primary = primary,
                    secondary = secondary,
                    tertiary = tertiary,
                    isSelected = selected == theme,
                    isEnabled = enabled,
                    onClick = { if (enabled) onSelected(theme) },
                )
            }
        }
    }
}

private fun previewSwatches(theme: ColorTheme, dark: Boolean): Triple<Color, Color, Color> =
    theme.scheme(dark).let { Triple(it.primary, it.secondary, it.tertiary) }

@Composable
private fun ColorThemeChip(
    name: String,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (isEnabled) 1f else 0.4f
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-10).dp),
            ) {
                SwatchDot(primary, alpha)
                SwatchDot(secondary, alpha)
                SwatchDot(tertiary, alpha)
            }
            if (isSelected) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SwatchDot(color: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = color.alpha * alpha))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha), CircleShape),
    )
}

@Composable
fun ThemeMode.localizedDisplayName(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> Res.string.appearance_theme_system
        ThemeMode.LIGHT -> Res.string.appearance_theme_light
        ThemeMode.DARK -> Res.string.appearance_theme_dark
    },
)

@Composable
fun ColorTheme.localizedDisplayName(): String = stringResource(
    when (this) {
        ColorTheme.DEFAULT -> Res.string.color_theme_default
        ColorTheme.GRIMOIRE -> Res.string.color_theme_grimoire
        ColorTheme.OCEAN -> Res.string.color_theme_ocean
        ColorTheme.SUNSET -> Res.string.color_theme_sunset
        ColorTheme.FOREST -> Res.string.color_theme_forest
        ColorTheme.ROSE -> Res.string.color_theme_rose
        ColorTheme.MIDNIGHT -> Res.string.color_theme_midnight
    },
)
