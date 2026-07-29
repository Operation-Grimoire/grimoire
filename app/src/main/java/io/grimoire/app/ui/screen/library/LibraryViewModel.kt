package io.grimoire.app.ui.screen.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import io.grimoire.api.source.epub.EpubSource
import io.grimoire.app.data.epub.EpubImporter
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.epub.StagedEpub
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.libraryupdate.LibraryUpdateScheduler
import io.grimoire.app.data.local.LibraryFavorites
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.NovelChapterStats
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.preferences.LibraryDisplayMode
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.NovelTypeFilter
import io.grimoire.app.data.preferences.SortDirection
import io.grimoire.app.data.preferences.SortField
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_SEARCH_QUERY = "library_search_query"

internal sealed interface LibraryImportMessage {
    data class Added(val title: String) : LibraryImportMessage
    data class Failed(val detail: String?) : LibraryImportMessage
}

internal data class LibrarySourceOption(
    val id: Long,
    val name: String?,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val novelDao: NovelDao,
    private val libraryFavorites: LibraryFavorites,
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

    private val _importMessage = MutableStateFlow<LibraryImportMessage?>(null)
    internal val importMessage: StateFlow<LibraryImportMessage?> = _importMessage.asStateFlow()

    /** Parse the picked EPUB and surface its metadata for confirmation. */
    fun stageEpub(uri: Uri) {
        if (_staging.value || _pendingImport.value != null) return
        _staging.value = true
        viewModelScope.launch {
            epubImporter.stage(uri).fold(
                onSuccess = { _pendingImport.value = it },
                onFailure = { e ->
                    _importMessage.value = LibraryImportMessage.Failed(e.message)
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
                onSuccess = { LibraryImportMessage.Added(it.title) },
                onFailure = { e -> LibraryImportMessage.Failed(e.message) },
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

    /** True when an unlock prompt would be useful: locked, a PIN is set, and at least one category is hidden. */
    val canUnlockHidden: StateFlow<Boolean> = combine(
        authManager.isUnlocked,
        authManager.hasPin,
        hiddenCategoryIds,
    ) { unlocked, hasPin, hidden -> !unlocked && hasPin && hidden.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Shared, app-scoped favorites flow (one Room observer for the whole process, also
    // used by CoverPreloader) — already null-until-loaded, so no extra stateIn here.
    val novels: StateFlow<List<NovelEntity>?> = libraryFavorites.favorites

    // null until the first database emission. The list renders as soon as `novels` is
    // ready; stat-derived badges (unread / downloaded / locked) and the unread/downloaded
    // filters fill in a frame later when this emits, rather than gating the whole screen
    // behind the chapter-stats aggregation. Scoped to favorites so the GROUP BY cost
    // tracks the library, not every browsed chapter.
    private val chapterStatsOrNull: StateFlow<Map<Long, NovelChapterStats>?> = chapterDao.getFavoriteStats()
        .map { list -> list.associateBy { it.novelId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chapterStats: StateFlow<Map<Long, NovelChapterStats>> = chapterStatsOrNull
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val displayMode: StateFlow<LibraryDisplayMode> = libraryPreferences.displayMode.stateIn(viewModelScope)
    val gridColumns: StateFlow<Int> = libraryPreferences.gridColumns.stateIn(viewModelScope)
    val showAllTab: StateFlow<Boolean> = libraryPreferences.showAllTab.stateIn(viewModelScope)
    val sortField: StateFlow<SortField> = libraryPreferences.sortField.stateIn(viewModelScope)
    val sortDirection: StateFlow<SortDirection> = libraryPreferences.sortDirection.stateIn(viewModelScope)
    val filterStatuses: StateFlow<Set<Int>> = libraryPreferences.filterStatuses.stateIn(viewModelScope)
    val filterStatusesExclude: StateFlow<Set<Int>> =
        libraryPreferences.filterStatusesExclude.stateIn(viewModelScope)
    val filterUnreadOnly: StateFlow<Boolean> = libraryPreferences.filterUnreadOnly.stateIn(viewModelScope)
    val filterDownloadedOnly: StateFlow<Boolean> = libraryPreferences.filterDownloadedOnly.stateIn(viewModelScope)
    val filterNotifyEnabled: StateFlow<Boolean> = libraryPreferences.filterNotifyEnabled.stateIn(viewModelScope)
    val filterAutoDownloadEnabled: StateFlow<Boolean> = libraryPreferences.filterAutoDownloadEnabled.stateIn(viewModelScope)
    val filterMinUserRating: StateFlow<Int> = libraryPreferences.filterMinUserRating.stateIn(viewModelScope)
    val filterMaxUserRating: StateFlow<Int> = libraryPreferences.filterMaxUserRating.stateIn(viewModelScope)
    val filterType: StateFlow<NovelTypeFilter> = libraryPreferences.filterType.stateIn(viewModelScope)
    val filterSourceIds: StateFlow<Set<Long>> = libraryPreferences.filterSourceIds.stateIn(viewModelScope)

    /**
     * Source ids that appear in the user's library, paired with their human label
     * (extension's source name, "Local" for imported EPUBs, or a fallback for
     * sources whose extension is no longer installed). Drives the source filter
     * chip row so it only lists sources the user actually has novels from.
     */
    internal val librarySources: StateFlow<List<LibrarySourceOption>> = combine(
        libraryFavorites.favorites,
        extensionManager.extensions,
    ) { favoritesOrNull, exts ->
        val favorites = favoritesOrNull.orEmpty()
        val sourceIds = favorites.map { it.sourceId }.toSortedSet()
        val nameBySourceId = exts.associate { it.id to it.source.name }
        sourceIds.map { id ->
            LibrarySourceOption(id = id, name = nameBySourceId[id])
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Source ids whose installed extension delivers whole-book EPUBs (e.g.
     * Z-Library, libgen). Combined with [LOCAL_SOURCE_ID] in [NovelEntity.isEpubType],
     * this lets the EPUB badge and the Type filter treat extension-backed EPUBs the
     * same as local file imports. Empty until the extension scan completes.
     */
    val epubSourceIds: StateFlow<Set<Long>> = extensionManager.extensions
        .map { exts -> exts.filter { it.source is EpubSource }.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val includeHiddenInAll: StateFlow<Boolean> = libraryPreferences.includeHiddenInAll.stateIn(viewModelScope)
    val includeLockedInTotals: StateFlow<Boolean> = libraryPreferences.includeLockedInTotals.stateIn(viewModelScope)
    val showReadBadge: StateFlow<Boolean> = libraryPreferences.showReadBadge.stateIn(viewModelScope)
    val showDownloadedBadge: StateFlow<Boolean> = libraryPreferences.showDownloadedBadge.stateIn(viewModelScope)
    val showLockedBadge: StateFlow<Boolean> = libraryPreferences.showLockedBadge.stateIn(viewModelScope)
    val showRatingBadge: StateFlow<Boolean> = libraryPreferences.showRatingBadge.stateIn(viewModelScope)
    val showEpubBadge: StateFlow<Boolean> = libraryPreferences.showEpubBadge.stateIn(viewModelScope)

    // null until the persisted value is read from disk, so the restore can wait for
    // the real id instead of acting on the default and locking in the wrong tab.
    val persistedCategoryId: StateFlow<Long?> = libraryPreferences.selectedCategoryId.changes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Backed by SavedStateHandle so the in-progress search survives process death.
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")

    fun setSearchQuery(value: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = value
    }

    /**
     * All visible tabs precomputed (filter + sort + search applied) off the main thread.
     *
     * Replaces the per-pager-page recomputation in the screen — the pager just picks the
     * tab by index and reads `novels`. A single combine drives the whole library view, so
     * preference toggles never leave a stale page hanging.
     */
    internal val displayedTabs: StateFlow<List<LibraryTab>> = libraryDisplayInputs()
        .map { buildLibraryTabs(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    private fun libraryDisplayInputs() = combine(
        listOf<kotlinx.coroutines.flow.Flow<Any?>>(
            novels,
            categories,
            chapterStatsOrNull,
            showAllTab,
            sortField,
            sortDirection,
            filterStatuses,
            filterUnreadOnly,
            filterDownloadedOnly,
            filterSourceIds,
            isUnlocked,
            hiddenCategoryIds,
            includeHiddenInAll,
            includeLockedInTotals,
            searchQuery.debounce(120L),
            // Appended after searchQuery so the existing positional indices below
            // stay put; read at [15]/[16]/[17].
            filterNotifyEnabled,
            filterAutoDownloadEnabled,
            filterType,
            epubSourceIds,
            filterMinUserRating,
            filterMaxUserRating,
            filterStatusesExclude,
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val stats = values[2] as Map<Long, NovelChapterStats>?
        @Suppress("UNCHECKED_CAST")
        LibraryFilterInputs(
            // Render the list as soon as novels load — don't gate it on the chapter-stats
            // aggregation. While stats are null, chapterStats is empty: badge counts read 0
            // and the unread/downloaded filters match nothing until stats emit a frame later.
            novels = values[0] as List<NovelEntity>?,
            categories = values[1] as List<CategoryEntity>,
            chapterStats = stats.orEmpty(),
            showAllTab = values[3] as Boolean,
            sortField = values[4] as SortField,
            sortDirection = values[5] as SortDirection,
            filterStatuses = values[6] as Set<Int>,
            filterUnreadOnly = values[7] as Boolean,
            filterDownloadedOnly = values[8] as Boolean,
            filterSourceIds = values[9] as Set<Long>,
            isUnlocked = values[10] as Boolean,
            hiddenCategoryIds = values[11] as Set<Long>,
            includeHiddenInAll = values[12] as Boolean,
            includeLockedInTotals = values[13] as Boolean,
            searchQuery = values[14] as String,
            filterNotifyEnabled = values[15] as Boolean,
            filterAutoDownloadEnabled = values[16] as Boolean,
            filterType = values[17] as NovelTypeFilter,
            epubSourceIds = values[18] as Set<Long>,
            filterMinUserRating = values[19] as Int,
            filterMaxUserRating = values[20] as Int,
            filterStatusesExclude = values[21] as Set<Int>,
        )
    }

    fun setSelectedCategoryId(id: Long) = viewModelScope.launch {
        libraryPreferences.selectedCategoryId.set(id)
    }

    fun pkgForNovel(novel: NovelEntity): String =
        if (novel.sourceId == LOCAL_SOURCE_ID) LOCAL_PKG
        else extensionManager.extensions.value
            .firstOrNull { it.id == novel.sourceId }
            ?.info?.packageName ?: ""

    fun addCategory(name: String) = viewModelScope.launch {
        // Order from the unfiltered table, not `categories` — that flow excludes
        // hidden categories while locked, so its size collides with their orders.
        val nextOrder = (categoryDao.getAllOnce().maxOfOrNull { it.order } ?: -1) + 1
        categoryDao.upsert(CategoryEntity(name = name.trim(), order = nextOrder))
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

    /** Toggles [status] in the active filter set, or clears the set entirely when null. */
    /**
     * Tri-state status filter: INCLUDE/EXCLUDE membership per status ordinal.
     *
     * The two prefs can't be written atomically, so ordering matters: on
     * INCLUDE → EXCLUDE the exclude-add lands *before* the include-remove.
     * The UI resolves include first, so the intermediate emission still reads
     * INCLUDE instead of flickering through ANY. The other transitions only
     * touch one set (untouched sets are never rewritten).
     */
    fun setFilterStatusState(status: Int, state: io.grimoire.app.ui.component.sheet.FilterTriState) =
        viewModelScope.launch {
            val inc = filterStatuses.value
            val exc = filterStatusesExclude.value
            when (state) {
                io.grimoire.app.ui.component.sheet.FilterTriState.INCLUDE -> {
                    libraryPreferences.filterStatuses.set(inc + status)
                    if (status in exc) libraryPreferences.filterStatusesExclude.set(exc - status)
                }
                io.grimoire.app.ui.component.sheet.FilterTriState.EXCLUDE -> {
                    libraryPreferences.filterStatusesExclude.set(exc + status)
                    if (status in inc) libraryPreferences.filterStatuses.set(inc - status)
                }
                io.grimoire.app.ui.component.sheet.FilterTriState.ANY -> {
                    if (status in inc) libraryPreferences.filterStatuses.set(inc - status)
                    if (status in exc) libraryPreferences.filterStatusesExclude.set(exc - status)
                }
            }
        }

    fun clearFilterStatuses() = viewModelScope.launch {
        libraryPreferences.filterStatuses.set(emptySet())
        libraryPreferences.filterStatusesExclude.set(emptySet())
    }

    fun setFilterUnreadOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterUnreadOnly.set(value)
    }

    fun setFilterDownloadedOnly(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterDownloadedOnly.set(value)
    }

    fun setFilterNotifyEnabled(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterNotifyEnabled.set(value)
    }

    fun setFilterAutoDownloadEnabled(value: Boolean) = viewModelScope.launch {
        libraryPreferences.filterAutoDownloadEnabled.set(value)
    }

    fun setFilterType(value: NovelTypeFilter) = viewModelScope.launch {
        libraryPreferences.filterType.set(value)
    }

    /** Sets the inclusive user-rating range (1–10) a novel must fall in; the full 1..10 clears it. */
    fun setFilterUserRatingRange(min: Int, max: Int) = viewModelScope.launch {
        val lo = min.coerceIn(1, 10)
        val hi = max.coerceIn(lo, 10)
        libraryPreferences.filterMinUserRating.set(lo)
        libraryPreferences.filterMaxUserRating.set(hi)
    }

    /** Toggles [sourceId] in the active source filter set, or clears it entirely when null. */
    fun toggleFilterSource(sourceId: Long?) = viewModelScope.launch {
        val current = filterSourceIds.value
        val next = when {
            sourceId == null -> emptySet()
            sourceId in current -> current - sourceId
            else -> current + sourceId
        }
        libraryPreferences.filterSourceIds.set(next)
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
