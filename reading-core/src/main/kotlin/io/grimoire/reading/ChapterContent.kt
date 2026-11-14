package io.grimoire.reading

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Boundary written between pages when chapter content is persisted, so the
 * original paragraph structure can be reconstructed on read. Legacy data only:
 * raw source controls were not escaped and can be ambiguous in this format.
 */
val CHAPTER_PAGE_SEPARATOR: String = 31.toChar().toString()

/**
 * Prefix marking a persisted page as an illustration: the rest of the token is the image URL
 * rather than prose. Retained only for interpreting existing legacy downloads.
 */
val CHAPTER_IMAGE_MARKER: String = 30.toChar().toString()

/**
 * Legacy token value marking a scene break (NovelPage.isSeparator).
 */
val CHAPTER_SEPARATOR_MARKER: String = 29.toChar().toString()

/**
 * Within a text page token, splits the plain `text` field from an optional `formattedText`
 * (constrained-HTML) payload. ASCII File Separator: legacy tokens without
 * this byte decode unchanged with `formattedText = null`.
 */
val CHAPTER_FORMATTED_MARKER: String = 28.toChar().toString()

const val LEGACY_CHAPTER_CONTENT_VERSION = 1
const val CURRENT_CHAPTER_CONTENT_VERSION = 2

/** Version is storage metadata, never guessed from a prefix in untrusted source text. */
data class StoredChapterContent(val content: String, val version: Int = LEGACY_CHAPTER_CONTENT_VERSION)

/** V2 is a JSON array of tagged pages; JSON escapes controls before SQLite/UTF-8 persistence. */
fun encodeChapterContent(pages: List<NovelPage>): StoredChapterContent = StoredChapterContent(
    JsonArray(pages.map { page -> JsonArray(when (val c = page.content) {
        is PageContent.Separator -> listOf(JsonPrimitive("separator"))
        is PageContent.Image -> listOf(JsonPrimitive("image"), JsonPrimitive(c.url))
        is PageContent.Text -> listOf(JsonPrimitive("text"), JsonPrimitive(c.text), c.html?.let(::JsonPrimitive) ?: JsonNull)
    }) }).toString(),
    CURRENT_CHAPTER_CONTENT_VERSION,
)

fun decodeChapterContent(stored: StoredChapterContent): List<NovelPage> = when (stored.version) {
    LEGACY_CHAPTER_CONTENT_VERSION -> decodeChapterContent(stored.content)
    CURRENT_CHAPTER_CONTENT_VERSION -> {
        val pages = Json.parseToJsonElement(stored.content)
        require(pages is JsonArray) { "Chapter content must be a page array" }
        pages.mapIndexed { index, element ->
            require(element is JsonArray && element.isNotEmpty()) { "Invalid chapter page" }
            val content = when (element[0].stringField()) {
                "text" -> {
                    require(element.size == 3) { "Invalid text page" }
                    PageContent.Text(element[1].stringField(), if (element[2] == JsonNull) null else element[2].stringField())
                }
                "image" -> {
                    require(element.size == 2) { "Invalid image page" }
                    PageContent.Image(element[1].stringField())
                }
                "separator" -> {
                    require(element.size == 1) { "Invalid separator page" }
                    PageContent.Separator()
                }
                else -> throw IllegalArgumentException("Unsupported chapter page type")
            }
            NovelPage(index, content)
        }
    }
    else -> throw IllegalArgumentException("Unsupported chapter content version: ${stored.version}")
}

private fun JsonElement.stringField(): String {
    require(this is JsonPrimitive && isString) { "Chapter page field must be a string" }
    return content
}

/** Counts prose, rather than JSON syntax, HTML duplicates or illustration URLs. */
fun chapterWordCount(pages: List<NovelPage>): Int = pages.sumOf { page ->
    var count = 0
    var inWord = false
    for (char in (page.content as? PageContent.Text)?.text.orEmpty()) {
        if (char.isWhitespace()) inWord = false
        else if (!inWord) { inWord = true; count++ }
    }
    count
}

/** Legacy-only reader. Existing bytes retain their original interpretation, even if ambiguous. */
fun decodeChapterContent(content: String): List<NovelPage> =
    content.split(CHAPTER_PAGE_SEPARATOR).mapIndexed { index, token ->
        when {
            token == CHAPTER_SEPARATOR_MARKER ->
                NovelPage(index, PageContent.Separator())
            token.startsWith(CHAPTER_IMAGE_MARKER) ->
                NovelPage(index, PageContent.Image(token.removePrefix(CHAPTER_IMAGE_MARKER)))
            token.contains(CHAPTER_FORMATTED_MARKER) -> {
                val parts = token.split(CHAPTER_FORMATTED_MARKER, limit = 2)
                NovelPage(index, PageContent.Text(parts[0], parts[1]))
            }
            else -> NovelPage(index, PageContent.Text(token))
        }
    }
