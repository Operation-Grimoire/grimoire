package io.grimoire.app.ui.screen.settings.appearance

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.preferences.ColorTheme
import io.grimoire.app.data.preferences.ThemeMode
import io.grimoire.app.ui.screen.settings.SettingsViewModel
import io.grimoire.app.ui.theme.ForestDark
import io.grimoire.app.ui.theme.ForestLight
import io.grimoire.app.ui.theme.GrimoireDark
import io.grimoire.app.ui.theme.GrimoireLight
import io.grimoire.app.ui.theme.MidnightDark
import io.grimoire.app.ui.theme.MidnightLight
import io.grimoire.app.ui.theme.OceanDark
import io.grimoire.app.ui.theme.OceanLight
import io.grimoire.app.ui.theme.RoseDark
import io.grimoire.app.ui.theme.RoseLight
import io.grimoire.app.ui.theme.SunsetDark
import io.grimoire.app.ui.theme.SunsetLight
import io.grimoire.app.ui.theme.DefaultDark
import io.grimoire.app.ui.theme.DefaultLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Appearance") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                    label = { Text(mode.displayName) },
                                )
                            }
                        }
                    },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text("Dynamic Color") },
                        supportingContent = { Text("Adapt colors to your wallpaper") },
                        trailingContent = {
                            Switch(
                                checked = useDynamicColor,
                                onCheckedChange = viewModel::setDynamicColor,
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setDynamicColor(!useDynamicColor) },
                    )
                }
            }

            item {
                val systemDark = isSystemInDarkTheme()
                val darkPreview = themeMode == ThemeMode.DARK ||
                    (themeMode == ThemeMode.SYSTEM && systemDark)
                ColorThemePicker(
                    selected = colorTheme,
                    enabled = !useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                    darkPreview = darkPreview,
                    onSelected = viewModel::setColorTheme,
                )
            }
        }
    }
}

@Composable
private fun ColorThemePicker(
    selected: ColorTheme,
    enabled: Boolean,
    darkPreview: Boolean,
    onSelected: (ColorTheme) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Color palette",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val supporting = if (enabled) {
            "Pick the accent palette used across the app."
        } else {
            "Turn off Dynamic Color to choose a custom palette."
        }
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ColorTheme.entries) { theme ->
                val (primary, secondary, tertiary) = previewSwatches(theme, darkPreview)
                ColorThemeChip(
                    name = theme.displayName,
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

private fun previewSwatches(theme: ColorTheme, dark: Boolean): Triple<Color, Color, Color> {
    val scheme = when (theme) {
        ColorTheme.DEFAULT -> if (dark) DefaultDark else DefaultLight
        ColorTheme.GRIMOIRE -> if (dark) GrimoireDark else GrimoireLight
        ColorTheme.OCEAN -> if (dark) OceanDark else OceanLight
        ColorTheme.SUNSET -> if (dark) SunsetDark else SunsetLight
        ColorTheme.FOREST -> if (dark) ForestDark else ForestLight
        ColorTheme.ROSE -> if (dark) RoseDark else RoseLight
        ColorTheme.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
    }
    return Triple(scheme.primary, scheme.secondary, scheme.tertiary)
}

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
            .clickable(enabled = isEnabled, onClick = onClick)
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
                    imageVector = Icons.Filled.Check,
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

internal val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
