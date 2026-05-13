package io.grimoire.app.ui.screen.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import io.grimoire.app.data.preferences.ReaderOrientation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    onOpenWebView: (url: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()
    val hasPrev by viewModel.hasPrev.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()

    val fontSize by viewModel.fontSize.collectAsState()
    val lineHeightTimes10 by viewModel.lineHeightTimes10.collectAsState()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsState()
    val readerFont by viewModel.readerFont.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val hideNotificationBar by viewModel.hideNotificationBar.collectAsState()

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
    var barsVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var restoredScrollUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentChapter?.url) {
        restoredScrollUrl = null
        listState.scrollToItem(0)
    }

    LaunchedEffect(currentChapter?.url, isLoading) {
        if (isLoading) return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val chapter = currentChapter ?: return@LaunchedEffect
        if (restoredScrollUrl == chapter.url) return@LaunchedEffect
        restoredScrollUrl = chapter.url
        val progress = chapter.readProgress
        if (progress <= 0f || chapter.read) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it > 0 }
        val total = listState.layoutInfo.totalItemsCount
        val targetIndex = (progress * total).toInt().coerceIn(0, total - 1)
        listState.animateScrollToItem(targetIndex)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@snapshotFlow 0f
            val first = info.visibleItemsInfo.firstOrNull() ?: return@snapshotFlow 0f
            first.index.toFloat() / total
        }
            .distinctUntilChanged()
            .collect { fraction -> viewModel.updateProgress(fraction) }
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
                    items(pages.filter { it.text.isNotBlank() }, key = { it.index }) { page ->
                        Text(
                            text = page.text.trim(),
                            style = textStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = paragraphSpacing.dp),
                        )
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

        // Top bar — overlaid, slides in from top
        AnimatedVisibility(
            visible = barsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background.copy(alpha = 0.95f),
                    titleContentColor = colors.foreground,
                    navigationIconContentColor = colors.foreground,
                    actionIconContentColor = colors.foreground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                    IconButton(onClick = { onOpenWebView(viewModel.chapterWebUrl) }) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Open in WebView",
                            tint = colors.foreground,
                        )
                    }
                    IconButton(onClick = viewModel::toggleRead) {
                        Icon(
                            if (currentChapter?.read == true) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = if (currentChapter?.read == true) "Mark as unread" else "Mark as read",
                            tint = if (currentChapter?.read == true) MaterialTheme.colorScheme.primary
                                   else colors.foreground.copy(alpha = 0.6f),
                        )
                    }
                },
            )
        }

        // Bottom bar — overlaid, slides in from bottom
        AnimatedVisibility(
            visible = barsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomAppBar(
                containerColor = colors.background.copy(alpha = 0.95f),
                contentColor = colors.foreground,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = viewModel::navigatePrev, enabled = hasPrev) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous chapter",
                            tint = if (hasPrev) colors.foreground else colors.foreground.copy(alpha = 0.3f),
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Reader settings", tint = colors.foreground)
                    }
                    IconButton(onClick = viewModel::navigateNext, enabled = hasNext) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next chapter",
                            tint = if (hasNext) colors.foreground else colors.foreground.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
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
            onDismiss = { showSettings = false },
            onFontSize = viewModel::setFontSize,
            onLineHeight = viewModel::setLineHeight,
            onParagraphSpacing = viewModel::setParagraphSpacing,
            onFont = viewModel::setReaderFont,
            onColorTheme = viewModel::setColorTheme,
            onOrientation = viewModel::setOrientation,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    sheetState: androidx.compose.material3.SheetState,
    colors: ReaderColors,
    textStyle: TextStyle,
    fontSize: Int,
    lineHeightTimes10: Int,
    paragraphSpacing: Int,
    readerFont: ReaderFont,
    colorTheme: ReaderColorTheme,
    orientation: ReaderOrientation,
    onDismiss: () -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Int) -> Unit,
    onParagraphSpacing: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onColorTheme: (ReaderColorTheme) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
    ) {
        // Live preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "The quick brown fox jumps over the lazy dog. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.",
                style = textStyle,
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionLabel("Color theme")
            ColorThemePicker(selected = colorTheme, onSelect = onColorTheme)

            SettingsSectionLabel("Font")
            FontPicker(selected = readerFont, onSelect = onFont)

            StepperRow(
                label = "Font size",
                value = "${fontSize}sp",
                onDecrement = { onFontSize(fontSize - 1) },
                onIncrement = { onFontSize(fontSize + 1) },
                decrementEnabled = fontSize > 12,
                incrementEnabled = fontSize < 32,
            )

            StepperRow(
                label = "Line height",
                value = "%.1f×".format(lineHeightTimes10 / 10f),
                onDecrement = { onLineHeight(lineHeightTimes10 - 1) },
                onIncrement = { onLineHeight(lineHeightTimes10 + 1) },
                decrementEnabled = lineHeightTimes10 > 10,
                incrementEnabled = lineHeightTimes10 < 30,
            )

            StepperRow(
                label = "Paragraph spacing",
                value = "${paragraphSpacing}dp",
                onDecrement = { onParagraphSpacing(paragraphSpacing - 4) },
                onIncrement = { onParagraphSpacing(paragraphSpacing + 4) },
                decrementEnabled = paragraphSpacing > 0,
                incrementEnabled = paragraphSpacing < 32,
            )

            SettingsSectionLabel("Screen rotation")
            OrientationPicker(selected = orientation, onSelect = onOrientation)
        }
    }
}

@Composable
private fun ReaderScrollbar(
    listState: LazyListState,
    colors: ReaderColors,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val scrollFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@derivedStateOf 0f
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0f
            if (last.index >= total - 1) return@derivedStateOf 1f
            val scrollRange = (total - info.visibleItemsInfo.size).coerceAtLeast(1)
            (first.index.toFloat() / scrollRange).coerceIn(0f, 1f)
        }
    }

    val readFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@derivedStateOf 0f
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0f
            ((last.index + 1).toFloat() / total).coerceIn(0f, 1f)
        }
    }

    val thumbSizeDp = 20.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    Box(
        modifier = modifier
            .width(52.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    fun scrollTo(y: Float) {
                        val effective = (trackHeightPx - thumbSizePx).coerceAtLeast(1f)
                        val f = ((y - thumbSizePx / 2) / effective).coerceIn(0f, 1f)
                        val total = listState.layoutInfo.totalItemsCount
                        if (total > 0) coroutineScope.launch {
                            listState.scrollToItem((f * total).toInt().coerceIn(0, total - 1))
                        }
                    }
                    scrollTo(down.position.y)
                    drag(down.id) { change ->
                        change.consume()
                        scrollTo(change.position.y)
                    }
                    isDragging = false
                }
            },
    ) {
        val effectiveRangePx = (trackHeightPx - thumbSizePx).coerceAtLeast(0f)
        val thumbOffsetDp = with(density) { (scrollFraction * effectiveRangePx).toDp() }
        val filledHeightDp = thumbOffsetDp + thumbSizeDp / 2
        val percentage = (readFraction * 100).toInt().coerceIn(0, 100)

        // Unfilled track
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 15.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.foreground.copy(alpha = 0.2f), RoundedCornerShape(1.dp)),
        )

        // Filled track (top → thumb centre)
        if (filledHeightDp > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 15.dp)
                    .width(2.dp)
                    .height(filledHeightDp)
                    .background(colors.foreground.copy(alpha = 0.7f), RoundedCornerShape(1.dp)),
            )
        }

        // Thumb circle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 5.dp)
                .offset(y = thumbOffsetDp)
                .size(thumbSizeDp)
                .background(
                    colors.foreground.copy(alpha = if (isDragging) 1f else 0.85f),
                    CircleShape,
                ),
        )

        // Percentage bubble — vertically centred on thumb, to the left
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 28.dp)
                .offset(y = thumbOffsetDp)
                .height(thumbSizeDp)
                .wrapContentHeight(Alignment.CenterVertically)
                .background(colors.background.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
