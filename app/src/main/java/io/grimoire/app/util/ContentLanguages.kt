package io.grimoire.app.util

import io.grimoire.api.model.lang.Language

/**
 * Bridges the app to the extensions-API [Language] enum, the single source of
 * truth for content languages. The per-source and global "Content languages"
 * pickers offer exactly [SELECTABLE] and persist each pick by its stable ISO
 * [Language.code]; [parse] also accepts the legacy lowercase English names that
 * earlier builds stored, so existing selections heal to codes on next save.
 */
object ContentLanguages {
    /** Every real language — all of [Language] except the MULTI / UNKNOWN sentinels. */
    val SELECTABLE: List<Language> =
        Language.entries.filter { it != Language.MULTI && it != Language.UNKNOWN }

    /**
     * Display names of [SELECTABLE], kept for the name-keyed callers that still
     * map by English name (the TTS voice map). Derived from the enum so the
     * language set stays in lock-step with the API.
     */
    val ALL: List<String> = SELECTABLE.map { it.displayName }

    fun normalize(name: String): String = name.trim().lowercase()

    fun normalize(names: Set<String>): Set<String> =
        names.mapNotNullTo(mutableSetOf()) { n ->
            normalize(n).takeIf { it.isNotEmpty() }
        }

    /**
     * Resolve a stored token — an ISO 639-1 [Language.code] or a legacy English
     * [Language.displayName] (any case) — to a [Language], or null when neither
     * matches.
     */
    fun parse(token: String): Language? {
        val t = token.trim()
        if (t.isEmpty()) return null
        Language.fromCode(t).let { if (it != Language.UNKNOWN) return it }
        val key = t.lowercase()
        return Language.entries.firstOrNull { it.displayName.lowercase() == key }
    }

    /** Serialize a language selection to a comma-joined list of [Language.code]s. */
    fun serialize(languages: Set<Language>): String = languages.joinToString(",") { it.code }

    /** Inverse of [serialize]; tolerant of legacy English-name tokens (see [parse]). */
    fun deserialize(raw: String): Set<Language> =
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNullTo(mutableSetOf()) { parse(it) }

    /** Resolve a stored English language name (any case) to a [Language], or null. */
    fun fromName(name: String): Language? = parse(name)

    /** Map stored language names to the [Language] set a MultiLanguageSource expects. */
    fun toLanguages(names: Set<String>): Set<Language> =
        names.mapNotNullTo(mutableSetOf()) { fromName(it) }

    /** Display names for a source's advertised [Language] list. */
    fun displayNames(languages: List<Language>): List<String> = languages.map { it.displayName }
}
