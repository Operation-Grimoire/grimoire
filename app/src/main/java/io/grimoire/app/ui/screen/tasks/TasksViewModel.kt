package io.grimoire.app.ui.screen.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.libraryupdate.LibraryUpdateWorker
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.TaskLogDao
import io.grimoire.app.data.local.entity.TaskLogType
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Identifies a kind of background task; cancellation is routed by this. */
enum class TaskId { LIBRARY_SYNC, DOWNLOADS }

data class TaskUiState(
    val id: TaskId,
    val title: String,
    val detail: String,
    /** 0..1 for determinate progress, or null when indeterminate. */
    val progress: Float?,
)

/** One finished run shown in the history list below the running tasks. */
data class TaskLogUiState(
    val id: Long,
    val kind: TaskId,
    val title: String,
    val summary: String,
    val success: Boolean,
    val completedAt: Long,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    @ApplicationContext context: Context,
    chapterDao: ChapterDao,
    private val taskLogDao: TaskLogDao,
    private val downloadManager: DownloadManager,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val localizedContext = AppLocale.wrap(context)

    private val librarySyncTask = combine(
        workManager.getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.ONE_OFF_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.UNIQUE_PERIODIC_NAME),
    ) { oneOff, periodic ->
        val running = (oneOff + periodic).firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: return@combine null
        val done = running.progress.getInt(LibraryUpdateWorker.KEY_DONE, -1)
        val total = running.progress.getInt(LibraryUpdateWorker.KEY_TOTAL, -1)
        val title = running.progress.getString(LibraryUpdateWorker.KEY_TITLE)
        TaskUiState(
            id = TaskId.LIBRARY_SYNC,
            title = localizedContext.getString(R.string.tasks_updating_library),
            detail = when {
                total <= 0 -> localizedContext.getString(R.string.tasks_starting)
                title.isNullOrBlank() -> localizedContext.resources.getQuantityString(
                    R.plurals.tasks_novels_progress,
                    total,
                    done,
                    total,
                )
                else -> localizedContext.getString(R.string.tasks_novel_progress_title, done, total, title)
            },
            progress = if (total > 0 && done >= 0) {
                (done.toFloat() / total).coerceIn(0f, 1f)
            } else {
                null
            },
        )
    }

    private val downloadTask = authManager.isUnlocked.map { !it }.distinctUntilChanged()
        .flatMapLatest { chapterDao.getAllDownloads(it) }
        .map { chapters ->
        val active = chapters.count { it.downloadStatus in ChapterDownloadStatus.IN_FLIGHT_ORDINALS }
        if (active == 0) {
            null
        } else {
            TaskUiState(
                id = TaskId.DOWNLOADS,
                title = localizedContext.getString(R.string.tasks_downloading_chapters),
                detail = localizedContext.resources.getQuantityString(
                    R.plurals.tasks_chapters_remaining,
                    active,
                    active,
                ),
                progress = null,
            )
        }
    }

    val tasks: StateFlow<List<TaskUiState>> =
        combine(librarySyncTask, downloadTask) { sync, download ->
            listOfNotNull(sync, download)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Aggregate-only rows (counts, never titles), so the log is shown in full
    // regardless of the hidden-category lock state — nothing here needs redacting.
    val history: StateFlow<List<TaskLogUiState>> =
        taskLogDao.getRecent(TaskLogDao.MAX_ENTRIES)
            .map { entries ->
                entries.map { e ->
                    val kind = if (e.type == TaskLogType.DOWNLOAD.ordinal) TaskId.DOWNLOADS
                               else TaskId.LIBRARY_SYNC
                    TaskLogUiState(
                        id = e.id,
                        kind = kind,
                        title = localizedContext.getString(
                            if (kind == TaskId.DOWNLOADS) R.string.tasks_downloads
                            else R.string.tasks_library_sync,
                        ),
                        summary = e.summary,
                        success = e.success,
                        completedAt = e.completedAt,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: TaskId) {
        when (id) {
            TaskId.LIBRARY_SYNC -> libraryUpdateScheduler.cancelRunning()
            TaskId.DOWNLOADS -> downloadManager.cancelAll()
        }
    }

    fun clearHistory() {
        viewModelScope.launch { taskLogDao.clearAll() }
    }
}
