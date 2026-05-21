package io.grimoire.app.ui.screen.migrate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.Chapter
import io.grimoire.api.model.Novel
import io.grimoire.api.source.CatalogueSource
import io.grimoire.api.source.PaginatedSource
import io.grimoire.api.source.Source
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.ui.screen.browse.GlobalSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of an in-flight or completed migration. */
sealed interface MigrationState {
    data object Idle : MigrationState
    data object Running : MigrationState
    data class Success(val targetPkg: String, val targetUrl: String) : MigrationState
    data class Error(val message: String) : MigrationState
}

@HiltViewModel
class MigrateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
) : ViewModel() {

    private val sourceNovelId: Long = checkNotNull(savedStateHandle["novelId"])

    private val _sourceTitle = MutableStateFlow("")
    val sourceTitle: StateFlow<String> = _sourceTitle.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val migrationState: StateFlow<MigrationState> = _migrationState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Pre-seed the search with the novel's title and run it straight away.
        viewModelScope.launch {
            val novel = novelDao.getById(sourceNovelId) ?: return@launch
            _sourceTitle.value = novel.title
            _searchQuery.value = novel.title
            submitSearch()
        }
    }

    fun setQuery(q: String) {
        _searchQuery.value = q
    }

    /** Queries every installed catalogue source in parallel for [searchQuery]. */
    fun submitSearch() {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val sources = extensionManager.extensions.value.mapNotNull { loaded ->
                val src = loaded.source as? CatalogueSource ?: return@mapNotNull null
                val name = loaded.info.label.substringAfter(": ", loaded.info.label)
                Triple(name, loaded.info.packageName, src)
            }

            if (sources.isEmpty()) {
                _searchResults.value = emptyList()
                _isSearching.value = false
                return@launch
            }

            _isSearching.value = true
            _searchResults.value = sources.map { (name, pkg, src) ->
                GlobalSearchResult(
                    sourceName = name,
                    packageName = pkg,
                    sourceId = src.id,
                    isLoading = true,
                )
            }

            sources.map { (_, pkg, src) ->
                async {
                    val result = runCatching { src.searchNovels(q, 1, emptyList()) }
                    _searchResults.update { current ->
                        current.map { entry ->
                            if (entry.packageName != pkg) return@map entry
                            result.fold(
                                onSuccess = { novels -> entry.copy(novels = novels, isLoading = false) },
                                onFailure = { e -> entry.copy(isLoading = false, error = e.message ?: "Failed") },
                            )
                        }
                    }
                }
            }.awaitAll()

            _isSearching.value = false
        }
    }

    /**
     * Saves [target] to the library and moves the original novel's read progress
     * onto it, matching chapters by their number, then drops the original from
     * the library.
     */
    fun migrate(target: Novel, targetPkg: String) {
        if (_migrationState.value == MigrationState.Running) return
        _migrationState.value = MigrationState.Running
        viewModelScope.launch {
            runCatching {
                val sourceNovel = novelDao.getById(sourceNovelId)
                    ?: error("Original novel not found")
                val targetSource = extensionManager.extensions.value
                    .firstOrNull { it.info.packageName == targetPkg }
                    ?.source
                    ?: error("Target source not available")

                if (targetSource.id == sourceNovel.sourceId && target.url == sourceNovel.url) {
                    error("Cannot migrate a novel onto itself")
                }

                // Fetch full target metadata and its complete chapter list.
                val fullTarget = targetSource.getNovelDetails(target)
                val targetChapters = fetchAllChapters(targetSource, fullTarget)
                if (targetChapters.isEmpty()) error("Target novel has no chapters")

                // Persist the target as a favorite, carrying over library metadata.
                val existingTarget = novelDao.getBySourceUrl(targetSource.id, target.url)
                val targetNovelId = novelDao.upsert(
                    fullTarget.toEntity(
                        sourceId = targetSource.id,
                        existingId = existingTarget?.id ?: 0L,
                        favorite = true,
                        chapterSortOrder = sourceNovel.chapterSortOrder,
                        categoryId = sourceNovel.categoryId,
                        url = target.url,
                        lastReadAt = sourceNovel.lastReadAt,
                    ),
                )

                // Move read progress, matching chapters by their number.
                val sourceProgress = chapterDao.getChaptersOnce(sourceNovelId)
                    .filter { it.chapterNumber > 0f && (it.read || it.readProgress > 0f) }
                    .associateBy { it.chapterNumber }
                val existingTargetChapters = chapterDao.getChaptersOnce(targetNovelId)
                    .associateBy { it.url }

                chapterDao.replaceChapters(
                    targetNovelId,
                    targetChapters.map { ch ->
                        val prev = existingTargetChapters[ch.url]
                        val migrated = sourceProgress[ch.chapterNumber]
                        ch.toEntity(targetNovelId).copy(
                            read = migrated?.read ?: prev?.read ?: false,
                            readProgress = migrated?.readProgress ?: prev?.readProgress ?: 0f,
                            firstReadAt = migrated?.firstReadAt ?: prev?.firstReadAt,
                            downloadStatus = prev?.downloadStatus ?: 0,
                            downloadedContent = prev?.downloadedContent,
                        )
                    },
                )

                // Drop the original from the library; its history stays in the DB.
                novelDao.upsert(sourceNovel.copy(favorite = false))
            }.fold(
                onSuccess = {
                    _migrationState.value = MigrationState.Success(targetPkg, target.url)
                },
                onFailure = { e ->
                    _migrationState.value =
                        MigrationState.Error(e.message ?: e::class.simpleName ?: "Migration failed")
                },
            )
        }
    }

    private suspend fun fetchAllChapters(src: Source, novel: Novel): List<Chapter> {
        if (src !is PaginatedSource) return src.getChapterList(novel)

        val all = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        var page = 1
        while (true) {
            val batch = src.getChapterList(novel, page)
            if (batch.isEmpty()) break
            val new = batch.filter { seen.add(it.url) }
            if (new.isEmpty()) break
            all += new
            page++
        }
        return all
    }
}

private fun Novel.toEntity(
    sourceId: Long,
    existingId: Long,
    favorite: Boolean,
    chapterSortOrder: Int,
    categoryId: Long?,
    url: String,
    lastReadAt: Long,
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
    rating = rating,
    ratingCount = ratingCount,
    language = language,
)

private fun Chapter.toEntity(novelId: Long) = ChapterEntity(
    novelId = novelId,
    url = url,
    name = name,
    uploadDate = uploadDate,
    chapterNumber = chapterNumber,
    translator = translator,
    locked = locked,
)
