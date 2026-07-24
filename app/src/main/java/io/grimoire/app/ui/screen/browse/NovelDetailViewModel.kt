package io.grimoire.app.ui.screen.browse

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.app.util.ContentLanguages
import io.grimoire.api.source.feature.ConfigurableSource
import io.grimoire.api.source.epub.EpubSource
import io.grimoire.api.source.feature.MultiHostSource
import io.grimoire.api.source.feature.MultiLanguageSource
import io.grimoire.api.source.Source
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.feature.WebViewLoginSource
import io.grimoire.api.source.sourceIdFor
import io.grimoire.app.data.cover.CustomCoverStore
import io.grimoire.app.data.source.fetchAllChapters
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.download.ChapterDownloadStatus
import io.grimoire.app.data.download.DownloadManager
import io.grimoire.app.data.epub.EpubExporter
import io.grimoire.app.data.epub.EpubImporter
import io.grimoire.app.data.epub.LOCAL_PKG
import io.grimoire.app.data.epub.LOCAL_SOURCE_ID
import io.grimoire.app.data.novelupdates.NuInfoState
import io.grimoire.app.data.novelupdates.NuSearchResult
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.domain.migration.MigrationState
import io.grimoire.app.domain.migration.NovelMigrator
import io.grimoire.app.domain.novelupdates.NovelUpdatesInfoRepository
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.ui.screen.webview.SOURCE_LOGIN_RESULT_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

internal const val BROWSE_TTL_MS = 30 * 60 * 1000L

@HiltViewModel
class NovelDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val browsingHistoryDao: io.grimoire.app.data.local.dao.BrowsingHistoryDao,
    private val incognitoManager: io.grimoire.app.data.preferences.IncognitoManager,
    private val categoryDao: CategoryDao,
    private val updateIssueDao: UpdateIssueDao,
    private val downloadManager: DownloadManager,
    private val epubImporter: EpubImporter,
    private val epubExporter: EpubExporter,
    private val novelUpdatesRepository: NovelUpdatesInfoRepository,
    private val migrator: NovelMigrator,
    private val coverStore: CustomCoverStore,
    libraryPreferences: LibraryPreferences,
    private val authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    /** Mirrors the library preference that decides whether locked chapters count toward totals. */
    val includeLockedInTotals: StateFlow<Boolean> =
        libraryPreferences.includeLockedInTotals.stateIn(viewModelScope)

    val pkg: String = checkNotNull(savedStateHandle["pkg"])
    private val novelUrl: String = checkNotNull(savedStateHandle["url"])

    /**
     * When this screen was opened as a migration target, the database id of the
     * novel whose read progress will be moved here; -1 otherwise.
     */
    val migrateFromId: Long = savedStateHandle.get<Long>("migrateFrom") ?: -1L
    val isMigrationTarget: Boolean get() = migrateFromId > 0L

    /** A locally-imported EPUB novel: fully stored in the DB, no backing extension. */
    val isLocal: Boolean = pkg == LOCAL_PKG

    /** Canonical id this novel is keyed by — derived from [pkg], or [LOCAL_SOURCE_ID] for a local book. */
    private val canonicalSourceId: Long
        get() = if (isLocal) LOCAL_SOURCE_ID else sourceIdFor(pkg)

    private val loaded get() = extensionManager.extensions.value.firstOrNull { it.info.packageName == pkg }
    private val source get() = loaded?.source

    val sourceName: String get() = loaded?.info?.label ?: ""

    /** This source delivers a whole-book EPUB rather than scraped chapters. */
    val isEpubSource: Boolean get() = source is EpubSource

    private val _bookDownload = MutableStateFlow<BookDownloadState>(BookDownloadState.Idle)
    val bookDownload: StateFlow<BookDownloadState> = _bookDownload.asStateFlow()

    /** Diff produced by the most recent user-triggered [refresh]; the screen shows a modal while non-null. */
    private val _refreshSummary = MutableStateFlow<RefreshSummary?>(null)
    val refreshSummary: StateFlow<RefreshSummary?> = _refreshSummary.asStateFlow()

    val novelWebUrl: String get() = absoluteWebUrl(_novel.value.url)

    /** Resolve a (possibly source-relative) chapter/novel url to an absolute web url. */
    fun absoluteWebUrl(url: String): String {
        if (url.startsWith("http")) return url
        val baseUrl = loaded?.source?.javaClass?.getAnnotation(SourceInfo::class.java)?.baseUrl ?: return url
        return "$baseUrl$url"
    }

    /** The source-provided novel (pre-override). Internal flows below apply overrides. */
    private val _novel = MutableStateFlow(Novel(url = novelUrl, title = "", language = Language.UNKNOWN))

    /** Source values, exposed so the edit sheet can diff overrides against them. */
    val sourceNovel: StateFlow<Novel> = _novel.asStateFlow()

    /** User overrides for this novel's cover + metadata (#151 / #152). */
    private val _overrides = MutableStateFlow(NovelOverrides())
    val overrides: StateFlow<NovelOverrides> = _overrides.asStateFlow()

    /** Effective novel shown in the UI: per-field overrides applied over the source values. */
    val novel: StateFlow<Novel> = combine(_novel, _overrides) { src, ov -> ov.applyTo(src) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _novel.value)

    /**
     * What to render for the cover: a local custom file > a custom url > the source
     * thumbnail. A [java.io.File] is used for the local path so Coil loads it directly.
     */
    val coverModel: StateFlow<Any?> = combine(_novel, _overrides) { src, ov ->
        ov.coverPath?.let { File(it) } ?: ov.coverUrl ?: src.thumbnailUrl
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _liveNovelId = MutableStateFlow(-1L)

    /** Database id of the saved novel, or <= 0 before it has been persisted. */
    val novelId: StateFlow<Long> = _liveNovelId.asStateFlow()

    @OptIn(FlowPreview::class)
    val chapters: StateFlow<List<ChapterEntity>> = _liveNovelId
        .flatMapLatest { id -> if (id > 0L) chapterDao.getChapters(id) else flowOf(emptyList()) }
        .debounce(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True once the chapter list contains at least one account-locked chapter. */
    val hasLockedChapters: StateFlow<Boolean> = chapters
        .map { list -> list.any { it.locked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Sign-in state for the backing source, used to decide whether to nudge the user. */
    private val _loginState = MutableStateFlow(LoginState.UNKNOWN)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _isLoadingNovel = MutableStateFlow(true)
    val isLoadingNovel: StateFlow<Boolean> = _isLoadingNovel.asStateFlow()

    private val _isLoadingChapters = MutableStateFlow(false)
    val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

    private val _novelError = MutableStateFlow<String?>(null)
    val novelError: StateFlow<String?> = _novelError.asStateFlow()

    private val _chaptersError = MutableStateFlow<String?>(null)
    val chaptersError: StateFlow<String?> = _chaptersError.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _notifyOnNewChapters = MutableStateFlow(false)
    val notifyOnNewChapters: StateFlow<Boolean> = _notifyOnNewChapters.asStateFlow()

    private val _notifyOnNewLockedChapters = MutableStateFlow(false)
    val notifyOnNewLockedChapters: StateFlow<Boolean> = _notifyOnNewLockedChapters.asStateFlow()

    private val _autoDownloadNewChapters = MutableStateFlow(false)
    val autoDownloadNewChapters: StateFlow<Boolean> = _autoDownloadNewChapters.asStateFlow()

    /** The user's own 1–10 rating for this novel; null when unrated. */
    private val _userRating = MutableStateFlow<Int?>(null)
    val userRating: StateFlow<Int?> = _userRating.asStateFlow()

    /** Title of the novel being migrated from, shown in the migration prompt. */
    private val _migrateFromTitle = MutableStateFlow("")
    val migrateFromTitle: StateFlow<String> = _migrateFromTitle.asStateFlow()

    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    private val _chapterPage = MutableStateFlow(0)
    val chapterPage: StateFlow<Int> = _chapterPage.asStateFlow()

    private val _chapterSort = MutableStateFlow(ChapterSort.NUMBER_ASC)
    val chapterSort: StateFlow<ChapterSort> = _chapterSort.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    /**
     * Sorted + search-filtered chapters fed to the screen. Single source of truth so the
     * pager, the FAB, and the fast scroller always agree about what the list will render.
     */
    @OptIn(FlowPreview::class)
    val displayedChapters: StateFlow<List<ChapterEntity>> = combine(
        chapters,
        _chapterSort,
        _searchQuery.debounce(120L),
    ) { list, sort, query ->
        projectChapters(list, sort, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _categoryId = MutableStateFlow<Long?>(null)
    val categoryId: StateFlow<Long?> = _categoryId.asStateFlow()

    private val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        combine(allCategories, authManager.isUnlocked) { list, unlocked ->
            if (unlocked) list else list.filter { !it.isHidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isUnlocked: StateFlow<Boolean> = authManager.isUnlocked

    val biometricEnabled: StateFlow<Boolean> = authManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True when an unlock prompt would be useful: locked, a PIN is set, and at least one category is hidden. */
    val canUnlockHidden: StateFlow<Boolean> = combine(
        authManager.isUnlocked,
        authManager.hasPin,
        allCategories,
    ) { unlocked, hasPin, all -> !unlocked && hasPin && all.any { it.isHidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = authManager.verifyPin(pin)
        if (ok) authManager.unlock()
        return ok
    }

    fun unlockFromBiometric() = authManager.unlock()

    private val _nuState = MutableStateFlow<NuInfoState>(NuInfoState.Idle)
    val nuState: StateFlow<NuInfoState> = _nuState.asStateFlow()

    private val _nuSearchResults = MutableStateFlow<List<NuSearchResult>>(emptyList())
    val nuSearchResults: StateFlow<List<NuSearchResult>> = _nuSearchResults.asStateFlow()

    private val _nuSearching = MutableStateFlow(false)
    val nuSearching: StateFlow<Boolean> = _nuSearching.asStateFlow()

    private var cachedNovelId: Long = -1L
    private var loadJob: Job? = null
    private var nuJob: Job? = null
    private var loginCheckJob: Job? = null

    init {
        if (isMigrationTarget) viewModelScope.launch {
            _migrateFromTitle.value = novelDao.getById(migrateFromId)?.title.orEmpty()
        }
        loadJob = viewModelScope.launch {
            if (isLocal) {
                loadLocalNovel()
            } else {
                extensionManager.extensions
                    .filter { list -> list.any { it.info.packageName == pkg } }
                    .take(1)
                    .collect {
                        loadNovel(forceRefresh = false)
                        refreshLoginState()
                    }
            }
        }
        viewModelScope.launch {
            when {
                !novelUpdatesRepository.isEnabled() ->
                    _nuState.value = NuInfoState.Disabled
                // Previously resolved/linked: restore it without re-prompting.
                novelUpdatesRepository.hasStoredLink(pkg, novelUrl) -> {
                    val title = novel.filter { it.title.isNotBlank() }.take(1).first().title
                    loadNovelUpdates(title)
                }
                // Otherwise just offer the button (no network until tapped).
                else -> _nuState.value = NuInfoState.NotLoaded
            }
        }
        // A successful WebView login signals back via this saved-state key;
        // re-fetch so newly-unlocked chapters and the banner update together.
        viewModelScope.launch {
            savedStateHandle.getStateFlow(SOURCE_LOGIN_RESULT_KEY, false).collect { done ->
                if (done) {
                    savedStateHandle[SOURCE_LOGIN_RESULT_KEY] = false
                    // Backup to the screen's ON_RESUME re-check; the saved-state
                    // result is easy to miss, so this isn't the primary trigger.
                    recheckLoginState()
                }
            }
        }
    }

    private fun loadNovelUpdates(title: String) {
        nuJob?.cancel()
        nuJob = viewModelScope.launch {
            _nuState.value = NuInfoState.Loading
            applyNuState(novelUpdatesRepository.infoFor(pkg, novelUrl, title))
        }
    }

    /**
     * When the match is ambiguous, pre-seed the picker with the candidates so
     * the dialog can open straight away without an extra search.
     */
    private fun applyNuState(state: NuInfoState) {
        if (state is NuInfoState.Ambiguous) {
            _nuSearchResults.value = state.candidates
        }
        _nuState.value = state
    }

    /** Triggered by the user tapping the "Load from NovelUpdates" button. */
    fun loadNovelUpdates() = retryNovelUpdates()

    fun retryNovelUpdates() {
        val title = _novel.value.title
        if (title.isNotBlank()) loadNovelUpdates(title)
    }

    fun linkNovelUpdates(slug: String) {
        nuJob?.cancel()
        nuJob = viewModelScope.launch {
            _nuState.value = NuInfoState.Loading
            applyNuState(novelUpdatesRepository.link(pkg, novelUrl, slug))
        }
    }

    fun searchNovelUpdates(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _nuSearching.value = true
            _nuSearchResults.value = novelUpdatesRepository.search(query)
            _nuSearching.value = false
        }
    }

    private suspend fun loadLocalNovel() {
        val existing = novelDao.getBySourceUrl(LOCAL_SOURCE_ID, novelUrl) ?: run {
            _novelError.value = "Imported book not found"
            _isLoadingNovel.value = false
            return
        }
        cachedNovelId = existing.id
        _liveNovelId.value = existing.id
        _novel.value = existing.toNovel()
        _overrides.value = existing.toOverrides()
        _isFavorite.value = existing.favorite
        _notifyOnNewChapters.value = existing.notifyOnNewChapters
        _notifyOnNewLockedChapters.value = existing.notifyOnNewLockedChapters
        _autoDownloadNewChapters.value = existing.autoDownloadNewChapters
        _userRating.value = existing.userRating
        _chapterSort.value = ChapterSort.entries.getOrElse(existing.chapterSortOrder) { ChapterSort.NUMBER_ASC }
        _categoryId.value = existing.categoryId
        _isLoadingNovel.value = false
        _isLoadingChapters.value = false
    }

    fun refresh() {
        if (isLocal) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Snapshot the chapter list before the refresh so we can diff against the
            // post-refresh list and surface the new/unlocked chapters to the user.
            val before = if (cachedNovelId > 0L) {
                chapterDao.getChaptersOnce(cachedNovelId).associateBy { it.url }
            } else emptyMap()

            loadNovel(forceRefresh = true)
            refreshLoginState()

            // Only emit a summary when the novel already had a chapter list — otherwise
            // a first-time fetch would flag every chapter as "new".
            if (before.isNotEmpty() && cachedNovelId > 0L) {
                val after = chapterDao.getChaptersOnce(cachedNovelId)
                val newChapters = after.filter { ch ->
                    val prev = before[ch.url]
                    prev == null || (prev.locked && !ch.locked)
                }
                if (newChapters.isNotEmpty()) {
                    _refreshSummary.value = RefreshSummary(
                        chapters = newChapters
                            .sortedByDescending { it.chapterNumber }
                            .map { ch ->
                                RefreshedChapter(
                                    name = ch.name,
                                    chapterNumber = ch.chapterNumber,
                                    locked = ch.locked,
                                    unlockedFromLocked = before[ch.url]?.locked == true,
                                )
                            },
                    )
                }
            }
        }
    }

    fun acknowledgeRefreshSummary() {
        _refreshSummary.value = null
    }

    /**
     * Refreshes the source's sign-in state.
     *
     * A source confirms its session over the network, and right after the login
     * WebView returns the server often doesn't report the session as live on the
     * very first check. When [retry] is set we poll with backoff for a few
     * seconds so a freshly-completed login is picked up and the locked-chapter
     * banner clears without having to leave and reopen the page. The first miss
     * still surfaces SIGNED_OUT immediately so the banner never sticks on
     * "Checking…"; later attempts can upgrade it to SIGNED_IN.
     */
    /** Whether this source signs in through a WebView (locked-chapter access). */
    val supportsWebViewLogin: Boolean get() = source is WebViewLoginSource

    /**
     * Whether opening this source's settings would show anything (configurable
     * prefs, multi-language picker, WebView login, or mirror hosts). Mirrors
     * ExtensionsScreen.hasSettings so we never offer a menu item that leads to an
     * empty page. Always false for the local EPUB pseudo-source.
     */
    val hasSourceSettings: Boolean
        get() = !isLocal && source.let {
            it is ConfigurableSource ||
                it is MultiLanguageSource ||
                it is WebViewLoginSource ||
                it is MultiHostSource
        }

    /**
     * Re-checks sign-in, polling with backoff so a just-completed login is
     * picked up. Called from the screen's ON_RESUME: returning from the login
     * WebView reliably fires a resume, whereas the nav saved-state result is
     * easy to miss (see SourceSettingsScreen for the same approach).
     */
    fun recheckLoginState() = refreshLoginState(retry = true)

    private fun refreshLoginState(retry: Boolean = false) {
        val src = source
        if (src !is WebViewLoginSource) {
            _loginState.value = LoginState.NOT_SUPPORTED
            return
        }
        loginCheckJob?.cancel()
        loginCheckJob = viewModelScope.launch {
            val waits = if (retry) {
                longArrayOf(0, 300, 500, 800, 1200, 1800, 2500)
            } else {
                longArrayOf(0)
            }
            for ((i, wait) in waits.withIndex()) {
                if (wait > 0) delay(wait)
                val signedIn = withContext(Dispatchers.IO) {
                    runCatching { src.isLoggedIn() }.getOrDefault(false)
                }
                if (signedIn) {
                    // Only a genuine signed-out -> signed-in flip means the user
                    // just logged in; reload so newly-unlocked chapters appear.
                    // UNKNOWN -> SIGNED_IN is the normal initial load and must not
                    // force a network refresh on every open.
                    val justLoggedIn = _loginState.value == LoginState.SIGNED_OUT
                    _loginState.value = LoginState.SIGNED_IN
                    if (justLoggedIn) refresh()
                    return@launch
                }
                if (i == 0) _loginState.value = LoginState.SIGNED_OUT
            }
        }
    }

    // --- Cover & metadata overrides (#151 / #152) ---

    /** Persist edited metadata fields. A null field clears that override (source reappears). */
    fun saveMetadataOverrides(o: NovelOverrides) {
        val id = cachedNovelId
        if (id <= 0L) return
        viewModelScope.launch {
            novelDao.updateMetadataOverrides(
                id = id,
                title = o.title,
                author = o.author,
                description = o.description,
                status = o.status?.ordinal,
                genres = o.genres?.joinToString(","),
            )
            _overrides.update {
                it.copy(
                    title = o.title,
                    author = o.author,
                    description = o.description,
                    status = o.status,
                    genres = o.genres,
                )
            }
        }
    }

    /** Replace the cover with a picked local image. Wins over a custom url and the source. */
    fun setCustomCoverFromUri(uri: Uri) {
        val id = cachedNovelId
        if (id <= 0L) return
        viewModelScope.launch {
            val path = runCatching { coverStore.saveFromUri(id, uri) }.getOrNull() ?: return@launch
            novelDao.updateCustomCover(id, path, null)
            _overrides.update { it.copy(coverPath = path, coverUrl = null) }
        }
    }

    /** Replace the cover with an image url. */
    fun setCustomCoverUrl(url: String) {
        val id = cachedNovelId
        val trimmed = url.trim()
        if (id <= 0L || trimmed.isBlank()) return
        viewModelScope.launch {
            coverStore.delete(id)
            novelDao.updateCustomCover(id, null, trimmed)
            _overrides.update { it.copy(coverPath = null, coverUrl = trimmed) }
        }
    }

    /** Clear both cover overrides; the source thumbnail reappears. */
    fun resetCustomCover() {
        val id = cachedNovelId
        if (id <= 0L) return
        viewModelScope.launch {
            coverStore.delete(id)
            novelDao.updateCustomCover(id, null, null)
            _overrides.update { it.copy(coverPath = null, coverUrl = null) }
        }
    }

    fun retryNovel() {
        if (isLocal) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val src = source ?: run { _novelError.value = "Source not available"; return@launch }
            fetchFromNetwork(src)
        }
    }

    fun retryChapters() {
        if (isLocal) return
        val src = source ?: return
        val novel = _novel.value.takeIf { it.initialized } ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetchChapters(src, novel) }
    }

    private suspend fun loadNovel(forceRefresh: Boolean) {
        val src = source ?: run {
            _novelError.value = "Source not available"
            _isLoadingNovel.value = false
            return
        }

        if (!forceRefresh) {
            val existing = novelDao.getBySourceUrl(canonicalSourceId, novelUrl)
            if (existing != null && existing.lastUpdated > 0L) {
                val age = System.currentTimeMillis() - existing.lastUpdated
                val fresh = existing.favorite || age < BROWSE_TTL_MS
                if (fresh) {
                    // Re-opening a cached novel extends its prune grace window.
                    if (!existing.favorite) {
                        novelDao.touchAccessed(existing.id, System.currentTimeMillis())
                        recordBrowseHistory(existing.id, existing.url, existing.title, existing.thumbnailUrl)
                    }
                    cachedNovelId = existing.id
                    _liveNovelId.value = existing.id
                    _novel.value = existing.toNovel()
                    _overrides.value = existing.toOverrides()
                    _isFavorite.value = existing.favorite
                    _notifyOnNewChapters.value = existing.notifyOnNewChapters
                    _notifyOnNewLockedChapters.value = existing.notifyOnNewLockedChapters
                    _autoDownloadNewChapters.value = existing.autoDownloadNewChapters
                    _userRating.value = existing.userRating
                    _chapterSort.value = ChapterSort.entries.getOrElse(existing.chapterSortOrder) { ChapterSort.NUMBER_ASC }
                    _categoryId.value = existing.categoryId
                    _isLoadingNovel.value = false
                    loadChaptersFromDb(existing.id, src, existing.toNovel())
                    return
                }
            }
        }

        fetchFromNetwork(src)
    }

    private suspend fun loadChaptersFromDb(novelId: Long, src: Source, novel: Novel) {
        _liveNovelId.value = novelId
        _isLoadingChapters.value = true
        _chaptersError.value = null
        val cached = chapterDao.getChaptersOnce(novelId)
        if (cached.isNotEmpty()) {
            // Wait for the debounced StateFlow to actually emit before hiding skeleton,
            // so there's no flash of empty chapter list.
            chapters.first { it.isNotEmpty() }
            _isLoadingChapters.value = false
        } else if (src is EpubSource) {
            _isLoadingChapters.value = false
        } else {
            fetchChapters(src, novel)
        }
    }

    private suspend fun fetchFromNetwork(src: Source) {
        _isLoadingNovel.value = true
        _novelError.value = null

        val full = runCatching {
            src.getNovelDetails(Novel(url = novelUrl, title = "", language = Language.UNKNOWN))
        }.onSuccess { novel ->
            _novel.value = novel
            val existing = novelDao.getBySourceUrl(canonicalSourceId, novelUrl)
            // Carry the user's overrides across the refresh so the source can never
            // clobber a custom cover or edited metadata field.
            _overrides.value = existing?.toOverrides() ?: NovelOverrides()
            val upsertId = novelDao.upsert(novel.toEntity(
                sourceId = canonicalSourceId,
                existingId = existing?.id ?: 0L,
                favorite = existing?.favorite ?: false,
                chapterSortOrder = existing?.chapterSortOrder ?: 0,
                categoryId = existing?.categoryId,
                url = novelUrl,
                lastReadAt = existing?.lastReadAt ?: 0L,
                notifyOnNewChapters = existing?.notifyOnNewChapters ?: false,
                notifyOnNewLockedChapters = existing?.notifyOnNewLockedChapters ?: false,
                autoDownloadNewChapters = existing?.autoDownloadNewChapters ?: false,
                customCoverPath = existing?.customCoverPath,
                customCoverUrl = existing?.customCoverUrl,
                overrideTitle = existing?.overrideTitle,
                overrideAuthor = existing?.overrideAuthor,
                overrideDescription = existing?.overrideDescription,
                overrideStatus = existing?.overrideStatus,
                overrideGenres = existing?.overrideGenres,
                userRating = existing?.userRating,
            ))
            cachedNovelId = existing?.id ?: upsertId
            _liveNovelId.value = cachedNovelId
            _isFavorite.value = existing?.favorite ?: false
            if (existing?.favorite != true) {
                recordBrowseHistory(cachedNovelId, novel.url, novel.title, novel.thumbnailUrl)
            }
            _notifyOnNewChapters.value = existing?.notifyOnNewChapters ?: false
            _notifyOnNewLockedChapters.value = existing?.notifyOnNewLockedChapters ?: false
            _autoDownloadNewChapters.value = existing?.autoDownloadNewChapters ?: false
            _userRating.value = existing?.userRating
            _chapterSort.value = ChapterSort.entries.getOrElse(existing?.chapterSortOrder ?: 0) { ChapterSort.NUMBER_ASC }
            _categoryId.value = existing?.categoryId
        }.onFailure { e ->
            _novelError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
        }.getOrNull()

        _isLoadingNovel.value = false
        // EPUB sources have no scraped chapter list; chapters appear after the
        // user downloads the book (see downloadBook()).
        if (full != null && src !is EpubSource) fetchChapters(src, full)
    }

    private suspend fun fetchChapters(src: Source, novel: Novel) {
        _isLoadingChapters.value = true
        _chaptersError.value = null
        _chapterPage.value = 0

        runCatching {
            fetchAllChapters(src, novel, onPageProgress = { _chapterPage.value = it })
        }.onSuccess { list ->
            if (cachedNovelId > 0L) {
                val existing = chapterDao.getChaptersOnce(cachedNovelId).associateBy { it.url }
                chapterDao.replaceChapters(cachedNovelId, list.map { ch ->
                    val prev = existing[ch.url]
                    ch.toEntity(cachedNovelId).copy(
                        read = prev?.read ?: false,
                        readProgress = prev?.readProgress ?: 0f,
                        downloadStatus = prev?.downloadStatus ?: 0,
                        downloadedContent = prev?.downloadedContent,
                    )
                })
                // A successful network fetch clears any stale library-update
                // warning/failure recorded for this novel.
                updateIssueDao.clearForNovel(cachedNovelId)
            }
        }.onFailure { e ->
            _chaptersError.value = "${e::class.simpleName}: ${e.message ?: "(no message)"}"
        }

        _isLoadingChapters.value = false
        _chapterPage.value = 0
    }

    fun markAllRead(read: Boolean) {
        if (cachedNovelId <= 0L) return
        viewModelScope.launch { chapterDao.markAllRead(cachedNovelId, read) }
    }

    fun markChaptersRead(ids: List<Long>, read: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.chunked(999).forEach { chunk -> chapterDao.markChapters(chunk, read) }
        }
    }

    fun setCategory(categoryId: Long?) {
        _categoryId.value = categoryId
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateCategory(cachedNovelId, categoryId)
        }
    }

    fun downloadChapter(chapter: ChapterEntity) = downloadManager.enqueue(listOf(chapter))
    fun downloadAll() = downloadManager.enqueue(chapters.value.filter { !it.locked })
    fun downloadUnread() = downloadManager.enqueue(chapters.value.filter { !it.read && !it.locked })

    /** Queue the next [count] unread, undownloaded chapters in reading order. */
    fun downloadNext(count: Int) = downloadManager.enqueue(
        chapters.value
            .filter {
                !it.locked && !it.read &&
                    (it.downloadStatus == ChapterDownloadStatus.NONE.ordinal ||
                        it.downloadStatus == ChapterDownloadStatus.ERROR.ordinal)
            }
            .sortedBy { it.chapterNumber }
            .take(count),
    )

    fun deleteAllDownloads() = downloadManager.deleteDownloads(
        chapters.value.filter { it.downloadStatus in ChapterDownloadStatus.HAS_CONTENT_ORDINALS },
    )
    fun cancelDownload(chapter: ChapterEntity) = downloadManager.cancel(chapter)
    fun cancelAllDownloads() { if (cachedNovelId > 0L) downloadManager.cancelAll(cachedNovelId) }
    fun deleteDownload(chapter: ChapterEntity) = downloadManager.deleteDownload(chapter)
    fun redownloadChapter(chapter: ChapterEntity) =
        downloadManager.enqueue(listOf(chapter), force = true)

    fun downloadChapters(chapters: List<ChapterEntity>) =
        downloadManager.enqueue(chapters.filter { !it.locked })
    fun deleteDownloads(chapters: List<ChapterEntity>) = downloadManager.deleteDownloads(chapters)
    fun cancelDownloads(chapters: List<ChapterEntity>) = downloadManager.cancelDownloads(chapters)
    fun redownloadChapters(chapters: List<ChapterEntity>) =
        downloadManager.enqueue(chapters.filter { !it.locked }, force = true)

    /** Downloads and imports the whole-book EPUB for an [EpubSource]. */
    fun downloadBook() {
        val src = source as? EpubSource ?: run {
            _bookDownload.value = BookDownloadState.Error("Source unavailable")
            return
        }
        if (_bookDownload.value is BookDownloadState.Downloading) return
        _bookDownload.value = BookDownloadState.Downloading
        viewModelScope.launch {
            runCatching { src.getEpub(_novel.value) }
                .mapCatching { bytes -> epubImporter.importBytes(bytes, canonicalSourceId, novelUrl).getOrThrow() }
                .onSuccess { result ->
                    cachedNovelId = result.novelId
                    _liveNovelId.value = result.novelId
                    _bookDownload.value = BookDownloadState.Done
                }
                .onFailure { e ->
                    _bookDownload.value = BookDownloadState.Error(
                        e.message ?: e::class.simpleName ?: "Download failed",
                    )
                }
        }
    }

    /** One-shot result of an [exportEpub] run; the screen shows it as a snackbar then consumes it. */
    private val _exportEvent = MutableStateFlow<ExportEvent?>(null)
    val exportEvent: StateFlow<ExportEvent?> = _exportEvent.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /** Suggested file name for the EPUB save dialog: the novel title, filesystem-safe, plus `.epub`. */
    fun suggestedExportFileName(): String {
        val base = novel.value.title.ifBlank { "novel" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(120)
            .ifBlank { "novel" }
        return "$base.epub"
    }

    /** Writes this novel's downloaded chapters out as an EPUB to the user-chosen [dest]. */
    fun exportEpub(dest: Uri) {
        val id = _liveNovelId.value
        if (id <= 0L) {
            _exportEvent.value = ExportEvent.Error("Save this novel to your library first")
            return
        }
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            epubExporter.export(id, dest).fold(
                onSuccess = { result ->
                    _exportEvent.value = ExportEvent.Success(
                        "Exported ${result.chapterCount} " +
                            (if (result.chapterCount == 1) "chapter" else "chapters"),
                    )
                },
                onFailure = { e ->
                    _exportEvent.value = ExportEvent.Error(
                        e.message ?: e::class.simpleName ?: "Export failed",
                    )
                },
            )
            _isExporting.value = false
        }
    }

    fun consumeExportEvent() {
        _exportEvent.value = null
    }

    fun setSort(sort: ChapterSort) {
        _chapterSort.value = sort
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateChapterSort(cachedNovelId, sort.ordinal)
        }
    }

    fun toggleFavorite() {
        if (!isLocal && source == null) return
        val sourceId = canonicalSourceId
        val next = !_isFavorite.value
        _isFavorite.value = next
        viewModelScope.launch {
            val entity = novelDao.getBySourceUrl(sourceId, novelUrl) ?: return@launch
            val updated = entity.copy(favorite = next)
            novelDao.upsert(updated)
            // Browsing history is only for non-library novels: drop the row once added.
            if (next) browsingHistoryDao.deleteByNovel(pkg, novelUrl)
        }
    }

    /**
     * Logs a non-library novel opened in Browse to the browsing history (newest-first),
     * unless incognito is on. Best-effort and fire-and-forget.
     */
    private fun recordBrowseHistory(novelId: Long, url: String, title: String, thumbnailUrl: String?) {
        if (incognitoManager.enabled.value) return
        viewModelScope.launch {
            browsingHistoryDao.upsert(
                io.grimoire.app.data.local.entity.BrowsingHistoryEntity(
                    sourcePackage = pkg,
                    novelId = novelId.takeIf { it > 0L },
                    novelUrl = url,
                    novelTitle = title,
                    novelThumbnailUrl = thumbnailUrl,
                    openedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun setNotifyOnNewChapters(value: Boolean) {
        _notifyOnNewChapters.value = value
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateNotifyOnNewChapters(cachedNovelId, value)
        }
    }

    fun setNotifyOnNewLockedChapters(value: Boolean) {
        _notifyOnNewLockedChapters.value = value
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateNotifyOnNewLockedChapters(cachedNovelId, value)
        }
    }

    fun setAutoDownloadNewChapters(value: Boolean) {
        _autoDownloadNewChapters.value = value
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateAutoDownloadNewChapters(cachedNovelId, value)
        }
    }

    /** Set (1–10) or clear (null) the user's own rating for this novel. */
    fun setUserRating(rating: Int?) {
        val clamped = rating?.coerceIn(1, 10)
        _userRating.value = clamped
        if (cachedNovelId > 0L) viewModelScope.launch {
            novelDao.updateUserRating(cachedNovelId, clamped)
        }
    }

    /** How many of this novel's chapters the pending migration would mark read. */
    suspend fun migrationMatchCount(): Int {
        if (!isMigrationTarget || cachedNovelId <= 0L) return 0
        return runCatching { migrator.matchReadProgress(migrateFromId, cachedNovelId).size }
            .getOrDefault(0)
    }

    /** Moves the source novel's read progress onto this novel. */
    fun confirmMigration() {
        if (!isMigrationTarget || cachedNovelId <= 0L) return
        if (_migrationState.value == MigrationState.Running) return
        _migrationState.value = MigrationState.Running
        viewModelScope.launch {
            runCatching { migrator.migrate(migrateFromId, cachedNovelId) }.fold(
                onSuccess = { _migrationState.value = MigrationState.Success },
                onFailure = { e ->
                    _migrationState.value = MigrationState.Error(
                        e.message ?: e::class.simpleName ?: "Migration failed",
                    )
                },
            )
        }
    }

    fun dismissMigrationError() {
        if (_migrationState.value is MigrationState.Error) {
            _migrationState.value = MigrationState.Idle
        }
    }

    /**
     * Snapshot of the facts the share card renders. Reading totals mirror the on-screen
     * stats row (locked chapters count only when [includeLockedInTotals] is set); word
     * counts sum [ChapterEntity.wordCount], which is 0 until a chapter has been read.
     */
    fun shareData(): io.grimoire.app.util.NovelShareData {
        val list = chapters.value
        val lockedCount = list.count { it.locked }
        val total = if (includeLockedInTotals.value) {
            list.size
        } else {
            (list.size - lockedCount).coerceAtLeast(0)
        }
        val read = list.count { it.read }
        val percent = if (total > 0) (read * 100 / total).coerceIn(0, 100) else 0
        val n = novel.value
        return io.grimoire.app.util.NovelShareData(
            coverModel = coverModel.value,
            title = n.title,
            author = n.author?.takeIf { it.isNotBlank() },
            sourceName = sourceName,
            readChapters = read,
            totalChapters = total,
            percent = percent,
            wordsRead = list.filter { it.read }.sumOf { it.wordCount },
            totalWords = list.sumOf { it.wordCount },
        )
    }
}

/** Outcome of an EPUB export, surfaced to the screen as a one-shot snackbar message. */
sealed interface ExportEvent {
    val message: String
    data class Success(override val message: String) : ExportEvent
    data class Error(override val message: String) : ExportEvent
}

sealed interface BookDownloadState {
    data object Idle : BookDownloadState
    data object Downloading : BookDownloadState
    data object Done : BookDownloadState
    data class Error(val message: String) : BookDownloadState
}

/** Chapters discovered by a user-triggered refresh of a single novel. */
data class RefreshSummary(val chapters: List<RefreshedChapter>)

data class RefreshedChapter(
    val name: String,
    val chapterNumber: Float,
    val locked: Boolean,
    /** True when the chapter existed before but was locked, and is now unlocked. */
    val unlockedFromLocked: Boolean,
)

/** Sign-in state of the backing source. */
enum class LoginState { UNKNOWN, NOT_SUPPORTED, SIGNED_OUT, SIGNED_IN }

internal fun NovelEntity.toNovel() = Novel(
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    description = description,
    genres = if (genres.isBlank()) emptyList() else genres.split(","),
    status = NovelStatus.entries.getOrElse(status) { NovelStatus.UNKNOWN },
    rating = rating,
    ratingCount = ratingCount,
    language = language?.let { ContentLanguages.fromName(it) } ?: Language.UNKNOWN,
    initialized = true,
)

internal fun Novel.toEntity(
    sourceId: Long,
    existingId: Long,
    favorite: Boolean,
    chapterSortOrder: Int = 0,
    categoryId: Long? = null,
    url: String = this.url,
    lastReadAt: Long = 0L,
    notifyOnNewChapters: Boolean = false,
    notifyOnNewLockedChapters: Boolean = false,
    autoDownloadNewChapters: Boolean = false,
    customCoverPath: String? = null,
    customCoverUrl: String? = null,
    overrideTitle: String? = null,
    overrideAuthor: String? = null,
    overrideDescription: String? = null,
    overrideStatus: Int? = null,
    overrideGenres: String? = null,
    userRating: Int? = null,
) = NovelEntity(
    id = existingId,
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    author = author,
    description = description,
    genres = genres.joinToString(","),
    status = status.ordinal,
    favorite = favorite,
    lastUpdated = System.currentTimeMillis(),
    chapterSortOrder = chapterSortOrder,
    categoryId = categoryId,
    lastReadAt = lastReadAt,
    lastAccessedAt = System.currentTimeMillis(),
    rating = rating,
    ratingCount = ratingCount,
    language = language.takeIf { it != Language.UNKNOWN && it != Language.MULTI }?.displayName,
    notifyOnNewChapters = notifyOnNewChapters,
    notifyOnNewLockedChapters = notifyOnNewLockedChapters,
    autoDownloadNewChapters = autoDownloadNewChapters,
    customCoverPath = customCoverPath,
    customCoverUrl = customCoverUrl,
    overrideTitle = overrideTitle,
    overrideAuthor = overrideAuthor,
    overrideDescription = overrideDescription,
    overrideStatus = overrideStatus,
    overrideGenres = overrideGenres,
    userRating = userRating,
)

/**
 * User cover + metadata overrides for the open novel. A null field means "no override"
 * (fall back to the source value). [genres] of an empty list means "override to no genres".
 */
data class NovelOverrides(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val status: NovelStatus? = null,
    val genres: List<String>? = null,
    val coverPath: String? = null,
    val coverUrl: String? = null,
) {
    /** Apply the per-field metadata overrides over a source [novel]. */
    fun applyTo(novel: Novel): Novel = novel.copy(
        title = title ?: novel.title,
        author = author ?: novel.author,
        description = description ?: novel.description,
        status = status ?: novel.status,
        genres = genres ?: novel.genres,
    )
}

internal fun NovelEntity.toOverrides() = NovelOverrides(
    title = overrideTitle,
    author = overrideAuthor,
    description = overrideDescription,
    status = overrideStatus?.let { NovelStatus.entries.getOrElse(it) { NovelStatus.UNKNOWN } },
    genres = overrideGenres?.let { if (it.isBlank()) emptyList() else it.split(",") },
    coverPath = customCoverPath,
    coverUrl = customCoverUrl,
)

private fun ChapterEntity.toChapter() = Chapter(
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
    locked = locked,
)

internal fun Chapter.toEntity(novelId: Long) = ChapterEntity(
    novelId = novelId,
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
    locked = locked,
)
