package io.grimoire.app.data.libraryupdate

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.preferences.LibraryUpdateFrequency
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
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

/** Schedules periodic and one-off library refreshes via WorkManager. */
@Singleton
class LibraryUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LibraryUpdatePreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun applyPreferredSchedule() {
        scope.launch {
            combine(
                preferences.frequency.changes(),
                preferences.onlyOnWifi.changes(),
                preferences.requiresCharging.changes(),
            ) { freq, wifi, charging -> Schedule(freq, wifi, charging) }
                .distinctUntilChanged()
                .collectLatest { applySchedule(it) }
        }
    }

    /** Enqueues an immediate refresh of [categoryId], or the whole library when null. */
    fun triggerOneOff(categoryId: Long?) {
        val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
            .setInputData(categoryData(categoryId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LibraryUpdateWorker.ONE_OFF_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
    }

    private fun categoryData(categoryId: Long?): Data = Data.Builder()
        .putLong(LibraryUpdateWorker.KEY_CATEGORY_ID, categoryId ?: LibraryUpdateWorker.ALL_LIBRARY)
        .build()

    private fun applySchedule(s: Schedule) {
        val wm = WorkManager.getInstance(context)
        if (s.frequency == LibraryUpdateFrequency.OFF) {
            wm.cancelUniqueWork(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(s.requiresCharging)
            .build()
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(
            s.frequency.hours, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInputData(categoryData(null))
            .build()
        wm.enqueueUniquePeriodicWork(
            LibraryUpdateWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private data class Schedule(
        val frequency: LibraryUpdateFrequency,
        val onlyOnWifi: Boolean,
        val requiresCharging: Boolean,
    )
}
