package io.grimoire.app.data.libraryupdate

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grimoire.app.GrimoireApp
import io.grimoire.app.MainActivity
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.ui.NAV_TARGET_UPDATES

/**
 * Runs a library refresh in the background. Used for both the periodic schedule
 * and one-off manual runs; the category to refresh is passed via [KEY_CATEGORY_ID]
 * ([ALL_LIBRARY] meaning the whole library).
 *
 * Runs as a foreground worker so the OS does not kill the process mid-run — which
 * would otherwise make WorkManager restart the refresh from the first novel.
 */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryUpdater: LibraryUpdater,
    private val preferences: LibraryUpdatePreferences,
) : CoroutineWorker(applicationContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(buildProgressNotification("Starting…", total = 0, done = 0))

    override suspend fun doWork(): Result {
        val rawCategory = inputData.getLong(KEY_CATEGORY_ID, ALL_LIBRARY)
        val categoryId = rawCategory.takeIf { it != ALL_LIBRARY }

        runCatching { setForeground(getForegroundInfo()) }

        val summary = runCatching {
            libraryUpdater.updateLibrary(categoryId) { done, total, title ->
                setProgress(
                    workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_TITLE to title),
                )
                updateProgress(done, total, title)
            }
        }.getOrElse { e ->
            preferences.lastRunAt.set(System.currentTimeMillis().toString())
            preferences.lastRunSuccess.set(false)
            preferences.lastRunMessage.set(
                "${e::class.simpleName}: ${e.message ?: "(no message)"}",
            )
            return Result.retry()
        }

        preferences.lastRunAt.set(System.currentTimeMillis().toString())
        preferences.lastRunSuccess.set(true)
        preferences.lastRunMessage.set(summaryLine(summary))
        showCompletion(summary)
        return Result.success()
    }

    private fun summaryLine(s: UpdateSummary): String {
        val parts = buildList {
            add("${s.newChapters} new chapter${plural(s.newChapters)}")
            if (s.warnings > 0) add("${s.warnings} warning${plural(s.warnings)}")
            if (s.errors > 0) add("${s.errors} error${plural(s.errors)}")
        }
        return "Checked ${s.novelsChecked} novel${plural(s.novelsChecked)} · ${parts.joinToString(", ")}"
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun tapIntent(navTarget: String? = null): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        navTarget?.hashCode() ?: 0,
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (navTarget != null) putExtra(MainActivity.EXTRA_NAV_TARGET, navTarget)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun foregroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PROGRESS_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIF_ID, notification)
        }

    /** Updates the ongoing foreground notification by re-posting it under the same id. */
    private fun updateProgress(done: Int, total: Int, title: String) {
        if (total <= 0 || done >= total) return
        NotificationManagerCompat.from(applicationContext).notify(
            PROGRESS_NOTIF_ID,
            buildProgressNotification("${done + 1}/$total · $title", total, done),
        )
    }

    private fun buildProgressNotification(text: String, total: Int, done: Int): Notification =
        NotificationCompat.Builder(applicationContext, GrimoireApp.LIBRARY_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Updating library")
            .setContentText(text)
            .setProgress(total.coerceAtLeast(1), done, total <= 0)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun showCompletion(summary: UpdateSummary) {
        val notification = NotificationCompat.Builder(applicationContext, GrimoireApp.LIBRARY_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Library updated")
            .setContentText(summaryLine(summary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryLine(summary)))
            .setContentIntent(tapIntent(NAV_TARGET_UPDATES))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(COMPLETE_NOTIF_ID, notification)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "grimoire-library-update"
        const val ONE_OFF_NAME = "grimoire-library-update-oneoff"
        const val KEY_CATEGORY_ID = "category_id"

        /** Progress keys published via setProgress and read by the Tasks screen. */
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_TITLE = "title"

        /** [KEY_CATEGORY_ID] value meaning "refresh every favorited novel". */
        const val ALL_LIBRARY = Long.MIN_VALUE

        private const val PROGRESS_NOTIF_ID = 1101
        private const val COMPLETE_NOTIF_ID = 1102
    }
}
