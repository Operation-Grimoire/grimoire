package io.grimoire.app.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide holder for the in-flight app update download so the foreground
 * service that runs the download and the update dialog that displays it observe
 * the same progress.
 */
@Singleton
class AppUpdateDownloadStore @Inject constructor() {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun set(value: DownloadState) {
        _state.value = value
    }
}
