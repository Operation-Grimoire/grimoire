package io.grimoire.app.data.epub

/**
 * Reserved source identity for novels imported from a local EPUB file. These
 * novels have no backing extension: their chapter text is fully extracted at
 * import time into [io.grimoire.app.data.local.entity.ChapterEntity.downloadedContent],
 * so the reader never needs a runtime [io.grimoire.api.source.Source].
 *
 * [LOCAL_SOURCE_ID] is 0 because no real extension reports that source id, and
 * [LOCAL_PKG] is the sentinel navigation `pkg` argument the detail/reader
 * screens use to recognise a local novel.
 */
const val LOCAL_SOURCE_ID: Long = 0L

const val LOCAL_PKG: String = "local"
