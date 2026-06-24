package io.grimoire.app.util

import io.grimoire.api.model.lang.Language

/**
 * Built-in list of content languages offered by the per-source "Content
 * languages" filter for multi-language sources (`lang == "all"`).
 *
 * Sources populate [io.grimoire.api.model.novel.Novel.language] with a plain English
 * language name; the filter compares case-insensitively, so the enabled set is
 * stored lowercased. The list is intentionally a broad common set rather than a
 * full ISO table — unknown/other languages still pass when nothing matches.
 */
object ContentLanguages {
    val ALL: List<String> = listOf(
        "English", "Spanish", "Portuguese", "French", "German", "Italian",
        "Dutch", "Russian", "Ukrainian", "Polish", "Czech", "Romanian",
        "Greek", "Turkish", "Arabic", "Hebrew", "Hindi", "Bengali",
        "Chinese", "Japanese", "Korean", "Vietnamese", "Thai", "Indonesian",
        "Malay", "Filipino", "Swedish", "Norwegian", "Danish", "Finnish",
        "Hungarian", "Persian", "Urdu",
    )

    fun normalize(name: String): String = name.trim().lowercase()

    fun normalize(names: Set<String>): Set<String> =
        names.mapNotNullTo(mutableSetOf()) { n ->
            normalize(n).takeIf { it.isNotEmpty() }
        }

    /** Resolve a stored English language name (any case) to a [Language], or null. */
    fun fromName(name: String): Language? {
        val key = normalize(name)
        return Language.entries.firstOrNull { it.displayName.lowercase() == key }
    }

    /** Map stored language names to the [Language] set a MultiLanguageSource expects. */
    fun toLanguages(names: Set<String>): Set<Language> =
        names.mapNotNullTo(mutableSetOf()) { fromName(it) }

    /** Display names for a source's advertised [Language] list. */
    fun displayNames(languages: List<Language>): List<String> = languages.map { it.displayName }
}
