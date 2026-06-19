package io.grimoire.app.ui.screen.more

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.LibraryUpdateDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.repo.ExtensionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val chapterDao: ChapterDao,
    libraryUpdateDao: LibraryUpdateDao,
    updateIssueDao: UpdateIssueDao,
    extensionRepository: ExtensionRepository,
    authManager: HiddenCategoriesAuthManager,
    private val novelDao: NovelDao,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val excludeHidden = authManager.isUnlocked.map { !it }.distinctUntilChanged()

    val activeDownloadCount = excludeHidden
        .flatMapLatest { chapterDao.getAllDownloads(it) }
        .map { chapters ->
            chapters.count { it.downloadStatus in ChapterDownloadStatus.IN_FLIGHT_ORDINALS }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Total new chapters logged on the Updates page — drives the More tab row count. */
    val updateCount: StateFlow<Int> = excludeHidden
        .flatMapLatest { libraryUpdateDao.count(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** New chapters from subscribed novels — drives the More tab alert icon. */
    val subscribedUpdateCount: StateFlow<Int> = excludeHidden
        .flatMapLatest { libraryUpdateDao.countSubscribed(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Novels with an unresolved refresh problem — drives the warnings badge. */
    val updateIssueCount: StateFlow<Int> = excludeHidden
        .flatMapLatest { updateIssueDao.count(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Installed extensions with an update available — drives the Browse tab badge. */
    val extensionUpdateCount: StateFlow<Int> = extensionRepository.updateCount

    /**
     * Reader route for the most-recently-read library novel, used by the Library
     * tab's re-tap "continue reading" shortcut. Picks the same chapter the novel
     * detail "Continue" FAB would: the first unread, unlocked chapter, else the
     * last unlocked, else the last chapter. Returns null when nothing has been
     * read yet, or the novel has no openable chapter / installed source.
     */
    suspend fun resolveResumeReadingRoute(): String? {
        val novel = novelDao.getMostRecentlyReadFavorite() ?: return null
        // getChaptersOnce returns chapters ordered by chapterNumber ascending.
        val chapters = chapterDao.getChaptersOnce(novel.id)
        val target = chapters.firstOrNull { !it.read && !it.locked }
            ?: chapters.lastOrNull { !it.locked }
            ?: chapters.lastOrNull()
            ?: return null
        val pkg = if (novel.sourceId == LOCAL_SOURCE_ID) {
            LOCAL_PKG
        } else {
            extensionManager.extensions.value
                .firstOrNull { it.source.id == novel.sourceId }
                ?.info?.packageName ?: return null
        }
        return "reader?pkg=${Uri.encode(pkg)}" +
            "&novelUrl=${Uri.encode(novel.url)}" +
            "&chapterUrl=${Uri.encode(target.url)}"
    }
}
