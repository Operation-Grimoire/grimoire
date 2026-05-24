package io.grimoire.app.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import io.grimoire.app.data.epub.EpubImporter
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.epub.StagedEpub
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val novelDao: NovelDao,
    private val categoryDao: CategoryDao,
    private val chapterDao: ChapterDao,
    private val extensionManager: ExtensionManager,
    private val libraryPreferences: LibraryPreferences,
    private val authManager: HiddenCategoriesAuthManager,
    private val epubImporter: EpubImporter,
    private val downloadManager: DownloadManager,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
) : ViewModel() {

    // True while the picked file is being read/parsed for the preview.
    private val _staging = MutableStateFlow(false)
    val staging: StateFlow<Boolean> = _staging.asStateFlow()

    // True while a confirmed import is being written to the library.
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    // The parsed EPUB awaiting the user's "Add to library" confirmation.
    private val _pendingImport = MutableStateFlow<StagedEpub?>(null)
    val pendingImport: StateFlow<StagedEpub?> = _pendingImport.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    /** Parse the picked EPUB and surface its metadata for confirmation. */
    fun stageEpub(uri: Uri) {
        if (_staging.value || _pendingImport.value != null) return
        _staging.value = true
        viewModelScope.launch {
            epubImporter.stage(uri).fold(
                onSuccess = { _pendingImport.value = it },
                onFailure = { e ->
                    _importMessage.value = "Import failed: ${e.message ?: "unknown error"}"
                },
            )
            _staging.value = false
        }
    }

    /** Discard the staged EPUB without adding it to the library. */
    fun cancelImport() {
        _pendingImport.value = null
    }

    /** Persist the staged EPUB as a favorited, just-read local book. */
    fun confirmImport() {
        val staged = _pendingImport.value ?: return
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val result = epubImporter.commit(staged)
            _importMessage.value = result.fold(
                onSuccess = { "Added \"${it.title}\" to library" },
                onFailure = { e -> "Import failed: ${e.message ?: "unknown error"}" },
            )
            _pendingImport.value = null
            _importing.value = false
        }
    }

    fun consumeImportMessage() {
        _importMessage.value = null
    }

    val isUnlocked: StateFlow<Boolean> = authManager.isUnlocked

    val hasPin: StateFlow<Boolean> = authManager.hasPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricEnabled: StateFlow<Boolean> = authManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // null until the first database emission, so callers can tell "no categories yet"
    // apart from "still loading" — the tab restore must wait for the real list.
    private val allCategories: StateFlow<List<CategoryEntity>?> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categoriesLoaded: StateFlow<Boolean> = allCategories
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val categories: StateFlow<List<CategoryEntity>> =
        combine(allCategories, authManager.isUnlocked) { all, unlocked ->
            val list = all.orEmpty()
            if (unlocked) list else list.filter { !it.isHidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenCategoryIds: StateFlow<Set<Long>> = allCategories
        .map { list -> list.orEmpty().filter { it.isHidden }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val novels: StateFlow<List<NovelEntity>?> = novelDao.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chapterStats: StateFlow<Map<Long, NovelChapterStats>> = chapterDao.getStatsForAll()
        .map { list -> list.associateBy { it.novelId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val displayMode: StateFlow<LibraryDisplayMode> = libraryPreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = libraryPreferences.gridColumns.stateIn(viewModelScope)
    val showAllTab: StateFlow<Boolean> = libraryPreferences.showAllTab.stateIn(viewModelScope)
    val sortField: StateFlow<SortField> = libraryPreferences.sortField.stateIn(viewModelScope)
    val sortDirection: StateFlow<SortDirection> = libraryPreferences.sortDirection.stateIn(viewModelScope)
    val filterStatus: StateFlow<Int> = libraryPreferences.filterStatus.stateIn(viewModelScope)
    val filterUnreadOnly: StateFlow<Boolean> = libraryPreferences.filterUnreadOnly.stateIn(viewModelScope)
    val filterDownloadedOnly: StateFlow<Boolean> = libraryPreferences.filterDownloadedOnly.stateIn(viewModelScope)
    val filterSourceId: StateFlow<Long> = libraryPreferences.filterSourceId.stateIn(viewModelScope)

    /**
     * Source ids that appear in the user's library, paired with their human label
     * (extension's source name, "Local" for imported EPUBs, or a fallback for
     * sources whose extension is no longer installed). Drives the source filter
     * chip row so it only lists sources the user actually has novels from.
     */
    val librarySources: StateFlow<List<Pair<Long, String>>> = combine(
        novelDao.getFavorites(),
        extensionManager.extensions,
    ) { favorites, exts ->
        val sourceIds = favorites.map { it.sourceId }.toSortedSet()
        val nameBySourceId = exts.associate { it.source.id to it.source.name }
        sourceIds.map { id ->
            val name = when {
                id == LOCAL_SOURCE_ID -> "Local"
                else -> nameBySourceId[id] ?: "Source $id"
            }
            id to name
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val includeHiddenInAll: StateFlow<Boolean> = libraryPreferences.includeHiddenInAll.stateIn(viewModelScope)
    val includeLockedInTotals: StateFlow<Boolean> = libraryPreferences.includeLockedInTotals.stateIn(viewModelScope)
    val showReadBadge: StateFlow<Boolean> = libraryPreferences.showReadBadge.stateIn(viewModelScope)
    val showDownloadedBadge: StateFlow<Boolean> = libraryPreferences.showDownloadedBadge.stateIn(viewModelScope)
    val showLockedBadge: StateFlow<Boolean> = libraryPreferences.showLockedBadge.stateIn(viewModelScope)

    // null until the persisted value is read from disk, so the restore can wait for
    // the real id instead of acting on the default and locking in the wrong tab.
    val persistedCategoryId: StateFlow<Long?> = libraryPreferences.selectedCategoryId.changes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSelectedCategoryId(id: Long) = viewModelScope.launch {
        libraryPreferences.selectedCategoryId.set(id)
    }

    fun pkgForNovel(novel: NovelEntity): String =
        if (novel.sourceId == LOCAL_SOURCE_ID) LOCAL_PKG
        else extensionManager.extensions.value
            .firstOrNull { it.source.id == novel.sourceId }
            ?.info?.packageName ?: ""

    fun addCategory(name: String) = viewModelScope.launch {
        categoryDao.upsert(CategoryEntity(name = name.trim(), order = categories.value.size))
    }

    fun renameCategory(category: CategoryEntity, name: String) = viewModelScope.launch {
        categoryDao.upsert(category.copy(name = name.trim()))
    }

    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        novelDao.clearCategory(category.id)
        categoryDao.delete(category)
    }

    fun moveCategory(ordered: List<CategoryEntity>, fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        if (fromIndex == toIndex ||
            fromIndex !in ordered.indices ||
            toIndex !in ordered.indices
        ) return@launch
        val list = ordered.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        list.forEachIndexed { index, cat ->
            if (cat.order != index) categoryDao.upsert(cat.copy(order = index))
        }
    }

    fun moveNovel(novel: NovelEntity, categoryId: Long?) = viewModelScope.launch {
        novelDao.updateCategory(novel.id, categoryId)
    }

    fun removeFromLibrary(novel: NovelEntity) = viewModelScope.launch {
        novelDao.upsert(novel.copy(favorite = false))
    }

    fun moveNovels(ids: Set<Long>, categoryId: Long?) = viewModelScope.launch {
        ids.forEach { novelDao.updateCategory(it, categoryId) }
    }

    fun removeNovelsFromLibrary(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { id ->
            novelDao.getById(id)?.let { novelDao.upsert(it.copy(favorite = false)) }
        }
    }

    fun setNovelsRead(ids: Set<Long>, read: Boolean) = viewModelScope.launch {
        ids.forEach { chapterDao.markAllRead(it, read) }
    }

    fun downloadNovels(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { id ->
            val chapters = chapterDao.getChaptersOnce(id).filter { !it.locked }
            if (chapters.isNotEmpty()) downloadManager.enqueue(chapters)
        }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) = viewModelScope.launch {
        libraryPreferences.displayMode.set(mode)
    }

    fun setGridColumns(count: Int) = viewModelScope.launch {
        libraryPreferences.gridColumns.set(count.coerceIn(2, 5))
    }

    fun setSortField(field: SortField) = viewModelScope.launch {
        libraryPreferences.sortField.set(field)
    }

    /** Flips the persisted sort direction without changing the active sort field. */
    fun toggleSortDirection() = viewModelScope.launch {
        val next = if (sortDirection.value == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
        libraryPreferences.sortDirection.set(next)
    }

    fun setFilterStatus(status: Int) = viewModelScope.launch {
        libraryPreferences.filterStatus.set(status)
    }

    fun setFilterUnreadOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterUnreadOnly.set(value)
    }

    fun setFilterDownloadedOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterDownloadedOnly.set(value)
    }

    fun setFilterSourceId(sourceId: Long) = viewModelScope.launch {
        libraryPreferences.filterSourceId.set(sourceId)
    }

    fun setCategoryHidden(category: CategoryEntity, hidden: Boolean) = viewModelScope.launch {
        categoryDao.upsert(category.copy(isHidden = hidden))
    }

    /** Queues a background refresh of every favorited novel in the library. */
    fun updateLibrary() = libraryUpdateScheduler.triggerOneOff(null)

    /** Queues a background refresh of the favorited novels in [categoryId]. */
    fun updateCategory(categoryId: Long) = libraryUpdateScheduler.triggerOneOff(categoryId)

    fun lock() {
        authManager.lock()
    }

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = authManager.verifyPin(pin)
        if (ok) authManager.unlock()
        return ok
    }

    fun unlockFromBiometric() {
        authManager.unlock()
    }
}
