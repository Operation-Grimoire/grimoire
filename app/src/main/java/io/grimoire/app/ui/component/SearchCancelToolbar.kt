package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(AppIcons.Close, contentDescription = contentDescription)
        }
    }
}
