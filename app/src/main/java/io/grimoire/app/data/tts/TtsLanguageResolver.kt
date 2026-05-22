package io.grimoire.app.data.tts

import io.grimoire.app.util.ContentLanguages
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a novel's content language — a plain English name like "English" (often null) —
 * to a [Locale] the speech engines and the voice picker can use.
 */
@Singleton
class TtsLanguageResolver @Inject constructor() {

    // English display name (lowercased) → language-only Locale.
    private val byEnglishName: Map<String, Locale> by lazy {
        val map = HashMap<String, Locale>()
        for (locale in Locale.getAvailableLocales()) {
            val lang = locale.language
            if (lang.isBlank()) continue
            val key = locale.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ENGLISH)
            if (key.isNotBlank()) map.putIfAbsent(key, Locale(lang))
        }
        map
    }

    /** Resolves [language] to a [Locale], falling back to the device default. */
    fun resolveLocale(language: String?): Locale {
        if (language.isNullOrBlank()) return Locale.getDefault()
        val key = ContentLanguages.normalize(language)
        byEnglishName[key]?.let { return it }
        // Tolerate language tags ("en", "ja") and English-name variants.
        runCatching { Locale.forLanguageTag(key) }
            .getOrNull()
            ?.takeIf { it.language.isNotBlank() }
            ?.let { return Locale(it.language) }
        return Locale.getDefault()
    }
}
