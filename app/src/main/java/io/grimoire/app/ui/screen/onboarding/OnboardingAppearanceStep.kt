package io.grimoire.app.ui.screen.onboarding

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.data.preferences.ThemeMode
import io.grimoire.app.ui.component.ColorThemePicker
import io.grimoire.app.ui.component.ThemeModeSegmentedRow
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.AutoStories
import io.grimoire.app.ui.icon.ColorLens

@Composable
internal fun OnboardingAppearanceStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkPreview = themeMode == ThemeMode.DARK ||
        (themeMode == ThemeMode.SYSTEM && systemDark)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        OnboardingStepHeader(
            icon = AppIcons.ColorLens,
            title = stringResource(R.string.onboarding_appearance_title),
            subtitle = stringResource(R.string.onboarding_appearance_subtitle),
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        AppearancePreviewCard(
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.appearance_theme),
                style = MaterialTheme.typography.titleMedium,
            )
            ThemeModeSegmentedRow(
                selected = themeMode,
                onSelected = viewModel::setThemeMode,
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.setDynamicColor(!useDynamicColor) },
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(stringResource(R.string.appearance_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.appearance_dynamic_color_summary)) },
                    trailingContent = {
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }
        }

        ColorThemePicker(
            selected = colorTheme,
            enabled = !useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
            darkPreview = darkPreview,
            onSelected = viewModel::setColorTheme,
            headerPadding = PaddingValues(horizontal = 24.dp),
            rowContentPadding = PaddingValues(horizontal = 24.dp),
        )
    }
}

@Composable
private fun AppearancePreviewCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = AppIcons.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_appearance_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.onboarding_appearance_preview_meta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 0.65f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_appearance_preview_badge),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
