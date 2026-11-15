-- Frozen DB33 schema from signed beta.347; run43687721590 upgraded snapshot.
-- Source680ec51; no user data. Used for DB33-to-34 migration and signed compatibility.
CREATE TABLE `browsing_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourcePackage` TEXT NOT NULL, `novelId` INTEGER, `novelUrl` TEXT NOT NULL, `novelTitle` TEXT NOT NULL, `novelThumbnailUrl` TEXT, `openedAt` INTEGER NOT NULL);
CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `order` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL);
CREATE TABLE `chapter_content` (`chapterId` INTEGER NOT NULL, `content` TEXT NOT NULL, contentVersion INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`chapterId`), FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE TABLE `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novelId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `uploadDate` INTEGER NOT NULL, `chapterNumber` REAL NOT NULL, `translator` TEXT, `read` INTEGER NOT NULL, `readProgress` REAL NOT NULL, `readAnchorItemIndex` INTEGER NOT NULL, `readAnchorItemOffset` INTEGER NOT NULL, `downloadStatus` INTEGER NOT NULL, `queueOrder` INTEGER NOT NULL, `firstReadAt` INTEGER, `wordCount` INTEGER NOT NULL, `locked` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE TABLE `library_updates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novelId` INTEGER NOT NULL, `sourcePackage` TEXT NOT NULL, `novelUrl` TEXT NOT NULL, `novelTitle` TEXT NOT NULL, `novelThumbnailUrl` TEXT, `chapterUrl` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `chapterNumber` REAL NOT NULL, `foundAt` INTEGER NOT NULL, `locked` INTEGER NOT NULL, `unlockedFromLocked` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE TABLE `novels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `author` TEXT, `description` TEXT, `genres` TEXT NOT NULL, `status` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `chapterSortOrder` INTEGER NOT NULL, `categoryId` INTEGER, `lastReadAt` INTEGER NOT NULL, `lastAccessedAt` INTEGER NOT NULL, `rating` REAL, `ratingCount` INTEGER, `userRating` INTEGER, `language` TEXT, `readerTextAlign` INTEGER NOT NULL, `notifyOnNewChapters` INTEGER NOT NULL, `notifyOnNewLockedChapters` INTEGER NOT NULL, `autoDownloadNewChapters` INTEGER NOT NULL, `customCoverPath` TEXT, `customCoverUrl` TEXT, `overrideTitle` TEXT, `overrideAuthor` TEXT, `overrideDescription` TEXT, `overrideStatus` INTEGER, `overrideGenres` TEXT);
CREATE TABLE `nu_bookmarks` (`slug` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `coverUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`slug`));
CREATE TABLE `reading_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourcePackage` TEXT NOT NULL, `novelId` INTEGER, `novelUrl` TEXT NOT NULL, `novelTitle` TEXT NOT NULL, `novelThumbnailUrl` TEXT, `chapterUrl` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `chapterNumber` REAL NOT NULL, `openedAt` INTEGER NOT NULL);
CREATE TABLE `repos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `indexUrl` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL);
CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
CREATE TABLE `task_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `success` INTEGER NOT NULL, `summary` TEXT NOT NULL);
CREATE TABLE `update_issues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `novelId` INTEGER NOT NULL, `sourcePackage` TEXT NOT NULL, `novelUrl` TEXT NOT NULL, `novelTitle` TEXT NOT NULL, `severity` INTEGER NOT NULL, `message` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE UNIQUE INDEX `index_browsing_history_sourcePackage_novelUrl` ON `browsing_history` (`sourcePackage`, `novelUrl`);
CREATE INDEX `index_chapters_downloadStatus` ON `chapters` (`downloadStatus`);
CREATE INDEX `index_chapters_novelId` ON `chapters` (`novelId`);
CREATE INDEX `index_chapters_novelId_read_downloadStatus_locked` ON `chapters` (`novelId`, `read`, `downloadStatus`, `locked`);
CREATE INDEX `index_library_updates_novelId` ON `library_updates` (`novelId`);
CREATE INDEX `index_novels_favorite` ON `novels` (`favorite`);
CREATE UNIQUE INDEX `index_novels_sourceId_url` ON `novels` (`sourceId`, `url`);
CREATE UNIQUE INDEX `index_reading_history_sourcePackage_novelUrl_chapterUrl` ON `reading_history` (`sourcePackage`, `novelUrl`, `chapterUrl`);
CREATE UNIQUE INDEX `index_repos_indexUrl` ON `repos` (`indexUrl`);
CREATE UNIQUE INDEX `index_update_issues_novelId` ON `update_issues` (`novelId`);
INSERT INTO room_master_table (id,identity_hash) VALUES (42,'af03604692f0d6032635caa46a5b4683');
PRAGMA user_version=33;
