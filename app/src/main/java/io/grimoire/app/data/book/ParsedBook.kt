package io.grimoire.app.data.book

/** One page within an imported chapter — either a text run or an embedded illustration. */
sealed interface BookPage {
    data class Text(val text: String) : BookPage
    data class Image(val bytes: ByteArray) : BookPage
}

/** One chapter extracted from a book: a title and its pages in document order. */
data class BookChapter(val title: String, val pages: List<BookPage>)

/** Cover image bytes plus the file extension to store it under. */
data class BookCover(val bytes: ByteArray, val extension: String)

data class ParsedBook(
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    /** BCP-47 tag, or null when the source format or document does not provide one. */
    val language: String?,
    val cover: BookCover?,
    val chapters: List<BookChapter>,
)

internal fun bookTitleFromFileName(fileName: String?): String? {
    val name = fileName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.')
        ?.trim()
        .orEmpty()
    return name.ifEmpty { null }
}
