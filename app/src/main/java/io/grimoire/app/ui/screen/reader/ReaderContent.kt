package io.grimoire.app.ui.screen.reader

import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.app.util.imageUrl
import io.grimoire.app.util.isSeparator
import io.grimoire.app.util.text
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.ChapterOrigin
import io.grimoire.reading.LoadedChapter
import io.grimoire.reading.anchor.ProjectedPage
import io.grimoire.reading.anchor.ReadingProjection
import io.grimoire.reading.storage.ChapterStorageObservation
import java.util.Collections
import java.util.UUID

/** Atomically published load. Audio keeps its previous input; the screen uses the mapped body. */
internal class ReaderContent(
    val chapterId: Long, val projection: ReadingProjection, val pages: List<NovelPage>,
    val storageObservation: ChapterStorageObservation? = null,
) {
    // Stable lazy-item identity for this load; not a measured-layout or persistence/session token.
    val loadKey: String = UUID.randomUUID().toString()
}

internal fun androidReaderContent(
    chapterId: Long, key: ChapterKey?, loaded: LoadedChapter,
    localImage: (NovelPage) -> String? = { null },
): ReaderContent {
    val raw = loaded.pages.toList()
    val mapped = raw.mapIndexedNotNull { ordinal, page ->
        if (page.text.isBlank() && page.imageUrl == null && !page.isSeparator) return@mapIndexedNotNull null
        val rendered = if (loaded.origin == ChapterOrigin.SAVED && page.imageUrl != null) {
            localImage(page)?.let { page.copy(content = PageContent.Image(it)) } ?: page
        } else page
        ProjectedPage(ordinal, rendered)
    }
    val projection = ReadingProjection.create(key, raw, mapped, leadingStart = true, trailingEnd = true)
    val legacyPages = if (loaded.origin == ChapterOrigin.SAVED) mapped.map { it.page } else raw
    return ReaderContent(chapterId, projection, Collections.unmodifiableList(legacyPages), loaded.storageObservation)
}

/** Bind UI observations to the exact loaded projection, including reloads of the same chapter. */
internal fun captureReaderPosition(
    currentChapterId: Long?, content: ReaderContent?, observed: ReadingProjection,
    chapterId: Long, fraction: Float, index: Int, offset: Int,
): ReaderPosition? {
    if (!acceptsReaderProgress(currentChapterId, content?.chapterId, chapterId) || content?.projection !== observed) return null
    if (index !in 0 until observed.itemCount || offset < 0) return null
    return ReaderPosition(chapterId, fraction, index, offset, observed.capture(index))
}
