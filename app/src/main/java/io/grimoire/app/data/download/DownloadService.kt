package io.grimoire.app.data.download

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.GrimoireApp
import io.grimoire.app.R
import io.grimoire.app.MainActivity
import io.grimoire.app.data.local.dao.TaskLogDao
import io.grimoire.app.data.local.entity.TaskLogEntity
import io.grimoire.app.data.local.entity.TaskLogType
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var taskLogDao: TaskLogDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Notification text follows the in-app language override, like [io.grimoire.app.data.backup.BackupManager]. */
    private val localizedContext by lazy { AppLocale.wrap(this) }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotification(localizedContext.getString(R.string.download_notification_starting))
        scope.launch {
            val result = downloadManager.processQueue { chapterName, remaining ->
                val text = when {
                    chapterName.isBlank() && remaining > 0 ->
                        localizedContext.getString(R.string.download_notification_queued, remaining)
                    chapterName.isBlank() ->
                        localizedContext.getString(R.string.download_notification_working)
                    remaining > 0 -> localizedContext.getString(
                        R.string.download_notification_chapter_queued,
                        chapterName,
                        remaining,
                    )
                    else -> chapterName
                }
                showNotification(text)
            }
            if (result.skipped) return@launch  // already processing in another coroutine — don't stop
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (!downloadManager.isPaused.value && result.downloaded > 0) {
                showCompletionNotification(result.downloaded)
            }
            recordHistory(result.downloaded, result.failed)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Logs a finished batch to the Tasks history; a no-op drain (nothing done) isn't recorded. */
    private suspend fun recordHistory(downloaded: Int, failed: Int) {
        if (downloaded == 0 && failed == 0) return
        val summary = buildString {
            append("$downloaded chapter${if (downloaded == 1) "" else "s"} downloaded")
            if (failed > 0) append(" · $failed failed")
        }
        taskLogDao.record(
            TaskLogEntity(
                type = TaskLogType.DOWNLOAD.ordinal,
                completedAt = System.currentTimeMillis(),
                success = failed == 0,
                summary = summary,
            ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun tapIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showNotification(text: String) {
        val notification = NotificationCompat.Builder(this, GrimoireApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(localizedContext.getString(R.string.download_notification_title))
            .setContentText(text)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showCompletionNotification(count: Int) {
        val notification = NotificationCompat.Builder(this, GrimoireApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(localizedContext.getString(R.string.download_notification_complete_title))
            .setContentText(
                localizedContext.resources.getQuantityString(
                    R.plurals.download_notification_complete_count,
                    count,
                    count,
                ),
            )
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(COMPLETE_NOTIF_ID, notification)
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val COMPLETE_NOTIF_ID = 1002
    }
}
