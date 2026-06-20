package io.grimoire.app.ui.tour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Full-window overlay that hosts the tour balloon above the app. Deliberately
 * has no scrim and no touch handling of its own, so the app underneath stays
 * usable (interactive steps rely on the user actually tapping through) — only
 * the balloon's own surface intercepts touches.
 *
 * Coordinates: this box fills the composition root and reads target bounds in
 * the same root space (see [TourRegistry]), so the balloon lines up with its
 * target regardless of system-bar insets.
 */
@Composable
fun TourOverlay(
    state: TourState,
    steps: List<TourStep>,
    registry: TourRegistry,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAction: (TourActionId) -> Unit,
) {
    if (!state.running) return
    val step = steps.getOrNull(state.index) ?: return

    var container by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        if (container != IntSize.Zero) {
            val target = step.target?.let { registry.bounds[it] }
            TourBalloon(
                step = step,
                index = state.index,
                stepCount = steps.size,
                target = target,
                container = container,
                onBack = onBack,
                onNext = onNext,
                onSkip = onSkip,
                onAction = onAction,
            )
        }
    }
}
