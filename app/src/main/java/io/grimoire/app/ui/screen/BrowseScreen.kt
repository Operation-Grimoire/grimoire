package io.grimoire.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.extension.repo.ExtensionItem

@Composable
fun BrowseScreen(
    onNavigateToManage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val installed by viewModel.installed.collectAsState()

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Browse",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNavigateToManage) {
                Icon(Icons.Default.Extension, contentDescription = "Manage extensions")
            }
        }

        if (installed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No extensions installed\nTap the extension icon to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(installed, key = { it.packageName }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text(item.lang.uppercase()) },
                        leadingContent = { ExtensionIcon(item.packageName, item.lang) },
                        modifier = Modifier.clickable { /* TODO: navigate to source */ },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

