package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import android.content.Intent
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.grimoire.app.ui.tour.TourKey
import io.grimoire.app.ui.tour.tourTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.SourceListItem
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.util.languageLabel
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigateToManage: () -> Unit,
    onNavigateToSource: (packageName: String) -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToNovelUpdatesSearch: () -> Unit = {},
    onNavigateToNovelUpdatesRankings: () -> Unit = {},
    onNavigateToNovelUpdatesLatest: () -> Unit = {},
    onNavigateToNovelUpdatesBookmarks: () -> Unit = {},
    onOpenSourceSettings: (packageName: String) -> Unit = {},
    onSelectionActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val installed by viewModel.installed.collectAsState()
    val ui by viewModel.sourcesUi.collectAsState()
    val nameFilter by viewModel.nameFilter.collectAsState()
    val languageFilter by viewModel.languageFilter.collectAsState()
    val pinned by viewModel.pinnedPackages.collectAsState()
    val showNovelUpdates by viewModel.showNovelUpdates.collectAsState()
    val context = LocalContext.current

    var selected by remember { mutableStateOf(emptySet<String>()) }
    val selectionMode = selected.isNotEmpty()
    val clearSelection = { selected = emptySet() }
    val toggleSelect: (String) -> Unit = { pkg ->
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    val listState = viewModel.listState
    var showFilterSheet by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState()
    val filterActive = languageFilter != null || nameFilter.isNotBlank()

    // Every visible source package (each source appears once per language group),
    // for the select-all toggle.
    val allVisible = ui.byLanguage.values.flatten().map { it.packageName }.toSet()
    val allSelectedPinned = selected.isNotEmpty() && selected.all { it in pinned }

    BackHandler(enabled = selectionMode) { clearSelection() }
    LaunchedEffect(selectionMode) { onSelectionActiveChange(selectionMode) }

    val onSourceClick: (String) -> Unit = { pkg ->
        if (selectionMode) toggleSelect(pkg) else onNavigateToSource(pkg)
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selected.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        selected = if (allVisible.isNotEmpty() && selected.containsAll(allVisible)) {
                            selected - allVisible
                        } else {
                            selected + allVisible
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_browse)) },
                )
            }
        },
        bottomBar = {
            TooltipBottomBar(visible = selectionMode) {
                TooltipIconButton(
                    icon = AppIcons.Settings,
                    label = stringResource(R.string.browse_source_settings),
                    visible = selected.size == 1,
                    onClick = {
                        selected.firstOrNull()?.let(onOpenSourceSettings)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = AppIcons.PushPin,
                    label = if (allSelectedPinned) {
                        stringResource(R.string.browse_unpin)
                    } else {
                        stringResource(R.string.browse_pin)
                    },
                    onClick = {
                        viewModel.setPinned(selected, !allSelectedPinned)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = AppIcons.Delete,
                    label = stringResource(R.string.browse_uninstall),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showUninstallConfirm = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // Clear the floating toolbar so the last row isn't blocked at the bottom.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (showNovelUpdates) {
                item(key = "__nu__") {
                    NovelUpdatesCard(
                        onSearch = onNavigateToNovelUpdatesSearch,
                        onRankings = onNavigateToNovelUpdatesRankings,
                        onLatest = onNavigateToNovelUpdatesLatest,
                        onBookmarks = onNavigateToNovelUpdatesBookmarks,
                    )
                }
            }

            if (installed.isEmpty()) {
                item(key = "__empty__") {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.browse_no_extensions),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                if (ui.pinned.isNotEmpty()) {
                    item(key = "__pinned_header__") {
                        SectionHeader(stringResource(R.string.browse_pinned))
                    }
                    items(ui.pinned, key = { "pin_${it.packageName}" }) { item ->
                        SourceListItem(
                            name = item.name,
                            lang = item.lang,
                            packageName = item.packageName,
                            iconUrl = item.iconUrl,
                            pinned = true,
                            adult = item.isAdult,
                            selected = item.packageName in selected,
                            onClick = { onSourceClick(item.packageName) },
                            onLongClick = { toggleSelect(item.packageName) },
                        )
                    }
                }

                ui.byLanguage.forEach { (lang, sources) ->
                        item(key = "__lang_$lang") { SectionHeader(languageLabel(lang)) }
                        items(sources, key = { it.packageName }) { item ->
                            SourceListItem(
                                name = item.name,
                                lang = item.lang,
                                packageName = item.packageName,
                                iconUrl = item.iconUrl,
                                pinned = item.packageName in pinned,
                                adult = item.isAdult,
                                selected = item.packageName in selected,
                                onClick = { onSourceClick(item.packageName) },
                                onLongClick = { toggleSelect(item.packageName) },
                            )
                        }
                    }
                }
            }

            if (!selectionMode) {
                BrowseBottomToolbar(
                    onManage = onNavigateToManage,
                    onSearch = onNavigateToGlobalSearch,
                    onFilter = { showFilterSheet = true },
                    filterActive = filterActive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
        }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.browse_filter_sources),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AppSearchField(
                    value = nameFilter,
                    onValueChange = viewModel::setNameFilter,
                    placeholder = stringResource(R.string.browse_filter_sources_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                if (ui.languages.size > 1) {
                    io.grimoire.app.ui.component.sheet.SheetSectionLabel(
                        stringResource(R.string.browse_language),
                    )
                    LanguageDropdown(
                        languages = ui.languages,
                        selected = languageFilter,
                        onSelect = viewModel::setLanguageFilter,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (showUninstallConfirm) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.browse_uninstall_title,
                        count,
                        count,
                    ),
                )
            },
            text = {
                Text(
                    pluralStringResource(R.plurals.browse_uninstall_description, count),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selected.forEach { pkg ->
                        @Suppress("DEPRECATION")
                        context.startActivity(
                            Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                data = Uri.parse("package:$pkg")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                    showUninstallConfirm = false
                    clearSelection()
                }) { Text(stringResource(R.string.browse_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** NovelUpdates entry points as a single compact row of tonal cards. */
@Composable
private fun NovelUpdatesCard(
    onSearch: () -> Unit,
    onRankings: () -> Unit,
    onLatest: () -> Unit,
    onBookmarks: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.browse_novelupdates_title))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NuEntry(
                AppIcons.Search,
                stringResource(R.string.browse_novelupdates_search),
                onSearch,
                Modifier.weight(1f),
            )
            NuEntry(
                AppIcons.TrendingUp,
                stringResource(R.string.browse_novelupdates_rankings),
                onRankings,
                Modifier.weight(1f),
            )
            NuEntry(
                AppIcons.NewReleases,
                stringResource(R.string.browse_novelupdates_latest),
                onLatest,
                Modifier.weight(1f),
            )
            NuEntry(
                AppIcons.Inventory2,
                stringResource(R.string.browse_novelupdates_saved),
                onBookmarks,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NuEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Material3-styled floating toolbar pill anchored bottom-centre on Browse: a
 * Filter button (opens the source filter sheet; tinted when a filter is active)
 * and a Search button (global search). Replaces the old search FAB and the
 * inline filter row.
 */
@Composable
private fun BrowseBottomToolbar(
    onManage: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    filterActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onManage) {
                Icon(
                    AppIcons.ExtensionFilled,
                    contentDescription = stringResource(R.string.browse_manage_extensions),
                    modifier = Modifier.tourTarget(TourKey.ExtensionManager),
                )
            }
            IconButton(onClick = onFilter) {
                Icon(
                    AppIcons.FilterList,
                    contentDescription = stringResource(R.string.browse_filter),
                    tint = if (filterActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    },
                )
            }
            IconButton(onClick = onSearch) {
                Icon(
                    AppIcons.Search,
                    contentDescription = stringResource(R.string.browse_search),
                )
            }
        }
    }
}

/**
 * Single-select language filter for the source list. Replaces the old chip
 * row: the full option set is in one anchored menu instead of scrolling off
 * the sheet edge. null = all languages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let(::languageLabel) ?: stringResource(R.string.filter_all),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(languageLabel(lang)) },
                    onClick = {
                        onSelect(lang)
                        expanded = false
                    },
                )
            }
        }
    }
}
