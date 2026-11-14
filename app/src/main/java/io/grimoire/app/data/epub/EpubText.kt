package io.grimoire.app.data.epub

/** XML 1.0 cannot represent most C0 controls, even with character references. */
internal fun requireEpubXmlText(text: String) {
    var index = 0
    while (index < text.length) {
        val code = text.codePointAt(index)
        require(code == 9 || code == 10 || code == 13 || code in 0x20..0xD7FF ||
            code in 0xE000..0xFFFD || code in 0x10000..0x10FFFF) {
            "Chapter or metadata contains a character EPUB cannot represent"
        }
        index += Character.charCount(code)
    }
}

internal fun escapeEpubXml(text: String): String {
    requireEpubXmlText(text)
    return buildString(text.length) {
        for (char in text) when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
