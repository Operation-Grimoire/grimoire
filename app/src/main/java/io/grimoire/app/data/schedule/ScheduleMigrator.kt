package io.grimoire.app.data.schedule

import io.grimoire.app.data.preferences.BackupPreferences
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.preferences.PreferenceStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time migration from the old fixed-frequency enums (`*_frequency`:
 * OFF / DAILY / EVERY_3_DAYS / WEEKLY) to the new enabled + count + unit model.
 * Without this, an existing install would silently fall back to "off" — a
 * scheduled backup or library sync would stop running until manually re-enabled.
 *
 * Runs once, guarded by [MIGRATED_KEY]. The old enum is read as its stored name
 * string so the removed enum classes don't have to be resurrected.
 */
@Singleton
class ScheduleMigrator @Inject constructor(
    private val store: PreferenceStore,
    private val library: LibraryUpdatePreferences,
    private val backup: BackupPreferences,
) {
    private val migrated = store.getBoolean(MIGRATED_KEY, false)

    suspend fun migrateIfNeeded() {
        if (migrated.changes().first()) return
        migrateOne(
            oldKey = "library_update_frequency",
            setEnabled = { library.enabled.set(it) },
            setCount = { library.intervalCount.set(it) },
            setUnit = { library.intervalUnit.set(it) },
        )
        migrateOne(
            oldKey = "backup_frequency",
            setEnabled = { backup.enabled.set(it) },
            setCount = { backup.intervalCount.set(it) },
            setUnit = { backup.intervalUnit.set(it) },
        )
        migrated.set(true)
    }

    private suspend fun migrateOne(
        oldKey: String,
        setEnabled: suspend (Boolean) -> Unit,
        setCount: suspend (Int) -> Unit,
        setUnit: suspend (ScheduleUnit) -> Unit,
    ) {
        // Empty == the key was never written, so there is nothing to carry over.
        val mapped = mapFrequency(store.getString(oldKey, "").changes().first()) ?: return
        setEnabled(mapped.enabled)
        if (mapped.enabled) {
            setCount(mapped.count)
            setUnit(mapped.unit)
        }
    }

    private fun mapFrequency(name: String): Mapped? = when (name) {
        "OFF" -> Mapped(enabled = false, count = 1, unit = ScheduleUnit.DAYS)
        "DAILY" -> Mapped(enabled = true, count = 1, unit = ScheduleUnit.DAYS)
        "EVERY_3_DAYS" -> Mapped(enabled = true, count = 3, unit = ScheduleUnit.DAYS)
        "WEEKLY" -> Mapped(enabled = true, count = 1, unit = ScheduleUnit.WEEKS)
        else -> null
    }

    private data class Mapped(val enabled: Boolean, val count: Int, val unit: ScheduleUnit)

    private companion object {
        const val MIGRATED_KEY = "schedule_prefs_migrated_v1"
    }
}
