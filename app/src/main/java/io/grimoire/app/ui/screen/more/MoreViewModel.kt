package io.grimoire.app.ui.screen.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    chapterDao: ChapterDao,
    libraryUpdateDao: LibraryUpdateDao,
    updateIssueDao: UpdateIssueDao,
    extensionRepository: ExtensionRepository,
) : ViewModel() {

    val activeDownloadCount = chapterDao.getAllDownloads()
        .map { chapters ->
            chapters.count {
                it.downloadStatus == ChapterDownloadStatus.QUEUED.ordinal ||
                    it.downloadStatus == ChapterDownloadStatus.DOWNLOADING.ordinal
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** New chapters logged on the Updates page — drives the More tab icon and row count. */
    val updateCount: StateFlow<Int> = libraryUpdateDao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Novels with an unresolved refresh problem — drives the warnings badge. */
    val updateIssueCount: StateFlow<Int> = updateIssueDao.count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Installed extensions with an update available — drives the Browse tab badge. */
    val extensionUpdateCount: StateFlow<Int> = extensionRepository.updateCount
}
