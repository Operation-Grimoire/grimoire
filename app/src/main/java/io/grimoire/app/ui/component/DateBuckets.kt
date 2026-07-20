package io.grimoire.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Midnight (local) of the day containing [timestamp], used to bucket entries by day. */
internal fun dayKey(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** "Today" / "Yesterday" / a medium date for a day header. */
@Composable
internal fun dayLabel(timestamp: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val day = dayKey(timestamp)
    return when (day) {
        today -> stringResource(R.string.common_today)
        today - 24 * 60 * 60 * 1000L -> stringResource(R.string.common_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

/** Short clock time, e.g. "6:41 PM". */
internal fun timeLabel(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
