package io.grimoire.app.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An [IconButton] that reveals a [tooltip] on long-press (and on hover with a
 * pointer). Use for icon-only buttons — top-bar actions, sheet/list-row icons,
 * reader/webview overlays — so their meaning is discoverable without a label.
 *
 * The [tooltip] usually mirrors the icon's `contentDescription`. For the
 * press-and-hold label widget used in the bottom selection/action bars, see the
 * [RowScope.TooltipIconButton] in `TooltipIconButton.kt` instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTooltipIconButton(
    onClick: () -> Unit,
    tooltip: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // enableUserInput stays true even when disabled: a dimmed icon button is
    // exactly where the long-press label helps, and the disabled IconButton
    // doesn't consume the gesture, so the tooltip still surfaces.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}
