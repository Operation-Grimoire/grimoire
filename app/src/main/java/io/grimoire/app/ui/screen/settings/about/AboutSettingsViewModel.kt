package io.grimoire.app.ui.screen.settings.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val version: String, val releaseUrl: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

private const val RELEASES_API = "https://api.github.com/repos/Operation-Grimoire/grimoire/releases/latest"

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Checking
            runCatching {
                val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val tagName = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)
                    ?.groupValues?.get(1) ?: error("No tag_name in response")
                val htmlUrl = Regex(""""html_url"\s*:\s*"([^"]+)"""").find(json)
                    ?.groupValues?.get(1) ?: error("No html_url in response")

                val current = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
                }.getOrDefault("0")

                if (isNewer(tagName.trimStart('v'), current.trimStart('v'))) {
                    _updateState.value = UpdateState.Available(tagName, htmlUrl)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            }.onFailure { e ->
                _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val diff = (l.getOrElse(i) { 0 }) - (c.getOrElse(i) { 0 })
            if (diff != 0) return diff > 0
        }
        return false
    }
}
