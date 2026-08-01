package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.grimoire.app.ui.icon.AppIcons
import io.grimoire.app.ui.icon.Close

/**
 * The floating-pill counterpart shown while a search field is open: a single X
 * that cancels the search. Matches the shape/elevation of the screens' regular
 * bottom toolbars so it reads as the same pill swapping contents.
 */
@Composable
fun SearchCancelToolbar(
    onCancel: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // The pill anchors bottom-centre, which the open keyboard covers — lift it
    // just clear of the keyboard so the search can be cancelled without
    // closing the keyboard first. A plain imePadding() overshoots: the pill
    // already sits above the app's bottom nav (and the system bar), and the
    // IME inset is measured from the window bottom. Subtract that resting gap
    // (window bottom − the pill's anchored bottom edge, which stays fixed
    // while the padding grows upward) so the lift is exactly keyboard-height.
    val view = LocalView.current
    val density = LocalDensity.current
    var restingGapPx by remember { mutableIntStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val lift = with(density) { (imeBottomPx - restingGapPx).coerceAtLeast(0).toDp() }
    Surface(
        modifier = modifier
            .onGloballyPositioned { coords ->
                restingGapPx = (view.height - coords.boundsInWindow().bottom).toInt()
            }
            .padding(bottom = lift),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        // Fixed square so the CircleShape renders as a true circle (the row
        // pills' asymmetric padding made this one slightly oval).
        IconButton(onClick = onCancel, modifier = Modifier.size(56.dp)) {
            Icon(AppIcons.Close, contentDescription = contentDescription)
        }
    }
}
