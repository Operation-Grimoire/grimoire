package io.grimoire.app.data.txt

import io.grimoire.app.data.book.BookChapter
import io.grimoire.app.data.book.BookPage
import io.grimoire.app.data.book.BookParser
import io.grimoire.app.data.book.ParsedBook
import io.grimoire.app.data.book.bookTitleFromFileName
import java.io.ByteArrayOutputStream
import java.io.File

object TxtParser : BookParser {
    private const val SAMPLE_SIZE_BYTES = 64 * 1024
    private const val MAX_HEADING_LENGTH = 120

    private val ENGLISH_HEADING = Regex(
        "^Chapter[\\t ]+(?:[0-9]+|[IVXLCDM]+)(?:[\\t]*[:.\\-–—][\\t ]*.*|[\\t ]+.*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val CJK_HEADING = Regex("^第[0-9零〇一二三四五六七八九十百千两]+[章节回卷話话].*$")

    override fun parse(file: File, originalName: String?): ParsedBook {
        val title = bookTitleFromFileName(originalName) ?: "Imported book"
        val charset = TxtEncodingDetector.detect(readSample(file))
        val chapters = ArrayList<BookChapter>()
        var chapterTitle: String? = null
        var pages = ArrayList<BookPage>()

        fun finishChapter() {
            if (pages.isEmpty()) return
            chapters += BookChapter(chapterTitle ?: title, pages)
            pages = ArrayList()
        }

        file.bufferedReader(charset).use { reader ->
            var firstLine = true
            reader.forEachLine { rawLine ->
                val line = (if (firstLine) rawLine.removePrefix("\uFEFF") else rawLine).trim()
                firstLine = false
                if (line.isEmpty()) return@forEachLine

                if (isChapterHeading(line)) {
                    finishChapter()
                    chapterTitle = line
                } else {
                    pages += BookPage.Text(line)
                }
            }
        }
        finishChapter()
        require(chapters.isNotEmpty()) { "TXT file contains no readable text" }

        return ParsedBook(
            title = title,
            author = null,
            description = null,
            genres = emptyList(),
            language = null,
            cover = null,
            chapters = chapters,
        )
    }

    private fun isChapterHeading(line: String): Boolean =
        line.length <= MAX_HEADING_LENGTH &&
            (ENGLISH_HEADING.matches(line) || CJK_HEADING.matches(line))

    private fun readSample(file: File): ByteArray {
        val out = ByteArrayOutputStream(SAMPLE_SIZE_BYTES)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (out.size() < SAMPLE_SIZE_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, SAMPLE_SIZE_BYTES - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
