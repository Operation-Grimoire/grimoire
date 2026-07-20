package io.grimoire.app.ui.screen.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.storage.StorageBreakdown
import io.grimoire.app.data.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataSettingsState(
    val breakdown: StorageBreakdown? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: DataSettingsMessage? = null,
)

sealed interface DataSettingsMessage {
    data object CoverCacheCleared : DataSettingsMessage
    data class BrowseDataCleared(val count: Int) : DataSettingsMessage
    data class InstallerFilesCleared(val count: Int) : DataSettingsMessage
}

@HiltViewModel
class DataSettingsViewModel @Inject constructor(
    private val storageManager: StorageManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DataSettingsState())
    val state: StateFlow<DataSettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val breakdown = storageManager.measure()
        _state.value = _state.value.copy(breakdown = breakdown, loading = false)
    }

    fun clearCoverCache() = runAction(DataSettingsMessage.CoverCacheCleared) {
        storageManager.clearCoverCache()
    }

    fun clearBrowseData() = runAction({ removed -> DataSettingsMessage.BrowseDataCleared(removed) }) {
        storageManager.clearBrowseData()
    }

    fun clearInstallerFiles() = runAction({ removed -> DataSettingsMessage.InstallerFilesCleared(removed) }) {
        storageManager.clearInstallerFiles()
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun runAction(message: DataSettingsMessage, action: suspend () -> Unit) =
        runAction({ message }, action)

    private fun <T> runAction(message: (T) -> DataSettingsMessage, action: suspend () -> T) = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true)
        val result = action()
        val fresh = storageManager.measure()
        _state.value = _state.value.copy(
            breakdown = fresh,
            busy = false,
            message = message(result),
        )
    }
}
