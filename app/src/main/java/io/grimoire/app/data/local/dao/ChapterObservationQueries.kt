package io.grimoire.app.data.local.dao

internal const val NOVEL_CHAPTER_STATS_QUERY = """
    SELECT novelId, COUNT(*) AS total, SUM(read) AS readCount,
           SUM(CASE WHEN downloadStatus IN (3, 5, 6, 7) THEN 1 ELSE 0 END) AS downloadedCount,
           SUM(CASE WHEN locked = 1 THEN 1 ELSE 0 END) AS lockedCount
    FROM chapters WHERE novelId = :novelId GROUP BY novelId
"""

internal const val ACTIVE_DOWNLOAD_COUNT_QUERY = """
    SELECT COUNT(*) FROM chapters ch
    INNER JOIN novels n ON n.id = ch.novelId
    LEFT JOIN categories c ON c.id = n.categoryId
    WHERE ch.downloadStatus IN (:statuses)
      AND (:excludeHidden = 0 OR IFNULL(c.isHidden, 0) = 0)
"""
