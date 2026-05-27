package io.grimoire.app.data.libraryupdate

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class LibraryUpdateInitialDelayTest {

    @Test
    fun `later today returns the gap to the target hour`() {
        val now = LocalDateTime.of(2026, 5, 27, 10, 0).atZone(UTC).toInstant().toEpochMilli()
        val delay = computeInitialDelayMillis(now, preferredMinutes = 15 * 60, zoneId = UTC)
        assertEquals(TimeUnit.HOURS.toMillis(5), delay)
    }

    @Test
    fun `earlier today rolls forward to tomorrow`() {
        val now = LocalDateTime.of(2026, 5, 27, 10, 0).atZone(UTC).toInstant().toEpochMilli()
        val delay = computeInitialDelayMillis(now, preferredMinutes = 3 * 60, zoneId = UTC)
        assertEquals(TimeUnit.HOURS.toMillis(17), delay)
    }

    @Test
    fun `target equal to now still rolls to next day`() {
        val now = LocalDateTime.of(2026, 5, 27, 3, 0).atZone(UTC).toInstant().toEpochMilli()
        val delay = computeInitialDelayMillis(now, preferredMinutes = 3 * 60, zoneId = UTC)
        assertEquals(TimeUnit.HOURS.toMillis(24), delay)
    }

    @Test
    fun `minute-precision rolls into next hour`() {
        val now = LocalDateTime.of(2026, 5, 27, 10, 45).atZone(UTC).toInstant().toEpochMilli()
        val delay = computeInitialDelayMillis(now, preferredMinutes = 11 * 60 + 15, zoneId = UTC)
        assertEquals(TimeUnit.MINUTES.toMillis(30), delay)
    }

    @Test
    fun `out-of-range minutes are coerced`() {
        val now = LocalDateTime.of(2026, 5, 27, 10, 0).atZone(UTC).toInstant().toEpochMilli()
        val delay = computeInitialDelayMillis(now, preferredMinutes = -1, zoneId = UTC)
        assertEquals(TimeUnit.HOURS.toMillis(14), delay)
    }

    private companion object {
        val UTC: ZoneId = ZoneOffset.UTC
    }
}
