package io.grimoire.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.grimoire.app.R
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.KeyboardDoubleArrowUp

/** How many items must scroll past before the jump-to-top button appears. */
private const val SCROLL_TOP_THRESHOLD = 10

/** True once the list is deep enough that jumping back to the top is useful. */
@Composable
fun LazyListState.showScrollToTop(): State<Boolean> =
    remember(this) { derivedStateOf { firstVisibleItemIndex > SCROLL_TOP_THRESHOLD } }

@Composable
fun LazyGridState.showScrollToTop(): State<Boolean> =
    remember(this) { derivedStateOf { firstVisibleItemIndex > SCROLL_TOP_THRESHOLD } }

/**
 * Ready-made wrapper for the common case: owns a [LazyListState], overlays the
 * button bottom-end, and animates back to the top on tap. The [content] lambda
 * must pass the given state to its list.
 */
@Composable
fun ScrollToTopBox(
    modifier: Modifier = Modifier,
    content: @Composable (LazyListState) -> Unit,
) {
    val state = rememberLazyListState()
    val show by state.showScrollToTop()
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxSize()) {
        content(state)
        ScrollToTopButton(
            visible = show,
            onClick = { scope.launch { state.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/**
 * Small floating button that jumps a long list back to the top. Overlay it on
 * the list's Box (bottom-end) and drive [visible] with [showScrollToTop]; the
 * caller owns the actual scroll (usually `state.animateScrollToItem(0)`).
 */
@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(
                AppIcons.KeyboardDoubleArrowUp,
                contentDescription = stringResource(R.string.action_scroll_to_top),
            )
        }
    }
}
