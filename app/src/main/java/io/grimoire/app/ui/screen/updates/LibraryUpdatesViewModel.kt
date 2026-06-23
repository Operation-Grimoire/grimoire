package io.grimoire.app.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryUpdatesViewModel @Inject constructor(
    private val libraryUpdateDao: LibraryUpdateDao,
    private val chapterDao: ChapterDao,
    private val downloadManager: DownloadManager,
    novelDao: NovelDao,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    private val excludeHidden = authManager.isUnlocked.map { !it }.distinctUntilChanged()

    val entries: StateFlow<List<LibraryUpdateEntity>> = excludeHidden
        .flatMapLatest { libraryUpdateDao.getAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Novel ids the user has opted in to notifications for; surfaced as a separate section above the rest. */
    val subscribedNovelIds: StateFlow<Set<Long>> = excludeHidden
        .flatMapLatest { novelDao.getSubscribedNovelIds(it).map { ids -> ids.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Live chapter state keyed by log entry id. The log denormalizes some fields
     * at refresh time, but lock/download status changes over time, so the UI
     * resolves them against the chapter table for an up-to-date display.
     */
    val chaptersByEntryId: StateFlow<Map<Long, ChapterEntity>> = entries
        .flatMapLatest { rows ->
            if (rows.isEmpty()) flowOf(emptyMap())
            // Resolve live chapter state against only the chapters the log
            // references (join in getChaptersForLogEntries), not every chapter of
            // every logged novel — the latter loaded most of the library's chapter
            // metadata into memory and OOM'd the app on the Updates page during a
            // sync (the chapters table churns as each novel is refreshed).
            else chapterDao.getChaptersForLogEntries()
                .map { chapters ->
                    val byKey = chapters.associateBy { it.novelId to it.url }
                    rows.mapNotNull { row ->
                        byKey[row.novelId to row.chapterUrl]?.let { row.id to it }
                    }.toMap()
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun clearLog() = viewModelScope.launch { libraryUpdateDao.clearAll() }

    /** Removes the selected log rows only; the chapter table is untouched. */
    fun deleteEntries(entryIds: Set<Long>) = viewModelScope.launch {
        if (entryIds.isNotEmpty()) libraryUpdateDao.deleteByIds(entryIds.toList())
    }

    fun setEntriesRead(entryIds: Set<Long>, read: Boolean) = viewModelScope.launch {
        val chapterIds = entryIds.mapNotNull { chaptersByEntryId.value[it]?.id }
        if (chapterIds.isNotEmpty()) chapterDao.markChapters(chapterIds, read)
    }

    fun downloadEntries(entryIds: Set<Long>) {
        val chapters = entryIds.mapNotNull { chaptersByEntryId.value[it] }
        if (chapters.isNotEmpty()) downloadManager.enqueue(chapters)
    }

    fun cancelDownloadEntries(entryIds: Set<Long>) {
        val chapters = entryIds.mapNotNull { chaptersByEntryId.value[it] }
        if (chapters.isNotEmpty()) downloadManager.cancelDownloads(chapters)
    }

    fun deleteDownloadEntries(entryIds: Set<Long>) {
        val chapters = entryIds.mapNotNull { chaptersByEntryId.value[it] }
        if (chapters.isNotEmpty()) downloadManager.deleteDownloads(chapters)
    }

    fun downloadChapter(chapter: ChapterEntity) = downloadManager.enqueue(listOf(chapter))
    fun cancelDownload(chapter: ChapterEntity) = downloadManager.cancel(chapter)
    fun deleteDownload(chapter: ChapterEntity) = downloadManager.deleteDownload(chapter)
    fun redownloadChapter(chapter: ChapterEntity) =
        downloadManager.enqueue(listOf(chapter), force = true)
}
