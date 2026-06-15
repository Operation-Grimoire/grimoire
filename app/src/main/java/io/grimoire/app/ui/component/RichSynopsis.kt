package io.grimoire.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Whether synopses (novel descriptions, NU series descriptions, reviews) render
 * embedded links as tappable links. Provided once at the app root from
 * `UiPreferences.renderSynopsisLinks`; defaults to `true` so any preview /
 * isolated composable still gets link rendering.
 */
val LocalSynopsisRenderLinks = compositionLocalOf { true }

/**
 * Builds the rich [AnnotatedString] for a synopsis.
 *
 * Sources populate `description` either as plain text (the common case — Jsoup
 * `.text()`) or as the constrained-HTML subset `AnnotatedString.fromHtml`
 * understands (`<br>`, `<p>`, `<a>`, `<b>/<i>/<u>`, entities). We detect HTML
 * and parse it; otherwise we keep the plain text verbatim (newlines included)
 * and auto-link bare URLs so a pasted link is still tappable.
 *
 * When [LocalSynopsisRenderLinks] is `false`, links keep their text but are
 * rendered as plain, non-tappable text.
 */
@Composable
fun rememberSynopsisAnnotatedString(text: String): AnnotatedString {
    val renderLinks = LocalSynopsisRenderLinks.current
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, renderLinks, linkColor) {
        buildSynopsisAnnotatedString(text, renderLinks, linkColor)
    }
}

internal fun buildSynopsisAnnotatedString(
    text: String,
    renderLinks: Boolean,
    linkColor: Color,
): AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )

    if (containsHtmlMarkup(text)) {
        val parsed = AnnotatedString.fromHtml(
            htmlString = text,
            linkStyles = if (renderLinks) linkStyles else null,
        )
        return if (renderLinks) parsed else parsed.withoutLinkAnnotations()
    }

    return buildAnnotatedString {
        parsePlainSynopsis(text, detectLinks = renderLinks).forEach { segment ->
            when (segment) {
                is SynopsisSegment.Plain -> append(segment.text)
                is SynopsisSegment.Link ->
                    withLink(LinkAnnotation.Url(url = segment.url, styles = linkStyles)) {
                        append(segment.text)
                    }
            }
        }
    }
}

/** A run of synopsis text, either plain or a detected link. */
internal sealed interface SynopsisSegment {
    data class Plain(val text: String) : SynopsisSegment
    data class Link(val text: String, val url: String) : SynopsisSegment
}

// Matches a tag from the constrained subset (or an HTML entity) so we only hand
// genuinely-formatted descriptions to fromHtml — a stray `<` in plain prose
// shouldn't trip it.
private val HTML_MARKUP = Regex(
    """<\s*/?\s*(br|p|a|i|b|em|strong|u|ul|ol|li|div|span|h[1-6])\b[^>]*>""" +
        """|&(amp|lt|gt|quot|apos|nbsp|#\d+|#x[0-9a-fA-F]+);""",
    RegexOption.IGNORE_CASE,
)

private val URL_REGEX = Regex("""(?:https?://|www\.)[^\s]+""", RegexOption.IGNORE_CASE)

// Punctuation that commonly trails a URL in prose and shouldn't be part of it.
private const val URL_TRAILING = ".,!?;:)]}>\"'"

internal fun containsHtmlMarkup(text: String): Boolean = HTML_MARKUP.containsMatchIn(text)

internal fun parsePlainSynopsis(text: String, detectLinks: Boolean): List<SynopsisSegment> {
    if (!detectLinks || text.isEmpty()) return listOf(SynopsisSegment.Plain(text))

    val segments = mutableListOf<SynopsisSegment>()
    var cursor = 0
    for (match in URL_REGEX.findAll(text)) {
        val token = match.value
        val core = token.trimEnd(*URL_TRAILING.toCharArray())
        if (core.isEmpty()) continue
        if (match.range.first > cursor) {
            segments += SynopsisSegment.Plain(text.substring(cursor, match.range.first))
        }
        val url = if (core.startsWith("www.", ignoreCase = true)) "https://$core" else core
        segments += SynopsisSegment.Link(text = core, url = url)
        val trailing = token.substring(core.length)
        if (trailing.isNotEmpty()) segments += SynopsisSegment.Plain(trailing)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) segments += SynopsisSegment.Plain(text.substring(cursor))
    return segments.ifEmpty { listOf(SynopsisSegment.Plain(text)) }
}

/**
 * Rebuilds the string keeping span/paragraph styling (bold, italic, line
 * breaks) but dropping link annotations, so links render as inert styled text
 * when the user has turned link rendering off.
 */
private fun AnnotatedString.withoutLinkAnnotations(): AnnotatedString = buildAnnotatedString {
    append(this@withoutLinkAnnotations.text)
    spanStyles.forEach { addStyle(it.item, it.start, it.end) }
    paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
}
