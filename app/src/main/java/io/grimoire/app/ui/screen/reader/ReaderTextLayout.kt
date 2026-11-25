package io.grimoire.app.ui.screen.reader

import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.local.entity.ReaderTextAlign
import io.grimoire.ui.reader.ReaderTextLayout
import io.grimoire.ui.reader.readerTextLayout

/** Languages whose script runs right-to-left. */
private val RTL_LANGUAGES = setOf(Language.AR, Language.HE, Language.FA, Language.UR)

/** Resolves the per-novel [ReaderTextAlign] against the novel's [language]. */
internal fun resolveReaderTextLayout(align: ReaderTextAlign, language: Language?): ReaderTextLayout =
    readerTextLayout(align, language in RTL_LANGUAGES)
