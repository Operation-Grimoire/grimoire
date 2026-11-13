package io.grimoire.app.data.txt

import io.grimoire.app.data.book.BookChapter
import io.grimoire.app.data.book.BookPage
import io.grimoire.app.data.book.BookParser
import io.grimoire.app.data.book.ParsedBook
import io.grimoire.app.data.book.bookTitleFromFileName
import java.io.ByteArrayOutputStream
import java.io.File

object TxtParser : BookParser {
    private data class Sample(val bytes: ByteArray, val complete: Boolean)

    private const val SAMPLE_SIZE_BYTES = 64 * 1024
    private const val MAX_HEADING_LENGTH = 120

    private val ENGLISH_HEADING = Regex(
        "^Chapter[\\t ]+(?:[0-9]+|[IVXLCDM]+)(?:[\\t]*[:.\\-–—][\\t ]*.*|[\\t ]+.*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val CJK_HEADING = Regex("^第[0-9零〇一二三四五六七八九十百千两]+[章节回卷話话].*$")

    override fun parse(file: File, originalName: String?): ParsedBook {
        val title = bookTitleFromFileName(originalName) ?: "Imported book"
        val sample = readSample(file)
        val charset = TxtEncodingDetector.detect(sample.bytes, sample.complete)
        val chapters = ArrayList<BookChapter>()
        var chapterTitle: String? = null
        var pages = ArrayList<BookPage>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            pages += BookPage.Text(paragraph.toString())
            paragraph.clear()
        }

        fun finishChapter() {
            flushParagraph()
            if (pages.isEmpty()) return
            chapters += BookChapter(chapterTitle ?: title, pages)
            pages = ArrayList()
        }

        file.bufferedReader(charset).use { reader ->
            var firstLine = true
            reader.forEachLine { rawLine ->
                val contentLine = if (firstLine) rawLine.removePrefix("\uFEFF") else rawLine
                val line = contentLine.trim()
                firstLine = false
                if (line.isEmpty()) {
                    flushParagraph()
                    return@forEachLine
                }

                if (isChapterHeading(line)) {
                    finishChapter()
                    chapterTitle = line
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(contentLine)
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

    private fun readSample(file: File): Sample {
        val out = ByteArrayOutputStream(SAMPLE_SIZE_BYTES)
        var complete = file.length() <= SAMPLE_SIZE_BYTES
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var firstChunk = true
            var collecting = false
            while (out.size() < SAMPLE_SIZE_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, SAMPLE_SIZE_BYTES - out.size()))
                if (read <= 0) {
                    complete = true
                    break
                }
                if (firstChunk) {
                    collecting = hasBom(buffer, read)
                    firstChunk = false
                }
                if (!collecting) {
                    val firstNonAscii = (0 until read).firstOrNull { buffer[it].toInt() and 0x80 != 0 }
                    if (firstNonAscii == null) continue
                    out.write(buffer, firstNonAscii, read - firstNonAscii)
                    collecting = true
                } else {
                    out.write(buffer, 0, read)
                }
            }
        }
        return Sample(out.toByteArray(), complete)
    }

    private fun hasBom(bytes: ByteArray, size: Int): Boolean {
        fun byte(index: Int) = bytes[index].toInt() and 0xFF
        return (size >= 3 && byte(0) == 0xEF && byte(1) == 0xBB && byte(2) == 0xBF) ||
            (size >= 2 && byte(0) == 0xFF && byte(1) == 0xFE) ||
            (size >= 2 && byte(0) == 0xFE && byte(1) == 0xFF) ||
            (size >= 4 && byte(0) == 0x00 && byte(1) == 0x00 && byte(2) == 0xFE && byte(3) == 0xFF)
    }
}
