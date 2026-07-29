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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import io.grimoire.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NovelDetailScreen(
    onNavigateBack: () -> Unit,
    onChapterClick: (pkg: String, novelUrl: String, chapterUrl: String) -> Unit = { _, _, _ -> },
    onOpenWebView: (url: String) -> Unit = {},
    onOpenNuSeries: (slug: String) -> Unit = {},
    onNavigateToLogin: (pkg: String) -> Unit = {},
    onOpenSourceSettings: (pkg: String) -> Unit = {},
    onOpenExtensions: (query: String) -> Unit = {},
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
    val sourceMissing by viewModel.sourceMissing.collectAsState()
    val missingSourceName by viewModel.missingSourceName.collectAsState()
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
    var showMissingSourceDialog by remember { mutableStateOf(false) }
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
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                io.grimoire.app.ui.component.sheet.SheetTitle(stringResource(R.string.novel_new_chapters_title))
                Text(
                    stringResource(R.string.novel_new_chapters_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                )
                io.grimoire.app.ui.component.sheet.SheetSwitchRow(
                    title = stringResource(R.string.novel_notify_new),
                    hint = stringResource(R.string.novel_notify_new_description),
                    checked = notifyOnNewChapters,
                    onCheckedChange = { viewModel.setNotifyOnNewChapters(it) },
                )
                io.grimoire.app.ui.component.sheet.SheetSwitchRow(
                    title = stringResource(R.string.novel_notify_locked),
                    hint = stringResource(R.string.novel_notify_locked_description),
                    checked = notifyOnNewLockedChapters,
                    onCheckedChange = { viewModel.setNotifyOnNewLockedChapters(it) },
                )
                io.grimoire.app.ui.component.sheet.SheetSwitchRow(
                    title = stringResource(R.string.novel_auto_download_new),
                    hint = stringResource(R.string.novel_auto_download_new_description),
                    checked = autoDownloadNewChapters,
                    onCheckedChange = { viewModel.setAutoDownloadNewChapters(it) },
                )
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

    if (showMissingSourceDialog) {
        val named = missingSourceName?.let { stringResource(R.string.novel_source_missing_named, it) }
            ?: stringResource(R.string.novel_source_missing_generic)
        AlertDialog(
            onDismissRequest = { showMissingSourceDialog = false },
            icon = { Icon(AppIcons.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.novel_source_missing_title)) },
            text = { Text(stringResource(R.string.novel_source_missing_message, named)) },
            confirmButton = {
                TextButton(onClick = {
                    showMissingSourceDialog = false
                    onOpenExtensions(missingSourceName.orEmpty())
                }) { Text(stringResource(R.string.action_open_extensions)) }
            },
            dismissButton = {
                TextButton(onClick = { showMissingSourceDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
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
                        stringResource(R.string.novel_chapter_number, pretty)
                    } else stringResource(R.string.novel_next_chapter)
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
            title = { Text(stringResource(R.string.novel_locked_title)) },
            text = {
                Text(
                    if (signedIn) {
                        stringResource(R.string.novel_locked_signed_in, locked.name, viewModel.sourceName)
                    } else {
                        stringResource(R.string.novel_locked_signed_out, locked.name, viewModel.sourceName)
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
                }) {
                    Text(
                        if (signedIn) stringResource(R.string.novel_open_source, viewModel.sourceName)
                        else stringResource(R.string.novel_log_in),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { lockedDialogChapter = null }) { Text(stringResource(R.string.action_close)) }
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
            title = { Text(stringResource(R.string.novel_migrate_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.novel_migrate_summary,
                        if (migrateMatchCount > 0) {
                        pluralStringResource(
                            R.plurals.novel_migrate_matched_chapters,
                            migrateMatchCount,
                            migrateMatchCount,
                        )
                        } else {
                            stringResource(R.string.novel_migrate_no_matches)
                        },
                        migrateFromTitle,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMigrateConfirm = false
                    viewModel.confirmMigration()
                }) { Text(stringResource(R.string.novel_migrate_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showMigrateConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    (migrationState as? MigrationState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMigrationError,
            title = { Text(stringResource(R.string.novel_migrate_failed)) },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMigrationError) { Text(stringResource(R.string.action_ok)) }
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
                    text = {
                        Text(stringResource(if (chapters.none { it.read }) R.string.novel_start else R.string.novel_continue))
                    },
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
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = stringResource(R.string.action_back)) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                        PlainTooltipIconButton(onClick = { showShareSheet = true }, tooltip = stringResource(R.string.action_share)) {
                            Icon(AppIcons.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    val hasBulkActions = chapters.isNotEmpty()
                    val canMigrate = isFavorite && novelId > 0L
                    // EPUB novels (local imports and EPUB-source extensions like
                    // Z-Library) have no scraped chapter list, so "notify / auto-download
                    // new chapters" can never fire — hide it for both.
                    val canConfigureNewChapters = isFavorite && novelId > 0L &&
                        !viewModel.isLocal && !viewModel.isEpubSource && !sourceMissing
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
                            notificationsOn && autoOn -> stringResource(R.string.novel_notifications_auto_on)
                            notificationsOn -> stringResource(R.string.novel_notifications_on)
                            autoOn -> stringResource(R.string.novel_auto_download_on)
                            else -> stringResource(R.string.novel_notifications_download)
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
                    if (!viewModel.isLocal && !sourceMissing) {
                        val downloading = chapters.any { it.downloadStatus in ChapterDownloadStatus.DOWNLOADING_ORDINALS }
                        PlainTooltipIconButton(
                            onClick = { showDownloadsSheet = true },
                            tooltip = stringResource(
                                if (downloading) R.string.novel_downloading else R.string.novel_downloads_title,
                            ),
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
                                    contentDescription = stringResource(R.string.novel_downloads_title),
                                    modifier = Modifier.size(if (downloading) 14.dp else 24.dp),
                                )
                            }
                        }
                    }
                    if (hasBulkActions || canMigrate || canConfigureNewChapters || canOpenSourceSettings || canExport) {
                        Box {
                            PlainTooltipIconButton(onClick = { overflowMenuExpanded = true }, tooltip = stringResource(R.string.action_more_actions)) {
                                Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.action_more_actions))
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false },
                            ) {
                                if (hasBulkActions) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.novel_mark_all_read)) },
                                        onClick = { viewModel.markAllRead(true); overflowMenuExpanded = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.novel_mark_all_unread)) },
                                        onClick = { viewModel.markAllRead(false); overflowMenuExpanded = false },
                                    )
                                }
                                if (canMigrate) {
                                    if (hasBulkActions) HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.novel_migrate_action)) },
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
                                        text = { Text(stringResource(R.string.novel_source_settings)) },
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
                                        text = { Text(stringResource(R.string.novel_export_epub)) },
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
                                stringResource(
                                    if (migrationState == MigrationState.Running) R.string.novel_migrating
                                    else R.string.novel_migrate_here,
                                ),
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
                        label = stringResource(R.string.novel_mark_read),
                        onClick = {
                            viewModel.markChaptersRead(selectedIds.toList(), true)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showMarkUnread,
                        icon = AppIcons.RemoveDone,
                        label = stringResource(R.string.novel_mark_unread),
                        onClick = {
                            viewModel.markChaptersRead(selectedIds.toList(), false)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showDownload,
                        icon = AppIcons.Download,
                        label = stringResource(R.string.novel_download),
                        onClick = {
                            viewModel.downloadChapters(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showDelete,
                        icon = AppIcons.Delete,
                        label = stringResource(R.string.action_delete),
                        onClick = {
                            viewModel.deleteDownloads(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showRedownload,
                        icon = AppIcons.Refresh,
                        label = stringResource(R.string.novel_redownload),
                        onClick = {
                            viewModel.redownloadChapters(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = showCancel,
                        icon = AppIcons.Close,
                        label = stringResource(R.string.action_cancel),
                        onClick = {
                            viewModel.cancelDownloads(selectedChapters)
                            clearSelection()
                        },
                    )
                    TooltipIconButton(
                        visible = singleSelection,
                        icon = AppIcons.VerticalAlignTop,
                        label = stringResource(R.string.novel_select_above),
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
                        label = stringResource(R.string.novel_select_below),
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
                                TextButton(onClick = viewModel::retryNovel) { Text(stringResource(R.string.action_retry)) }
                            }
                            else -> NovelHeader(
                                novel = novel,
                                overrides = overrides,
                                coverModel = coverModel,
                                sourceName = viewModel.sourceName,
                                isLocal = viewModel.isLocal,
                                sourceMissing = sourceMissing,
                                missingSourceName = missingSourceName,
                                onSourceMissingClick = { showMissingSourceDialog = true },
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
                                showWebView = !viewModel.isLocal && !sourceMissing,
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
                                    downloading -> stringResource(R.string.novel_downloading)
                                    hasBook -> stringResource(R.string.novel_redownload_epub)
                                    else -> stringResource(R.string.novel_download_epub)
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
                                        isLoadingChapters && chapterPage > 0 -> stringResource(R.string.novel_loading_page, chapterPage)
                                        isLoadingChapters -> stringResource(R.string.novel_loading_chapters)
                                        chaptersError != null -> stringResource(R.string.novel_chapters_title)
                                        searchQuery.isNotBlank() -> stringResource(
                                            R.string.novel_chapter_filtered_count,
                                            displayedChapters.size,
                                            chapters.size,
                                        )
                                        else -> pluralStringResource(
                                            R.plurals.novel_chapter_total,
                                            chapters.size,
                                            chapters.size,
                                        )
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
                                    }, tooltip = stringResource(
                                        if (searchActive) R.string.novel_close_search
                                        else R.string.novel_search_chapters,
                                    )) {
                                        Icon(
                                            if (searchActive) AppIcons.Close else AppIcons.Search,
                                            contentDescription = stringResource(
                                                if (searchActive) R.string.novel_close_search
                                                else R.string.novel_search_chapters,
                                            ),
                                        )
                                    }
                                    Box {
                                        PlainTooltipIconButton(onClick = { sortMenuExpanded = true }, tooltip = stringResource(R.string.novel_sort_options)) {
                                            Icon(AppIcons.SwapVert, contentDescription = stringResource(R.string.novel_sort_options))
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                        ) {
                                            ChapterSort.entries.forEach { sort ->
                                                val dateSort = sort == ChapterSort.DATE_ASC || sort == ChapterSort.DATE_DESC
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            stringResource(
                                                                when (sort) {
                                                                    ChapterSort.NUMBER_ASC -> R.string.novel_sort_number_asc
                                                                    ChapterSort.NUMBER_DESC -> R.string.novel_sort_number_desc
                                                                    ChapterSort.DATE_ASC -> R.string.novel_sort_date_oldest
                                                                    ChapterSort.DATE_DESC -> R.string.novel_sort_date_newest
                                                                },
                                                            ),
                                                        )
                                                    },
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
                                                contentDescription = stringResource(R.string.library_locked_chapters),
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
                                    placeholder = { Text(stringResource(R.string.novel_search_chapters_placeholder)) },
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
                                TextButton(onClick = viewModel::retryChapters) { Text(stringResource(R.string.action_retry)) }
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
                                    stringResource(R.string.novel_locked_login_banner, viewModel.sourceName),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = { onNavigateToLogin(viewModel.pkg) }) {
                                    Text(stringResource(R.string.novel_log_in))
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
        if (viewModel.isLocal || sourceMissing) {
            // No live source to refresh against — render without pull-to-refresh.
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
