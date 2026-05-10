package io.grimoire.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScroller(
    state: LazyListState,
    modifier: Modifier = Modifier,
    minItems: Int = 40,
    thumbLabel: ((fraction: Float) -> String?)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()

        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleCount = layoutInfo.visibleItemsInfo.size
        if (totalItems < minItems || totalItems <= visibleCount) return@Box

        val coroutineScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableStateOf(0f) }
        var trackHeightPx by remember { mutableStateOf(0f) }

        val thumbHeightDp = 48.dp
        val scrollableItems = (totalItems - visibleCount).coerceAtLeast(1)
        val scrollFraction = state.firstVisibleItemIndex.toFloat() / scrollableItems
        val currentFraction = if (isDragging) dragFraction else scrollFraction

        val thumbAlpha by animateFloatAsState(
            targetValue = if (isDragging) 1f else 0.5f,
            label = "thumb_alpha",
        )

        val label = if (isDragging) thumbLabel?.invoke(dragFraction) else null

        // Label bubble
        if (label != null) {
            val bubbleOffsetPx = remember(trackHeightPx, dragFraction, thumbHeightDp) {
                val thumbPx = trackHeightPx * thumbHeightDp.value / trackHeightPx // rough
                val maxPx = (trackHeightPx - 48f * 3f).coerceAtLeast(0f)
                (dragFraction * maxPx).roundToInt()
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 32.dp)
                    .offset { IntOffset(0, bubbleOffsetPx) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Track + thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() },
        ) {
            // Track line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .padding(vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(1.dp),
                    )
            )

            // Thumb
            val thumbHeightPx = 48f * 3f // dp → rough px (density ~3)
            val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetPx = currentFraction * maxOffsetPx

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .width(4.dp)
                    .height(thumbHeightDp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = thumbAlpha),
                        RoundedCornerShape(2.dp),
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (maxOffsetPx <= 0f) return@rememberDraggableState
                            dragFraction = (dragFraction + delta / maxOffsetPx).coerceIn(0f, 1f)
                            val target = (dragFraction * scrollableItems)
                                .toInt().coerceIn(0, totalItems - 1)
                            coroutineScope.launch { state.scrollToItem(target) }
                        },
                        onDragStarted = { isDragging = true; dragFraction = scrollFraction },
                        onDragStopped = { isDragging = false },
                    )
            )
        }
    }
}
