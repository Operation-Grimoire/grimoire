package io.grimoire.app.ui.screen.settings.source

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.source.ConfigurableSource
import io.grimoire.api.source.MultiLanguageSource
import io.grimoire.api.source.SourcePreference
import io.grimoire.api.source.WebViewLoginSource
import io.grimoire.app.ui.screen.webview.SOURCE_LOGIN_RESULT_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.app.data.preferences.SourceSettingsPreferences
import io.grimoire.app.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val extensionManager: ExtensionManager,
    private val sourceSettings: SourceSettingsPreferences,
    private val appLanguages: AppLanguagePreferences,
) : ViewModel() {

    val pkg: String = checkNotNull(savedStateHandle["pkg"])

    private val loaded get() = extensionManager.extensions.value
        .firstOrNull { it.info.packageName == pkg }

    val sourceName: String =
        loaded?.info?.label?.substringAfter(": ", loaded?.info?.label.orEmpty()).orEmpty()
            .ifEmpty { pkg }

    val preferences: List<SourcePreference> =
        (loaded?.source as? ConfigurableSource)?.getPreferences().orEmpty()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Whether the source supports validating its configuration (e.g. login). */
    val canValidate: Boolean = loaded?.source is ConfigurableSource

    /** Whether the source signs in through a WebView (locked-chapter access). */
    val supportsWebViewLogin: Boolean = loaded?.source is WebViewLoginSource

    enum class LoginUiState { UNKNOWN, SIGNED_IN, SIGNED_OUT }

    private val _loginState = MutableStateFlow(LoginUiState.UNKNOWN)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    /** Multi-language sources get the dedicated content-language picker row. */
    val isMultiLanguage: Boolean =
        loaded?.source is MultiLanguageSource || loaded?.source?.lang == "all"

    /**
     * Summary shown on the "Content languages" nav row. Reflects the effective
     * set the source is using right now — "Using global · 3 languages",
     * "Using global · no filter", "Override · 2 languages", etc.
     */
    val languageSummary: StateFlow<String> = combine(
        sourceSettings.contentLanguagesOverride(pkg).changes(),
        sourceSettings.contentLanguages(pkg).changes(),
        appLanguages.enabled.changes(),
    ) { override, perSource, global ->
        val effective = if (override) perSource else global
        val prefix = if (override) "Override" else "Using global"
        val tail = when {
            effective.isEmpty() -> "no filter"
            effective.size == 1 -> "1 language"
            else -> "${effective.size} languages"
        }
        "$prefix · $tail"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Using global · no filter")

    sealed interface ValidationState {
        data object Idle : ValidationState
        data object Running : ValidationState
        data class Done(val success: Boolean, val message: String) : ValidationState
    }

    private val _validation = MutableStateFlow<ValidationState>(ValidationState.Idle)
    val validation: StateFlow<ValidationState> = _validation.asStateFlow()

    init {
        viewModelScope.launch {
            val keys = preferences.map { it.key }
            val stored = sourceSettings.snapshot(pkg, keys)
            _values.value = preferences.associate { pref ->
                val current = stored[pref.key].orEmpty()
                pref.key to current.ifEmpty { defaultValue(pref) }
            }
        }
        if (supportsWebViewLogin) {
            // Poll on creation: navigating back from the login WebView recreates
            // this VM, and the source confirms its session over the network so a
            // single immediate check right after login often reports signed-out.
            checkLoginState(retry = true)
            // Re-check after returning from the login WebView (it sets this key).
            viewModelScope.launch {
                savedStateHandle.getStateFlow(SOURCE_LOGIN_RESULT_KEY, false).collect { done ->
                    if (done) {
                        savedStateHandle[SOURCE_LOGIN_RESULT_KEY] = false
                        checkLoginState(retry = true)
                    }
                }
            }
        }
    }

    private var loginCheckJob: Job? = null

    /**
     * Refreshes the source's sign-in state.
     *
     * A source confirms its session over the network, and right after the login
     * WebView returns the server may not report the session as live on the very
     * first check. When [retry] is set we keep polling with backoff for a few
     * seconds so a freshly-completed login is picked up. The first result is
     * surfaced immediately (so the row never sticks on "Checking…"); later
     * attempts can still upgrade a signed-out result to signed-in.
     */
    fun checkLoginState(retry: Boolean = false) {
        if (loaded?.source !is WebViewLoginSource) return
        loginCheckJob?.cancel()
        loginCheckJob = viewModelScope.launch {
            // First entry is immediate; the rest back off to ~7s total.
            val waits = if (retry) {
                longArrayOf(0, 300, 500, 800, 1200, 1800, 2500)
            } else {
                longArrayOf(0)
            }
            for ((i, wait) in waits.withIndex()) {
                if (wait > 0) delay(wait)
                val src = loaded?.source as? WebViewLoginSource ?: return@launch
                val signedIn = withContext(Dispatchers.IO) {
                    runCatching { src.isLoggedIn() }.getOrDefault(false)
                }
                if (signedIn) {
                    _loginState.value = LoginUiState.SIGNED_IN
                    return@launch
                }
                // Show "Not signed in" after the first miss rather than holding
                // the row on "Checking…" for the whole poll; keep polling in
                // case the session comes up a moment later (just-logged-in case).
                if (i == 0) _loginState.value = LoginUiState.SIGNED_OUT
            }
        }
    }

    fun logout() {
        val src = loaded?.source as? WebViewLoginSource ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { src.logout() } }
            checkLoginState()
        }
    }

    private fun defaultValue(pref: SourcePreference): String = when (pref) {
        is SourcePreference.EditText -> pref.default
        is SourcePreference.Switch -> pref.default.toString()
    }

    fun update(key: String, value: String) {
        _values.update { it + (key to value) }
        _saved.value = false
        _validation.value = ValidationState.Idle
    }

    /**
     * Pushes the pending (unsaved) values into the source and asks it to
     * validate them, so the user can confirm e.g. their login works before
     * relying on it.
     */
    fun validate() {
        val configurable = loaded?.source as? ConfigurableSource ?: return
        if (_validation.value is ValidationState.Running) return
        _validation.value = ValidationState.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    configurable.setPreferences(_values.value)
                    configurable.validateConfiguration()
                }
            }
            _validation.value = result.fold(
                onSuccess = { res ->
                    if (res == null) {
                        ValidationState.Done(true, "This source has nothing to validate.")
                    } else {
                        ValidationState.Done(res.success, res.message)
                    }
                },
                onFailure = { e ->
                    ValidationState.Done(false, e.message ?: "Validation failed.")
                },
            )
        }
    }

    fun save() {
        val snapshot = _values.value
        viewModelScope.launch {
            snapshot.forEach { (key, value) -> sourceSettings.pref(pkg, key).set(value) }
            extensionManager.reapplyPreferences(pkg)
            _saved.value = true
        }
    }
}
