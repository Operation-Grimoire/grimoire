package io.grimoire.ui.reader

import androidx.compose.ui.text.AnnotatedString

/**
 * Parses the constrained HTML subset sources use for rich chapter text: `<i>`/`<em>`,
 * `<b>`/`<strong>`, `<u>`, `<a href>`, `<br>` and character entities.
 */
expect fun readerHtml(html: String): AnnotatedString
