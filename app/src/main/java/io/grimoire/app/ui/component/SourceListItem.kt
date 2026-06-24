package io.grimoire.app.ui.component

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.grimoire.app.util.languageLabel

/**
 * Shared source / extension row used by Browse home and Extensions. Renders the
 * extension icon, name (with an optional pin marker), a supporting slot, and a
 * trailing action slot. Supports tap + long-press (e.g. pin/unpin).
 *
 * When [supporting] is null the row falls back to the humanised language label,
 * matching the simple Browse-home rows; the Extensions screen passes a richer
 * multi-line slot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SourceListItem(
    name: String,
    lang: String,
    packageName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    pinned: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = if (selected) {
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        ListItemDefaults.colors()
    }
    ListItem(
        colors = colors,
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pinned) {
                    Icon(
                        AppIcons.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        supportingContent = supporting ?: { Text(languageLabel(lang)) },
        leadingContent = { ExtensionIcon(packageName, lang, iconUrl) },
        trailingContent = trailing,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}
