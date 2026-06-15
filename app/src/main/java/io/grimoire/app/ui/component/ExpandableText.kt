package io.grimoire.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Body text that collapses to [collapsedMaxLines] with a "Show more / Show
 * less" toggle. The whole block is tappable so the ripple covers the full
 * description instead of a tiny region around the toggle.
 *
 * [text] is rendered as a synopsis: HTML formatting is parsed and bare URLs are
 * auto-linked (see [rememberSynopsisAnnotatedString]). Taps on a link open it;
 * taps anywhere else toggle the expand state.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val rich = rememberSynopsisAnnotatedString(text)
    Column(
        modifier = modifier.clickable { expanded = !expanded },
    ) {
        Text(
            rich,
            style = style,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
