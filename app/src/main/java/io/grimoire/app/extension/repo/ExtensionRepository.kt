package io.grimoire.app.extension.repo

import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.data.preferences.AppPreferences
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.LoadedExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepository @Inject constructor(
    private val repoDao: RepoDao,
    private val fetcher: ExtensionIndexFetcher,
    private val extensionManager: ExtensionManager,
    private val appPreferences: AppPreferences,
) {
    // Process-lifetime scope: this @Singleton lives for the whole app session.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reposFlow: Flow<List<RepoEntity>> = repoDao.getAllFlow()

    private val _items = MutableStateFlow<List<ExtensionItem>>(emptyList())
    val items: StateFlow<List<ExtensionItem>> = _items.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    /**
     * Set when at least one enabled repo's index returned 401/404 from a
     * github.com host — the user almost certainly needs to connect their
     * GitHub account before that repo will load. UI consumes this to surface
     * a connect prompt. Cleared on the next refresh that doesn't see one.
     */
    private val _authRequiredRepos = MutableStateFlow<List<RepoEntity>>(emptyList())
    val authRequiredRepos: StateFlow<List<RepoEntity>> = _authRequiredRepos.asStateFlow()

    /**
     * True after the last refresh hit a GitHub 403 rate limit. The UI uses this
     * to prompt the user to connect their GitHub account (5,000/hr) instead of
     * the 60/hr anonymous bucket. Only emits a fresh value when it changes, so
     * a dismissed prompt won't keep re-firing on identical refreshes.
     */
    private val _rateLimited = MutableStateFlow(false)
    val rateLimited: StateFlow<Boolean> = _rateLimited.asStateFlow()

    /** Count of installed extensions that have a newer version in an enabled repo. */
    val updateCount: StateFlow<Int> = items
        .map { list -> list.count { it is ExtensionItem.Installed && it.hasUpdate } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    suspend fun refresh() {
        _isFetching.value = true
        _fetchError.value = null
        try {
            extensionManager.refresh()
            val installed = extensionManager.extensions.value
                .associateBy { it.info.packageName }
            val enabledRepos = repoDao.getEnabled()

            // Fast path: emit cached index immediately so UI isn't blank.
            val cached = mutableMapOf<String, RemoteExtension>()
            for (repo in enabledRepos) {
                fetcher.loadCached(repo.indexUrl)?.forEach { cached[it.pkg] = it }
            }
            if (cached.isNotEmpty()) _items.value = merge(installed, cached)

            // Slow path: fetch fresh index, then re-emit.
            val fresh = mutableMapOf<String, RemoteExtension>()
            val errors = mutableListOf<String>()
            val authRequired = mutableListOf<RepoEntity>()
            var rateLimited = false
            for (repo in enabledRepos) {
                fetcher.fetch(repo.indexUrl)
                    .onSuccess { list -> list.forEach { fresh[it.pkg] = it } }
                    .onFailure { e ->
                        if (e is IndexAuthRequiredException) authRequired.add(repo)
                        if (e is GitHubRateLimitException) rateLimited = true
                        errors.add("${repo.name}: ${e.message}")
                        // A transient fetch failure shouldn't blank a repo that
                        // loaded fine moments ago — keep its cached entries so
                        // the list doesn't collapse to installed-only. A
                        // successful repo's fresh data still wins (assignment
                        // above overrides putIfAbsent here).
                        fetcher.loadCached(repo.indexUrl)?.forEach {
                            fresh.putIfAbsent(it.pkg, it)
                        }
                    }
            }

            _fetchError.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            _authRequiredRepos.value = authRequired
            _rateLimited.value = rateLimited
            _items.value = merge(installed, fresh)
        } finally {
            _isFetching.value = false
        }
    }

    /**
     * Refreshes the index off the main thread so installed-extension updates are
     * detected on app launch, without the user opening the extension manager.
     * Seeds the bundled default repo first (one-time) so a fresh install has the
     * official extension catalogue available without any manual setup.
     */
    fun checkForUpdatesOnLaunch() {
        scope.launch {
            runCatching { seedDefaultReposIfNeeded() }
            runCatching { refresh() }
        }
    }

    /**
     * Inserts the official Grimoire extension repo on first run. Guarded by a
     * persisted flag (not by "is the table empty") so removing the default repo
     * is respected — it won't reappear on the next launch. The insert itself is
     * a no-op if a repo with the same index URL already exists (unique index +
     * IGNORE), so a user who added it manually won't get a duplicate.
     */
    private suspend fun seedDefaultReposIfNeeded() {
        if (appPreferences.defaultReposSeeded.changes().first()) return
        repoDao.insert(RepoEntity(name = DEFAULT_REPO_NAME, indexUrl = DEFAULT_REPO_INDEX_URL))
        appPreferences.defaultReposSeeded.set(true)
    }

    private fun merge(
        installed: Map<String, LoadedExtension>,
        remote: Map<String, RemoteExtension>,
    ): List<ExtensionItem> {
        val merged = mutableListOf<ExtensionItem>()
        for ((pkg, rem) in remote) {
            val load = installed[pkg]
            merged.add(if (load != null) ExtensionItem.Installed(load, rem) else ExtensionItem.Available(rem))
        }
        for ((pkg, load) in installed) {
            if (pkg !in remote) merged.add(ExtensionItem.InstalledOnly(load))
        }
        return merged.sortedBy { it.name.lowercase() }
    }

    /**
     * Index entries (with install state) whose declared NovelUpdates groups
     * intersect [groups], matched case-insensitively. Reads each enabled repo's
     * cached index, falling back to a network fetch only when no cache exists —
     * so opening a NovelUpdates series can surface a "read with this source"
     * link without forcing a full refresh. Returns [ExtensionItem.Installed]
     * when the matched extension is already installed, else
     * [ExtensionItem.Available].
     */
    suspend fun extensionsForNovelUpdatesGroups(groups: Collection<String>): List<ExtensionItem> {
        val wanted = groups.mapNotNull { normalizeGroupKey(it).takeIf(String::isNotEmpty) }.toSet()
        if (wanted.isEmpty()) return emptyList()

        extensionManager.refresh()
        val installed = extensionManager.extensions.value.associateBy { it.info.packageName }

        val remotes = mutableMapOf<String, RemoteExtension>()
        for (repo in repoDao.getEnabled()) {
            val list = fetcher.loadCached(repo.indexUrl) ?: fetcher.fetch(repo.indexUrl).getOrNull()
            list?.forEach { remotes[it.pkg] = it }
        }

        return remotes.values
            .filter { rem -> rem.novelUpdatesGroups.any { normalizeGroupKey(it) in wanted } }
            .map { rem ->
                installed[rem.pkg]?.let { ExtensionItem.Installed(it, rem) }
                    ?: ExtensionItem.Available(rem)
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Collapses a NovelUpdates group identifier to a comparison key: lowercase,
     * alphanumeric only. This bridges the gap between a declared display name
     * ("Cale Red Hair") and the slug/handle NU actually shows ("caleredhair"),
     * so either form a source declares matches what's scraped from a release.
     */
    private fun normalizeGroupKey(s: String): String =
        s.lowercase().filter(Char::isLetterOrDigit)

    suspend fun addRepo(name: String, indexUrl: String) =
        repoDao.insert(RepoEntity(name = name, indexUrl = indexUrl))

    suspend fun updateRepo(repo: RepoEntity) = repoDao.update(repo)

    suspend fun deleteRepo(repo: RepoEntity) = repoDao.delete(repo)

    companion object {
        // The official first-party extension catalogue, shipped enabled by
        // default. The index is the `index.json` asset on the repo's rolling
        // `latest` release.
        private const val DEFAULT_REPO_NAME = "Grimoire Extensions"
        private const val DEFAULT_REPO_INDEX_URL =
            "https://github.com/Operation-Grimoire/grimoire-extensions/" +
                "releases/download/latest/index.json"
    }
}
