package io.grimoire.app.data.athenaeum

import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import java.net.URI
import java.time.Instant

/**
 * Maps Grimoire's local entities (+ the source they came from) to Athenaeum
 * ingest observations. Pure — takes the source's base URL + language rather
 * than the Source object, so it's unit-testable. releaseKind is intentionally
 * omitted (the platform's default applies server-side).
 */
object ObservationMapper {

    /** A SERIES observation for [novel] scraped from the source at [baseUrl]/[lang]. */
    fun series(baseUrl: String, lang: String, novel: NovelEntity): ObservationItem =
        ObservationItem(
            kind = "SERIES",
            platformDomain = domainOf(baseUrl),
            url = resolveUrl(baseUrl, novel.url),
            title = novel.title,
            status = novelStatus(novel.status),
            language = lang,
            format = "WEB",
            synopsis = novel.description,
            coverUrl = novel.thumbnailUrl,
        )

    /** A CHAPTER observation, or null when the chapter has no usable number. */
    fun chapter(baseUrl: String, lang: String, novel: NovelEntity, chapter: ChapterEntity): ObservationItem? {
        if (chapter.chapterNumber < 0f) return null
        return ObservationItem(
            kind = "CHAPTER",
            platformDomain = domainOf(baseUrl),
            url = resolveUrl(baseUrl, chapter.url),
            seriesUrl = resolveUrl(baseUrl, novel.url),
            title = chapter.name.ifBlank { null },
            number = chapter.chapterNumber.toDouble(),
            publishedAt = chapter.uploadDate.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() },
        )
    }

    private fun domainOf(baseUrl: String): String =
        URI(baseUrl).host?.lowercase().orEmpty().removePrefix("www.")

    // Mirrors HttpSource.resolveUrl: absolute as-is, else baseUrl + relative path.
    private fun resolveUrl(baseUrl: String, url: String): String =
        if (url.startsWith("http")) url else baseUrl.trimEnd('/') + "/" + url.trimStart('/')

    // NovelStatus names line up with the backend's SeriesStatus names.
    private fun novelStatus(ordinal: Int): String =
        NovelStatus.entries.getOrElse(ordinal) { NovelStatus.UNKNOWN }.name
}
