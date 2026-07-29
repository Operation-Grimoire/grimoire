package io.grimoire.app.ui.screen.novelupdates

import io.grimoire.app.ui.icon.*
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.novelupdates.NuGenres
import io.grimoire.app.data.novelupdates.NuLanguages
import io.grimoire.app.data.novelupdates.NuListingFilter
import io.grimoire.app.data.novelupdates.NuRankingType
import io.grimoire.app.ui.component.dialog.FullScreenDialog
import io.grimoire.app.ui.component.sheet.MultiSelectSummaryRow
import io.grimoire.app.ui.component.sheet.SearchableMultiSelectDialog
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUpdatesBrowserScreen(
    onNavigateBack: () -> Unit,
    onSeriesClick: (slug: String) -> Unit,
    onOpenWebView: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelUpdatesBrowserViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val rankingType by viewModel.rankingType.collectAsState()
    val filter by viewModel.filter.collectAsState()

    var showFilters by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            title = {
                Text(stringResource(if (mode == NuBrowseMode.RANKINGS) R.string.nu_rankings else R.string.nu_latest))
            },
            actions = {
                PlainTooltipIconButton(onClick = { onOpenWebView(viewModel.currentPageUrl()) }, tooltip = stringResource(R.string.action_open_in_webview)) {
                    Icon(AppIcons.Language, contentDescription = stringResource(R.string.action_open_in_webview))
                }
                PlainTooltipIconButton(onClick = { showFilters = true }, tooltip = stringResource(R.string.source_browse_filters)) {
                    Icon(AppIcons.FilterList, contentDescription = stringResource(R.string.source_browse_filters))
                }
            },
        )

        if (mode == NuBrowseMode.RANKINGS) {
            RankingTypeDropdown(
                selected = rankingType,
                onSelect = viewModel::setRankingType,
            )
        }

        NuResultsArea(
            results = results,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            error = error,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onSeriesClick = onSeriesClick,
            modifier = Modifier.weight(1f),
        )
    }

    if (showFilters) {
        ListingFilterDialog(
            current = filter,
            onDismiss = { showFilters = false },
            onApply = {
                showFilters = false
                viewModel.applyFilter(it)
            },
        )
    }
}

/** Exclusive ranking window — 7 options, so a dropdown instead of a chip row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingTypeDropdown(
    selected: NuRankingType,
    onSelect: (NuRankingType) -> Unit,
) {
    @Composable
    fun label(type: NuRankingType) = stringResource(
        when (type) {
            NuRankingType.POPULAR_MONTH -> R.string.nu_ranking_popular_month
            NuRankingType.POPULAR_ALL -> R.string.nu_ranking_popular_all
            NuRankingType.ACTIVITY_WEEK -> R.string.nu_ranking_activity_week
            NuRankingType.ACTIVITY_MONTH -> R.string.nu_ranking_activity_month
            NuRankingType.ACTIVITY_ALL -> R.string.nu_ranking_activity_all
        },
    )

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NuRankingType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(label(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Full-screen listing-filter form. Edits stay local until the top-bar Apply
 * commits them; X/back is an explicit cancel.
 */
@Composable
private fun ListingFilterDialog(
    current: NuListingFilter,
    onDismiss: () -> Unit,
    onApply: (NuListingFilter) -> Unit,
) {
    val genres = remember {
        current.genres.mapNotNull { id -> NuGenres.all.entries.firstOrNull { it.value == id }?.key }
            .toMutableStateList()
    }
    val languages = remember {
        current.languages.mapNotNull { id -> NuLanguages.all.entries.firstOrNull { it.value == id }?.key }
            .toMutableStateList()
    }
    var matchAll by remember { mutableStateOf(current.genresMatchAll) }
    var showGenrePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val genreLabels = NuGenres.all.keys.associateWith { localizedNuGenre(it) }
    val languageLabels = NuLanguages.all.keys.associateWith { localizedNuLanguage(it) }

    FullScreenDialog(
        title = stringResource(R.string.source_browse_filters),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_apply),
        onConfirm = {
            onApply(
                NuListingFilter(
                    languages = languages.mapNotNull { NuLanguages.all[it] },
                    genres = genres.mapNotNull { NuGenres.all[it] },
                    genresMatchAll = matchAll,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            MultiSelectSummaryRow(
                title = stringResource(R.string.nu_genres),
                selectedCount = genres.size,
                onClick = { showGenrePicker = true },
            )
            MatchModeSegmented(matchAll = matchAll, onChange = { matchAll = it })

            MultiSelectSummaryRow(
                title = stringResource(R.string.nu_language),
                selectedCount = languages.size,
                onClick = { showLanguagePicker = true },
            )
        }
    }

    if (showGenrePicker) {
        SearchableMultiSelectDialog(
            title = stringResource(R.string.nu_genres),
            options = NuGenres.all.keys.toList(),
            optionLabel = { genreLabels[it].orEmpty() },
            isChecked = { it in genres },
            onToggle = { name -> if (!genres.remove(name)) genres.add(name) },
            onClear = { genres.clear() },
            clearEnabled = genres.isNotEmpty(),
            onDismiss = { showGenrePicker = false },
        )
    }
    if (showLanguagePicker) {
        SearchableMultiSelectDialog(
            title = stringResource(R.string.nu_language),
            options = NuLanguages.all.keys.toList(),
            optionLabel = { languageLabels[it].orEmpty() },
            isChecked = { it in languages },
            onToggle = { name -> if (!languages.remove(name)) languages.add(name) },
            onClear = { languages.clear() },
            clearEnabled = languages.isNotEmpty(),
            onDismiss = { showLanguagePicker = false },
        )
    }
}
