package io.grimoire.app.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Text that turns specific substrings into clickable, primary-coloured,
 * underlined links — the Compose equivalent of writing an `<a>` tag inside
 * a sentence. Click handling is delegated to the platform [UriHandler], which
 * opens the URL in the system browser.
 *
 * Each entry in [links] is a `(linkText, url)` pair. Every occurrence of
 * [Pair.first] inside [text] becomes a link to [Pair.second]; if two entries
 * overlap (e.g. `"github.com"` and `"github.com/settings"`), the longer
 * match wins so the more specific link is preserved.
 */
@Composable
fun LinkText(
    text: String,
    vararg links: Pair<String, String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )
    // Match longest labels first so a more-specific link doesn't get
    // shadowed by a shorter prefix entry.
    val sorted = links.sortedByDescending { it.first.length }

    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val match = sorted.firstOrNull { (label, _) -> text.startsWith(label, i) }
            if (match != null) {
                withLink(LinkAnnotation.Url(url = match.second, styles = linkStyles)) {
                    append(match.first)
                }
                i += match.first.length
            } else {
                append(text[i])
                i++
            }
        }
    }

    Text(annotated, modifier = modifier, style = style, color = color)
}
