package io.grimoire.app.extension.repo

import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.extension.LoadedExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepository @Inject constructor(
    private val repoDao: RepoDao,
    private val fetcher: ExtensionIndexFetcher,
    private val extensionManager: ExtensionManager,
) {
    val reposFlow: Flow<List<RepoEntity>> = repoDao.getAllFlow()

    private val _items = MutableStateFlow<List<ExtensionItem>>(emptyList())
    val items: StateFlow<List<ExtensionItem>> = _items.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

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
            for (repo in enabledRepos) {
                fetcher.fetch(repo.indexUrl)
                    .onSuccess { list -> list.forEach { fresh[it.pkg] = it } }
                    .onFailure { errors.add("${repo.name}: ${it.message}") }
            }

            if (errors.isNotEmpty()) _fetchError.value = errors.joinToString("\n")
            _items.value = merge(installed, fresh)
        } finally {
            _isFetching.value = false
        }
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

    suspend fun addRepo(name: String, indexUrl: String) =
        repoDao.insert(RepoEntity(name = name, indexUrl = indexUrl))

    suspend fun updateRepo(repo: RepoEntity) = repoDao.update(repo)

    suspend fun deleteRepo(repo: RepoEntity) = repoDao.delete(repo)
}
