package io.grimoire.ui.component.sheet

import io.grimoire.ui.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import io.grimoire.ui.component.PlainTooltipIconButton
import io.grimoire.ui.icon.*

/** Numeric setting row: label left, − value + right. One stepper for the whole app. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    enabled: Boolean = true,
    hint: String? = null,
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val hintColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = hintColor,
                )
            }
        }
        PlainTooltipIconButton(
            onClick = onDecrement,
            enabled = enabled && decrementEnabled,
            tooltip = stringResource(Res.string.action_decrease),
        ) {
            Icon(AppIcons.Remove, contentDescription = stringResource(Res.string.action_decrease))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(min = 56.dp, max = 128.dp)
                .padding(horizontal = 4.dp),
        )
        PlainTooltipIconButton(
            onClick = onIncrement,
            enabled = enabled && incrementEnabled,
            tooltip = stringResource(Res.string.action_increase),
        ) {
            Icon(AppIcons.Add, contentDescription = stringResource(Res.string.action_increase))
        }
    }
}
