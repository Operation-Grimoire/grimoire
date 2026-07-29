package io.grimoire.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.local.dao.ChapterDao
import io.grimoire.app.data.local.dao.NovelDao
import io.grimoire.app.data.local.dao.NuBookmarkDao
import io.grimoire.app.data.local.dao.RepoDao
import io.grimoire.app.data.local.entity.CategoryEntity
import io.grimoire.app.data.local.entity.ChapterEntity
import io.grimoire.app.data.local.entity.NovelEntity
import io.grimoire.app.data.local.entity.NuBookmarkEntity
import io.grimoire.app.data.local.entity.RepoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import io.grimoire.app.R
import io.grimoire.app.util.AppLocale
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val fileName: String, val fileUri: String, val novelCount: Int) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val novelCount: Int, val chapterCount: Int) : RestoreResult()
    data class Failure(val message: String) : RestoreResult()
}

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val repoDao: RepoDao,
    private val nuBookmarkDao: NuBookmarkDao,
    private val dataStore: DataStore<Preferences>,
) {

    private val localizedContext = AppLocale.wrap(context)

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun backupTo(folderUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext BackupResult.Failure(localizedContext.getString(R.string.backup_folder_inaccessible))
        if (!folder.exists() || !folder.canWrite()) {
            return@withContext BackupResult.Failure(localizedContext.getString(R.string.backup_folder_not_writable))
        }
        val fileName = generateFileName()
        val target = folder.createFile(BACKUP_MIME_TYPE, fileName)
            ?: return@withContext BackupResult.Failure(localizedContext.getString(R.string.backup_create_file_failed))

        runCatching {
            val novels = novelDao.getForBackup()
            val categories = categoryDao.getAllOnce()
            val categoryById = categories.associateBy { it.id }
            val repos = repoDao.getAllOnce()
            val nuBookmarks = nuBookmarkDao.getAll().first()
            val preferences = dumpPreferences()
            val info = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            val appVersionCode = info?.let {
                @Suppress("DEPRECATION")
                it.versionCode
            } ?: 0
            val appVersionName = info?.versionName.orEmpty()

            context.contentResolver.openOutputStream(target.uri)?.use { raw ->
                GZIPOutputStream(BufferedOutputStream(raw)).use { gz ->
                    writeBackupStream(
                        out = gz,
                        novels = novels,
                        categories = categories,
                        categoryById = categoryById,
                        repos = repos,
                        nuBookmarks = nuBookmarks,
                        preferences = preferences,
                        appVersionCode = appVersionCode,
                        appVersionName = appVersionName,
                    )
                }
            } ?: return@runCatching BackupResult.Failure(localizedContext.getString(R.string.backup_open_output_failed))
            BackupResult.Success(fileName, target.uri.toString(), novels.size)
        }.getOrElse { e ->
            runCatching { target.delete() }
            BackupResult.Failure(e.message ?: localizedContext.getString(R.string.backup_failed))
        }
    }

    suspend fun restoreFrom(fileUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        runCatching {
            val backup = context.contentResolver.openInputStream(fileUri)?.use { raw ->
                val buffered = BufferedInputStream(raw)
                buffered.mark(2)
                val b1 = buffered.read()
                val b2 = buffered.read()
                buffered.reset()
                val isGzip = b1 == 0x1f && b2 == 0x8b
                if (isGzip) {
                    GZIPInputStream(buffered).use { json.decodeFromStream(BackupFile.serializer(), it) }
                } else {
                    json.decodeFromStream(BackupFile.serializer(), buffered)
                }
            } ?: return@withContext RestoreResult.Failure(localizedContext.getString(R.string.backup_read_file_failed))

            if (backup.version > BackupFile.CURRENT_VERSION) {
                return@withContext RestoreResult.Failure(
                    localizedContext.getString(R.string.backup_newer_version, backup.version)
                )
            }
            applyBackup(backup)
            RestoreResult.Success(backup.novels.size, backup.novels.sumOf { it.chapters.size })
        }.getOrElse { e ->
            RestoreResult.Failure(e.message ?: localizedContext.getString(R.string.restore_failed))
        }
    }

    private suspend fun writeBackupStream(
        out: OutputStream,
        novels: List<NovelEntity>,
        categories: List<CategoryEntity>,
        categoryById: Map<Long, CategoryEntity>,
        repos: List<RepoEntity>,
        nuBookmarks: List<NuBookmarkEntity>,
        preferences: List<BackupPreference>,
        appVersionCode: Int,
        appVersionName: String,
    ) {
        // Stream the JSON manually so we never hold all chapters in memory at once.
        out.writeUtf8("{")
        out.writeUtf8("\"version\":${BackupFile.CURRENT_VERSION},")
        out.writeUtf8("\"createdAt\":${System.currentTimeMillis()},")
        out.writeUtf8("\"appVersionCode\":$appVersionCode,")
        out.writeUtf8("\"appVersionName\":")
        json.encodeToStream(String.serializer(), appVersionName, out)
        out.writeUtf8(",\"categories\":")
        json.encodeToStream(
            ListSerializer(BackupCategory.serializer()),
            categories.map { BackupCategory(it.name, it.order, it.isDefault, it.isHidden) },
            out,
        )
        out.writeUtf8(",\"repos\":")
        json.encodeToStream(
            ListSerializer(BackupRepo.serializer()),
            repos.map { BackupRepo(it.name, it.indexUrl, it.enabled, it.addedAt) },
            out,
        )
        out.writeUtf8(",\"nuBookmarks\":")
        json.encodeToStream(
            ListSerializer(BackupNuBookmark.serializer()),
            nuBookmarks.map { BackupNuBookmark(it.slug, it.url, it.title, it.coverUrl, it.addedAt) },
            out,
        )
        out.writeUtf8(",\"preferences\":")
        json.encodeToStream(ListSerializer(BackupPreference.serializer()), preferences, out)
        out.writeUtf8(",\"novels\":[")
        var first = true
        for (n in novels) {
            if (!first) out.writeUtf8(",")
            first = false
            // Only chapters with restorable state — untouched ones are re-scraped on open.
            val chapters = chapterDao.getBackupChaptersOnce(n.id)
            // Build one novel at a time and stream it, then let the GC reclaim chapters.
            val backupNovel = BackupNovel(
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
                userRating = n.userRating,
                notifyOnNewChapters = n.notifyOnNewChapters,
                notifyOnNewLockedChapters = n.notifyOnNewLockedChapters,
                autoDownloadNewChapters = n.autoDownloadNewChapters,
                language = n.language,
                chapters = chapters.map { c ->
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
                    )
                },
            )
            json.encodeToStream(BackupNovel.serializer(), backupNovel, out)
        }
        out.writeUtf8("]}")
    }

    /**
     * Snapshot every DataStore preference, tagged by runtime value type.
     * Credential-bearing keys are excluded: a backup is plaintext JSON the
     * user may park in shared storage or a cloud drive. Old backups that do
     * contain such a key still restore it ([restorePreferences] stays
     * permissive) — the exclusion only stops new exports from leaking.
     */
    private suspend fun dumpPreferences(): List<BackupPreference> =
        dataStore.data.first().asMap().mapNotNull { (key, value) ->
            if (key.name in SENSITIVE_PREF_KEYS) return@mapNotNull null
            when (value) {
                is Boolean -> BackupPreference(key.name, "b", value.toString())
                is Int -> BackupPreference(key.name, "i", value.toString())
                is Long -> BackupPreference(key.name, "l", value.toString())
                is Float -> BackupPreference(key.name, "f", value.toString())
                is Double -> BackupPreference(key.name, "d", value.toString())
                is String -> BackupPreference(key.name, "s", value)
                is Set<*> -> BackupPreference(key.name, "ss", stringSet = value.map { it.toString() })
                else -> null // e.g. ByteArray — not used by app prefs, skip
            }
        }

    /** Overwrite stored preferences with the backed-up values (backup wins). */
    private suspend fun restorePreferences(preferences: List<BackupPreference>) {
        if (preferences.isEmpty()) return
        dataStore.edit { prefs ->
            for (p in preferences) {
                when (p.type) {
                    "b" -> p.value?.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(p.key)] = it }
                    "i" -> p.value?.toIntOrNull()?.let { prefs[intPreferencesKey(p.key)] = it }
                    "l" -> p.value?.toLongOrNull()?.let { prefs[longPreferencesKey(p.key)] = it }
                    "f" -> p.value?.toFloatOrNull()?.let { prefs[floatPreferencesKey(p.key)] = it }
                    "d" -> p.value?.toDoubleOrNull()?.let { prefs[doublePreferencesKey(p.key)] = it }
                    "s" -> p.value?.let { prefs[stringPreferencesKey(p.key)] = it }
                    "ss" -> p.stringSet?.let { prefs[stringSetPreferencesKey(p.key)] = it.toSet() }
                }
            }
        }
    }

    private suspend fun applyBackup(backup: BackupFile) {
        restorePreferences(backup.preferences)

        val existingCategories = categoryDao.getAllOnce()
        val existingByName = existingCategories.associateBy { it.name }
        val existingDefault = existingCategories.firstOrNull { it.isDefault }
        val categoryIdByName = mutableMapOf<String, Long>()
        for (cat in backup.categories) {
            val existing = existingByName[cat.name]
            val id = when {
                existing != null -> existing.id
                // The app always has exactly one default category. Fold the backup's
                // default into the one already present (carrying over its name/order)
                // instead of inserting a second default row.
                cat.isDefault && existingDefault != null -> {
                    categoryDao.upsert(
                        existingDefault.copy(
                            name = cat.name,
                            order = cat.order,
                            isHidden = cat.isHidden,
                        )
                    )
                    existingDefault.id
                }
                else -> categoryDao.upsert(
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

        for (bookmark in backup.nuBookmarks) {
            nuBookmarkDao.upsert(
                NuBookmarkEntity(
                    slug = bookmark.slug,
                    url = bookmark.url,
                    title = bookmark.title,
                    coverUrl = bookmark.coverUrl,
                    addedAt = bookmark.addedAt,
                )
            )
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
                userRating = novel.userRating ?: existing?.userRating,
                notifyOnNewChapters = novel.notifyOnNewChapters || (existing?.notifyOnNewChapters ?: false),
                notifyOnNewLockedChapters = novel.notifyOnNewLockedChapters || (existing?.notifyOnNewLockedChapters ?: false),
                autoDownloadNewChapters = novel.autoDownloadNewChapters || (existing?.autoDownloadNewChapters ?: false),
                language = novel.language ?: existing?.language,
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
                        downloadStatus = existingCh?.downloadStatus ?: 0,
                        downloadedContent = existingCh?.downloadedContent,
                        queueOrder = existingCh?.queueOrder ?: 0L,
                        firstReadAt = c.firstReadAt ?: existingCh?.firstReadAt,
                        wordCount = if (c.wordCount > 0) c.wordCount else (existingCh?.wordCount ?: 0),
                    )
                }
                chapterDao.upsertAll(merged)
            }
        }
    }

    private fun OutputStream.writeUtf8(s: String) = write(s.toByteArray(Charsets.UTF_8))

    private fun generateFileName(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        return "grimoire_backup_$ts.json.gz"
    }

    companion object {
        const val BACKUP_MIME_TYPE = "application/gzip"

        /** Preference keys holding credentials/secrets — never exported (see [dumpPreferences]). */
        private val SENSITIVE_PREF_KEYS = setOf("tts_elevenlabs_api_key")
    }
}
