package io.grimoire.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grimoire.app.data.preferences.BackupPreferences
import kotlinx.coroutines.flow.first

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val backupPreferences: BackupPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val folderUriString = backupPreferences.backupFolderUri.changes().first()
        if (folderUriString.isBlank()) {
            backupPreferences.lastAutoBackupSuccess.set(false)
            backupPreferences.lastAutoBackupMessage.set("No backup folder selected")
            backupPreferences.lastAutoBackupAt.set(System.currentTimeMillis().toString())
            return Result.failure()
        }
        val uri = runCatching { Uri.parse(folderUriString) }.getOrNull()
            ?: return Result.failure()

        return when (val result = backupManager.backupTo(uri)) {
            is BackupResult.Success -> {
                backupPreferences.lastAutoBackupAt.set(System.currentTimeMillis().toString())
                backupPreferences.lastAutoBackupFile.set(result.fileName)
                backupPreferences.lastAutoBackupSuccess.set(true)
                backupPreferences.lastAutoBackupMessage.set(
                    "Backed up ${result.novelCount} novels"
                )
                Result.success()
            }
            is BackupResult.Failure -> {
                backupPreferences.lastAutoBackupAt.set(System.currentTimeMillis().toString())
                backupPreferences.lastAutoBackupSuccess.set(false)
                backupPreferences.lastAutoBackupMessage.set(result.message)
                Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "grimoire-auto-backup"
        const val ONE_OFF_NAME = "grimoire-one-off-backup"
    }
}
