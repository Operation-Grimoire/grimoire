package io.grimoire.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles

/**
 * Parses the constrained HTML subset sources use for rich text: `<i>`/`<em>`, `<b>`/`<strong>`,
 * `<u>`, `<a href>`, `<br>` and character entities. Links get [linkStyles] when given.
 */
expect fun readerHtml(html: String, linkStyles: TextLinkStyles? = null): AnnotatedString
