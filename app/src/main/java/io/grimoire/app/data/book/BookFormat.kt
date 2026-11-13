package io.grimoire.app.data.book

import java.util.Locale

enum class BookFormat(val extension: String) {
    EPUB("epub"),
    TXT("txt"),
}

internal fun detectBookFormat(mimeType: String?, fileName: String?): BookFormat {
    val extension = fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    when (extension) {
        BookFormat.EPUB.extension -> return BookFormat.EPUB
        BookFormat.TXT.extension -> return BookFormat.TXT
    }

    val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return when {
        mime == "application/epub+zip" -> BookFormat.EPUB
        mime?.startsWith("text/") == true -> BookFormat.TXT
        else -> throw IllegalArgumentException("Unsupported book format. Choose an EPUB or TXT file.")
    }
}
