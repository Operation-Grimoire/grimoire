package io.grimoire.app.ui.screen.settings.languages

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSourceLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Languages") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ListItem(
                    headlineContent = { Text("App language", color = disabled) },
                    supportingContent = {
                        Text(
                            "English only — more languages coming in a future release",
                            color = disabled,
                        )
                    },
                    trailingContent = { Text("English", color = disabled) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Source language") },
                    supportingContent = {
                        Text("Pick which languages multi-language sources show by default")
                    },
                    trailingContent = {
                        Icon(
                            AppIcons.ArrowForwardIos,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToSourceLanguages),
                )
            }
        }
    }
}
