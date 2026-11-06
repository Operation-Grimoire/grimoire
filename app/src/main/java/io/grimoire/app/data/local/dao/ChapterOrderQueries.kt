package io.grimoire.app.data.local.dao

// Some sources omit chapter numbers. Ties must not follow an index containing
// mutable read/download state, otherwise a state update changes reading order.
internal const val CHAPTERS_IN_READING_ORDER_QUERY =
    "SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC, id ASC"

internal const val CHAPTER_METADATA_IN_READING_ORDER_QUERY =
    "SELECT id, novelId, url, name, uploadDate, chapterNumber, translator, read, " +
        "readProgress, readAnchorItemIndex, readAnchorItemOffset, downloadStatus, queueOrder, " +
        "firstReadAt, wordCount, locked FROM chapters WHERE novelId = :novelId ORDER BY chapterNumber ASC, id ASC"
