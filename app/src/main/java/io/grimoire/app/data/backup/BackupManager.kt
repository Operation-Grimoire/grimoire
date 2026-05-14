package io.grimoire.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.RepoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val fileName: String, val fileUri: String, val novelCount: Int) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val novelCount: Int, val chapterCount: Int) : RestoreResult()
    data class Failure(val message: String) : RestoreResult()
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val repoDao: RepoDao,
) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun backupTo(folderUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext BackupResult.Failure("Backup folder is not accessible")
        if (!folder.exists() || !folder.canWrite()) {
            return@withContext BackupResult.Failure("Backup folder is not writable")
        }
        runCatching {
            val payload = buildBackup()
            val fileName = generateFileName()
            val target = folder.createFile(BACKUP_MIME_TYPE, fileName)
                ?: return@withContext BackupResult.Failure("Could not create backup file")
            context.contentResolver.openOutputStream(target.uri)?.use { raw ->
                GZIPOutputStream(raw).use { gz ->
                    gz.write(json.encodeToString(BackupFile.serializer(), payload).toByteArray())
                }
            } ?: return@withContext BackupResult.Failure("Could not open output stream")
            BackupResult.Success(fileName, target.uri.toString(), payload.novels.size)
        }.getOrElse { e ->
            BackupResult.Failure(e.message ?: "Backup failed")
        }
    }

    suspend fun restoreFrom(fileUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(fileUri)?.use { raw ->
                val maybeGzip = runCatching { GZIPInputStream(raw).bufferedReader().readText() }
                if (maybeGzip.isSuccess) {
                    maybeGzip.getOrThrow()
                } else {
                    context.contentResolver.openInputStream(fileUri)!!.bufferedReader().readText()
                }
            } ?: return@withContext RestoreResult.Failure("Could not read backup file")

            val backup = json.decodeFromString(BackupFile.serializer(), text)
            if (backup.version > BackupFile.CURRENT_VERSION) {
                return@withContext RestoreResult.Failure(
                    "Backup file is from a newer app version (v${backup.version})"
                )
            }
            applyBackup(backup)
            RestoreResult.Success(backup.novels.size, backup.novels.sumOf { it.chapters.size })
        }.getOrElse { e ->
            RestoreResult.Failure(e.message ?: "Restore failed")
        }
    }

    private suspend fun buildBackup(): BackupFile {
        val novels = novelDao.getAll()
        val chapters = chapterDao.getAll().groupBy { it.novelId }
        val categories = categoryDao.getAllOnce()
        val categoryById = categories.associateBy { it.id }
        val repos = repoDao.getAllOnce()
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return BackupFile(
            createdAt = System.currentTimeMillis(),
            appVersionCode = info?.let {
                @Suppress("DEPRECATION")
                it.versionCode
            } ?: 0,
            appVersionName = info?.versionName.orEmpty(),
            novels = novels.map { n ->
                BackupNovel(
                    sourceId = n.sourceId,
                    url = n.url,
                    title = n.title,
                    thumbnailUrl = n.thumbnailUrl,
                    author = n.author,
                    description = n.description,
                    genres = n.genres,
                    status = n.status,
                    favorite = n.favorite,
                    lastUpdated = n.lastUpdated,
                    chapterSortOrder = n.chapterSortOrder,
                    categoryName = n.categoryId?.let { categoryById[it]?.name },
                    lastReadAt = n.lastReadAt,
                    rating = n.rating,
                    ratingCount = n.ratingCount,
                    chapters = (chapters[n.id] ?: emptyList()).map { c ->
                        BackupChapter(
                            url = c.url,
                            name = c.name,
                            uploadDate = c.uploadDate,
                            chapterNumber = c.chapterNumber,
                            translator = c.translator,
                            read = c.read,
                            readProgress = c.readProgress,
                            firstReadAt = c.firstReadAt,
                            wordCount = c.wordCount,
                            downloadedContent = c.downloadedContent,
                        )
                    },
                )
            },
            categories = categories.map { c ->
                BackupCategory(
                    name = c.name,
                    order = c.order,
                    isDefault = c.isDefault,
                    isHidden = c.isHidden,
                )
            },
            repos = repos.map { r ->
                BackupRepo(
                    name = r.name,
                    indexUrl = r.indexUrl,
                    enabled = r.enabled,
                    addedAt = r.addedAt,
                )
            },
        )
    }

    private suspend fun applyBackup(backup: BackupFile) {
        val existingCategories = categoryDao.getAllOnce().associateBy { it.name }
        val categoryIdByName = mutableMapOf<String, Long>()
        for (cat in backup.categories) {
            val existing = existingCategories[cat.name]
            val id = if (existing != null) {
                existing.id
            } else {
                categoryDao.upsert(
                    CategoryEntity(
                        name = cat.name,
                        order = cat.order,
                        isDefault = cat.isDefault,
                        isHidden = cat.isHidden,
                    )
                )
            }
            categoryIdByName[cat.name] = id
        }

        val existingRepos = repoDao.getAllOnce().associateBy { it.indexUrl }
        for (repo in backup.repos) {
            if (!existingRepos.containsKey(repo.indexUrl)) {
                repoDao.insert(
                    RepoEntity(
                        name = repo.name,
                        indexUrl = repo.indexUrl,
                        enabled = repo.enabled,
                        addedAt = repo.addedAt,
                    )
                )
            }
        }

        for (novel in backup.novels) {
            val existing = novelDao.getBySourceUrl(novel.sourceId, novel.url)
            val categoryId = novel.categoryName?.let { categoryIdByName[it] }
            val mergedNovel = NovelEntity(
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
            )
            val novelId = novelDao.upsert(mergedNovel)
            val effectiveId = if (existing != null && novelId == -1L) existing.id else novelId
            if (novel.chapters.isNotEmpty()) {
                val existingChapters = chapterDao.getChaptersOnce(effectiveId).associateBy { it.url }
                val merged = novel.chapters.map { c ->
                    val existingCh = existingChapters[c.url]
                    ChapterEntity(
                        id = existingCh?.id ?: 0,
                        novelId = effectiveId,
                        url = c.url,
                        name = c.name,
                        uploadDate = c.uploadDate,
                        chapterNumber = c.chapterNumber,
                        translator = c.translator ?: existingCh?.translator,
                        read = c.read || (existingCh?.read ?: false),
                        readProgress = maxOf(c.readProgress, existingCh?.readProgress ?: 0f),
                        downloadStatus = if (c.downloadedContent != null) 3 else (existingCh?.downloadStatus ?: 0),
                        downloadedContent = c.downloadedContent ?: existingCh?.downloadedContent,
                        queueOrder = existingCh?.queueOrder ?: 0L,
                        firstReadAt = c.firstReadAt ?: existingCh?.firstReadAt,
                        wordCount = if (c.wordCount > 0) c.wordCount else (existingCh?.wordCount ?: 0),
                    )
                }
                chapterDao.upsertAll(merged)
            }
        }
    }

    private fun generateFileName(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "grimoire_backup_$ts.json.gz"
    }

    companion object {
        const val BACKUP_MIME_TYPE = "application/gzip"
        const val BACKUP_PICK_MIME_TYPE = "*/*"
    }
}
