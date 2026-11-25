package io.grimoire.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// Compose Desktop has no AnnotatedString.fromHtml; this covers the same subset Android renders.
actual fun readerHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString = buildAnnotatedString {
    fun AnnotatedString.Builder.append(node: Node) {
        when (node) {
            is TextNode -> append(node.text())
            is Element -> {
                val children = { node.childNodes().forEach { append(it) } }
                when (node.tagName().lowercase()) {
                    "br" -> append('\n')
                    "i", "em", "cite", "dfn" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children() }
                    "b", "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children() }
                    "u" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { children() }
                    "s", "strike", "del" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { children() }
                    "a" -> node.attr("href").takeIf { it.isNotBlank() }
                        ?.let { href -> withLink(LinkAnnotation.Url(href, linkStyles)) { children() } } ?: children()
                    else -> children()
                }
            }
        }
    }
    Jsoup.parseBodyFragment(html).body().childNodes().forEach { append(it) }
}
