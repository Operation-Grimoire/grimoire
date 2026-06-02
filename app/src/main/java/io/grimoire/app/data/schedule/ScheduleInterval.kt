package io.grimoire.app.data.schedule

/**
 * A periodic schedule interval expressed as a [count] of a time [ScheduleUnit]
 * (e.g. count 3 + [ScheduleUnit.HOURS] = every 3 hours). Shared by the library
 * refresh and backup schedulers so both offer the same count + unit selector.
 */
enum class ScheduleUnit(val hours: Long) {
    HOURS(1),
    DAYS(24),
    WEEKS(168),
}

/** Smallest and largest interval count the schedule UI offers. */
const val SCHEDULE_MIN_COUNT = 1
const val SCHEDULE_MAX_COUNT = 12

/** Periodic interval in hours for [count] of [unit], clamped to the UI bounds. */
fun scheduleIntervalHours(count: Int, unit: ScheduleUnit): Long =
    count.coerceIn(SCHEDULE_MIN_COUNT, SCHEDULE_MAX_COUNT) * unit.hours
