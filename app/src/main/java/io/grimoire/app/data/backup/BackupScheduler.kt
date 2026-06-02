package io.grimoire.app.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.preferences.BackupPreferences
import io.grimoire.app.data.schedule.ScheduleUnit
import io.grimoire.app.data.schedule.computeInitialDelayMillis
import io.grimoire.app.data.schedule.scheduleIntervalHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BackupPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun applyPreferredSchedule() {
        scope.launch {
            var isInitial = true
            // Pack count + unit into one flow so the outer combine stays within
            // the typed 5-arg overload (the vararg overload reifies a mixed-type
            // array, which Kotlin warns about).
            val interval = combine(
                preferences.intervalCount.changes(),
                preferences.intervalUnit.changes(),
            ) { count, unit -> count to unit }
            combine(
                preferences.enabled.changes(),
                preferences.backupFolderUri.changes(),
                interval,
                schedulingConstraints(),
                preferences.preferredTimeOfDayMinutes.changes(),
            ) { enabled, folder, (count, unit), constraints, minutes ->
                Schedule(
                    enabled = enabled,
                    folderUri = folder,
                    count = count,
                    unit = unit,
                    onlyOnWifi = constraints.first,
                    requiresCharging = constraints.second,
                    preferredMinutes = minutes,
                )
            }
                .distinctUntilChanged()
                .collectLatest {
                    // The first emission is the stored prefs replayed at process
                    // start — not a real change — so KEEP the live schedule and
                    // its existing next-run anchor. Only a genuine later change
                    // (the user editing a pref) re-anchors via REPLACE.
                    applySchedule(it, reAnchor = !isInitial)
                    isInitial = false
                }
        }
    }

    private fun schedulingConstraints() = combine(
        preferences.onlyOnWifi.changes(),
        preferences.requiresCharging.changes(),
    ) { wifi, charging -> wifi to charging }

    fun triggerOneOffNow() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BackupWorker.ONE_OFF_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.UNIQUE_PERIODIC_NAME)
    }

    private fun applySchedule(s: Schedule, reAnchor: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!s.enabled || s.folderUri.isBlank()) {
            wm.cancelUniqueWork(BackupWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.onlyOnWifi) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .setRequiresCharging(s.requiresCharging)
            .setRequiresStorageNotLow(true)
            .build()
        // Anchor the first run to the user's preferred time-of-day; see
        // LibraryUpdateScheduler for why we avoid the flex-interval constructor.
        val initialDelay = computeInitialDelayMillis(System.currentTimeMillis(), s.preferredMinutes)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            scheduleIntervalHours(s.count, s.unit), TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        // KEEP on the initial application preserves the live schedule and its
        // next-run anchor across app restarts; REPLACE on a genuine change
        // re-anchors so a new interval / time-of-day takes effect. See
        // LibraryUpdateScheduler for the full rationale.
        val policy = if (reAnchor) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        wm.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_PERIODIC_NAME,
            policy,
            request,
        )
    }

    private data class Schedule(
        val enabled: Boolean,
        val folderUri: String,
        val count: Int,
        val unit: ScheduleUnit,
        val onlyOnWifi: Boolean,
        val requiresCharging: Boolean,
        val preferredMinutes: Int,
    )
}
