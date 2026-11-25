package io.grimoire.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.AppIcons
import io.grimoire.ui.icon.Clear
import io.grimoire.ui.icon.Close
import io.grimoire.ui.icon.ErrorOutline
import io.grimoire.ui.icon.Search

/**
 * Desktop counterpart to Android's borderless `AppSearchField`, set inside a soft tonal pill
 * so search sits cleanly inside top bars and toolbars without heavy boxed outlines.
 */
@Composable
internal fun DesktopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, maxLines = 1) },
            singleLine = true,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    AppIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = if (value.isNotEmpty()) {
                {
                    PlainTooltipIconButton(
                        onClick = { onValueChange("") },
                        tooltip = "Clear search",
                        enabled = enabled,
                    ) {
                        Icon(AppIcons.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = placeholder }
                .let { base ->
                    if (onSearch == null) base
                    else base.onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (enter && event.type == KeyEventType.KeyUp) onSearch()
                        enter
                    }
                },
        )
    }
}

/** Centered empty or offline state with an icon, headline, supporting copy, and optional action. */
@Composable
internal fun DesktopEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 380.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAction, enabled = actionEnabled) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Dismissible tonal error banner overlaid at the bottom of the workspace so errors never jump the layout. */
@Composable
internal fun DesktopErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.ErrorOutline, contentDescription = null)
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            PlainTooltipIconButton(onClick = onDismiss, tooltip = "Dismiss") {
                Icon(AppIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
}
