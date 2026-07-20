package io.grimoire.app.util

import android.content.Context
import android.content.res.Configuration
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
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    /** Sentinel tag meaning "follow the system language". */
    const val SYSTEM = ""

    /** UI languages we ship translations for, in menu order. [SYSTEM] first. */
    val supported: List<String> = listOf(SYSTEM, "en", "zh")

    /** The persisted tag, or [SYSTEM] when the user follows the system language. */
    fun storedTag(context: Context): String =
        prefs(context).getString(KEY_TAG, SYSTEM).orEmpty()

    fun setTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_TAG, tag).apply()
    }

    /**
     * Wraps [base] so its resources resolve in the stored UI language. A no-op when the user
     * follows the system language.
     */
    fun wrap(base: Context): Context {
        val tag = storedTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
