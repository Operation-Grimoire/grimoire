package io.grimoire.app.util

import io.grimoire.api.model.lang.Language

/**
 * Humanises a source's `lang` code for display via the extensions-API
 * [Language] enum (the source of truth). Unknown codes fall back to the
 * uppercased code so nothing renders blank.
 */
fun languageLabel(code: String): String {
    val lang = Language.fromCode(code)
    return if (lang == Language.UNKNOWN) code.uppercase() else lang.displayName
}
