package io.grimoire.desktop

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Clear
import io.grimoire.ui.icon.Close
import io.grimoire.ui.icon.ErrorOutline
import io.grimoire.ui.icon.Search

/** Height of the floating top header bar; scrolling content starts below this and fades underneath it. */
internal val DESKTOP_HEADER_HEIGHT = 56.dp

/** Window chrome hooks provided by the frameless desktop Window; no-ops in headless UI tests. */
internal data class WindowChrome(
    val dragArea: @Composable (Modifier, @Composable () -> Unit) -> Unit = { modifier, content -> Box(modifier) { content() } },
    val controls: (@Composable () -> Unit)? = null,
)

internal val LocalWindowChrome = staticCompositionLocalOf { WindowChrome() }

/**
 * Floating top header strip with a soft vertical scroll-underneath fade and integrated window controls.
 * Designed to be overlaid at `Alignment.TopCenter` of each screen's root `Box`.
 */
@Composable
internal fun DesktopHeaderBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val chrome = LocalWindowChrome.current
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DESKTOP_HEADER_HEIGHT + 12.dp)
            .background(
                Brush.verticalGradient(
                    0f to bg,
                    0.78f to bg.copy(alpha = 0.94f),
                    1f to Color.Transparent,
                ),
            ),
    ) {
        chrome.dragArea(Modifier.fillMaxWidth().height(DESKTOP_HEADER_HEIGHT)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (navigationIcon != null) 16.dp else 24.dp, end = 12.dp),
            ) {
                navigationIcon?.invoke()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    ProvideTextStyle(
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    ) {
                        title()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions,
                )
                chrome.controls?.let { controls ->
                    Spacer(Modifier.width(4.dp))
                    controls()
                }
            }
        }
    }
}

/** Crisp 34dp circular pill icon button with subtle hairline border and smooth hover fill. */
@Composable
internal fun DesktopIconCircle(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = if (hovered && enabled) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(120),
        label = "iconCircleBg",
    )
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.size(34.dp).hoverable(hoverSource),
    ) {
        PlainTooltipIconButton(onClick = onClick, tooltip = tooltip, enabled = enabled) {
            Icon(icon, contentDescription = tooltip, modifier = Modifier.size(18.dp))
        }
    }
}

/** Minimal frameless window controls (minimize, maximize/restore, close) matching sleek desktop apps. */
@Composable
internal fun DesktopWindowControls(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WindowControlGlyph("Minimize", isClose = false, onClick = onMinimize) { color ->
            val y = size.height / 2f
            drawLine(color, Offset(size.width * 0.25f, y), Offset(size.width * 0.75f, y), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
        }
        WindowControlGlyph(if (maximized) "Restore" else "Maximize", isClose = false, onClick = onToggleMaximize) { color ->
            val pad = size.width * 0.26f
            val side = size.width - pad * 2f
            drawRect(color, topLeft = Offset(pad, pad), size = Size(side, side), style = Stroke(width = 1.3.dp.toPx()))
        }
        WindowControlGlyph("Close", isClose = true, onClick = onClose) { color ->
            val pad = size.width * 0.28f
            val stroke = 1.4.dp.toPx()
            drawLine(color, Offset(pad, pad), Offset(size.width - pad, size.height - pad), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width - pad, pad), Offset(pad, size.height - pad), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun WindowControlGlyph(
    label: String,
    isClose: Boolean,
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            hovered && isClose -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(110),
        label = "winCtrlBg",
    )
    val fg by animateColorAsState(
        targetValue = if (hovered && isClose) MaterialTheme.colorScheme.onError
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(110),
        label = "winCtrlFg",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 36.dp, height = 30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(hoverSource)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(16.dp)) { draw(fg) }
    }
}

/**
 * Compact 38dp desktop pill search input backed by `BasicTextField`, with subtle hairline border,
 * focus accent ring, leading search icon, and clear button.
 */
@Composable
internal fun DesktopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(150),
        label = "searchBorder",
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(38.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
        ) {
            Icon(
                AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder }
                        .let { base ->
                            if (onSearch == null) base
                            else base.onPreviewKeyEvent { event ->
                                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                                if (enter && event.type == KeyEventType.KeyUp) onSearch()
                                enter
                            }
                        },
                )
            }
            if (value.isNotEmpty()) {
                PlainTooltipIconButton(
                    onClick = { onValueChange("") },
                    tooltip = "Clear search",
                    enabled = enabled,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(AppIcons.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Centered empty or offline state with an icon, headline, supporting copy, and optional action. */
@Composable
internal fun DesktopEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 380.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAction, enabled = actionEnabled) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Dismissible tonal error banner overlaid at the bottom of the workspace so errors never jump the layout. */
@Composable
internal fun DesktopErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.ErrorOutline, contentDescription = null)
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            PlainTooltipIconButton(onClick = onDismiss, tooltip = "Dismiss") {
                Icon(AppIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
}

private const val NAV_MS = 300
private const val OUTGOING_MS = 90
private const val INCOMING_MS = NAV_MS - OUTGOING_MS

private fun View.hierarchyDepth(): Int = when (this) {
    View.READER -> 2
    View.BOOK -> 1
    else -> 0
}

/**
 * Material 3 motion matching Android's `NavTransitions.kt`: Shared Axis X for hierarchical
 * drill-in/back and Fade-Through for lateral tab switches.
 */
internal fun desktopNavTransition(from: View, to: View, slideDistancePx: Int): ContentTransform {
    val fromDepth = from.hierarchyDepth()
    val toDepth = to.hierarchyDepth()
    if (fromDepth != toDepth) {
        return sharedAxisX(forward = toDepth > fromDepth, slideDistancePx = slideDistancePx)
    }
    return (fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing), initialScale = 0.96f)) togetherWith
        fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))
}

internal fun sharedAxisX(forward: Boolean, slideDistancePx: Int): ContentTransform {
    val enter = slideInHorizontally(
        animationSpec = tween(NAV_MS, easing = FastOutSlowInEasing),
        initialOffsetX = { if (forward) slideDistancePx else -slideDistancePx },
    ) + fadeIn(tween(INCOMING_MS, delayMillis = OUTGOING_MS, easing = LinearOutSlowInEasing))
    val exit = slideOutHorizontally(
        animationSpec = tween(NAV_MS, easing = FastOutSlowInEasing),
        targetOffsetX = { if (forward) -slideDistancePx else slideDistancePx },
    ) + fadeOut(tween(OUTGOING_MS, easing = FastOutLinearInEasing))
    return enter togetherWith exit
}

/** Subtle GPU-composited scale lift when hovering desktop cover cards. */
internal fun Modifier.desktopCardHover(): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "cardHoverScale",
    )
    this.hoverable(source).graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
