package io.grimoire.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wraps the platform [HapticFeedback] so all haptics — including the ones
 * Compose triggers internally (e.g. `Modifier.combinedClickable`'s long-press)
 * — are gated on a single user preference.
 */
private class GatedHapticFeedback(
    private val delegate: HapticFeedback,
    private val enabled: Boolean,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (enabled) delegate.performHapticFeedback(hapticFeedbackType)
    }
}

/**
 * Provides a haptics-aware [LocalHapticFeedback] so every consumer in the
 * subtree — both this app's code and Compose internals — respects the
 * user's haptics preference.
 */
@Composable
fun ProvideAppHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val platform = LocalHapticFeedback.current
    val gated = remember(platform, enabled) { GatedHapticFeedback(platform, enabled) }
    CompositionLocalProvider(LocalHapticFeedback provides gated) {
        content()
    }
}
