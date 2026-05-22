package io.grimoire.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Icon button that reveals a short text [label] above the icon while pressed
 * and held: the button widens to fit the label and the icon slides down to
 * make room, reversing on release. In a row, widening pushes siblings aside.
 * A quick tap just invokes [onClick].
 */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)

    val collapsedWidth = 48.dp
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val expandedWidth = remember(label, labelStyle, density) {
        val textWidthPx = textMeasurer.measure(
            text = label,
            style = labelStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width
        (with(density) { textWidthPx.toDp() } + 20.dp).coerceAtLeast(collapsedWidth)
    }

    val width by animateDpAsState(
        targetValue = if (pressed) expandedWidth else collapsedWidth,
        label = "tooltipWidth",
    )
    val iconShift by animateDpAsState(
        targetValue = if (pressed) 8.dp else 0.dp,
        label = "tooltipIconShift",
    )
    val labelProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        label = "tooltipLabelProgress",
    )

    Box(
        modifier = modifier
            .width(width)
            .height(56.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnClick() },
                    onLongPress = { pressed = true },
                    onPress = {
                        tryAwaitRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = tint,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = labelProgress
                    translationY = (1f - labelProgress) * 8.dp.toPx()
                },
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.graphicsLayer { translationY = iconShift.toPx() },
        )
    }
}
