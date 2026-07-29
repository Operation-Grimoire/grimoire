package io.grimoire.app.ui.component

import io.grimoire.app.R
import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Body text that collapses to [collapsedMaxLines] with a "Show more / Show
 * less" toggle. The whole block is tappable so the ripple covers the full
 * description instead of a tiny region around the toggle.
 *
 * [text] is rendered as a synopsis: HTML formatting is parsed and bare URLs are
 * auto-linked (see [rememberSynopsisAnnotatedString]). Taps on a link open it;
 * taps anywhere else toggle the expand state. [onLongClick], when set, fires on a
 * long press (e.g. to edit the synopsis) — combinedClickable keeps the press ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onLongClick: (() -> Unit)? = null,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val rich = rememberSynopsisAnnotatedString(text)
    Column(
        modifier = modifier.combinedClickable(
            onClick = { expanded = !expanded },
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            rich,
            // Content-derived direction: an RTL synopsis (Arabic etc.) reads
            // right-aligned per paragraph without knowing the novel's language.
            style = style.copy(textAlign = TextAlign.Start, textDirection = TextDirection.Content),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.action_show_less else R.string.action_show_more,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
