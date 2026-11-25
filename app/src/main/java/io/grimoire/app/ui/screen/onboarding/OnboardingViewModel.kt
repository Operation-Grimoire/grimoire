package io.grimoire.app.ui.screen.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.backup.BackupManager
import io.grimoire.app.data.backup.RestoreResult
import io.grimoire.app.data.crash.AptabaseCrashReporter
import io.grimoire.app.data.preferences.AnalyticsPreferences
import io.grimoire.app.data.preferences.AppLanguagePreferences
import io.grimoire.ui.theme.ColorTheme
import io.grimoire.app.data.preferences.OnboardingPreferences
import io.grimoire.ui.theme.ThemeMode
import io.grimoire.app.data.preferences.UiPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.extension.ExtensionManager
import io.grimoire.app.ui.tour.TourController
import io.grimoire.app.ui.tour.TourId
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface OnboardingRestoreState {
    data object Idle : OnboardingRestoreState
    data object Restoring : OnboardingRestoreState
    data class Success(val novelCount: Int, val chapterCount: Int) : OnboardingRestoreState
    data class Error(val message: String) : OnboardingRestoreState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appLanguages: AppLanguagePreferences,
    private val onboardingPreferences: OnboardingPreferences,
    private val uiPreferences: UiPreferences,
    private val analyticsPreferences: AnalyticsPreferences,
    private val crashReporter: AptabaseCrashReporter,
    private val backupManager: BackupManager,
    private val extensionManager: ExtensionManager,
    private val tourController: TourController,
) : ViewModel() {

    /**
     * Kept true while the user is viewing a backup restore result inside onboarding,
     * so restoring a backup whose preferences include `onboarding_done = true` doesn't
     * yank the user out of onboarding before they see the summary.
     */
    private val holdForRestore = MutableStateFlow(false)

    /** null while the pref loads (render nothing to avoid a flash), then false/true. */
    val done: StateFlow<Boolean?> = combine(
        onboardingPreferences.done.changes().map<Boolean, Boolean?> { it },
        holdForRestore,
    ) { persistedDone, holding ->
        when {
            persistedDone == null -> null
            holding -> false
            else -> persistedDone
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selected = MutableStateFlow<Set<Language>>(emptySet())
    val selected: StateFlow<Set<Language>> = _selected.asStateFlow()

    private val _languageQuery = MutableStateFlow("")
    val languageQuery: StateFlow<String> = _languageQuery.asStateFlow()

    val filteredLanguages: StateFlow<List<Language>> =
        _languageQuery
            .debounce(120L)
            .map { q -> filterLanguages(ContentLanguages.SELECTABLE, q) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContentLanguages.SELECTABLE)

    val themeMode: StateFlow<ThemeMode> = uiPreferences.themeMode.stateIn(viewModelScope)
    val useDynamicColor: StateFlow<Boolean> = uiPreferences.useDynamicColor.stateIn(viewModelScope)
    val colorTheme: StateFlow<ColorTheme> = uiPreferences.colorTheme.stateIn(viewModelScope)

    val crashReportsEnabled: StateFlow<Boolean> =
        analyticsPreferences.crashReportsEnabled.stateIn(viewModelScope)
    val usageAnalyticsEnabled: StateFlow<Boolean> =
        analyticsPreferences.usageAnalyticsEnabled.stateIn(viewModelScope)

    private val _restoreState = MutableStateFlow<OnboardingRestoreState>(OnboardingRestoreState.Idle)
    val restoreState: StateFlow<OnboardingRestoreState> = _restoreState.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed from anything already stored (update installs), else the system
            // locale + English — a sane default the user can just accept.
            val stored = appLanguages.enabled.changes().first()
            _selected.value = stored.ifEmpty {
                setOfNotNull(Language.EN, ContentLanguages.parse(Locale.getDefault().language))
            }
        }
    }

    fun setLanguageQuery(query: String) {
        _languageQuery.value = query
    }

    fun toggle(language: Language) {
        _selected.update { if (language in it) it - language else it + language }
    }

    fun clearLanguages() {
        _selected.value = emptySet()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        uiPreferences.themeMode.set(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        uiPreferences.useDynamicColor.set(enabled)
    }

    fun setColorTheme(theme: ColorTheme) = viewModelScope.launch {
        uiPreferences.colorTheme.set(theme)
    }

    fun setCrashReports(enabled: Boolean) {
        crashReporter.selectCollection(enabled)
        viewModelScope.launch { analyticsPreferences.crashReportsEnabled.set(enabled) }
    }

    fun setUsageAnalytics(enabled: Boolean) = viewModelScope.launch {
        analyticsPreferences.usageAnalyticsEnabled.set(enabled)
    }

    fun restoreFrom(fileUri: Uri) = viewModelScope.launch {
        holdForRestore.value = true
        _restoreState.value = OnboardingRestoreState.Restoring
        when (val result = backupManager.restoreFrom(fileUri)) {
            is RestoreResult.Success -> {
                _selected.value = appLanguages.enabled.changes().first()
                _restoreState.value = OnboardingRestoreState.Success(
                    novelCount = result.novelCount,
                    chapterCount = result.chapterCount,
                )
            }
            is RestoreResult.Failure -> {
                holdForRestore.value = false
                _restoreState.value = OnboardingRestoreState.Error(result.message)
            }
        }
    }

    fun dismissRestoreMessage() {
        _restoreState.value = OnboardingRestoreState.Idle
    }

    fun enterAfterRestore() = viewModelScope.launch {
        if (_restoreState.value is OnboardingRestoreState.Restoring) return@launch
        extensionManager.reapplyAllPreferences()
        tourController.markCompleted(TourId.Welcome)
        holdForRestore.value = false
        onboardingPreferences.done.set(true)
    }

    fun skip() = finish(startTour = false)

    fun finish(
        startTour: Boolean = false,
        onBeforeEnter: () -> Unit = {},
    ) = viewModelScope.launch {
        if (_restoreState.value is OnboardingRestoreState.Restoring) return@launch
        appLanguages.enabled.set(_selected.value)
        extensionManager.reapplyAllPreferences()
        if (startTour) {
            tourController.rearm(TourId.Welcome)
        } else {
            tourController.markCompleted(TourId.Welcome)
        }
        onBeforeEnter()
        holdForRestore.value = false
        onboardingPreferences.done.set(true)
    }
}

internal fun filterLanguages(languages: List<Language>, query: String): List<Language> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return languages
    return languages.filter { lang ->
        lang.displayName.contains(trimmed, ignoreCase = true) ||
            lang.nativeName.contains(trimmed, ignoreCase = true) ||
            lang.code.contains(trimmed, ignoreCase = true)
    }
}
