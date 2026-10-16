package io.grimoire.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Self-contained per-app UI language override.
 *
 * The selected BCP-47 language tag is stored in a dedicated [android.content.SharedPreferences]
 * file rather than DataStore: it has to be read *synchronously* from
 * [android.content.ContextWrapper.attachBaseContext], which runs before Hilt injection and can't
 * await an async DataStore read. An empty tag means "follow the system language".
 *
 * Callers apply it by wrapping the base context ([wrap]) and, after a change, recreating the
 * activity so every resource re-resolves in the new language. This intentionally avoids pulling in
 * `androidx.appcompat` (the app is Compose + [androidx.fragment.app.FragmentActivity] only).
 *
 * On Android 13+ the system's per-app language ([LocaleManager]) is the source of truth instead,
 * so the choice also shows up in system settings. The SharedPreferences tag is then only a
 * one-time migration source ([migrateToSystem]).
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    /** Sentinel tag meaning "follow the system language". */
    const val SYSTEM = ""

    /**
     * UI languages we ship translations for, in menu order, with the name each language uses for
     * itself. Keep in sync with `res/xml/locales_config.xml` (LocaleCoverageTest checks it).
     */
    private val nativeNames: Map<String, String> = linkedMapOf(
        "en" to "English",
        // Android's legacy code for Indonesian, matching values-in.
        "in" to "Bahasa Indonesia",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "pl" to "Polski",
        "pt-BR" to "Português (Brasil)",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "uk" to "Українська",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "th" to "ไทย",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh" to "简体中文",
        "zh-TW" to "繁體中文",
    )

    /** [SYSTEM] first, then [nativeNames] order. */
    val supported: List<String> = listOf(SYSTEM) + nativeNames.keys

    /** The language's own name for a supported tag; null for [SYSTEM] or an unknown tag. */
    fun nativeName(tag: String): String? = nativeNames[tag]

    /** The persisted tag, or [SYSTEM] when the user follows the system language. */
    fun storedTag(context: Context): String {
        val legacy = prefs(context).getString(KEY_TAG, SYSTEM).orEmpty()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return legacy
        // Before migrateToSystem has run, fall back to the legacy tag.
        return systemTag(context).ifBlank { legacy }
    }

    /**
     * Stores [tag]. Returns true when the caller must recreate the activity itself; on Android 13+
     * the system recreates it.
     */
    fun setTag(context: Context, tag: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            prefs(context).edit().putString(KEY_TAG, tag).apply()
            return true
        }
        prefs(context).edit().remove(KEY_TAG).apply()
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        return false
    }

    /**
     * Moves a tag saved before Android 13 support into the system per-app language, unless one was
     * already picked in system settings.
     */
    fun migrateToSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val legacy = prefs(context).getString(KEY_TAG, SYSTEM).orEmpty()
        if (legacy.isBlank()) return
        if (systemTag(context).isBlank()) {
            setTag(context, legacy)
        } else {
            prefs(context).edit().remove(KEY_TAG).apply()
        }
    }

    /** The system per-app language, mapped onto [supported] so regional picks (zh-CN, en-GB) match. */
    private fun systemTag(context: Context): String {
        val locales = systemLocales(context)
        if (locales == null || locales.isEmpty) return SYSTEM
        return matchSupported(locales[0])
    }

    private fun matchSupported(picked: Locale): String {
        if (picked.language == "zh") {
            val traditional = picked.script == "Hant" || picked.country in setOf("TW", "HK", "MO")
            return if (traditional) "zh-TW" else "zh"
        }
        val sameLanguage = nativeNames.keys.filter { Locale.forLanguageTag(it).language == picked.language }
        return sameLanguage.firstOrNull { Locale.forLanguageTag(it).country == picked.country }
            ?: sameLanguage.firstOrNull()
            ?: picked.toLanguageTag()
    }

    /**
     * Wraps [base] so its resources resolve in the stored UI language. A no-op when the user
     * follows the system language.
     */
    fun wrap(base: Context): Context {
        // The system already applies its per-app language, region included.
        if (systemLocales(base)?.isEmpty == false) return base
        val tag = storedTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun systemLocales(context: Context): LocaleList? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            null
        } else {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
