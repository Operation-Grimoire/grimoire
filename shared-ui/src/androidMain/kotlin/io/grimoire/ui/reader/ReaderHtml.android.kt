package io.grimoire.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml

actual fun readerHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString =
    AnnotatedString.fromHtml(htmlString = html, linkStyles = linkStyles)
