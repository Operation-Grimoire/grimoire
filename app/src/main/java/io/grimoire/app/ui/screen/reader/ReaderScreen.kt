package io.grimoire.app.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.grimoire.app.data.preferences.ReaderColorTheme
import io.grimoire.app.data.preferences.ReaderFont
import kotlinx.coroutines.flow.distinctUntilChanged

private data class ReaderColors(val background: Color, val foreground: Color)

private val ReaderColorTheme.readerColors: ReaderColors
    get() = when (this) {
        ReaderColorTheme.LIGHT -> ReaderColors(Color.White, Color(0xFF1A1A1A))
        ReaderColorTheme.SEPIA -> ReaderColors(Color(0xFFFBF0D9), Color(0xFF4A3728))
        ReaderColorTheme.DARK -> ReaderColors(Color(0xFF1E1E2E), Color(0xFFCDD6F4))
        ReaderColorTheme.BLACK -> ReaderColors(Color.Black, Color(0xFFCCCCCC))
    }

private val ReaderFont.fontFamily: FontFamily
    get() = when (this) {
        ReaderFont.DEFAULT -> FontFamily.Default
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.MONOSPACE -> FontFamily.Monospace
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
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

    val colors = colorTheme.readerColors
    val fontFamily = readerFont.fontFamily
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeightTimes10 / 10f).sp,
        fontFamily = fontFamily,
        color = colors.foreground,
    )

    val listState = rememberLazyListState()
    var barsVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(currentChapter?.url) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 0) return@snapshotFlow 0f
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            (lastVisible + 1).toFloat() / total
        }
            .distinctUntilChanged()
            .collect { fraction -> viewModel.updateProgress(fraction) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Content — inset-aware, tap toggles bars
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
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
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = currentChapter?.name ?: "",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = fontFamily,
                                color = colors.foreground,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(pages, key = { it.index }) { page ->
                        Text(
                            text = page.text,
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
            onDismiss = { showSettings = false },
            onFontSize = viewModel::setFontSize,
            onLineHeight = viewModel::setLineHeight,
            onParagraphSpacing = viewModel::setParagraphSpacing,
            onFont = viewModel::setReaderFont,
            onColorTheme = viewModel::setColorTheme,
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
    onDismiss: () -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Int) -> Unit,
    onParagraphSpacing: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onColorTheme: (ReaderColorTheme) -> Unit,
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
            // Color theme
            SettingsSectionLabel("Color theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderColorTheme.entries.forEach { theme ->
                    val tc = theme.readerColors
                    val selected = theme == colorTheme
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tc.background)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            )
                            .clickable { onColorTheme(theme) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = tc.foreground,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Font family
            SettingsSectionLabel("Font")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFont.entries.forEach { font ->
                    val selected = font == readerFont
                    val label = when (font) {
                        ReaderFont.DEFAULT -> "Sans"
                        ReaderFont.SERIF -> "Serif"
                        ReaderFont.MONOSPACE -> "Mono"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            .clickable { onFont(font) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Aa",
                                fontFamily = font.fontFamily,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = label,
                                fontFamily = font.fontFamily,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // Font size
            StepperRow(
                label = "Font size",
                value = "${fontSize}sp",
                onDecrement = { onFontSize(fontSize - 1) },
                onIncrement = { onFontSize(fontSize + 1) },
                decrementEnabled = fontSize > 12,
                incrementEnabled = fontSize < 32,
            )

            // Line height
            StepperRow(
                label = "Line height",
                value = "%.1f×".format(lineHeightTimes10 / 10f),
                onDecrement = { onLineHeight(lineHeightTimes10 - 1) },
                onIncrement = { onLineHeight(lineHeightTimes10 + 1) },
                decrementEnabled = lineHeightTimes10 > 10,
                incrementEnabled = lineHeightTimes10 < 30,
            )

            // Paragraph spacing
            StepperRow(
                label = "Paragraph spacing",
                value = "${paragraphSpacing}dp",
                onDecrement = { onParagraphSpacing(paragraphSpacing - 4) },
                onIncrement = { onParagraphSpacing(paragraphSpacing + 4) },
                decrementEnabled = paragraphSpacing > 0,
                incrementEnabled = paragraphSpacing < 32,
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (decrementEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = onIncrement,
                enabled = incrementEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (incrementEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
