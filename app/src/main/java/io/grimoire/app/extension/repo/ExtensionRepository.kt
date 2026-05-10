package io.grimoire.app.extension.repo

import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.ExtensionManager
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

            val remoteAll = mutableMapOf<String, RemoteExtension>()
            val errors = mutableListOf<String>()

            for (repo in repoDao.getEnabled()) {
                fetcher.fetch(repo.indexUrl)
                    .onSuccess { list -> list.forEach { remoteAll[it.pkg] = it } }
                    .onFailure { errors.add("${repo.name}: ${it.message}") }
            }

            if (errors.isNotEmpty()) _fetchError.value = errors.joinToString("\n")

            val merged = mutableListOf<ExtensionItem>()
            for ((pkg, remote) in remoteAll) {
                val load = installed[pkg]
                merged.add(if (load != null) ExtensionItem.Installed(load, remote) else ExtensionItem.Available(remote))
            }
            for ((pkg, load) in installed) {
                if (pkg !in remoteAll) merged.add(ExtensionItem.InstalledOnly(load))
            }

            _items.value = merged.sortedBy { it.name.lowercase() }
        } finally {
            _isFetching.value = false
        }
    }

    suspend fun addRepo(name: String, indexUrl: String) =
        repoDao.insert(RepoEntity(name = name, indexUrl = indexUrl))

    suspend fun updateRepo(repo: RepoEntity) = repoDao.update(repo)

    suspend fun deleteRepo(repo: RepoEntity) = repoDao.delete(repo)
}
