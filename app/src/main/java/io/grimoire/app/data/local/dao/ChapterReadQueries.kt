package io.grimoire.app.data.local.dao

internal const val CHAPTER_BY_NOVEL_URL_QUERY = """
    SELECT c.* FROM chapters c
    INNER JOIN novels n ON n.id = c.novelId
    WHERE n.sourceId = :sourceId AND n.url = :novelUrl AND c.url = :chapterUrl
    LIMIT 1
"""

internal const val CHAPTER_BY_NOVEL_ID_QUERY = """
    SELECT c.* FROM chapters c
    INNER JOIN novels n ON n.id = c.novelId
    WHERE n.id = :novelId AND n.url = :novelUrl AND c.url = :chapterUrl
    LIMIT 1
"""
