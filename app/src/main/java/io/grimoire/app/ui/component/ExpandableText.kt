package io.grimoire.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Body text that collapses to [collapsedMaxLines] with a "Show more / Show
 * less" toggle. Shared by the novel-detail and NovelUpdates series screens so
 * long descriptions don't dominate the page.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(if (expanded) "Show less" else "Show more")
        }
    }
}
