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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.ui.component.AppSearchField
import io.grimoire.app.ui.component.LanguageFilterChips
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.SourceListItem
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.util.languageLabel

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
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex < 1 } }

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
                    title = { Text("Browse") },
                    actions = {
                        PlainTooltipIconButton(onClick = onNavigateToManage, tooltip = "Manage extensions") {
                            Icon(
                                AppIcons.Extension,
                                contentDescription = "Manage extensions",
                                modifier = Modifier.tourTarget(TourKey.ExtensionManager),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToGlobalSearch,
                    icon = { Icon(AppIcons.Search, contentDescription = null) },
                    text = { Text("Search") },
                    expanded = fabExpanded,
                )
            }
        },
        bottomBar = {
            TooltipBottomBar(visible = selectionMode) {
                TooltipIconButton(
                    icon = AppIcons.Settings,
                    label = "Settings",
                    visible = selected.size == 1,
                    onClick = {
                        selected.firstOrNull()?.let(onOpenSourceSettings)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = AppIcons.PushPin,
                    label = if (allSelectedPinned) "Unpin" else "Pin",
                    onClick = {
                        viewModel.setPinned(selected, !allSelectedPinned)
                        clearSelection()
                    },
                )
                TooltipIconButton(
                    icon = AppIcons.Delete,
                    label = "Uninstall",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showUninstallConfirm = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState) {
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

            if (installed.isNotEmpty()) {
                item(key = "__filter__") {
                    AppSearchField(
                        value = nameFilter,
                        onValueChange = viewModel::setNameFilter,
                        placeholder = "Filter sources…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            }

            if (ui.languages.size > 1) {
                item(key = "__lang_chips__") {
                    LanguageFilterChips(
                        languages = ui.languages,
                        selected = languageFilter,
                        onSelect = viewModel::setLanguageFilter,
                        modifier = Modifier.padding(vertical = 4.dp),
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
                            "No extensions installed\nTap the extension icon to add one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                if (ui.pinned.isNotEmpty()) {
                    item(key = "__pinned_header__") { SectionHeader("Pinned") }
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
        }

    if (showUninstallConfirm) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text(if (count == 1) "Uninstall extension?" else "Uninstall $count extensions?") },
            text = {
                Text(
                    if (count == 1) "Android will ask you to confirm the removal."
                    else "Android will ask you to confirm each removal in turn."
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
                }) { Text("Uninstall") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) { Text("Cancel") }
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
        SectionHeader("NovelUpdates")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NuEntry(AppIcons.Search, "Search", onSearch, Modifier.weight(1f))
            NuEntry(AppIcons.TrendingUp, "Rankings", onRankings, Modifier.weight(1f))
            NuEntry(AppIcons.NewReleases, "Latest", onLatest, Modifier.weight(1f))
            NuEntry(AppIcons.Inventory2, "Saved", onBookmarks, Modifier.weight(1f))
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
