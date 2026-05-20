package io.grimoire.app.util

/**
 * Humanises a source's `lang` code for display. "ALL" is the multi-language
 * marker — these sources serve content in many languages, not all of them.
 */
fun languageLabel(code: String): String = when (code.uppercase()) {
    "EN" -> "English"
    "ALL" -> "Multi-language"
    else -> code.uppercase()
}
