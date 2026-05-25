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
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

@HiltViewModel
class TasksViewModel @Inject constructor(
    @ApplicationContext context: Context,
    chapterDao: ChapterDao,
    private val downloadManager: DownloadManager,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

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
            title = "Updating library",
            detail = when {
                total <= 0 -> "Starting…"
                title.isNullOrBlank() -> "$done / $total novels"
                else -> "$done / $total · $title"
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
                title = "Downloading chapters",
                detail = "$active chapter${if (active == 1) "" else "s"} remaining",
                progress = null,
            )
        }
    }

    val tasks: StateFlow<List<TaskUiState>> =
        combine(librarySyncTask, downloadTask) { sync, download ->
            listOfNotNull(sync, download)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(id: TaskId) {
        when (id) {
            TaskId.LIBRARY_SYNC -> libraryUpdateScheduler.cancelRunning()
            TaskId.DOWNLOADS -> downloadManager.cancelAll()
        }
    }
}
