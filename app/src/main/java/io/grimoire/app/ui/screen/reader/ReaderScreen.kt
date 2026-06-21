package io.grimoire.app.ui.screen.reader

import android.app.Activity
import io.grimoire.app.ui.component.PlainTooltipIconButton
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.preferences.ReaderOrientation
import io.grimoire.app.data.tts.TtsPlaybackState
import io.grimoire.app.ui.component.PrivacyImage
import io.grimoire.app.ui.component.TooltipBottomBar
import io.grimoire.app.ui.component.TooltipIconButton
import io.grimoire.app.ui.component.ZoomableCoverImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private data class ProgressSnapshot(
    val anchorIndex: Int,
    val anchorOffset: Int,
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val fraction: Float,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    onOpenWebView: (url: String) -> Unit = {},
    onOpenTtsSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()
    val hasPrev by viewModel.hasPrev.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val bookmarkCount by viewModel.currentChapterBookmarkCount.collectAsState()
    val pendingJump by viewModel.pendingJump.collectAsState()

    val fontSize by viewModel.fontSize.collectAsState()
    val lineHeightTimes10 by viewModel.lineHeightTimes10.collectAsState()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsState()
    val readerFont by viewModel.readerFont.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val hideNotificationBar by viewModel.hideNotificationBar.collectAsState()
    val hideInlineImages by viewModel.hideInlineImages.collectAsState()
    val showChapterProgressPercent by viewModel.showChapterProgressPercent.collectAsState()
    val showNovelProgressPercent by viewModel.showNovelProgressPercent.collectAsState()
    val grimoireEasterEggEnabled by viewModel.grimoireEasterEggEnabled.collectAsState()
    val markAsReadStrategy by viewModel.markAsReadStrategy.collectAsState()
    val markAsReadThreshold by viewModel.markAsReadThreshold.collectAsState()
    val markAsReadParagraphsFromEnd by viewModel.markAsReadParagraphsFromEnd.collectAsState()
    val novelProgress by viewModel.novelProgress.collectAsState()
    val revealedImageUrls by viewModel.revealedImageUrls.collectAsState()

    val ttsState by viewModel.ttsState.collectAsState()
    val ttsCurrentUrl by viewModel.ttsCurrentUrl.collectAsState()
    val ttsSpokenPageIndex by viewModel.ttsSpokenPageIndex.collectAsState()
    val ttsError by viewModel.ttsError.collectAsState()

    val ttsEnabled by viewModel.ttsEnabled.collectAsState()
    val ttsEngine by viewModel.ttsEngine.collectAsState()
    val ttsSpeechRate by viewModel.ttsSpeechRate.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val ttsAutoAdvance by viewModel.ttsAutoAdvance.collectAsState()

    val ttsActiveForChapter = currentChapter?.url != null &&
        currentChapter?.url == ttsCurrentUrl &&
        ttsState != TtsPlaybackState.IDLE && ttsState != TtsPlaybackState.ERROR
    val ttsPlayingThisChapter = ttsActiveForChapter && ttsState == TtsPlaybackState.PLAYING

    val colors = colorTheme.readerColors
    val fontFamily = readerFont.fontFamily
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeightTimes10 / 10f).sp,
        fontFamily = fontFamily,
        color = colors.foreground,
    )

    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(orientation) {
        val activity = context.findActivity()
        val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = when (orientation) {
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ReaderOrientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = original
        }
    }

    DisposableEffect(hideNotificationBar) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hideNotificationBar) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    val listState = rememberLazyListState()
    val visiblePages = remember(pages) {
        pages.filter { it.text.isNotBlank() || it.imageUrl != null || it.isSeparator }
    }
    var barsVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGrimoirePopup by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showBookmarks by remember { mutableStateOf(false) }
    var restoredScrollUrl by remember { mutableStateOf<String?>(null) }

    // Pause the easter-egg colour animation when the reader leaves the foreground —
    // avoids waking the recomposer for off-screen frames.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // One ticker drives every visible "grimoire" — each character samples this phase plus
    // its own position so the highlight reads as a wave rolling along the word.
    val grimoireTransition = rememberInfiniteTransition(label = "grimoire")
    val grimoireWavePhase by grimoireTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "grimoire_wave_phase",
    )
    val grimoireEffectActive = grimoireEasterEggEnabled && isResumed

    // Reset scroll only on genuine chapter changes (next/prev/TTS auto-advance) — NOT on
    // composition re-entry from transient screens (e.g. returning from the in-chapter webview).
    // `rememberLazyListState` already persists scroll across navigation via rememberSaveable, so
    // we just need to stop fighting it. See issue #133.
    LaunchedEffect(Unit) {
        viewModel.chapterChanged.collect {
            restoredScrollUrl = null
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(currentChapter?.url, isLoading) {
        if (isLoading) return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val chapter = currentChapter ?: return@LaunchedEffect
        if (restoredScrollUrl == chapter.url) return@LaunchedEffect
        restoredScrollUrl = chapter.url
        val anchorIndex = chapter.readAnchorItemIndex
        val anchorOffset = chapter.readAnchorItemOffset
        if (anchorIndex <= 0 && anchorOffset <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val total = listState.layoutInfo.totalItemsCount
        val targetIndex = anchorIndex.coerceIn(0, total - 1)
        listState.scrollToItem(targetIndex, anchorOffset.coerceAtLeast(0))
    }

    // Bookmark jump: once the target chapter's pages are present, scroll to the
    // bookmark (re-anchoring by stored text). Suppress the readAnchor restore for
    // this chapter so the two don't fight.
    LaunchedEffect(pendingJump, pages, isLoading) {
        val bookmark = pendingJump ?: return@LaunchedEffect
        if (isLoading || pages.isEmpty()) return@LaunchedEffect
        val chapter = currentChapter ?: return@LaunchedEffect
        if (chapter.url != bookmark.chapterUrl) return@LaunchedEffect
        restoredScrollUrl = chapter.url
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        val total = listState.layoutInfo.totalItemsCount
        val target = viewModel.resolveBookmarkItemIndex(bookmark, visiblePages)
            .coerceIn(0, total - 1)
        listState.scrollToItem(target, bookmark.anchorOffset.coerceAtLeast(0))
        viewModel.consumePendingJump()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@snapshotFlow null
            val first = info.visibleItemsInfo.firstOrNull() ?: return@snapshotFlow null
            val last = info.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow null
            // Fraction is last-visible-based so it matches the existing scrollbar percentage
            // (see ReaderScrollbar `readFraction`) — what the user has actually scrolled past.
            val fraction = ((last.index + 1).toFloat() / total).coerceIn(0f, 1f)
            ProgressSnapshot(
                anchorIndex = first.index,
                anchorOffset = listState.firstVisibleItemScrollOffset,
                lastVisibleIndex = last.index,
                totalItems = total,
                fraction = fraction,
            )
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                viewModel.updateProgress(
                    fraction = it.fraction,
                    anchorIndex = it.anchorIndex,
                    anchorOffset = it.anchorOffset,
                    lastVisibleIndex = it.lastVisibleIndex,
                    totalItems = it.totalItems,
                )
            }
    }

    // Follow TTS: scroll the paragraph being spoken into view, unless the user is scrolling.
    LaunchedEffect(ttsSpokenPageIndex) {
        val pageIndex = ttsSpokenPageIndex ?: return@LaunchedEffect
        val target = visiblePages.indexOfFirst { it.index == pageIndex }
        if (target >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(target + 1) // +1 for the chapter-title item
        }
    }

    LaunchedEffect(ttsError) {
        ttsError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearTtsError()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Content — keep a gap at the top/bottom for the system bars even while
        // they're hidden, so text isn't drawn under the notch / status bar area.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                .pointerInput(Unit) {
                    detectTapGestures { barsVisible = !barsVisible }
                },
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::loadPages) { Text("Retry") }
                }
                else -> SelectionContainer {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = currentChapter?.name ?: "",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = fontFamily,
                                color = colors.foreground,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        )
                    }
                    items(visiblePages, key = { it.index }) { page ->
                        val imageUrl = page.imageUrl
                        when {
                            page.isSeparator -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = (paragraphSpacing * 2).dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(0.4f),
                                        color = colors.foreground.copy(alpha = 0.4f),
                                    )
                                }
                            }
                            imageUrl != null -> {
                                val imageModifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = paragraphSpacing.dp)
                                if (hideInlineImages) {
                                    PrivacyImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        revealed = imageUrl in revealedImageUrls,
                                        onTapToggle = { viewModel.toggleImageReveal(imageUrl) },
                                        modifier = imageModifier,
                                    )
                                } else {
                                    ZoomableCoverImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = imageModifier,
                                    )
                                }
                            }
                            else -> {
                                val highlighted = page.index == ttsSpokenPageIndex
                                // Sources that opt into rich formatting populate
                                // `formattedText` with the constrained-HTML subset that
                                // AnnotatedString.fromHtml understands (<i>, <b>, <u>,
                                // <a>, <br>, &nbsp;). Otherwise fall back to plain text.
                                // Cached per page so we don't re-parse HTML on every
                                // recomposition (scroll, TTS highlight, etc.).
                                val rendered = remember(page.formattedText, page.text) {
                                    page.formattedText?.let { AnnotatedString.fromHtml(it) }
                                        ?: AnnotatedString(page.text.trim())
                                }
                                val grimoireMatches = remember(rendered) {
                                    GRIMOIRE_REGEX.findAll(rendered.text).map { it.range }.toList()
                                }
                                val hasMatches = grimoireMatches.isNotEmpty()
                                val displayText = if (grimoireEffectActive && hasMatches) {
                                    buildAnnotatedString {
                                        append(rendered)
                                        grimoireMatches.forEach { r ->
                                            val len = (r.last - r.first + 1).coerceAtLeast(1)
                                            // One SpanStyle per character so each letter can carry a
                                            // different phase of the wave.
                                            for (i in 0 until len) {
                                                val pos = i.toFloat() / len
                                                val color = grimoireWaveColor(grimoireWavePhase, pos)
                                                addStyle(
                                                    SpanStyle(
                                                        color = color,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    r.first + i,
                                                    r.first + i + 1,
                                                )
                                            }
                                            addStringAnnotation(
                                                GRIMOIRE_ANNOTATION_TAG,
                                                rendered.text.substring(r),
                                                r.first,
                                                r.last + 1,
                                            )
                                        }
                                    }
                                } else rendered
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                val tapModifier = if (grimoireEffectActive && hasMatches) {
                                    Modifier.pointerInput(grimoireMatches) {
                                        detectTapGestures { pos ->
                                            val lr = layoutResult
                                            val onWord = lr != null && grimoireMatches.any { range ->
                                                val offset = lr.getOffsetForPosition(pos)
                                                offset in range.first..range.last
                                            }
                                            if (onWord) showGrimoirePopup = true
                                            else barsVisible = !barsVisible
                                        }
                                    }
                                } else Modifier
                                Text(
                                    text = displayText,
                                    style = textStyle,
                                    onTextLayout = { layoutResult = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (highlighted) {
                                                Modifier.background(colors.foreground.copy(alpha = 0.10f))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .then(tapModifier)
                                        .padding(bottom = paragraphSpacing.dp),
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hasNext) {
                                TextButton(onClick = viewModel::navigateNext) {
                                    Text("Next chapter →", color = colors.foreground.copy(alpha = 0.7f))
                                }
                            } else {
                                Text(
                                    "End of book",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.foreground.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
                }
            }
        }

        // Top bar — overlaid, slides in from top. The progress bar slides in with it as a
        // single unit so the layout below the chrome stays consistent.
        AnimatedVisibility(
            visible = barsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background.copy(alpha = 0.95f),
                    titleContentColor = colors.foreground,
                    navigationIconContentColor = colors.foreground,
                    actionIconContentColor = colors.foreground,
                ),
                navigationIcon = {
                    PlainTooltipIconButton(onClick = onNavigateBack, tooltip = "Back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        currentChapter?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    val chapterImageUrls = visiblePages.mapNotNull { it.imageUrl }
                    if (hideInlineImages && chapterImageUrls.isNotEmpty()) {
                        val allRevealed = chapterImageUrls.all { it in revealedImageUrls }
                        PlainTooltipIconButton(
                            onClick = {
                                if (allRevealed) viewModel.hideAllImagesInCurrentChapter()
                                else viewModel.revealAllImagesInCurrentChapter()
                            }, tooltip = if (allRevealed) "Hide all images in this chapter"
                                                     else "Reveal all images in this chapter") {
                            Icon(
                                if (allRevealed) Icons.Outlined.HideImage else Icons.Outlined.Image,
                                contentDescription = if (allRevealed) "Hide all images in this chapter"
                                                     else "Reveal all images in this chapter",
                                tint = colors.foreground,
                            )
                        }
                    }
                    PlainTooltipIconButton(
                        onClick = {
                            viewModel.addBookmark()
                            Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                        },
                        tooltip = "Add bookmark",
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Add bookmark", tint = colors.foreground)
                    }
                    PlainTooltipIconButton(
                        onClick = { showBookmarks = true },
                        tooltip = "Bookmarks",
                    ) {
                        BadgedBox(
                            badge = {
                                if (bookmarkCount > 0) Badge { Text(bookmarkCount.toString()) }
                            },
                        ) {
                            Icon(Icons.Outlined.Bookmarks, contentDescription = "Bookmarks", tint = colors.foreground)
                        }
                    }
                    PlainTooltipIconButton(onClick = { onOpenWebView(viewModel.chapterWebUrl) }, tooltip = "Open in WebView") {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Open in WebView",
                            tint = colors.foreground,
                        )
                    }
                    PlainTooltipIconButton(onClick = viewModel::toggleRead, tooltip = if (currentChapter?.read == true) "Mark as unread" else "Mark as read") {
                        Icon(
                            if (currentChapter?.read == true) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = if (currentChapter?.read == true) "Mark as unread" else "Mark as read",
                            tint = if (currentChapter?.read == true) MaterialTheme.colorScheme.primary
                                   else colors.foreground.copy(alpha = 0.6f),
                        )
                    }
                },
            )
            // Thin progress strip directly under the top bar; hides with it.
            if (showChapterProgressPercent || showNovelProgressPercent) {
                val chapterPct = ((currentChapter?.readProgress ?: 0f) * 100).toInt().coerceIn(0, 100)
                val novelPct = (novelProgress * 100).toInt().coerceIn(0, 100)
                val parts = buildList {
                    if (showChapterProgressPercent) add("Chapter $chapterPct%")
                    if (showNovelProgressPercent) add("Book $novelPct%")
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = parts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.foreground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
            }
        }

        // Bottom bar — overlaid, slides in from bottom
        TooltipBottomBar(
            visible = barsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            containerColor = colors.background.copy(alpha = 0.95f),
            contentColor = colors.foreground,
        ) {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Filled.NavigateBefore,
                label = "Previous",
                onClick = viewModel::navigatePrev,
                enabled = hasPrev,
            )
            TooltipIconButton(
                visible = ttsEnabled,
                icon = if (ttsPlayingThisChapter) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (ttsPlayingThisChapter) "Pause" else "Read aloud",
                onClick = viewModel::toggleTts,
            )
            TooltipIconButton(
                visible = ttsEnabled && ttsActiveForChapter,
                icon = Icons.Default.Stop,
                label = "Stop",
                onClick = viewModel::stopTts,
            )
            TooltipIconButton(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = { showSettings = true },
            )
            TooltipIconButton(
                icon = Icons.AutoMirrored.Filled.NavigateNext,
                label = "Next",
                onClick = viewModel::navigateNext,
                enabled = hasNext,
            )
        }
    }

    if (showGrimoirePopup) {
        GrimoireEasterEggDialog(
            enabled = grimoireEasterEggEnabled,
            onToggle = viewModel::setGrimoireEasterEggEnabled,
            onDismiss = { showGrimoirePopup = false },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            sheetState = sheetState,
            colors = colors,
            textStyle = textStyle,
            fontSize = fontSize,
            lineHeightTimes10 = lineHeightTimes10,
            paragraphSpacing = paragraphSpacing,
            readerFont = readerFont,
            colorTheme = colorTheme,
            orientation = orientation,
            hideInlineImages = hideInlineImages,
            showChapterProgressPercent = showChapterProgressPercent,
            showNovelProgressPercent = showNovelProgressPercent,
            grimoireEasterEggEnabled = grimoireEasterEggEnabled,
            markAsReadStrategy = markAsReadStrategy,
            markAsReadThreshold = markAsReadThreshold,
            markAsReadParagraphsFromEnd = markAsReadParagraphsFromEnd,
            ttsEnabled = ttsEnabled,
            ttsEngine = ttsEngine,
            ttsSpeechRate = ttsSpeechRate,
            ttsPitch = ttsPitch,
            ttsAutoAdvance = ttsAutoAdvance,
            onDismiss = { showSettings = false },
            onFontSize = viewModel::setFontSize,
            onLineHeight = viewModel::setLineHeight,
            onParagraphSpacing = viewModel::setParagraphSpacing,
            onFont = viewModel::setReaderFont,
            onColorTheme = viewModel::setColorTheme,
            onOrientation = viewModel::setOrientation,
            onHideInlineImages = viewModel::setHideInlineImages,
            onShowChapterProgressPercent = viewModel::setShowChapterProgressPercent,
            onShowNovelProgressPercent = viewModel::setShowNovelProgressPercent,
            onGrimoireEasterEggEnabled = viewModel::setGrimoireEasterEggEnabled,
            onMarkAsReadStrategy = viewModel::setMarkAsReadStrategy,
            onMarkAsReadThreshold = viewModel::setMarkAsReadThreshold,
            onMarkAsReadParagraphsFromEnd = viewModel::setMarkAsReadParagraphsFromEnd,
            onTtsEnabled = viewModel::setTtsEnabled,
            onTtsEngine = viewModel::setTtsEngine,
            onTtsSpeechRate = viewModel::setTtsSpeechRate,
            onTtsPitch = viewModel::setTtsPitch,
            onTtsAutoAdvance = viewModel::setTtsAutoAdvance,
            onOpenTtsSettings = {
                showSettings = false
                onOpenTtsSettings()
            },
        )
    }

    if (showBookmarks) {
        ReaderBookmarksSheet(
            bookmarks = bookmarks,
            currentChapterUrl = currentChapter?.url,
            onJump = { bookmark ->
                showBookmarks = false
                viewModel.jumpToBookmark(bookmark)
            },
            onEditNote = viewModel::updateBookmarkNote,
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showBookmarks = false },
        )
    }
}

