package io.grimoire.app.ui.screen.settings

import io.grimoire.app.ui.icon.*
import androidx.compose.foundation.layout.Arrangement
import io.grimoire.app.ui.component.PlainTooltipIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.grimoire.app.data.schedule.SCHEDULE_MAX_COUNT
import io.grimoire.app.data.schedule.SCHEDULE_MIN_COUNT
import io.grimoire.app.data.schedule.ScheduleUnit
import java.text.DateFormat
import java.util.Calendar
import io.grimoire.app.R

/**
 * Count stepper + Hours/Days/Weeks unit selector. Shared by the library refresh
 * and backup schedule screens so both pick an interval the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntervalSelector(
    count: Int,
    unit: ScheduleUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (ScheduleUnit) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.schedule_every), style = MaterialTheme.typography.bodyLarge)
            PlainTooltipIconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > SCHEDULE_MIN_COUNT, tooltip = stringResource(R.string.action_decrease)) {
                Icon(AppIcons.Remove, contentDescription = stringResource(R.string.action_decrease))
            }
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            PlainTooltipIconButton(
                onClick = { onCountChange(count + 1) },
                enabled = count < SCHEDULE_MAX_COUNT, tooltip = stringResource(R.string.action_increase)) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.action_increase))
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScheduleUnit.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = unit == entry,
                    onClick = { onUnitChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, ScheduleUnit.entries.size),
                ) {
                    Text(entry.label())
                }
            }
        }
    }
}

/** "3 hours" / "hour" — singular noun when [count] is 1. */
@Composable
internal fun intervalSummary(count: Int, unit: ScheduleUnit): String = pluralStringResource(
    when (unit) {
        ScheduleUnit.HOURS -> R.plurals.schedule_hours
        ScheduleUnit.DAYS -> R.plurals.schedule_days
        ScheduleUnit.WEEKS -> R.plurals.schedule_weeks
    },
    count,
    count,
)

/** Segmented-button label, capitalised and pluralised to match [count]. */
@Composable
private fun ScheduleUnit.label(): String = stringResource(
    when (this) {
        ScheduleUnit.HOURS -> R.string.schedule_unit_hour
        ScheduleUnit.DAYS -> R.string.schedule_unit_day
        ScheduleUnit.WEEKS -> R.string.schedule_unit_week
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_time_of_day)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

internal fun formatTimeOfDay(minutesSinceMidnight: Int): String {
    val safe = minutesSinceMidnight.coerceIn(0, 24 * 60 - 1)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, safe / 60)
        set(Calendar.MINUTE, safe % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(cal.time)
}
