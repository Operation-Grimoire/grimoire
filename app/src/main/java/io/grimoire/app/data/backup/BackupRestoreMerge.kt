package io.grimoire.app.data.backup

import io.grimoire.app.data.book.LOCAL_SOURCE_ID
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity

internal fun mergeBackupNovel(novel: BackupNovel, existing: NovelEntity?, categoryId: Long?): NovelEntity? {
    // App backups carry local-book reading state, not the imported book text.
    if (novel.sourceId == LOCAL_SOURCE_ID && existing == null) return null
    return NovelEntity(
        id = existing?.id ?: 0,
        sourceId = novel.sourceId,
        url = novel.url,
        title = novel.title,
        thumbnailUrl = novel.thumbnailUrl ?: existing?.thumbnailUrl,
        author = novel.author ?: existing?.author,
        description = novel.description ?: existing?.description,
        genres = if (novel.genres.isNotEmpty()) novel.genres else (existing?.genres ?: ""),
        status = novel.status,
        favorite = novel.favorite || (existing?.favorite ?: false),
        lastUpdated = maxOf(novel.lastUpdated, existing?.lastUpdated ?: 0L),
        chapterSortOrder = novel.chapterSortOrder,
        categoryId = categoryId ?: existing?.categoryId,
        lastReadAt = maxOf(novel.lastReadAt, existing?.lastReadAt ?: 0L),
        rating = novel.rating ?: existing?.rating,
        ratingCount = novel.ratingCount ?: existing?.ratingCount,
        userRating = novel.userRating ?: existing?.userRating,
        notifyOnNewChapters = novel.notifyOnNewChapters || (existing?.notifyOnNewChapters ?: false),
        notifyOnNewLockedChapters = novel.notifyOnNewLockedChapters || (existing?.notifyOnNewLockedChapters ?: false),
        autoDownloadNewChapters = novel.autoDownloadNewChapters || (existing?.autoDownloadNewChapters ?: false),
        language = novel.language ?: existing?.language,
        readerTextAlign = if (novel.readerTextAlign != 0) novel.readerTextAlign
            else (existing?.readerTextAlign ?: 0),
        // Never in the backup (device-local file) — keep whatever this device has.
        customCoverPath = existing?.customCoverPath,
        customCoverUrl = novel.customCoverUrl ?: existing?.customCoverUrl,
        overrideTitle = novel.overrideTitle ?: existing?.overrideTitle,
        overrideAuthor = novel.overrideAuthor ?: existing?.overrideAuthor,
        overrideDescription = novel.overrideDescription ?: existing?.overrideDescription,
        overrideStatus = novel.overrideStatus ?: existing?.overrideStatus,
        overrideGenres = novel.overrideGenres ?: existing?.overrideGenres,
    )
}

internal fun mergeBackupChapter(c: BackupChapter, existingCh: ChapterEntity?, novelId: Long): ChapterEntity {
    val backupHasAnchor = c.readAnchorItemIndex != null && c.readAnchorItemOffset != null &&
        (c.readAnchorItemIndex != 0 || c.readAnchorItemOffset != 0)
    val localHasAnchor = existingCh != null &&
        (existingCh.readAnchorItemIndex != 0 || existingCh.readAnchorItemOffset != 0)
    val useBackupAnchor = backupHasAnchor && (existingCh == null ||
        c.readProgress > existingCh.readProgress ||
        (!localHasAnchor && c.readProgress >= existingCh.readProgress))
    return ChapterEntity(
        id = existingCh?.id ?: 0,
        novelId = novelId,
        url = c.url,
        name = c.name,
        uploadDate = c.uploadDate,
        chapterNumber = c.chapterNumber,
        translator = c.translator ?: existingCh?.translator,
        read = c.read || (existingCh?.read ?: false),
        readProgress = maxOf(c.readProgress, existingCh?.readProgress ?: 0f),
        readAnchorItemIndex = if (useBackupAnchor) c.readAnchorItemIndex!! else existingCh?.readAnchorItemIndex ?: 0,
        readAnchorItemOffset = if (useBackupAnchor) c.readAnchorItemOffset!! else existingCh?.readAnchorItemOffset ?: 0,
        locked = existingCh?.locked ?: c.locked ?: false,
        downloadStatus = existingCh?.downloadStatus ?: 0,
        queueOrder = existingCh?.queueOrder ?: 0L,
        firstReadAt = c.firstReadAt ?: existingCh?.firstReadAt,
        wordCount = if (c.wordCount > 0) c.wordCount else (existingCh?.wordCount ?: 0),
    )
}
