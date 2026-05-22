package io.grimoire.app.data.tts

/** Which speech backend produces the audio. */
enum class TtsEngineType { DEVICE, ELEVENLABS }

/** High-level playback lifecycle, observed by the UI and the foreground service. */
enum class TtsPlaybackState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

/** Metadata about the chapter currently being read aloud. */
data class TtsNowPlaying(
    val pkg: String,
    val novelId: Long,
    val novelTitle: String,
    val chapterUrl: String,
    val chapterName: String,
)

/** Position within the current chapter, used for highlighting and resume. */
data class TtsProgress(
    val currentUtteranceIndex: Int = 0,
    val totalUtterances: Int = 0,
    /** [io.grimoire.api.model.NovelPage.index] of the paragraph being spoken, or -1. */
    val currentPageIndex: Int = -1,
)

/** One speakable chunk: a paragraph, or a sentence-bounded slice of a long paragraph. */
data class Utterance(
    val pageIndex: Int,
    val text: String,
)

/** A voice a [TtsEngine] can speak with, shown in the per-language voice picker. */
data class TtsVoice(
    /** Stable id: [android.speech.tts.Voice.getName] for device, voice id for ElevenLabs. */
    val id: String,
    val displayName: String,
    val detail: String? = null,
    val engine: TtsEngineType,
    val localeTag: String? = null,
    val needsNetwork: Boolean = false,
    val notInstalled: Boolean = false,
)

/** Everything an engine needs to start speaking; irrelevant fields are ignored per engine. */
data class TtsEngineConfig(
    val voiceId: String?,
    val localeTag: String?,
    /** Speech rate multiplier, 1.0 = normal. */
    val rate: Float,
    /** Voice pitch multiplier, 1.0 = normal (device engine only). */
    val pitch: Float,
    val elevenLabsApiKey: String = "",
    val elevenLabsModel: String = "eleven_multilingual_v2",
)
