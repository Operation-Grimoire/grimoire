package io.grimoire.app.data.local.dao

internal const val CHAPTER_BY_NOVEL_URL_QUERY = """
    SELECT c.* FROM chapters c
    INNER JOIN novels n ON n.id = c.novelId
    WHERE n.sourceId = :sourceId AND n.url = :novelUrl AND c.url = :chapterUrl
    LIMIT 1
"""

/**
 * Chapter lookup by stored novel identity. The row id alone scopes the chapter: novel
 * ids are AUTOINCREMENT and never reused, so the novel's URL is deliberately not
 * re-checked here. Callers' novel URLs can drift from the stored key (sources return
 * a canonicalised/absolute URL from getNovelDetails), and matching on it made the
 * reader fail with "Chapter not found in database".
 */
internal const val CHAPTER_BY_NOVEL_ID_QUERY = """
    SELECT * FROM chapters WHERE novelId = :novelId AND url = :url
    LIMIT 1
"""
