package io.grimoire.app.ui.screen.settings.tts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.preferences.TtsPreferences
import io.grimoire.app.data.preferences.stateIn
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.tts.DeviceTtsEngine
import io.grimoire.app.data.tts.ElevenLabsApi
import io.grimoire.app.data.tts.ElevenLabsUsage
import io.grimoire.app.data.tts.TtsController
import io.grimoire.app.data.tts.TtsEngineConfig
import io.grimoire.app.data.tts.TtsEngineType
import io.grimoire.app.data.tts.TtsLanguageResolver
import io.grimoire.app.data.tts.TtsPreviewManager
import io.grimoire.app.data.tts.TtsVoice
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Voice list state for [TtsVoicePickerScreen]. */
sealed interface VoiceListState {
    data object Loading : VoiceListState
    data object NeedsApiKey : VoiceListState
    data class Error(val message: String) : VoiceListState
    data class Loaded(val voices: List<TtsVoice>) : VoiceListState
}

/** ElevenLabs character-quota state shown on the main settings screen. */
sealed interface UsageState {
    data object Idle : UsageState
    data object Loading : UsageState
    data class Error(val message: String) : UsageState
    data class Loaded(val usage: ElevenLabsUsage) : UsageState
}

/**
 * Backs both the main Text-to-speech settings screen and the per-language voice
 * picker. When opened as the picker, [language] is supplied as a navigation argument.
 */
@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prefs: TtsPreferences,
    private val novelDao: NovelDao,
    private val deviceEngine: DeviceTtsEngine,
    private val elevenLabsApi: ElevenLabsApi,
    private val languageResolver: TtsLanguageResolver,
    private val ttsController: TtsController,
    private val previewManager: TtsPreviewManager,
) : ViewModel() {

    /** Non-null only on the voice-picker screen. */
    val language: String? = savedStateHandle["lang"]

    val enabled: StateFlow<Boolean> = prefs.enabled.stateIn(viewModelScope)
    val engine: StateFlow<TtsEngineType> = prefs.engine.stateIn(viewModelScope)
    val speechRate: StateFlow<Int> = prefs.speechRate.stateIn(viewModelScope)
    val pitch: StateFlow<Int> = prefs.pitch.stateIn(viewModelScope)
    val autoAdvance: StateFlow<Boolean> = prefs.autoAdvance.stateIn(viewModelScope)
    val apiKey: StateFlow<String> = prefs.elevenLabsApiKey.stateIn(viewModelScope)

    private val deviceVoices: StateFlow<Map<String, String>> =
        prefs.deviceVoiceByLanguage.stateIn(viewModelScope)
    private val cloudVoices: StateFlow<Map<String, String>> =
        prefs.cloudVoiceByLanguage.stateIn(viewModelScope)

    private val _languages = MutableStateFlow(ContentLanguages.ALL)
    val languages: StateFlow<List<String>> = _languages.asStateFlow()

    /** Voice id chosen for [language] under the active engine, or null for system default. */
    val selectedVoiceId: StateFlow<String?> =
        combine(engine, deviceVoices, cloudVoices) { eng, device, cloud ->
            val lang = language ?: return@combine null
            val map = if (eng == TtsEngineType.ELEVENLABS) cloud else device
            map[ContentLanguages.normalize(lang)]
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Subtitle text for a language row on the main screen (the chosen voice name). */
    fun voiceSummary(language: String, voices: Map<String, String>): String {
        val id = voices[ContentLanguages.normalize(language)]
        return id?.let { "Custom voice selected" } ?: "System default"
    }

    val deviceVoiceMap: StateFlow<Map<String, String>> get() = deviceVoices
    val cloudVoiceMap: StateFlow<Map<String, String>> get() = cloudVoices

    private val _voiceState = MutableStateFlow<VoiceListState>(VoiceListState.Loading)
    val voiceState: StateFlow<VoiceListState> = _voiceState.asStateFlow()

    /** Which picker row (if any) is currently auditioning a sample, and its phase. */
    val previewState: StateFlow<TtsPreviewManager.State> = previewManager.state

    private val _usageState = MutableStateFlow<UsageState>(UsageState.Idle)
    val usageState: StateFlow<UsageState> = _usageState.asStateFlow()

    init {
        viewModelScope.launch {
            val novelLanguages = runCatching { novelDao.getAll() }.getOrDefault(emptyList())
                .mapNotNull { it.language?.trim()?.takeIf { l -> l.isNotEmpty() } }
            _languages.value = (ContentLanguages.ALL + novelLanguages)
                .distinctBy { ContentLanguages.normalize(it) }
        }
        if (language != null) loadVoices()
    }

    fun setEnabled(value: Boolean) = viewModelScope.launch {
        prefs.enabled.set(value)
        if (!value) ttsController.stop()
    }

    fun setEngine(value: TtsEngineType) = viewModelScope.launch { prefs.engine.set(value) }

    fun setSpeechRate(percent: Int) = viewModelScope.launch {
        prefs.speechRate.set(percent.coerceIn(25, 300))
    }

    fun setPitch(percent: Int) = viewModelScope.launch {
        prefs.pitch.set(percent.coerceIn(50, 200))
    }

    fun setAutoAdvance(value: Boolean) = viewModelScope.launch { prefs.autoAdvance.set(value) }

    fun setApiKey(value: String) = viewModelScope.launch {
        prefs.elevenLabsApiKey.set(value.trim())
    }

    /** Loads ElevenLabs credit usage; no-op (Idle) when no API key is set. */
    fun loadUsage() {
        viewModelScope.launch {
            val key = prefs.elevenLabsApiKey.changes().first()
            if (key.isBlank()) {
                _usageState.value = UsageState.Idle
                return@launch
            }
            _usageState.value = UsageState.Loading
            _usageState.value = runCatching {
                UsageState.Loaded(elevenLabsApi.getUsage(key))
            }.getOrElse { UsageState.Error(it.message ?: "Could not load usage") }
        }
    }

    fun loadVoices() {
        val lang = language ?: return
        _voiceState.value = VoiceListState.Loading
        viewModelScope.launch {
            val isCloud = prefs.engine.changes().first() == TtsEngineType.ELEVENLABS
            _voiceState.value = runCatching {
                if (isCloud) {
                    val key = prefs.elevenLabsApiKey.changes().first()
                    if (key.isBlank()) return@runCatching VoiceListState.NeedsApiKey
                    VoiceListState.Loaded(elevenLabsApi.listVoices(key))
                } else {
                    val localeTag = languageResolver.resolveLocale(lang).toLanguageTag()
                    VoiceListState.Loaded(deviceEngine.availableVoices(localeTag))
                }
            }.getOrElse { VoiceListState.Error(it.message ?: "Could not load voices") }
        }
    }

    fun selectVoice(voiceId: String?) {
        val lang = language ?: return
        val key = ContentLanguages.normalize(lang)
        viewModelScope.launch {
            val isCloud = prefs.engine.changes().first() == TtsEngineType.ELEVENLABS
            val pref = if (isCloud) prefs.cloudVoiceByLanguage else prefs.deviceVoiceByLanguage
            val updated = pref.changes().first().toMutableMap()
            if (voiceId == null) updated.remove(key) else updated[key] = voiceId
            pref.set(updated)
        }
    }

    /** Picker-row identity for [previewState]: a voice's id, or [SYSTEM_DEFAULT_KEY]. */
    fun previewKey(voiceId: String?): String = voiceId ?: TtsPreviewManager.SYSTEM_DEFAULT_KEY

    /**
     * Auditions [voiceId] (null = system default) with the current engine/rate/pitch, speaking a
     * short sample in the picker's language. Toggling the already-playing row stops it.
     */
    fun previewVoice(voiceId: String?) {
        viewModelScope.launch {
            val isCloud = prefs.engine.changes().first() == TtsEngineType.ELEVENLABS
            val locale = languageResolver.resolveLocale(language)
            val config = TtsEngineConfig(
                voiceId = voiceId,
                localeTag = locale.toLanguageTag(),
                rate = prefs.speechRate.changes().first() / 100f,
                pitch = prefs.pitch.changes().first() / 100f,
                elevenLabsApiKey = prefs.elevenLabsApiKey.changes().first(),
                elevenLabsModel = prefs.elevenLabsModel.changes().first(),
            )
            previewManager.toggle(
                key = previewKey(voiceId),
                engine = if (isCloud) TtsEngineType.ELEVENLABS else TtsEngineType.DEVICE,
                config = config,
                sampleText = previewSampleFor(locale.language),
            )
        }
    }

    fun stopPreview() = previewManager.stop()

    fun clearPreviewError() = previewManager.clearError()

    override fun onCleared() {
        previewManager.stop()
    }
}

/** A short, language-appropriate sample sentence to audition a voice with. */
private fun previewSampleFor(languageCode: String): String = when (languageCode) {
    "es" -> "Esta es una muestra de la voz seleccionada."
    "fr" -> "Ceci est un aperçu de la voix sélectionnée."
    "de" -> "Dies ist eine Hörprobe der ausgewählten Stimme."
    "it" -> "Questa è un'anteprima della voce selezionata."
    "pt" -> "Esta é uma amostra da voz selecionada."
    "ru" -> "Это пример выбранного голоса."
    "ja" -> "これは選択した音声のサンプルです。"
    "ko" -> "선택한 음성의 미리듣기입니다."
    "zh" -> "这是所选语音的预览。"
    "ar" -> "هذه عينة من الصوت المحدد."
    "hi" -> "यह चयनित आवाज़ का एक नमूना है।"
    "id" -> "Ini adalah contoh suara yang dipilih."
    else -> "This is a preview of the selected voice."
}
