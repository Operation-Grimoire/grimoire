package io.grimoire.app.data.athenaeum

import io.grimoire.api.model.NovelStatus
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import java.math.BigDecimal
import java.math.RoundingMode
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

    /** A single CHAPTER observation, or null when no chapter number can be determined. */
    fun chapter(baseUrl: String, lang: String, novel: NovelEntity, chapter: ChapterEntity): ObservationItem? {
        val number = explicitNumber(chapter) ?: return null
        return buildChapterItem(baseUrl, lang, novel, chapter, number)
    }

    /**
     * CHAPTER observations for a novel's chapters **in reading order**. Numbers a
     * source left unset are inferred by interpolating between numbered neighbours
     * (e.g. an interlude between 101 and 102 becomes 101.5; a bare 102 after 101
     * becomes 102), so chapters from sources that don't number their list still
     * contribute. Chapters that can't be numbered at all are dropped.
     */
    fun chapters(baseUrl: String, lang: String, novel: NovelEntity, ordered: List<ChapterEntity>): List<ObservationItem> {
        val numbers = inferNumbers(ordered.map(::explicitNumber))
        return ordered.mapIndexedNotNull { i, ch ->
            numbers[i]?.let { buildChapterItem(baseUrl, lang, novel, ch, it) }
        }
    }

    private fun buildChapterItem(
        baseUrl: String,
        lang: String,
        novel: NovelEntity,
        chapter: ChapterEntity,
        number: Double,
    ): ObservationItem = ObservationItem(
        kind = "CHAPTER",
        platformDomain = domainOf(baseUrl),
        url = resolveUrl(baseUrl, chapter.url),
        seriesUrl = resolveUrl(baseUrl, novel.url),
        title = chapter.name.ifBlank { null },
        number = number,
        publishedAt = chapter.uploadDate.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() },
    )

    /**
     * The chapter number from the source field, else parsed from the chapter name
     * ("Chapter 37") or URL slug (".../chapter-37-..."). Rounded to 2 decimals (the
     * Float -> Double widening leaks noise and the backend caps at 2). Null when
     * nothing usable is found — [inferNumbers] may still fill it from neighbours.
     */
    private fun explicitNumber(chapter: ChapterEntity): Double? {
        val raw = when {
            chapter.chapterNumber >= 0f -> chapter.chapterNumber.toDouble()
            else -> parseNumber(chapter.name) ?: parseNumber(chapter.url) ?: return null
        }
        return round2(raw)
    }

    /**
     * Fill null entries by piecewise-linear interpolation between the known
     * (anchor) numbers, in list order. Extrapolates past the ends using the
     * overall step. With no anchors at all, falls back to 1-based position.
     */
    private fun inferNumbers(explicit: List<Double?>): List<Double?> {
        val anchors = explicit.indices.filter { explicit[it] != null }
        if (anchors.isEmpty()) return explicit.indices.map { (it + 1).toDouble() }

        val firstIdx = anchors.first()
        val lastIdx = anchors.last()
        val step = if (anchors.size >= 2) {
            val s = (explicit[lastIdx]!! - explicit[firstIdx]!!) / (lastIdx - firstIdx)
            if (s == 0.0) 1.0 else s
        } else {
            1.0
        }

        return explicit.indices.map { i ->
            explicit[i]?.let { return@map it }
            val prev = anchors.lastOrNull { it < i }
            val next = anchors.firstOrNull { it > i }
            val value = if (prev != null && next != null) {
                val pv = explicit[prev]!!
                pv + (explicit[next]!! - pv) * (i - prev).toDouble() / (next - prev)
            } else if (prev != null) {
                explicit[prev]!! + step * (i - prev)
            } else {
                // anchors is non-empty, so next is guaranteed here.
                explicit[next!!]!! - step * (next - i)
            }
            round2(value)
        }
    }

    private fun round2(v: Double): Double =
        BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toDouble()

    // "Chapter 37", "Ch. 37", "Episode 5", "chapter-37-...", "#37". First match wins.
    private val CHAPTER_NUMBER_RE =
        Regex("""(?:chapter|chap|episode|ep|part|ch|#)[\s._-]*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    private fun parseNumber(text: String): Double? =
        CHAPTER_NUMBER_RE.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun domainOf(baseUrl: String): String =
        URI(baseUrl).host?.lowercase().orEmpty().removePrefix("www.")

    // Mirrors HttpSource.resolveUrl: absolute as-is, else baseUrl + relative path.
    private fun resolveUrl(baseUrl: String, url: String): String =
        if (url.startsWith("http")) url else baseUrl.trimEnd('/') + "/" + url.trimStart('/')

    // NovelStatus names line up with the backend's SeriesStatus names.
    private fun novelStatus(ordinal: Int): String =
        NovelStatus.entries.getOrElse(ordinal) { NovelStatus.UNKNOWN }.name
}
