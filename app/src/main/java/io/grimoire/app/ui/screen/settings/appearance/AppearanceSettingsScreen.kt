package io.grimoire.app.ui.screen.settings.appearance

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.R
import io.grimoire.app.data.preferences.ThemeMode
import io.grimoire.app.ui.component.ColorThemePicker
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.component.ThemeModeSegmentedRow
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.ArrowBack
import io.grimoire.app.ui.screen.settings.SettingsViewModel

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
    val renderSynopsisLinks by viewModel.renderSynopsisLinks.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_appearance_title)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.appearance_theme)) },
                    supportingContent = {
                        ThemeModeSegmentedRow(
                            selected = themeMode,
                            onSelected = viewModel::setThemeMode,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.appearance_dynamic_color)) },
                        supportingContent = { Text(stringResource(R.string.appearance_dynamic_color_summary)) },
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

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.appearance_synopsis_links)) },
                    supportingContent = {
                        Text(stringResource(R.string.appearance_synopsis_links_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = renderSynopsisLinks,
                            onCheckedChange = viewModel::setRenderSynopsisLinks,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setRenderSynopsisLinks(!renderSynopsisLinks)
                    },
                )
            }
        }
    }
}
