package io.grimoire.app.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.source.SourceInfo
import io.grimoire.app.data.libraryupdate.LibraryUpdater
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateIssuesViewModel @Inject constructor(
    private val updateIssueDao: UpdateIssueDao,
    private val updater: LibraryUpdater,
    private val extensionManager: ExtensionManager,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    val issues: StateFlow<List<UpdateIssueEntity>> = authManager.isUnlocked
        .map { !it }.distinctUntilChanged()
        .flatMapLatest { updateIssueDao.getAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Novel ids with a single-novel refresh in flight — rows show a spinner. */
    private val _retrying = MutableStateFlow<Set<Long>>(emptySet())
    val retrying: StateFlow<Set<Long>> = _retrying.asStateFlow()

    /** Re-runs the failed refresh; a clean run deletes the row via the Flow. */
    fun retry(issue: UpdateIssueEntity) {
        if (issue.novelId in _retrying.value) return
        viewModelScope.launch { retryOne(issue.novelId) }
    }

    /** Sequential sweep over everything listed, so one bad source can't starve the rest. */
    fun retryAll() {
        val targets = issues.value.map { it.novelId } - _retrying.value
        if (targets.isEmpty()) return
        viewModelScope.launch { targets.forEach { retryOne(it) } }
    }

    /**
     * Retries just the Cloudflare-blocked rows — fired when the user comes
     * back from the WebView, where they presumably solved the challenge.
     */
    fun retryCloudflareIssues() {
        val targets = issues.value
            .filter { issueKindFor(it.message) == UpdateIssueKind.CLOUDFLARE }
            .map { it.novelId } - _retrying.value
        if (targets.isEmpty()) return
        viewModelScope.launch { targets.forEach { retryOne(it) } }
    }

    private suspend fun retryOne(novelId: Long) {
        _retrying.update { it + novelId }
        try {
            updater.updateNovel(novelId)
        } finally {
            _retrying.update { it - novelId }
        }
    }

    /** Clears the row; it returns on the next sync if the problem persists. */
    fun dismiss(issue: UpdateIssueEntity) {
        viewModelScope.launch { updateIssueDao.clearForNovel(issue.novelId) }
    }

    fun dismissAll() {
        val all = issues.value
        viewModelScope.launch { all.forEach { updateIssueDao.clearForNovel(it.novelId) } }
    }

    /** True while any listed issue is a Cloudflare block — arms the WebView-return auto-retry. */
    fun hasCloudflareIssues(): Boolean =
        issues.value.any { issueKindFor(it.message) == UpdateIssueKind.CLOUDFLARE }

    /**
     * Absolute web URL for the issue's novel, for the open-in-WebView action.
     * The stored URL is source-relative for most extensions; prefix the
     * source's declared baseUrl (same resolution the novel detail screen uses).
     */
    fun webUrlFor(issue: UpdateIssueEntity): String {
        if (issue.novelUrl.startsWith("http")) return issue.novelUrl
        val baseUrl = extensionManager.extensions.value
            .firstOrNull { it.info.packageName == issue.sourcePackage }
            ?.source?.javaClass?.getAnnotation(SourceInfo::class.java)?.baseUrl
            ?: return issue.novelUrl
        return "$baseUrl${issue.novelUrl}"
    }
}
