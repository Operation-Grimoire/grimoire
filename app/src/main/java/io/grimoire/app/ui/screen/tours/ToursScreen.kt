package io.grimoire.app.ui.screen.tours

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.PlainTooltipIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToursScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToursViewModel = hiltViewModel(),
) {
    val completed by viewModel.completed.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Tours") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(viewModel.tours, key = { it.id }) { tour ->
                val done = tour.id in completed
                ListItem(
                    headlineContent = { Text(tour.title) },
                    supportingContent = { Text(tour.description) },
                    trailingContent = {
                        if (done) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                        }
                    },
                    modifier = Modifier.clickable { viewModel.replay(tour.id) },
                )
            }
        }
    }
}
