package io.grimoire.app.data.libraryupdate

import java.time.Instant
import java.time.ZoneId

/**
 * Milliseconds from [nowMillis] until the next occurrence of the wall-clock
 * time-of-day [preferredMinutes] (minutes since local midnight). If today's
 * occurrence has already passed, the next one is tomorrow.
 *
 * Pulled out as a top-level function so [LibraryUpdateScheduler] stays a thin
 * WorkManager wrapper and the day-rollover edge case can be unit-tested
 * without an Android runtime.
 */
internal fun computeInitialDelayMillis(
    nowMillis: Long,
    preferredMinutes: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val safeMinutes = preferredMinutes.coerceIn(0, 24 * 60 - 1)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val target = now
        .withHour(safeMinutes / 60)
        .withMinute(safeMinutes % 60)
        .withSecond(0)
        .withNano(0)
        .let { if (it.isAfter(now)) it else it.plusDays(1) }
    return java.time.Duration.between(now, target).toMillis()
}
