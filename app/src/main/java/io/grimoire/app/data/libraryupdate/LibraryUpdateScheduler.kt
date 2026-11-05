package io.grimoire.app.data.libraryupdate

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.data.schedule.ScheduleUnit
import io.grimoire.app.data.schedule.computeInitialDelayMillis
import io.grimoire.app.data.schedule.scheduleIntervalHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules periodic and one-off library refreshes via WorkManager. */
@Singleton
class LibraryUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LibraryUpdatePreferences,
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
                interval,
                preferences.onlyOnWifi.changes(),
                preferences.requiresCharging.changes(),
                preferences.preferredTimeOfDayMinutes.changes(),
            ) { enabled, (count, unit), wifi, charging, minutes ->
                Schedule(enabled, count, unit, wifi, charging, minutes)
            }
                .distinctUntilChanged()
                .collectLatest {
                    // The first emission is the stored prefs replayed at process
                    // start — update constraints without moving the live schedule's
                    // next-run anchor. Only a genuine later change
                    // (the user editing a pref) re-anchors via REPLACE.
                    val reAnchor = !isInitial
                    isInitial = false
                    applySchedule(it, reAnchor = reAnchor)
                }
        }
    }

    /** Requests a refresh; an unfinished manual run is kept, including across category scopes. */
    fun triggerOneOff(categoryId: Long?) {
        val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
            .setInputData(categoryData(categoryId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LibraryUpdateWorker.ONE_OFF_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
    }

    /** Cancels an in-progress refresh (manual or scheduled), keeping the periodic schedule. */
    fun cancelRunning() {
        scope.launch {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(LibraryUpdateWorker.ONE_OFF_NAME)
            // Cancelling the periodic work also unschedules it, so re-apply the schedule.
            wm.cancelUniqueWork(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
            applySchedule(
                Schedule(
                    enabled = preferences.enabled.changes().first(),
                    count = preferences.intervalCount.changes().first(),
                    unit = preferences.intervalUnit.changes().first(),
                    onlyOnWifi = preferences.onlyOnWifi.changes().first(),
                    requiresCharging = preferences.requiresCharging.changes().first(),
                    preferredMinutes = preferences.preferredTimeOfDayMinutes.changes().first(),
                ),
                reAnchor = true,
            )
        }
    }

    private fun categoryData(categoryId: Long?): Data = Data.Builder()
        .putLong(LibraryUpdateWorker.KEY_CATEGORY_ID, categoryId ?: LibraryUpdateWorker.ALL_LIBRARY)
        .build()

    private suspend fun applySchedule(s: Schedule, reAnchor: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!s.enabled) {
            wm.cancelUniqueWork(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val intervalHours = scheduleIntervalHours(s.count, s.unit)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(s.requiresCharging)
            .setRequiresBatteryNotLow(true)
            .build()
        // Anchor the first run to the user's preferred time-of-day. WorkManager
        // schedules each subsequent run on the repeat interval from that anchor,
        // so they should land near the same hour barring Doze / constraint
        // delays. We avoid the flex-interval constructor because flex windows
        // sit at the END of each repeat period, which would push the first run
        // ~[repeatInterval] past the requested time.
        // UPDATE retains enqueue time, so also retain the original delay for a
        // schedule whose first run is still pending.
        val existingDelay = if (reAnchor) null else {
            wm.getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
                .first().firstOrNull { !it.state.isFinished }?.initialDelayMillis
        }
        val initialDelay = existingDelay
            ?: computeInitialDelayMillis(System.currentTimeMillis(), s.preferredMinutes)
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
            intervalHours, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(categoryData(null))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // User edits re-anchor atomically. Startup updates retain enqueue time
        // and initial delay while applying new constraints/backoff after upgrades.
        val policy = if (reAnchor) {
            ExistingPeriodicWorkPolicy.REPLACE
        } else {
            ExistingPeriodicWorkPolicy.UPDATE
        }
        wm.enqueueUniquePeriodicWork(
            LibraryUpdateWorker.UNIQUE_PERIODIC_NAME,
            policy,
            request,
        )
    }

    private data class Schedule(
        val enabled: Boolean,
        val count: Int,
        val unit: ScheduleUnit,
        val onlyOnWifi: Boolean,
        val requiresCharging: Boolean,
        val preferredMinutes: Int,
    )
}
