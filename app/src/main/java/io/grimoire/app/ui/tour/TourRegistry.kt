package io.grimoire.app.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Shared map of [TourKey] → on-screen bounds, in root-composition coordinates
 * (the same space [TourOverlay] positions the balloon in, so the two align
 * regardless of system-bar insets). Provided once near the root via
 * [LocalTourRegistry]; targets register themselves with [Modifier.tourTarget].
 */
class TourRegistry {
    val bounds = mutableStateMapOf<TourKey, Rect>()
}

val LocalTourRegistry = staticCompositionLocalOf<TourRegistry?> { null }

/**
 * Tags this composable as a tour target. Reports its bounds while present and
 * clears them on dispose, so a stale rect from a screen that left the
 * composition never misplaces the balloon. No-op when no registry is provided
 * (e.g. previews), so it's safe to leave on elements unconditionally.
 */
fun Modifier.tourTarget(key: TourKey): Modifier = composed {
    val registry = LocalTourRegistry.current ?: return@composed this
    DisposableEffect(key, registry) {
        onDispose { registry.bounds.remove(key) }
    }
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) registry.bounds[key] = coords.boundsInRoot()
    }
}
