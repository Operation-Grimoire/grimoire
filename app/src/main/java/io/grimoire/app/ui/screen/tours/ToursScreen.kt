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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.R

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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.more_tours)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(viewModel.tours, key = { it.id }) { tour ->
                val done = tour.id in completed
                ListItem(
                    headlineContent = { Text(stringResource(tour.titleRes)) },
                    supportingContent = { Text(stringResource(tour.descriptionRes)) },
                    trailingContent = {
                        if (done) {
                            Icon(
                                AppIcons.CheckCircle,
                                contentDescription = stringResource(R.string.status_completed),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(AppIcons.PlayArrow, contentDescription = stringResource(R.string.tour_start))
                        }
                    },
                    modifier = Modifier.clickable { viewModel.replay(tour.id) },
                )
            }
        }
    }
}
