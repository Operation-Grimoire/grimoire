package io.grimoire.app.data.libraryupdate

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
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
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryUpdatePreferences
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.first

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
    private val extensionManager: ExtensionManager,
    private val categoryDao: CategoryDao,
    private val authManager: HiddenCategoriesAuthManager,
    private val downloadManager: DownloadManager,
) : CoroutineWorker(applicationContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(buildProgressNotification("Starting…", total = 0, done = 0))

    override suspend fun doWork(): Result {
        val rawCategory = inputData.getLong(KEY_CATEGORY_ID, ALL_LIBRARY)
        val categoryId = rawCategory.takeIf { it != ALL_LIBRARY }

        runCatching { setForeground(getForegroundInfo()) }

        val summary = runCatching {
            libraryUpdater.updateLibrary(
                categoryId,
                onProgress = { done, total, title ->
                    setProgress(
                        workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_TITLE to title),
                    )
                    updateProgress(done, total, title)
                },
                onNovelComplete = { novel, newReadable, newLocked ->
                    maybeNotifyNovel(novel, newReadable, newLocked)
                },
            )
        }.getOrElse { e ->
            preferences.lastRunAt.set(System.currentTimeMillis().toString())
            preferences.lastRunSuccess.set(false)
            preferences.lastRunMessage.set(
                "${e::class.simpleName}: ${e.message ?: "(no message)"}",
            )
            return Result.retry()
        }

        // Auto-download queues chapters via DownloadManager.enqueue, but the
        // startForegroundService() call there is a no-op for background callers
        // on Android 12+. Drain the queue inline from this foreground worker
        // so the chapters actually download in the same run.
        if (summary.newChapters > 0 && preferences.autoDownloadNewChapters.changes().first()) {
            runCatching {
                downloadManager.processQueue { chapterName, remaining ->
                    updateForegroundText(downloadingText(chapterName, remaining))
                }
            }
        }

        preferences.lastRunAt.set(System.currentTimeMillis().toString())
        preferences.lastRunSuccess.set(true)
        preferences.lastRunMessage.set(summaryLine(summary))
        return Result.success()
    }

    private fun downloadingText(chapterName: String, remaining: Int): String = when {
        chapterName.isBlank() && remaining > 0 -> "Downloading · +$remaining queued"
        chapterName.isBlank() -> "Downloading…"
        remaining > 0 -> "Downloading $chapterName · +$remaining queued"
        else -> "Downloading $chapterName"
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
        val text = if (title.isBlank()) "${done + 1}/$total" else "${done + 1}/$total · $title"
        NotificationManagerCompat.from(applicationContext).notify(
            PROGRESS_NOTIF_ID,
            buildProgressNotification(text, total, done),
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

    /** Re-posts the ongoing notification with new body text and an indeterminate bar. */
    private fun updateForegroundText(text: String) {
        NotificationManagerCompat.from(applicationContext).notify(
            PROGRESS_NOTIF_ID,
            buildProgressNotification(text, total = 0, done = 0),
        )
    }

    private suspend fun maybeNotifyNovel(novel: NovelEntity, newReadable: Int, newLocked: Int) {
        val readablePart = if (novel.notifyOnNewChapters) newReadable else 0
        val lockedPart = if (novel.notifyOnNewLockedChapters) newLocked else 0
        if (readablePart == 0 && lockedPart == 0) return
        // Same race as the updater: the eager scan may not be done yet on a
        // fresh background run, so the notification could lose its deep-link.
        extensionManager.awaitReady()

        // Hidden-category novels do not surface a notification when the app is locked.
        // Novels with categoryId == null are in the default category and never hidden.
        if (!authManager.isUnlocked.value && novel.categoryId != null) {
            val hidden = categoryDao.getAllOnce()
                .firstOrNull { it.id == novel.categoryId }?.isHidden == true
            if (hidden) return
        }

        val body = when {
            readablePart > 0 && lockedPart > 0 ->
                "${readablePart} new chapter${plural(readablePart)} · ${lockedPart} locked"
            readablePart > 0 ->
                "${readablePart} new chapter${plural(readablePart)}"
            else ->
                "${lockedPart} new locked chapter${plural(lockedPart)}"
        }

        val pkg = extensionManager.extensions.value
            .firstOrNull { it.source.id == novel.sourceId }
            ?.info?.packageName
        val target = if (pkg != null) {
            "novel?pkg=${Uri.encode(pkg)}&url=${Uri.encode(novel.url)}"
        } else null

        val notification = NotificationCompat.Builder(applicationContext, GrimoireApp.LIBRARY_UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(novel.title)
            .setContentText(body)
            .setContentIntent(tapIntent(target))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(NOVEL_NOTIF_ID_BASE + novel.id.toInt(), notification)
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

        /** Base offset for per-novel notification ids; added to novel.id so each
         *  novel gets a stable, distinct slot a follow-up sync can replace. */
        private const val NOVEL_NOTIF_ID_BASE = 2_000_000
    }
}
