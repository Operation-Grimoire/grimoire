package io.grimoire.app.ui.screen.browse

import io.grimoire.app.ui.icon.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.component.rememberDelayedVisibility
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.domain.migration.MigrationState
import io.grimoire.app.ui.component.ChapterItem
import io.grimoire.app.ui.component.FastScroller
import io.grimoire.app.ui.component.ExpandableText
import io.grimoire.app.ui.component.GenreChips
import io.grimoire.app.ui.component.MoveToCategorySheet
import io.grimoire.app.ui.screen.library.HiddenCategoriesUnlockDialog
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.SelectionTopBar
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.ui.component.rememberShimmerAlpha
import io.grimoire.app.ui.theme.premiumGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovelDetailScreen(
    onNavigateBack: () -> Unit,
    onChapterClick: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit = { _, _, _ -> },
    onOpenWebView: (url: String) -> Unit = {},
    onOpenNuSeries: (slug: String) -> Unit = {},
    onNavigateToLogin: (pkg: String) -> Unit = {},
    onOpenSourceSettings: (pkg: String) -> Unit = {},
    onMigrate: (novelId: Long) -> Unit = {},
    onMigrationComplete: (pkg: String, url: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: NovelDetailViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isLoadingNovel by viewModel.isLoadingNovel.collectAsState()
    val isLoadingChapters by viewModel.isLoadingChapters.collectAsState()
    val novelError by viewModel.novelError.collectAsState()
    val chaptersError by viewModel.chaptersError.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val novelId by viewModel.novelId.collectAsState()
    val chapterPage by viewModel.chapterPage.collectAsState()
    val chapterSort by viewModel.chapterSort.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val bookDownload by viewModel.bookDownload.collectAsState()
    val nuState by viewModel.nuState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val overrides by viewModel.overrides.collectAsState()
    val coverModel by viewModel.coverModel.collectAsState()
    val sourceNovel by viewModel.sourceNovel.collectAsState()
    val hasLockedChapters by viewModel.hasLockedChapters.collectAsState()
    val includeLockedInTotals by viewModel.includeLockedInTotals.collectAsState()
    val notifyOnNewChapters by viewModel.notifyOnNewChapters.collectAsState()
    val notifyOnNewLockedChapters by viewModel.notifyOnNewLockedChapters.collectAsState()
    val autoDownloadNewChapters by viewModel.autoDownloadNewChapters.collectAsState()
    val migrationState by viewModel.migrationState.collectAsState()
    val migrateFromTitle by viewModel.migrateFromTitle.collectAsState()
    val refreshSummary by viewModel.refreshSummary.collectAsState()

    // Re-check sign-in on every resume. Returning from the login WebView fires a
    // resume reliably, whereas the nav saved-state result is easy to miss — so
    // this is what clears the locked-chapters banner after a fresh login. Mirrors
    // SourceSettingsScreen. recheckLoginState polls + de-dupes itself.
    if (viewModel.supportsWebViewLogin) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckLoginState()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val canUnlockHidden by viewModel.canUnlockHidden.collectAsState()
    var showMigrateConfirm by remember { mutableStateOf(false) }
    var migrateMatchCount by remember { mutableStateOf(0) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showNotifSheet by remember { mutableStateOf(false) }
    var lockedDialogChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var editingField by remember { mutableStateOf<EditableField?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showJumpDialog by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    val userRating by viewModel.userRating.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isExporting by viewModel.isExporting.collectAsState()
    val exportEvent by viewModel.exportEvent.collectAsState()
    LaunchedEffect(exportEvent) {
        exportEvent?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.consumeExportEvent()
        }
    }
    val epubExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { uri -> if (uri != null) viewModel.exportEpub(uri) }

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val clearSelection = { selectedIds = emptySet() }
    val toggleSelect: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    BackHandler(enabled = selectionMode) { clearSelection() }
    val selectedChapters = chapters.filter { it.id in selectedIds }

    val hasUploadDates by remember(chapters) {
        derivedStateOf { chapters.any { it.uploadDate > 0L } }
    }

    val continueChapter by remember(chapters) {
        derivedStateOf {
            val sorted = chapters.sortedBy { it.chapterNumber }
            // Don't send the reader into a locked chapter from the Continue FAB.
            sorted.firstOrNull { !it.read && !it.locked }
                ?: sorted.lastOrNull { !it.locked }
                ?: sorted.lastOrNull()
        }
    }

    // Nudge the user to sign in only when it would actually help: there are
    // locked chapters and the source supports login but isn't signed in.
    val showLoginBanner = loginState == LoginState.SIGNED_OUT && hasLockedChapters

    val displayedChapters by viewModel.displayedChapters.collectAsState()

    val listState = rememberLazyListState()
    // Show the title in the bar (and make the bar prominent) once the header title scrolls under it.
    val density = LocalDensity.current
    val showBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 48.dp.toPx() }
        }
    }
    // Delay chapter loaders so a fast Room read doesn't flash a loader.
    val showChapterSkeleton = rememberDelayedVisibility(isLoadingChapters && chapters.isEmpty())
    val showChapterLoadingBar = rememberDelayedVisibility(isLoadingChapters)
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }

    // Number of LazyColumn items before chapter items — used for fast scroller label
    val chapterHeaderOffset by remember(isLoadingNovel, novelError, novel, chaptersError, isFavorite, categories, nuState, showLoginBanner) {
        derivedStateOf {
            var count = 1 // novel header / skeleton / error
            if (!isLoadingNovel && novelError == null) {
                if (isFavorite && categories.isNotEmpty()) count++ // category row
                if (novel.genres.isNotEmpty()) count++
                if (!novel.description.isNullOrBlank()) count++
            }
            if (nuState !is NuInfoState.Idle && nuState !is NuInfoState.Disabled) count++ // NovelUpdates section
            count++ // chapter controls row
            if (chaptersError != null) count++
            if (showLoginBanner) count++ // locked-chapters login banner
            count
        }
    }

    if (showCategoryDialog && categories.isNotEmpty()) {
        MoveToCategorySheet(
            categories = categories,
            count = 1,
            onSelect = { targetId ->
                viewModel.setCategory(targetId)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false },
            currentCategoryId = categoryId,
            showCurrent = true,
            onUnlockClick = if (canUnlockHidden) { { showUnlockDialog = true } } else null,
        )
    }

    if (showUnlockDialog) {
        HiddenCategoriesUnlockDialog(
            biometricEnabled = biometricEnabled,
            onVerifyPin = { pin -> viewModel.verifyAndUnlock(pin) },
            onUnlockedByBiometric = { viewModel.unlockFromBiometric() },
            onDismiss = { showUnlockDialog = false },
        )
    }

    if (showNotifSheet) {
        val notifSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showNotifSheet = false },
            sheetState = notifSheetState,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "New chapters",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Choose what happens when sync finds new chapters for this novel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setNotifyOnNewChapters(!notifyOnNewChapters) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notify on new chapters", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When sync finds chapters you can read now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notifyOnNewChapters,
                        onCheckedChange = { viewModel.setNotifyOnNewChapters(it) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setNotifyOnNewLockedChapters(!notifyOnNewLockedChapters) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notify on new locked chapters", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When sync finds chapters gated behind a paid tier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notifyOnNewLockedChapters,
                        onCheckedChange = { viewModel.setNotifyOnNewLockedChapters(it) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAutoDownloadNewChapters(!autoDownloadNewChapters) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-download new chapters", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Queue readable chapters for download as sync finds them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoDownloadNewChapters,
                        onCheckedChange = { viewModel.setAutoDownloadNewChapters(it) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showRateDialog) {
        RateNovelDialog(
            current = userRating,
            onSetRating = viewModel::setUserRating,
            onDismiss = { showRateDialog = false },
        )
    }

    if (showShareSheet) {
        // Snapshot the stats once as the sheet opens so the card renders a single time.
        val shareData = remember { viewModel.shareData() }
        ShareNovelDialog(
            data = shareData,
            novelUrl = viewModel.novelWebUrl,
            showCopyLink = !viewModel.isLocal,
            onDismiss = { showShareSheet = false },
        )
    }

    if (showDownloadsSheet) {
        DownloadsSheet(
            chapters = chapters,
            onDownloadAll = viewModel::downloadAll,
            onDownloadUnread = viewModel::downloadUnread,
            onDownloadNext = { viewModel.downloadNext(10) },
            onCancelQueued = viewModel::cancelAllDownloads,
            onDeleteAll = viewModel::deleteAllDownloads,
            onDismiss = { showDownloadsSheet = false },
        )
    }

    if (showJumpDialog) {
        val nextUnread = continueChapter?.takeIf { !it.read }
        JumpDialog(
            nextUnreadLabel = nextUnread?.let { ch ->
                ch.name.ifBlank {
                    val n = ch.chapterNumber
                    if (n > 0f) {
                        val pretty = if (n % 1f == 0f) n.toInt().toString() else n.toString()
                        "Chapter $pretty"
                    } else "Next chapter"
                }
            },
            onJumpToNextUnread = {
                val target = nextUnread
                if (target != null) {
                    val idx = displayedChapters.indexOfFirst { it.url == target.url }
                    if (idx >= 0) coroutineScope.launch {
                        listState.scrollToItem(chapterHeaderOffset + idx)
                    }
                }
                showJumpDialog = false
            },
            onDismiss = { showJumpDialog = false },
            onJump = { target ->
                val idx = displayedChapters.indexOfFirst { ch ->
                    ch.chapterNumber.toInt() == target ||
                        ch.name.contains(target.toString(), ignoreCase = true)
                }
                if (idx >= 0) coroutineScope.launch {
                    listState.scrollToItem(chapterHeaderOffset + idx)
                }
                showJumpDialog = false
            },
        )
    }

    lockedDialogChapter?.let { locked ->
        // When already signed in, a still-locked chapter is one the account hasn't
        // unlocked/purchased — telling the user to "Log in" is wrong. Only nudge to
        // log in when actually signed out.
        val signedIn = loginState == LoginState.SIGNED_IN
        AlertDialog(
            onDismissRequest = { lockedDialogChapter = null },
            icon = {
                Icon(
                    AppIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.premiumGold,
                )
            },
            title = { Text("Chapter locked") },
            text = {
                Text(
                    if (signedIn) {
                        "\"${locked.name}\" is a premium chapter your " +
                            "${viewModel.sourceName} account hasn't unlocked. Unlock it " +
                            "on ${viewModel.sourceName}, then refresh to read it here."
                    } else {
                        "\"${locked.name}\" is locked. Reading it requires a " +
                            "${viewModel.sourceName} account that has purchased these " +
                            "chapters. Log in to read the chapters your account has unlocked."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chapterUrl = viewModel.absoluteWebUrl(locked.url)
                    lockedDialogChapter = null
                    // Signed in: open the chapter page itself so they can unlock/buy it.
                    // Signed out: send them through the source login flow first.
                    if (signedIn) onOpenWebView(chapterUrl) else onNavigateToLogin(viewModel.pkg)
                }) { Text(if (signedIn) "Open ${viewModel.sourceName}" else "Log in") }
            },
            dismissButton = {
                TextButton(onClick = { lockedDialogChapter = null }) { Text("Close") }
            },
        )
    }

    refreshSummary?.let { summary ->
        RefreshSummaryDialog(
            summary = summary,
            onDismiss = viewModel::acknowledgeRefreshSummary,
        )
    }

    editingField?.let { field ->
        MetadataFieldEditSheet(
            field = field,
            source = sourceNovel,
            overrides = overrides,
            onSave = viewModel::saveMetadataOverrides,
            onDismiss = { editingField = null },
        )
    }

    LaunchedEffect(migrationState) {
        if (migrationState == MigrationState.Success) onMigrationComplete(viewModel.pkg, novel.url)
    }

    if (showMigrateConfirm) {
        AlertDialog(
            onDismissRequest = { showMigrateConfirm = false },
            title = { Text("Migrate to this novel?") },
            text = {
                Text(
                    (if (migrateMatchCount > 0) {
                        "$migrateMatchCount chapter${if (migrateMatchCount == 1) "" else "s"} " +
                            "will be marked as read here."
                    } else {
                        "No chapters could be matched, so no read progress will carry over."
                    }) + " \"$migrateFromTitle\" will be removed from your library.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMigrateConfirm = false
                    viewModel.confirmMigration()
                }) { Text("Migrate") }
            },
            dismissButton = {
                TextButton(onClick = { showMigrateConfirm = false }) { Text("Cancel") }
            },
        )
    }

    (migrationState as? MigrationState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMigrationError,
            title = { Text("Migration failed") },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMigrationError) { Text("OK") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (continueChapter != null && !viewModel.isMigrationTarget && !selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onChapterClick(viewModel.pkg, novel.url, continueChapter!!.url)
                    },
                    icon = { Icon(AppIcons.PlayArrow, contentDescription = null) },
                    text = { Text(if (chapters.none { it.read }) "Start" else "Continue") },
                    expanded = fabExpanded,
                )
            }
        },
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClear = clearSelection,
                    onSelectAll = {
                        val ids = displayedChapters.map { it.id }.toSet()
                        selectedIds = if (selectedIds.containsAll(ids)) emptySet() else ids
                    },
                )
            } else {
            val barColor by animateColorAsState(
                targetValue = if (showBarTitle) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                label = "barColor",
            )
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    AnimatedVisibility(visible = showBarTitle, enter = fadeIn(), exit = fadeOut()) {
                        Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    val canShare = !isLoadingNovel && novelError == null && novel.title.isNotBlank()
                    if (canShare) {
                        PlainTooltipIconButton(onClick = { showShareSheet = true }, tooltip = "Share") {
                            Icon(AppIcons.Share, contentDescription = "Share")
                        }
                    }
                    val hasBulkActions = chapters.isNotEmpty()
                    val canMigrate = isFavorite && novelId > 0L
                    // EPUB novels (local imports and EPUB-source extensions like
                    // Z-Library) have no scraped chapter list, so "notify / auto-download
                    // new chapters" can never fire — hide it for both.
                    val canConfigureNewChapters = isFavorite && novelId > 0L &&
                        !viewModel.isLocal && !viewModel.isEpubSource
                    val canOpenSourceSettings = viewModel.hasSourceSettings
                    // Exporting reads chapter text straight from the local download
                    // store, so it's offered whenever at least one chapter is saved
                    // on-device — including books that were themselves imported.
                    val canExport = novelId > 0L && chapters.any {
                        it.downloadStatus in ChapterDownloadStatus.HAS_CONTENT_ORDINALS
                    }
                    if (canConfigureNewChapters) {
                        val notificationsOn = notifyOnNewChapters || notifyOnNewLockedChapters
                        val autoOn = autoDownloadNewChapters
                        val active = notificationsOn || autoOn
                        val tooltip = when {
                            notificationsOn && autoOn -> "Notifications + auto-download on"
                            notificationsOn -> "Notifications on"
                            autoOn -> "Auto-download on"
                            else -> "Notifications & download"
                        }
                        // wifi_notification icon for auto-download; fill when a notify toggle is on.
                        val icon = when {
                            autoOn && notificationsOn -> AppIcons.WifiNotificationFilled
                            autoOn -> AppIcons.WifiNotification
                            notificationsOn -> AppIcons.Notifications
                            else -> AppIcons.NotificationsNone
                        }
                        PlainTooltipIconButton(onClick = { showNotifSheet = true }, tooltip = tooltip) {
                            Icon(
                                icon,
                                contentDescription = tooltip,
                                tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                    if (!viewModel.isLocal) {
                        val downloading = chapters.any { it.downloadStatus in ChapterDownloadStatus.DOWNLOADING_ORDINALS }
                        PlainTooltipIconButton(
                            onClick = { showDownloadsSheet = true },
                            tooltip = if (downloading) "Downloading…" else "Downloads",
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (downloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Icon(
                                    AppIcons.Download,
                                    contentDescription = "Downloads",
                                    modifier = Modifier.size(if (downloading) 14.dp else 24.dp),
                                )
                            }
                        }
                    }
                    if (hasBulkActions || canMigrate || canConfigureNewChapters || canOpenSourceSettings || canExport) {
                        Box {
                            PlainTooltipIconButton(onClick = { overflowMenuExpanded = true }, tooltip = "More actions") {
                                Icon(AppIcons.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) {
                                if (hasBulkActions) {
                                    DropdownMenuItem(
                                        text = { Text("Mark all as read") },
                                        onClick = { viewModel.markAllRead(true); overflowMenuExpanded = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mark all as unread") },
                                        onClick = { viewModel.markAllRead(false); overflowMenuExpanded = false },
                                    )
                                }
                                if (canMigrate) {
                                    if (hasBulkActions) HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Migrate") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            onMigrate(novelId)
                                        },
                                        leadingIcon = { Icon(AppIcons.SwapVert, contentDescription = null) },
                                    )
                                }
                                if (canOpenSourceSettings) {
                                    if (hasBulkActions || canMigrate) HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Source settings") },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            onOpenSourceSettings(viewModel.pkg)
                                        },
                                        leadingIcon = { Icon(AppIcons.Tune, contentDescription = null) },
                                    )
                                }
                                if (canExport) {
                                    if (hasBulkActions || canMigrate || canOpenSourceSettings) HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Export as EPUB") },
                                        enabled = !isExporting,
                                        onClick = {
                                            overflowMenuExpanded = false
                                            epubExportLauncher.launch(viewModel.suggestedExportFileName())
                                        },
                                        leadingIcon = { Icon(AppIcons.SaveAlt, contentDescription = null) },
                                    )
                                }
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            Column {
                if (!selectionMode && viewModel.isMigrationTarget && novelId > 0L &&
                    novelId != viewModel.migrateFromId
                ) {
                    Surface(shadowElevation = 8.dp) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    migrateMatchCount = viewModel.migrationMatchCount()
                                    showMigrateConfirm = true
                                }
                            },
                            enabled = migrationState != MigrationState.Running && chapters.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                if (migrationState == MigrationState.Running) "Migrating…"
                                else "Migrate here",
                            )
                        }
                    }
                }
                TooltipBottomBar(visible = selectionMode) {
                    val showMarkRead = selectedChapters.any { !it.read }
                    val showMarkUnread = selectedChapters.any { it.read }
                    val showDownload = selectedChapters.any {
                        !it.locked &&
                            (it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                                it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal)
                    }
                    val showDelete = selectedChapters.any {
                        it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal
                    }
                    val showRedownload = selectedChapters.any {
                        !it.locked && (
                            it.downloadStatus == ChapterDownloadStatus.DOWNLOADED.ordinal ||
                                it.downloadStatus == ChapterDownloadStatus.REDOWNLOAD_ERROR.ordinal
                            )
                    }
                    val showCancel = selectedChapters.any {
                        it.downloadStatus in ChapterDownloadStatus.QUEUED_ORDINALS
                    }
                    val singleSelection = selectedIds.size == 1
                    TooltipIconButton(
                        visible = showMarkRead,
                        icon = AppIcons.DoneAll,
                        label = "Mark read",
                        onClick = {
                            viewModel.markChaptersRead(selectedIds.toList(), true)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showMarkUnread,
                        icon = AppIcons.RemoveDone,
                        label = "Mark unread",
                        onClick = {
                            viewModel.markChaptersRead(selectedIds.toList(), false)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showDownload,
                        icon = AppIcons.Download,
                        label = "Download",
                        onClick = {
                            viewModel.downloadChapters(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showDelete,
                        icon = AppIcons.Delete,
                        label = "Delete",
                        onClick = {
                            viewModel.deleteDownloads(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showRedownload,
                        icon = AppIcons.Refresh,
                        label = "Redownload",
                        onClick = {
                            viewModel.redownloadChapters(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showCancel,
                        icon = AppIcons.Close,
                        label = "Cancel",
                        onClick = {
                            viewModel.cancelDownloads(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = singleSelection,
                        icon = AppIcons.VerticalAlignTop,
                        label = "Select above",
                        onClick = {
                            val idx = displayedChapters.indexOfFirst { it.id in selectedIds }
                            if (idx >= 0) {
                                selectedIds = selectedIds +
                                    displayedChapters.subList(0, idx + 1).map { it.id }
                            }
                        },
                    )
                    TooltipIconButton(
                        visible = singleSelection,
                        icon = AppIcons.VerticalAlignBottom,
                        label = "Select below",
                        onClick = {
                            val idx = displayedChapters.indexOfFirst { it.id in selectedIds }
                            if (idx >= 0) {
                                selectedIds = selectedIds +
                                    displayedChapters.subList(idx, displayedChapters.size).map { it.id }
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        val detailBody = @Composable {
            FastScroller(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                thumbLabel = { fraction ->
                    val rawIdx = (fraction * listState.layoutInfo.totalItemsCount).toInt()
                    val chapterIdx = (rawIdx - chapterHeaderOffset).coerceIn(0, displayedChapters.size - 1)
                    displayedChapters.getOrNull(chapterIdx)?.name
                },
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = if (continueChapter != null && !selectionMode) 88.dp else 0.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Novel header
                    item(key = "novel_header") {
                        when {
                            isLoadingNovel -> NovelHeaderSkeleton()
                            novelError != null -> Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(novelError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = viewModel::retryNovel) { Text("Retry") }
                            }
                            else -> NovelHeader(
                                novel = novel,
                                overrides = overrides,
                                coverModel = coverModel,
                                sourceName = viewModel.sourceName,
                                isLocal = viewModel.isLocal,
                                onEditField = { editingField = it },
                                onSetCoverUri = viewModel::setCustomCoverFromUri,
                                onSetCoverUrl = viewModel::setCustomCoverUrl,
                                onResetCover = viewModel::resetCustomCover,
                            )
                        }
                    }

                    // Action row (library + WebView + category) below the header
                    if (!isLoadingNovel && novelError == null) {
                        item(key = "novel_actions") {
                            val currentCategoryName = if (isFavorite && categories.isNotEmpty()) {
                                categories.firstOrNull { cat ->
                                    if (cat.isDefault) categoryId == null else cat.id == categoryId
                                }?.name ?: "—"
                            } else {
                                null
                            }
                            NovelActionRow(
                                inLibrary = isFavorite,
                                onToggleLibrary = viewModel::toggleFavorite,
                                showWebView = !viewModel.isLocal,
                                onOpenWebView = { onOpenWebView(viewModel.novelWebUrl) },
                                categoryName = currentCategoryName,
                                onEditCategory = { showCategoryDialog = true },
                                userRating = userRating,
                                onRate = { showRateDialog = true },
                            )
                        }
                    }

                    // Genres
                    if (!isLoadingNovel && novelError == null && (novel.genres.isNotEmpty() || overrides.genres != null)) {
                        item(key = "genres") {
                            Row(
                                modifier = Modifier.animateItem().fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GenreChips(
                                    genres = novel.genres,
                                    onLongPress = { editingField = EditableField.GENRES },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                OverrideIndicator(overrides.genres != null) { editingField = EditableField.GENRES }
                            }
                        }
                    }

                    // Description
                    if (!isLoadingNovel && novelError == null && !novel.description.isNullOrBlank()) {
                        item(key = "description") {
                            Row(
                                modifier = Modifier.animateItem().fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                ExpandableText(
                                    text = novel.description!!,
                                    onLongClick = { editingField = EditableField.DESCRIPTION },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                OverrideIndicator(overrides.description != null) { editingField = EditableField.DESCRIPTION }
                            }
                        }
                    }

                    // NovelUpdates metadata + recommendations
                    if (nuState !is NuInfoState.Idle && nuState !is NuInfoState.Disabled) {
                        item(key = "novelupdates") {
                            NovelUpdatesSection(
                                state = nuState,
                                viewModel = viewModel,
                                onOpenWebView = onOpenWebView,
                                onOpenNuSeries = onOpenNuSeries,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Whole-book EPUB download (EpubSource only)
                    if (!isLoadingNovel && novelError == null && viewModel.isEpubSource) {
                        item(key = "epub_download") {
                            Column(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val downloading = bookDownload is BookDownloadState.Downloading
                                val hasBook = chapters.isNotEmpty()
                                val label = when {
                                    downloading -> "Downloading…"
                                    hasBook -> "Re-download EPUB"
                                    else -> "Download EPUB"
                                }
                                val content: @Composable RowScope.() -> Unit = {
                                    if (downloading) {
                                        CircularProgressIndicator(
                                            Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = LocalContentColor.current,
                                        )
                                    } else {
                                        Icon(AppIcons.Download, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                                if (hasBook) {
                                    // The book is already downloaded: Continue reading is the
                                    // primary action (the FAB), so keep re-download quiet.
                                    OutlinedButton(
                                        onClick = { viewModel.downloadBook() },
                                        enabled = !downloading,
                                        content = content,
                                    )
                                } else {
                                    Button(
                                        onClick = { viewModel.downloadBook() },
                                        enabled = !downloading,
                                        modifier = Modifier.fillMaxWidth(),
                                        content = content,
                                    )
                                }
                                (bookDownload as? BookDownloadState.Error)?.let { err ->
                                    Text(
                                        err.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Chapter controls
                    item(key = "chapter_controls") {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = when {
                                        isLoadingChapters && chapterPage > 0 -> "Loading page $chapterPage…"
                                        isLoadingChapters -> "Loading chapters…"
                                        chaptersError != null -> "Chapters"
                                        searchQuery.isNotBlank() -> "${displayedChapters.size} of ${chapters.size}"
                                        else -> "${chapters.size} chapter${if (chapters.size != 1) "s" else ""}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = chapters.isNotEmpty()) { showJumpDialog = true },
                                )
                                if (chapters.isNotEmpty()) {
                                    PlainTooltipIconButton(onClick = {
                                        searchActive = !searchActive
                                        if (!searchActive) viewModel.setSearchQuery("")
                                    }, tooltip = if (searchActive) "Close search" else "Search chapters") {
                                        Icon(
                                            if (searchActive) AppIcons.Close else AppIcons.Search,
                                            contentDescription = if (searchActive) "Close search" else "Search chapters",
                                        )
                                    }
                                    Box {
                                        PlainTooltipIconButton(onClick = { sortMenuExpanded = true }, tooltip = "Sort options") {
                                            Icon(AppIcons.SwapVert, contentDescription = "Sort options")
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                        ) {
                                            ChapterSort.entries.forEach { sort ->
                                                val dateSort = sort == ChapterSort.DATE_ASC || sort == ChapterSort.DATE_DESC
                                                DropdownMenuItem(
                                                    text = { Text(sort.label) },
                                                    onClick = { viewModel.setSort(sort); sortMenuExpanded = false },
                                                    enabled = !dateSort || hasUploadDates,
                                                    trailingIcon = if (chapterSort == sort) {
                                                        { Icon(AppIcons.Check, contentDescription = null) }
                                                    } else null,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (showChapterLoadingBar) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                            if (!isLoadingChapters && chaptersError == null && chapters.isNotEmpty()) {
                                val readCount = chapters.count { it.read }
                                val downloadedCount = chapters.count {
                                    it.downloadStatus in ChapterDownloadStatus.HAS_CONTENT_ORDINALS
                                }
                                val lockedCount = chapters.count { it.locked }
                                val displayedTotal = if (includeLockedInTotals) {
                                    chapters.size
                                } else {
                                    (chapters.size - lockedCount).coerceAtLeast(0)
                                }
                                val percent = if (displayedTotal > 0) {
                                    (readCount * 100 / displayedTotal).coerceIn(0, 100)
                                } else 0
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            AppIcons.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "$readCount / $displayedTotal ($percent%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (downloadedCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                AppIcons.DownloadDone,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = "$downloadedCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    if (lockedCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                AppIcons.Lock,
                                                contentDescription = "Locked chapters",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.premiumGold,
                                            )
                                            Text(
                                                text = "$lockedCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible = searchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search chapters…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                )
                            }
                        }
                    }

                    // Chapters error
                    if (chaptersError != null) {
                        item(key = "chapters_error") {
                            Column(
                                modifier = Modifier.animateItem().fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(chaptersError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = viewModel::retryChapters) { Text("Retry") }
                            }
                        }
                    }

                    // Locked-chapters login nudge
                    if (showLoginBanner) {
                        item(key = "locked_login_banner") {
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onNavigateToLogin(viewModel.pkg) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    AppIcons.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    "Some chapters are locked. Log in to ${viewModel.sourceName} " +
                                        "to read the chapters your account has unlocked.",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = { onNavigateToLogin(viewModel.pkg) }) {
                                    Text("Log in")
                                }
                            }
                        }
                    }

                    // Chapter list or skeleton
                    if (showChapterSkeleton) {
                        item(key = "skeletons") {
                            val alpha = rememberShimmerAlpha()
                            Column {
                                repeat(14) { ChapterSkeletonItem(alpha = alpha) }
                            }
                        }
                    } else {
                        // Key by Room id, not URL — sources occasionally emit
                        // chapters that share a URL (e.g. paid Madara rows all
                        // serve href="#"), and Compose crashes the screen with
                        // IllegalArgumentException once a duplicate key composes.
                        items(displayedChapters, key = { it.id }) { chapter ->
                            ChapterItem(
                                chapter = chapter,
                                selected = chapter.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = { onChapterClick(viewModel.pkg, novel.url, chapter.url) },
                                onLockedClick = { lockedDialogChapter = chapter },
                                onToggleSelection = { toggleSelect(chapter.id) },
                                onDownload = { viewModel.downloadChapter(chapter) },
                                onCancelDownload = { viewModel.cancelDownload(chapter) },
                                onDeleteDownload = { viewModel.deleteDownload(chapter) },
                                onRedownload = { viewModel.redownloadChapter(chapter) },
                            )
                        }
                    }
                }
            }
        }
        if (viewModel.isLocal) {
            Box(Modifier.padding(padding)) { detailBody() }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoadingNovel && novel.initialized,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding),
            ) { detailBody() }
        }
    }
}

