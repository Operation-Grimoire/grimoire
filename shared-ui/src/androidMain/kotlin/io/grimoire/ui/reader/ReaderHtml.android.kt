package io.grimoire.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

actual fun readerHtml(html: String): AnnotatedString = AnnotatedString.fromHtml(html)
