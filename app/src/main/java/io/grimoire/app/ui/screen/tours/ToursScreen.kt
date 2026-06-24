package io.grimoire.app.ui.screen.tours

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
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
                                AppIcons.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(AppIcons.PlayArrow, contentDescription = "Start")
                        }
                    },
                    modifier = Modifier.clickable { viewModel.replay(tour.id) },
                )
            }
        }
    }
}
