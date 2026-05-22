package io.grimoire.app.data.libraryupdate

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grimoire.app.GrimoireApp
import io.grimoire.app.MainActivity
import io.grimoire.app.data.preferences.LibraryUpdatePreferences

/**
 * Runs a library refresh in the background. Used for both the periodic schedule
 * and one-off manual runs; the category to refresh is passed via [KEY_CATEGORY_ID]
 * ([ALL_LIBRARY] meaning the whole library).
 */
@HiltWorker
class LibraryUpdateWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted params: WorkerParameters,
    private val libraryUpdater: LibraryUpdater,
    private val preferences: LibraryUpdatePreferences,
) : CoroutineWorker(applicationContext, params) {

    override suspend fun doWork(): Result {
        val rawCategory = inputData.getLong(KEY_CATEGORY_ID, ALL_LIBRARY)
        val categoryId = rawCategory.takeIf { it != ALL_LIBRARY }

        val summary = runCatching {
            libraryUpdater.updateLibrary(categoryId) { done, total, title ->
                showProgress(done, total, title)
            }
        }.getOrElse { e ->
            cancelProgress()
            preferences.lastRunAt.set(System.currentTimeMillis().toString())
            preferences.lastRunSuccess.set(false)
            preferences.lastRunMessage.set(
                "${e::class.simpleName}: ${e.message ?: "(no message)"}",
            )
            return Result.retry()
        }

        cancelProgress()
        preferences.lastRunAt.set(System.currentTimeMillis().toString())
        preferences.lastRunSuccess.set(true)
        preferences.lastRunMessage.set(summaryLine(summary))
        if (summary.newChapters > 0 || summary.errors > 0 || summary.warnings > 0) {
            showCompletion(summary)
        }
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

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showProgress(done: Int, total: Int, title: String) {
        if (total <= 0 || done >= total) return
        val notification = NotificationCompat.Builder(applicationContext, GrimoireApp.LIBRARY_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Updating library")
            .setContentText("${done + 1}/$total · $title")
            .setProgress(total, done, false)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(PROGRESS_NOTIF_ID, notification)
    }

    private fun cancelProgress() {
        NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_NOTIF_ID)
    }

    private fun showCompletion(summary: UpdateSummary) {
        val notification = NotificationCompat.Builder(applicationContext, GrimoireApp.LIBRARY_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Library updated")
            .setContentText(summaryLine(summary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryLine(summary)))
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(COMPLETE_NOTIF_ID, notification)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "grimoire-library-update"
        const val ONE_OFF_NAME = "grimoire-library-update-oneoff"
        const val KEY_CATEGORY_ID = "category_id"

        /** [KEY_CATEGORY_ID] value meaning "refresh every favorited novel". */
        const val ALL_LIBRARY = Long.MIN_VALUE

        private const val PROGRESS_NOTIF_ID = 1101
        private const val COMPLETE_NOTIF_ID = 1102
    }
}
