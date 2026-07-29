package io.grimoire.app.data.update

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.grimoire.app.GrimoireApp
import io.grimoire.app.MainActivity
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Downloads an app update APK behind a foreground-service notification so the
 * download keeps running when the user leaves the app. On success it posts a
 * notification that launches the system installer.
 */
@AndroidEntryPoint
class AppUpdateService : Service() {

    @Inject lateinit var checker: AppUpdateChecker
    @Inject lateinit var downloadStore: AppUpdateDownloadStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Notification text follows the in-app language override, like [io.grimoire.app.data.backup.BackupManager]. */
    private val localizedContext by lazy { AppLocale.wrap(this) }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        startForegroundNotification(progressNotification(version, 0L, 0L))

        val apkUrl = intent?.getStringExtra(EXTRA_APK_URL)
        if (apkUrl.isNullOrBlank()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val sha256 = intent?.getStringExtra(EXTRA_SHA256)
        downloadStore.set(DownloadState.Downloading(0L, 0L))

        scope.launch {
            var lastPercent = -1
            checker.download(apkUrl, sha256) { read, total ->
                downloadStore.set(DownloadState.Downloading(read, total))
                val percent = if (total > 0) (read * 100 / total).toInt() else -1
                if (percent != lastPercent) {
                    lastPercent = percent
                    notify(NOTIF_ID, progressNotification(version, read, total))
                }
            }
                .onSuccess { file ->
                    downloadStore.set(DownloadState.Completed(file))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notify(COMPLETE_NOTIF_ID, completionNotification(version, file))
                    stopSelf()
                }
                .onFailure { e ->
                    val message = when (e) {
                        is AppUpdateHashMismatchException ->
                            localizedContext.getString(R.string.app_update_verification_failed)
                        else -> e.message
                            ?: localizedContext.getString(R.string.app_update_download_failed)
                    }
                    downloadStore.set(DownloadState.Error(message))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notify(ERROR_NOTIF_ID, errorNotification(message))
                    stopSelf()
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startForegroundNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(id: Int, notification: Notification) {
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun tapIntent() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progressNotification(version: String, read: Long, total: Long): Notification {
        val title = if (version.isBlank()) {
            localizedContext.getString(R.string.app_update_notification_downloading)
        } else {
            localizedContext.getString(R.string.app_update_notification_downloading_version, version)
        }
        val builder = NotificationCompat.Builder(this, GrimoireApp.APP_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setSilent(true)
        if (total > 0) {
            val percent = (read * 100 / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
            builder.setContentText(
                localizedContext.getString(
                    R.string.app_update_notification_progress,
                    percent,
                    mb(read),
                    mb(total),
                ),
            )
        } else {
            builder.setProgress(0, 0, true)
            builder.setContentText(
                if (read > 0) {
                    localizedContext.getString(R.string.app_update_notification_size, mb(read))
                } else {
                    localizedContext.getString(R.string.app_update_notification_starting)
                },
            )
        }
        return builder.build()
    }

    private fun completionNotification(version: String, file: File): Notification {
        val installIntent = PendingIntent.getActivity(
            this, 0, checker.installIntent(file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (version.isBlank()) {
            localizedContext.getString(R.string.app_update_notification_ready)
        } else {
            localizedContext.getString(R.string.app_update_notification_ready_version, version)
        }
        return NotificationCompat.Builder(this, GrimoireApp.APP_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(localizedContext.getString(R.string.app_update_notification_tap_to_install))
            .setContentIntent(installIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun errorNotification(message: String): Notification =
        NotificationCompat.Builder(this, GrimoireApp.APP_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(localizedContext.getString(R.string.app_update_notification_failed))
            .setContentText(message)
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()

    private fun mb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0)

    companion object {
        private const val NOTIF_ID = 1003
        private const val COMPLETE_NOTIF_ID = 1004
        private const val ERROR_NOTIF_ID = 1005
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_VERSION = "version"

        fun start(context: Context, release: ReleaseInfo) {
            val intent = Intent(context, AppUpdateService::class.java).apply {
                putExtra(EXTRA_APK_URL, release.apkUrl)
                putExtra(EXTRA_SHA256, release.sha256)
                putExtra(EXTRA_VERSION, release.displayVersion)
            }
            context.startForegroundService(intent)
        }
    }
}
