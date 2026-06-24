package io.grimoire.app.data.download

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.app.util.formattedText
import io.grimoire.app.util.imageUrl
import io.grimoire.app.util.text
import kotlinx.coroutines.delay

/**
 * Raised when a chapter fetch keeps coming back with no readable content. The
 * download worker catches it like any other failure and flips the chapter to an
 * ERROR / REDOWNLOAD_ERROR status, so the existing retry UI can re-queue it —
 * rather than silently persisting a blank chapter.
 */
class EmptyChapterContentException(message: String) : Exception(message)

/**
 * A page list is "readable" when at least one page carries prose or an image.
 * Pages that are only scene-break separators (or blank whitespace) don't count:
 * a chapter made of nothing but those is what the user saw as "empty text".
 *
 * Sources occasionally answer a chapter request with a 200 whose body never
 * rendered the content (rate-limiting, a half-loaded Royal Road page, a CDN
 * hiccup). The parse then yields zero pages — or pages whose text is all
 * whitespace — and without this check that empties straight into the download.
 */
fun hasReadableContent(pages: List<NovelPage>): Boolean =
    pages.any { page ->
        page.imageUrl != null ||
            page.text.isNotBlank() ||
            !page.formattedText.isNullOrBlank()
    }

/**
 * Fetches a chapter's pages, retrying when the result has no readable content.
 * The reported failure mode is transient — deleting and re-downloading the same
 * chapter returns text — so a short backed-off retry usually recovers it within
 * the same download pass. After [maxAttempts] empty results it throws
 * [EmptyChapterContentException] so the caller can fail the chapter instead of
 * saving the blank.
 *
 * Exceptions thrown by [fetch] propagate immediately (unchanged behaviour): a
 * network/parse error is already surfaced as a chapter ERROR by the caller, and
 * retrying every hard failure here would slow a large batch full of genuinely
 * failing chapters. [sleep] is injectable so tests don't wait on real time.
 */
suspend fun fetchReadablePages(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1_000L,
    sleep: suspend (Long) -> Unit = { delay(it) },
    fetch: suspend () -> List<NovelPage>,
): List<NovelPage> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    repeat(maxAttempts) { attempt ->
        val pages = fetch()
        if (hasReadableContent(pages)) return pages
        if (attempt < maxAttempts - 1) sleep(initialDelayMs * (attempt + 1))
    }
    throw EmptyChapterContentException("Chapter content was empty after $maxAttempts attempts")
}
