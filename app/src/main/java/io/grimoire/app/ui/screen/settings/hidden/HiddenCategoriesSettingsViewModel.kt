package io.grimoire.app.ui.screen.settings.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.preferences.LibraryPreferences
import io.grimoire.app.domain.auth.HiddenCategoriesAuthManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiddenCategoriesSettingsViewModel @Inject constructor(
    private val authManager: HiddenCategoriesAuthManager,
    private val categoryDao: CategoryDao,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    val isUnlocked: StateFlow<Boolean> = authManager.isUnlocked

    val hasPin: StateFlow<Boolean> = authManager.hasPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricEnabled: StateFlow<Boolean> = authManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val includeHiddenInAll: StateFlow<Boolean> = libraryPreferences.includeHiddenInAll.changes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setIncludeHiddenInAll(value: Boolean) = viewModelScope.launch {
        libraryPreferences.includeHiddenInAll.set(value)
    }

    fun setPin(pin: String) = viewModelScope.launch {
        authManager.setPin(pin)
        authManager.unlock()
    }

    suspend fun verifyAndUnlock(pin: String): Boolean {
        val ok = authManager.verifyPin(pin)
        if (ok) authManager.unlock()
        return ok
    }

    fun unlockFromBiometric() = authManager.unlock()

    fun lock() = authManager.lock()

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        authManager.setBiometricEnabled(enabled)
    }

    fun clearPin() = viewModelScope.launch {
        authManager.clearPin()
    }

    fun setCategoryHidden(category: CategoryEntity, hidden: Boolean) = viewModelScope.launch {
        categoryDao.upsert(category.copy(isHidden = hidden))
    }
}
