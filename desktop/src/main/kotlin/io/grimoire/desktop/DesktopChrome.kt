package io.grimoire.desktop

import androidx.compose.animation.ContentTransform
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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Clear
import io.grimoire.ui.icon.Close
import io.grimoire.ui.icon.ErrorOutline
import io.grimoire.ui.icon.Search

/**
 * Desktop counterpart to Android's borderless `AppSearchField`, set inside a soft tonal pill
 * so search sits cleanly inside top bars and toolbars without heavy boxed outlines.
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, maxLines = 1) },
            singleLine = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    AppIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    PlainTooltipIconButton(
                        onClick = { onValueChange("") },
                        tooltip = "Clear search",
                        enabled = enabled,
                    ) {
                        Icon(AppIcons.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
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
