package io.grimoire.app.ui.screen.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
import io.grimoire.app.auth.github.GitHubAuthStore
import io.grimoire.app.util.ContentLanguages
import io.grimoire.app.data.local.entity.RepoEntity
import io.grimoire.app.extension.repo.ExtensionInstaller
import io.grimoire.app.extension.repo.ExtensionItem
import io.grimoire.app.extension.repo.ExtensionRepository
import io.grimoire.app.extension.repo.GitHubRateLimitException
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.repo.HashMismatchException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class InstallState {
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : InstallState()
    data class Error(val message: String) : InstallState()
}

/** Which extensions section the user is viewing on the Extensions screen. */
enum class ExtensionSection { ALL, INSTALLED, AVAILABLE, UPDATES }

/** Adult-content (18+) filter on the Extensions screen. */
enum class AdultFilter { ALL, HIDE, ONLY }

/**
 * Name/language-filtered, partitioned extension lists plus the available
 * language codes (for the chips) and the count of installed extensions with a
 * pending update (for the Updates chip badge).
 */
data class ExtensionsUi(
    val installed: List<ExtensionItem> = emptyList(),
    val available: List<ExtensionItem> = emptyList(),
    val updates: List<ExtensionItem> = emptyList(),
    val languages: List<String> = emptyList(),
    val updateCount: Int = 0,
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val installer: ExtensionInstaller,
    private val appLanguages: io.grimoire.app.data.preferences.AppLanguagePreferences,
    private val extensionManager: io.grimoire.app.extension.ExtensionManager,
    githubAuthStore: GitHubAuthStore,
) : ViewModel() {

    val items: StateFlow<List<ExtensionItem>> = repository.items
    val isFetching: StateFlow<Boolean> = repository.isFetching
    val fetchError: StateFlow<String?> = repository.fetchError
    val authRequiredRepos: StateFlow<List<RepoEntity>> = repository.authRequiredRepos

    /** Login of the currently-connected GitHub account, or null if disconnected. */
    val githubLogin: StateFlow<String?> = githubAuthStore.account
        .map { it?.login }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            githubAuthStore.account.value?.login,
        )

    val repos: StateFlow<List<RepoEntity>> = repository.reposFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates.asStateFlow()

    /** Non-null when an APK is ready to be installed. Screen consumes this and clears it. */
    private val _pendingInstall = MutableStateFlow<File?>(null)
    val pendingInstall: StateFlow<File?> = _pendingInstall.asStateFlow()

    /**
     * True when a GitHub 403 rate limit was hit (during refresh or a download).
     * The screen shows a prompt to connect GitHub / try later; dismissing it
     * clears this until the next time the limit is hit.
     */
    private val _rateLimitPrompt = MutableStateFlow(false)
    val rateLimitPrompt: StateFlow<Boolean> = _rateLimitPrompt.asStateFlow()

    private val _nameFilter = MutableStateFlow("")
    val nameFilter: StateFlow<String> = _nameFilter.asStateFlow()

    /**
     * The app-wide content-language selection — the same single list the Browse
     * settings picker edits and multi-language sources filter by. Editing it
     * here (via the chips) edits it everywhere. Empty = show all.
     */
    val enabledLanguages: StateFlow<Set<Language>> =
        appLanguages.enabled.stateIn(viewModelScope)

    private val _adultFilter = MutableStateFlow(AdultFilter.ALL)
    val adultFilter: StateFlow<AdultFilter> = _adultFilter.asStateFlow()

    private val _section = MutableStateFlow(ExtensionSection.ALL)
    val section: StateFlow<ExtensionSection> = _section.asStateFlow()

    fun setNameFilter(query: String) { _nameFilter.value = query }
    fun setAdultFilter(value: AdultFilter) { _adultFilter.value = value }
    fun setSection(value: ExtensionSection) { _section.value = value }

    /**
     * Toggle a language in the *global* content-language selection. [allLanguages]
     * is the full list of codes on offer, so an empty (= "all") selection can be
     * materialised before removing one. Never collapses back to empty — the global
     * set outlives this screen's language list, so "everything currently listed"
     * is not the same as "no filter". Reapplies to loaded sources immediately.
     */
    fun toggleLanguage(lang: String, allLanguages: List<String>) = viewModelScope.launch {
        val language = ContentLanguages.parse(lang) ?: return@launch
        val current = enabledLanguages.value.ifEmpty {
            allLanguages.mapNotNullTo(mutableSetOf()) { ContentLanguages.parse(it) }
        }
        val next = if (language in current) current - language else current + language
        appLanguages.enabled.set(next)
        extensionManager.reapplyAllPreferences()
    }

    /** Reset the global selection to "no filter — every language". */
    fun clearLanguageFilter() = viewModelScope.launch {
        appLanguages.enabled.set(emptySet())
        extensionManager.reapplyAllPreferences()
    }

    @OptIn(FlowPreview::class)
    val ui: StateFlow<ExtensionsUi> = combine(
        items,
        _nameFilter.debounce(120L),
        enabledLanguages,
        _adultFilter,
    ) { all, query, enabledLangs, adultFilter ->
        val q = query.trim()
        // Multi-language ("all") and unclassifiable codes are always shown — hide
        // only what we can positively say is a language the user didn't pick.
        fun matchesLanguage(code: String): Boolean {
            if (enabledLangs.isEmpty()) return true
            val lang = Language.fromCode(code)
            return lang == Language.MULTI || lang == Language.UNKNOWN || lang in enabledLangs
        }
        fun matches(item: ExtensionItem): Boolean =
            (q.isBlank() || item.name.contains(q, ignoreCase = true)) &&
                matchesLanguage(item.lang) &&
                when (adultFilter) {
                    AdultFilter.ALL -> true
                    AdultFilter.HIDE -> !item.isAdult
                    AdultFilter.ONLY -> item.isAdult
                }

        // Chip list: real codes only — "all" sources are always visible, so they
        // get no chip.
        val languages = all.map { it.lang.lowercase() }
            .filter { it != "all" }
            .distinct()
            .sorted()
        val updateCount = all.filterIsInstance<ExtensionItem.Installed>().count { it.hasUpdate }

        val installed = (all.filterIsInstance<ExtensionItem.Installed>() +
            all.filterIsInstance<ExtensionItem.InstalledOnly>())
            .filter(::matches)
            .sortedBy { it.name.lowercase() }
        val available = all.filterIsInstance<ExtensionItem.Available>()
            .filter(::matches)
            .sortedBy { it.name.lowercase() }
        val updates = installed.filter { it is ExtensionItem.Installed && it.hasUpdate }

        ExtensionsUi(
            installed = installed,
            available = available,
            updates = updates,
            languages = languages,
            updateCount = updateCount,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtensionsUi())

    /** Download every installed extension that has a newer version available. */
    fun updateAll() {
        items.value.filterIsInstance<ExtensionItem.Installed>()
            .filter { it.hasUpdate }
            .forEach { update(it) }
    }

    init {
        refresh()
        viewModelScope.launch {
            repository.rateLimited.collect { if (it) _rateLimitPrompt.value = true }
        }
    }

    fun dismissRateLimitPrompt() {
        _rateLimitPrompt.value = false
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun install(item: ExtensionItem.Available) =
        doInstall(item.packageName, item.remote.url, item.remote.sha256)

    fun update(item: ExtensionItem.Installed) =
        doInstall(item.packageName, item.apkUrl, item.remote.sha256)

    private fun doInstall(pkg: String, apkUrl: String, expectedSha256: String?) {
        viewModelScope.launch {
            _installStates.update { it + (pkg to InstallState.Downloading(0L, 0L)) }
            installer.download(apkUrl, pkg, expectedSha256) { read, total ->
                _installStates.update { it + (pkg to InstallState.Downloading(read, total)) }
            }
                .onSuccess { file ->
                    _installStates.update { it - pkg }
                    _pendingInstall.value = file
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is HashMismatchException ->
                            "Download verification failed — try again or switch networks"
                        is GitHubRateLimitException -> {
                            _rateLimitPrompt.value = true
                            "GitHub rate limit reached"
                        }
                        else -> e.message ?: "Download failed"
                    }
                    _installStates.update { it + (pkg to InstallState.Error(msg)) }
                }
        }
    }

    /** Called by the Screen immediately after launching the install intent. */
    fun consumePendingInstall() {
        _pendingInstall.value = null
    }

    /** Called by the Screen's ActivityResult callback after the system install dialog finishes. */
    fun onInstallResult() {
        viewModelScope.launch { repository.refresh() }
    }

    fun dismissInstallError(pkg: String) {
        _installStates.update { it - pkg }
    }

    fun addRepo(name: String, url: String) {
        viewModelScope.launch {
            repository.addRepo(name.trim(), url.trim())
            repository.refresh()
        }
    }

    fun updateRepo(repo: RepoEntity, name: String, url: String) {
        viewModelScope.launch {
            repository.updateRepo(repo.copy(name = name.trim(), indexUrl = url.trim()))
            repository.refresh()
        }
    }

    fun toggleRepo(repo: RepoEntity) {
        viewModelScope.launch {
            repository.updateRepo(repo.copy(enabled = !repo.enabled))
            repository.refresh()
        }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch {
            repository.deleteRepo(repo)
            repository.refresh()
        }
    }
}
