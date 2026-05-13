package io.grimoire.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToReader: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                SettingsNavItem(
                    icon = Icons.Default.ColorLens,
                    title = "Appearance",
                    subtitle = "Theme, dynamic color",
                    onClick = onNavigateToAppearance,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.LocalLibrary,
                    title = "Library",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToLibrary,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Explore,
                    title = "Browse",
                    subtitle = "Display mode, grid columns",
                    onClick = onNavigateToBrowse,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.MenuBook,
                    title = "Reader",
                    subtitle = "Font size, reading direction",
                    onClick = onNavigateToReader,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version, update channel, licenses",
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}

@Composable
internal fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
