package io.grimoire.app.data.preferences

import io.grimoire.api.model.lang.Language
import io.grimoire.app.util.ContentLanguages
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's global "enabled content languages" preference, applied to every
 * multi-language source by default. A multi-language source can opt out via
 * its own override flag (see [SourceSettingsPreferences.contentLanguagesOverride]).
 *
 * Stored as a comma-joined list of ISO [Language.code]s; an empty set means
 * "no filter — show every language". Legacy values stored as English names are
 * read transparently (see [ContentLanguages.deserialize]) and rewritten to
 * codes on the next save.
 */
@Singleton
class AppLanguagePreferences @Inject constructor(
    store: PreferenceStore,
) {
    val enabled: Preference<Set<Language>> = store.getObject(
        key = "app.content_languages",
        defaultValue = emptySet(),
        serialize = { ContentLanguages.serialize(it) },
        deserialize = { ContentLanguages.deserialize(it) },
    )

    suspend fun enabledNow(): Set<Language> = enabled.changes().first()
}
