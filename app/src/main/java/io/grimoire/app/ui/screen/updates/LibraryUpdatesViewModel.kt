package io.grimoire.app.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.entity.LibraryUpdateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryUpdatesViewModel @Inject constructor(
    private val libraryUpdateDao: LibraryUpdateDao,
    private val chapterDao: ChapterDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val entries: StateFlow<List<LibraryUpdateEntity>> = libraryUpdateDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearLog() = viewModelScope.launch { libraryUpdateDao.clearAll() }

    /** Removes the selected log rows only; the chapter table is untouched. */
    fun deleteEntries(entryIds: Set<Long>) = viewModelScope.launch {
        if (entryIds.isNotEmpty()) libraryUpdateDao.deleteByIds(entryIds.toList())
    }

    fun markEntriesRead(entryIds: Set<Long>) = viewModelScope.launch {
        val chapterIds = entries.value
            .filter { it.id in entryIds }
            .mapNotNull { chapterDao.getByUrl(it.novelId, it.chapterUrl)?.id }
        if (chapterIds.isNotEmpty()) chapterDao.markChapters(chapterIds, true)
    }

    fun downloadEntries(entryIds: Set<Long>) = viewModelScope.launch {
        val chapters = entries.value
            .filter { it.id in entryIds }
            .mapNotNull { chapterDao.getByUrl(it.novelId, it.chapterUrl) }
        if (chapters.isNotEmpty()) downloadManager.enqueue(chapters)
    }
}
