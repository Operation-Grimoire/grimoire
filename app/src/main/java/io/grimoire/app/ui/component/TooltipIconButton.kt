package io.grimoire.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Row action button that reveals a short text [label] above the icon while
 * pressed and held: the icon slides down to make room and the button's row
 * weight grows, so it gains space while siblings give way. A quick tap just
 * invokes [onClick]. Must be placed inside a [RowScope] (e.g. an action bar).
 *
 * When the same row toggles which actions apply to the current selection,
 * pass [visible] for the relevant predicate instead of conditionally
 * skipping the call — the button then smoothly shrinks/grows its weight
 * and fades, and siblings reflow to fill the freed space.
 */
@Composable
fun RowScope.TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    // Combine press-grow (1f → 2f) with show/hide (×1f or ×0f). RowScope.weight
    // requires a strictly positive value, so the hidden state floors to a
    // sub-pixel weight that effectively removes the button from the row.
    val weight by animateFloatAsState(
        targetValue = (if (visible) 1f else 0f) * (if (pressed) 2f else 1f),
        label = "tooltipWeight",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "tooltipAlpha",
    )
    val iconShift by animateDpAsState(
        targetValue = if (pressed) 8.dp else 0.dp,
        label = "tooltipIconShift",
    )
    val labelProgress by animateFloatAsState(
        targetValue = if (pressed && visible) 1f else 0f,
        label = "tooltipLabelProgress",
    )

    Box(
        modifier = modifier
            .weight(weight.coerceAtLeast(0.0001f))
            .height(56.dp)
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (visible) {
                    Modifier
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
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            // Visible overflow so the label is never clipped even if the
            // grown button is still narrower than the text.
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    this.alpha = labelProgress
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
