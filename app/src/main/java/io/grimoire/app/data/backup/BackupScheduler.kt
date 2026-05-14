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
import io.grimoire.app.data.preferences.BackupFrequency
import io.grimoire.app.data.preferences.BackupPreferences
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
            // Observe changes to relevant preferences and re-schedule accordingly.
            combine(
                preferences.frequency.changes(),
                preferences.backupFolderUri.changes(),
                preferences.onlyOnWifi.changes(),
                preferences.requiresCharging.changes(),
            ) { freq, folder, wifi, charging ->
                Schedule(freq, folder, wifi, charging)
            }
                .distinctUntilChanged()
                .collectLatest { applySchedule(it) }
        }
    }

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

    private fun applySchedule(s: Schedule) {
        val wm = WorkManager.getInstance(context)
        if (s.frequency == BackupFrequency.OFF || s.folderUri.isBlank()) {
            wm.cancelUniqueWork(BackupWorker.UNIQUE_PERIODIC_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.onlyOnWifi) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .setRequiresCharging(s.requiresCharging)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            s.frequency.hours, TimeUnit.HOURS,
        ).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private data class Schedule(
        val frequency: BackupFrequency,
        val folderUri: String,
        val onlyOnWifi: Boolean,
        val requiresCharging: Boolean,
    )
}
