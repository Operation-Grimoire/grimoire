package io.grimoire.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.grimoire.api.network.SourceNetwork
import io.grimoire.ui.component.ColorThemePicker
import io.grimoire.ui.component.ThemeModeSegmentedRow
import io.grimoire.ui.icon.*
import io.grimoire.ui.library.ChapterCounts
import io.grimoire.ui.library.NovelCoverCard
import io.grimoire.ui.theme.GrimoireMaterialTheme
import io.grimoire.ui.theme.isDark
import io.grimoire.ui.theme.scheme
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    require(args.size % 2 == 0) { "Use --extension MANIFEST --data DIRECTORY" }
    val options = args.toList().chunked(2).associate { it[0] to it[1] }
    require(options.keys.all { it == "--extension" || it == "--data" }) { "Unknown desktop argument" }
    val data = options["--data"]?.let(Paths::get) ?: Paths.get(
        System.getenv("APPDATA") ?: System.getProperty("user.home"), "GrimoireDesktop")
    val hostApi = HostRuntime.apiVersion
    val networkEnabled = AtomicBoolean(true)
    SourceNetwork.install(DesktopHttpPlatform(networkEnabled))
    installCoverLoader(data, networkEnabled)
    val library = LocalLibrary(data)
    val extensions = ExtensionManager(library, hostApi, HttpArtifactFetcher(networkEnabled), listOf(OFFICIAL_REPOSITORY))
    val model = desktopReaderModel(library, hostApi, networkEnabled, extensions)
    options["--extension"]?.let { model.load(Paths.get(it)) }
    application {
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        var closeError by remember { mutableStateOf<String?>(null) }
        val windowState = rememberWindowState(width = 1220.dp, height = 820.dp)
        val requestClose: () -> Unit = {
            if (!closing) {
                closing = true
                closeError = null
                scope.launch {
                    try { model.close(); exitApplication() }
                    catch (failure: Exception) { closeError = "Could not save the library: ${failure.message}"; closing = false }
                }
            }
        }
        Window(
            title = "Grimoire",
            state = windowState,
            undecorated = true,
            onKeyEvent = { event ->
                val current = model.state.value
                val ready = event.type == KeyEventType.KeyUp && !current.busy && !closing
                when {
                    ready && event.key == Key.Escape && current.view.isBookView -> { model.back(); true }
                    // Arrow keys turn chapters, like the reader's previous and next buttons.
                    ready && event.key == Key.DirectionLeft && current.view == View.READER -> { model.readPrevious(); true }
                    ready && event.key == Key.DirectionRight && current.view == View.READER -> { model.readNext(); true }
                    else -> false
                }
            },
            onCloseRequest = requestClose,
        ) {
            SideEffect { window.minimumSize = java.awt.Dimension(900, 600) }
            val maximized = windowState.placement == WindowPlacement.Maximized
            val chrome = remember(maximized) {
                WindowChrome(
                    dragArea = { modifier, content -> WindowDraggableArea(modifier) { content() } },
                    controls = {
                        DesktopWindowControls(
                            maximized = maximized,
                            onMinimize = { windowState.isMinimized = true },
                            onToggleMaximize = {
                                windowState.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                            },
                            onClose = requestClose,
                        )
                    },
                )
            }
            CompositionLocalProvider(LocalWindowChrome provides chrome) {
                DesktopReader(model, closing, closeError) {
                    chooseManifest(window)?.let(model::load)
                }
            }
        }
    }
}

private fun chooseManifest(parent: Frame): Path? = FileDialog(parent, "Open local extension manifest", FileDialog.LOAD).run {
    try {
        isVisible = true
        file?.let { Paths.get(directory, it) }
    } finally { dispose() }
}

@Composable
fun DesktopReader(model: ReaderModel, closing: Boolean, closeError: String?, openExtension: () -> Unit) {
    val state by model.state.collectAsState()
    val enabled = !state.busy && !closing
    val section = if (state.view.isBookView) state.bookOrigin else state.view
    val appearance = state.appearance
    val slideDistancePx = with(LocalDensity.current) { 30.dp.roundToPx() }
    val inReader = state.view == View.READER && state.projection != null
    val readerTransition = updateTransition(targetState = inReader, label = "readerTransition")
    var lastReaderState by remember { mutableStateOf<ReaderState?>(null) }
    if (inReader) lastReaderState = state
    LaunchedEffect(readerTransition.currentState, readerTransition.targetState) {
        if (!readerTransition.currentState && !readerTransition.targetState) lastReaderState = null
    }
    GrimoireMaterialTheme(appearance.colorTheme.scheme(appearance.themeMode.isDark())) {
    CompositionLocalProvider(LocalCoversOnline provides !state.offline) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().let { base ->
            if (LocalWindowChrome.current.controls != null) {
                base.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            } else base
        },
    ) {
    // Smoothly transition between the navigation shell and the full-window reader.
    readerTransition.AnimatedContent(
        transitionSpec = { sharedAxisX(forward = targetState, slideDistancePx = slideDistancePx) },
    ) { showingReader ->
        if (showingReader) {
            val readerState = if (inReader) state else lastReaderState
            if (readerState != null) {
                ReaderScreen(readerState.copy(error = closeError ?: readerState.error), enabled && inReader, state.busy || closing, model)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopNavigation(section, state, enabled, model)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = state.view,
                        transitionSpec = { desktopNavTransition(initialState, targetState, slideDistancePx) },
                        label = "desktopView",
                    ) { view ->
                        when (view) {
                            View.LIBRARY -> LibraryPane(state, enabled, model, Modifier.fillMaxSize())
                            View.HISTORY -> HistoryPane(state, enabled, model, Modifier.fillMaxSize())
                            View.BROWSE -> BrowsePane(state, enabled, model, Modifier.fillMaxSize())
                            View.EXTENSIONS -> ExtensionsPane(state, enabled, model, openExtension, Modifier.fillMaxSize())
                            View.SETTINGS -> SettingsPane(state, enabled, model, Modifier.fillMaxSize())
                            View.BOOK, View.READER -> BookScreen(state, enabled, model, Modifier.fillMaxSize())
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.busy || closing,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    val error = closeError ?: state.error
                    var dismissedError by remember(error) { mutableStateOf(false) }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = error != null && !dismissedError,
                        enter = slideInVertically(tween(220, easing = LinearOutSlowInEasing)) { it / 2 } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(150, easing = FastOutLinearInEasing)) { it / 2 } + fadeOut(tween(150)),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp),
                    ) {
                        if (error != null) {
                            DesktopErrorBanner(
                                message = error,
                                onDismiss = { dismissedError = true },
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }
    }
}

@Composable
private fun SettingsPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier) {
    val appearance = state.appearance
    val scroll = rememberLazyListState()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = scroll,
            contentPadding = PaddingValues(top = DESKTOP_HEADER_HEIGHT + 8.dp, bottom = 24.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxHeight().align(Alignment.TopCenter),
        ) {
            item {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = {
                        ThemeModeSegmentedRow(
                            appearance.themeMode,
                            { if (enabled) model.setThemeMode(it) },
                            Modifier.padding(top = 8.dp).widthIn(max = 420.dp),
                        )
                    },
                )
            }
            item {
                ColorThemePicker(
                    appearance.colorTheme,
                    enabled,
                    appearance.themeMode.isDark(),
                    model::setColorTheme,
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) }
            item {
                Text(
                    "Network",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(AppIcons.FileDownloadOff, contentDescription = null) },
                    headlineContent = { Text("Offline mode") },
                    supportingContent = { Text("Disable source requests. Saved chapters stay available.") },
                    trailingContent = {
                        Switch(checked = state.offline, onCheckedChange = null, enabled = enabled)
                    },
                    modifier = Modifier.toggleable(
                        value = state.offline,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = model::offline,
                    ),
                )
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(scroll),
            Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(top = DESKTOP_HEADER_HEIGHT),
        )
        DesktopHeaderBar(title = { Text("Settings") }, modifier = Modifier.align(Alignment.TopCenter))
    }
}

private val View.isBookView: Boolean get() = this == View.BOOK || this == View.READER

@Composable
private fun DesktopNavigation(section: View, state: ReaderState, enabled: Boolean, model: ReaderModel) {
    val chrome = LocalWindowChrome.current
    val installedCount = remember(state.extensionRows) {
        state.extensionRows.count { it.status == ExtensionStatus.INSTALLED || it.status == ExtensionStatus.UPDATE }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.width(232.dp).fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            chrome.dragArea(Modifier.fillMaxWidth().height(DESKTOP_HEADER_HEIGHT)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "Grimoire",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SidebarItem("Library", AppIcons.LocalLibrary, AppIcons.LocalLibraryFilled, section == View.LIBRARY, enabled, state.library.size, model::showLibrary)
                SidebarItem("Browse", AppIcons.Explore, AppIcons.ExploreFilled, section == View.BROWSE, enabled, 0, model::showBrowse)
                SidebarItem("History", AppIcons.History, AppIcons.History, section == View.HISTORY, enabled, state.history.size, model::showHistory)
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = state.offline,
                enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(150, easing = FastOutLinearInEasing)) + fadeOut(tween(120)),
            ) {
                Surface(
                    onClick = { if (enabled) model.offline(false) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Icon(AppIcons.FileDownloadOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Offline", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("Go online", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SidebarItem("Extensions", AppIcons.Extension, AppIcons.ExtensionFilled, section == View.EXTENSIONS, enabled, installedCount, model::showExtensions)
                SidebarItem("Settings", AppIcons.Settings, AppIcons.Settings, section == View.SETTINGS, enabled, 0, model::showSettings)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    count: Int,
    navigate: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            hovered && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "sidebarItemBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            active -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(120),
        label = "sidebarItemFg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .hoverable(hoverSource)
            .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = navigate)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            if (active) selectedIcon else icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else content,
            modifier = Modifier.size(19.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BrowsePane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier) {
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(top = DESKTOP_HEADER_HEIGHT)) {
            when {
                state.sourceName == null -> DesktopEmptyState(
                    icon = AppIcons.Extension,
                    title = "No sources installed",
                    subtitle = "Install an extension to browse its catalog.",
                    actionLabel = "Manage extensions",
                    onAction = model::showExtensions,
                    actionEnabled = enabled,
                )
                state.offline -> DesktopEmptyState(
                    icon = AppIcons.FileDownloadOff,
                    title = "Offline mode",
                    subtitle = "Go online to browse this source.",
                    actionLabel = "Go online",
                    onAction = { model.offline(false) },
                    actionEnabled = enabled,
                )
                state.browseModes.isEmpty() -> DesktopEmptyState(
                    icon = AppIcons.Explore,
                    title = "No catalog available",
                    subtitle = "${state.sourceName} has no catalog to browse.",
                )
                else -> {
                    BrowseControls(state, enabled, model)
                    if (state.browseMode != null) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(COVER_WIDTH),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(state.novels, key = { it.url }) { novel ->
                                    NovelCoverCard(
                                        title = novel.title,
                                        cover = { BookCover(novel.thumbnailUrl, novel.title, it) },
                                        stats = null, includeLockedInTotals = false, showReadBadge = false, showDownloadedBadge = false,
                                        showLockedBadge = false, showRatingBadge = false, showEpubBadge = false, isEpub = false,
                                        notify = false, autoDownload = false, userRating = null, selected = false,
                                        onClick = { if (enabled) model.details(novel) }, onLongClick = {},
                                        modifier = Modifier.animateItem().desktopCardHover(),
                                    )
                                }
                                item(span = full) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    ) {
                                        BrowseFooter(state, enabled, model)
                                    }
                                }
                            }
                            VerticalScrollbar(rememberScrollbarAdapter(gridState), Modifier.fillMaxHeight().align(Alignment.CenterEnd))
                        }
                    }
                }
            }
        }
        DesktopHeaderBar(
            title = { Text(if (state.sources.isEmpty()) "Browse sources" else "Browse") },
            actions = {
                if (state.sources.isNotEmpty()) {
                    SourcePicker(state, enabled, model)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun BrowseControls(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        if (state.browseModes.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.browseModes.forEach { mode ->
                    val icon = when (mode) {
                        BrowseMode.POPULAR -> AppIcons.Explosion
                        BrowseMode.LATEST -> AppIcons.AvgTime
                        BrowseMode.SEARCH -> AppIcons.Search
                    }
                    FilterChip(
                        selected = mode == state.browseMode,
                        onClick = { if (mode != state.browseMode) model.setBrowseMode(mode) },
                        enabled = enabled,
                        label = { Text(mode.label) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = state.browseMode == BrowseMode.SEARCH,
            enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(150, easing = FastOutLinearInEasing)) + fadeOut(tween(120)),
        ) {
            // Keyed by source so a new source starts with its own empty field.
            var query by remember(state.browseSource) { mutableStateOf(state.browseQuery) }
            val submit = { if (query.isNotBlank()) model.search(query) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesktopSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search ${state.sourceName} by title",
                    enabled = enabled,
                    onSearch = submit,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = submit, enabled = enabled && query.isNotBlank()) { Text("Search") }
            }
        }
    }
}

@Composable
private fun BrowseFooter(state: ReaderState, enabled: Boolean, model: ReaderModel) {
    when {
        state.browseHasMore -> FilledTonalButton(onClick = model::loadMore, enabled = enabled) { Text("Load more") }
        state.browsePage == 0 && state.browseMode != BrowseMode.SEARCH && enabled ->
            FilledTonalButton(onClick = model::browse) { Text("Load ${state.browseMode?.label?.lowercase()} titles") }
        state.browsePage > 0 && state.novels.isEmpty() -> Text(
            if (state.browseMode == BrowseMode.SEARCH) "No results for “${state.browseQuery}”."
            else "${state.sourceName} returned no titles.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.browsePage > 0 -> Text(
            "End of results",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryPane(state: ReaderState, enabled: Boolean, model: ReaderModel, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val books = remember(state.library, query) { filterLibrary(state.library, query) }
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.library.isEmpty() -> DesktopEmptyState(
                icon = AppIcons.LocalLibrary,
                title = "Your library is empty",
                subtitle = "Books you add from Browse appear here.",
                actionLabel = "Browse sources",
                onAction = model::showBrowse,
                actionEnabled = enabled,
            )
            books.isEmpty() -> DesktopEmptyState(
                icon = AppIcons.SearchOff,
                title = "No matching books",
                subtitle = "No books match \u201c${query.trim()}\u201d.",
                actionLabel = "Clear search",
                onAction = { query = "" },
                actionEnabled = enabled,
            )
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(COVER_WIDTH),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = DESKTOP_HEADER_HEIGHT + 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(books, key = { it.key.toString() }) { book ->
                        NovelCoverCard(
                            title = book.novel.title,
                            cover = { BookCover(book.novel.thumbnailUrl, book.novel.title, it) },
                            stats = book.counts(), includeLockedInTotals = false, showReadBadge = false, showDownloadedBadge = true,
                            showLockedBadge = false, showRatingBadge = false, showEpubBadge = false, isEpub = false,
                            notify = false, autoDownload = false, userRating = null, selected = false,
                            onClick = { if (enabled) model.openSaved(book) }, onLongClick = {},
                            modifier = Modifier.animateItem().desktopCardHover(),
                        )
                    }
                }
                VerticalScrollbar(
                    rememberScrollbarAdapter(gridState),
                    Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(top = DESKTOP_HEADER_HEIGHT),
                )
            }
        }
        DesktopHeaderBar(
            title = { Text("Library") },
            actions = {
                if (state.library.isNotEmpty()) {
                    DesktopSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search library",
                        enabled = enabled,
                        modifier = Modifier.width(260.dp),
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private val COVER_WIDTH = 140.dp

/** Saved books whose title or author contains [query], ignoring case. */
internal fun filterLibrary(books: List<SavedBook>, query: String): List<SavedBook> {
    val text = query.trim()
    if (text.isEmpty()) return books
    return books.filter { it.novel.title.contains(text, ignoreCase = true) || it.novel.author?.contains(text, ignoreCase = true) == true }
}

/** Downloaded and locked counts for the library badges; desktop does not count read chapters here yet. */
internal fun SavedBook.counts(): ChapterCounts {
    val chapterUrls = chapters.mapTo(HashSet(chapters.size)) { it.url }
    return ChapterCounts(
        total = chapters.size, readCount = 0, downloadedCount = downloaded.count { it in chapterUrls },
        lockedCount = chapters.count { it.locked },
    )
}

