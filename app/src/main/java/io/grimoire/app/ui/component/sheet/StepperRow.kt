package io.grimoire.app.ui.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R
import io.grimoire.app.ui.component.PlainTooltipIconButton
import io.grimoire.app.ui.icon.*

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
    hint: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlainTooltipIconButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
            tooltip = stringResource(R.string.action_decrease),
        ) {
            Icon(AppIcons.Remove, contentDescription = stringResource(R.string.action_decrease))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        PlainTooltipIconButton(
            onClick = onIncrement,
            enabled = incrementEnabled,
            tooltip = stringResource(R.string.action_increase),
        ) {
            Icon(AppIcons.Add, contentDescription = stringResource(R.string.action_increase))
        }
    }
}
