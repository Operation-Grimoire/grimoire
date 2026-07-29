package io.grimoire.app.ui.screen.reader

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.local.entity.ReaderTextAlign

/** Languages whose script runs right-to-left. */
private val RTL_LANGUAGES = setOf(Language.AR, Language.HE, Language.FA, Language.UR)

/** Resolved alignment + direction for the reader's chapter text. */
internal data class ReaderTextLayout(
    val textAlign: TextAlign,
    val textDirection: TextDirection,
)

/**
 * Resolves the per-novel [ReaderTextAlign] against the novel's [language].
 *
 * AUTO leans on paragraph content (`ContentOr*`): each paragraph picks its
 * direction from its own first strong character, falling back to the novel's
 * language — so an English quote inside an Arabic novel still reads LTR.
 * LEFT / RIGHT force the direction outright; CENTER / JUSTIFY only change
 * alignment and keep the content-derived direction.
 */
internal fun resolveReaderTextLayout(
    align: ReaderTextAlign,
    language: Language?,
): ReaderTextLayout {
    val contentDirection =
        if (language in RTL_LANGUAGES) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
    return when (align) {
        ReaderTextAlign.AUTO -> ReaderTextLayout(TextAlign.Start, contentDirection)
        ReaderTextAlign.LEFT -> ReaderTextLayout(TextAlign.Left, TextDirection.Ltr)
        ReaderTextAlign.RIGHT -> ReaderTextLayout(TextAlign.Right, TextDirection.Rtl)
        ReaderTextAlign.CENTER -> ReaderTextLayout(TextAlign.Center, contentDirection)
        ReaderTextAlign.JUSTIFY -> ReaderTextLayout(TextAlign.Justify, contentDirection)
    }
}
