package io.grimoire.app.ui.screen.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.UpdateIssueDao
import io.grimoire.app.data.local.entity.UpdateIssueEntity
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UpdateIssuesViewModel @Inject constructor(
    updateIssueDao: UpdateIssueDao,
    authManager: HiddenCategoriesAuthManager,
) : ViewModel() {

    val issues: StateFlow<List<UpdateIssueEntity>> = authManager.isUnlocked
        .map { !it }.distinctUntilChanged()
        .flatMapLatest { updateIssueDao.getAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
